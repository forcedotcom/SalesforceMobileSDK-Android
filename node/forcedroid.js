#!/usr/bin/env node

var exec = require('child_process').exec;
var path = require('path');
var shellJs = require('shelljs');
var fs = require('fs');
var commandLineUtils = require('./commandLineUtils');

var sdkDir = path.resolve(__dirname, '..');

var commandLineArgs = process.argv.slice(2, process.argv.length);
var command = commandLineArgs.shift();
if (typeof command !== 'string') {
    usage();
    process.exit(1);
}

var commandLineArgsMap = commandLineUtils.parseArgs(commandLineArgs);
if (commandLineArgsMap === null) {
    console.log('Command line args could not be processed.');
    usage();
    process.exit(2);
}

switch  (command) {
    case 'create':
      createApp();
      break;
    default:
      console.log('Unknown option: \'' + command + '\'.');
      usage();
      process.exit(2);
}

function usage() {
    console.log('Usage:');
    console.log('forcedroid create');
    console.log('    --apptype=<Application Type> (native, hybrid_remote, hybrid_local)');
    console.log('    --appname=<Application Name>');
    console.log('    --targetdir=<Target App Folder>');
    console.log('    --packagename=<App Package Identifier> (com.myCompany.myApp)');
    console.log('    --apexpage=<Path to Apex start page> (/apex/MyPage — Only required for \'hybrid_remote\'');
    console.log('    [--usesmartstore=<Whether or not to use SmartStore> (--usesmartstore=true — false by default)]');
}

function createApp() {
    var argsAreValid = validateCreateAppArgs();
    if (!argsAreValid) {
        console.log('Args for creating an app are not valid.');
        usage();
        process.exit(3);
    }

    var appInputProperties = configureInputAppProperties();

    // Copy the template files to the destination directory.
    shellJs.mkdir('-p', commandLineArgsMap.targetdir);
    shellJs.cp('-R', path.join(appInputProperties.templateDir, '*'), commandLineArgsMap.targetdir);
    shellJs.cp(path.join(appInputProperties.templateDir, '.project'), commandLineArgsMap.targetdir);  // No clean way to glob .project in.
    if (shellJs.error()) {
        console.log('There was an error copying the template files from \'' + appInputProperties.templateDir + '\' to \'' + commandLineArgsMap.targetdir + '\': ' + shellJs.error());
        process.exit(4);
    }

    // Copy the SDK into the app folder as well.
    shellJs.cp('-R', sdkDir, commandLineArgsMap.targetdir);
    if (shellJs.error()) {
        console.log('There was an error copying the SDK directory from \'' + sdkDir + '\' to \'' + commandLineArgsMap.targetdir + '\': ' + shellJs.error());
        process.exit(5);
    }
    var newSdkDir = path.join(commandLineArgsMap.targetdir, path.basename(sdkDir));

    var contentFilesWithReplacements = makeContentReplacementPathsArray(appInputProperties);

    // Library project reference
    console.log('Adjusting SalesforceSDK library project reference.');
    var absNativeSdkPath = path.join(newSdkDir, 'native', 'SalesforceSDK');
    var nativeSdkPathRelativeToProject = path.relative(commandLineArgsMap.targetdir, absNativeSdkPath);
    shellJs.sed('-i', /=.*SalesforceSDK/g, '=' + nativeSdkPathRelativeToProject, path.join(commandLineArgsMap.targetdir, 'project.properties'));

    // Substitute app class name
    var appClassName = commandLineArgsMap.appname + 'App';
    console.log('Renaming application class to ' + appClassName + '.');
    contentFilesWithReplacements.forEach(function(file) {
        shellJs.sed('-i', appInputProperties.templateAppClassName, appClassName, file);
    });

    // Substitute app name
    console.log('Renaming application to ' + commandLineArgsMap.appname + '.');
    contentFilesWithReplacements.forEach(function(file) {
        shellJs.sed('-i', appInputProperties.templateAppName, commandLineArgsMap.appname, file);
    });

    // Substitute package name.
    console.log('Renaming package name to ' + commandLineArgsMap.packagename + '.');
    contentFilesWithReplacements.forEach(function(file) {
        shellJs.sed('-i', appInputProperties.templatePackageName, commandLineArgsMap.packagename, file);
    });

    // Rename source package folders.
    console.log('Moving source files.')
    var srcFilePaths = getTemplateSourceFilePaths(appInputProperties);
    srcFilePaths.forEach(function(srcFile) {
        fs.renameSync(srcFile.path, path.join(commandLineArgsMap.targetdir, 'src', srcFile.name));
    });
    shellJs.rm('-rf', path.join(commandLineArgsMap.targetdir, 'src', 'com'));
    var packageDir = commandLineArgsMap.packagename.replace(/\./g, path.sep);
    shellJs.mkdir('-p', path.resolve(commandLineArgsMap.targetdir, 'src', packageDir));
    srcFilePaths.forEach(function(srcFile) {
        fs.renameSync(path.join(commandLineArgsMap.targetdir, 'src', srcFile.name), path.resolve(commandLineArgsMap.targetdir, 'src', packageDir, srcFile.name));
    });

    // Rename the app class name.
    console.log('Renaming the app class filename to ' + appClassName + '.java.');
    var templateAppClassNamePath = path.join(commandLineArgsMap.targetdir, 'src', packageDir);
    fs.renameSync(path.join(templateAppClassNamePath, appInputProperties.templateAppClassName) + '.java', path.join(templateAppClassNamePath, appClassName) + '.java');
}

function validateCreateAppArgs() {
    // Check required args.
    var allRequiredArgs = commandLineUtils.requiredArgsPresent(commandLineArgsMap, [ 'apptype', 'appname', 'targetdir', 'packagename' ]);
    if (!allRequiredArgs) return false;

    // App type
    var appType = commandLineArgsMap.apptype;
    if (appType !== 'native' && appType !== 'hybrid_remote' && appType !== 'hybrid_local') {
        console.log('Unrecognized app type: ' + appType);
        return false;
    }
    if (appType === 'hybrid_remote') {
        var apexPagePresent = commandLineUtils.requiredArgsPresent(commandLineArgsMap, [ 'apexpage' ]);
        if (!apexPagePresent)
            return false;
    }

    // Package name
    var validPackageRegExp = /^[a-z]+[a-z0-9_]*(\.[a-z]+[a-z0-9_]*)*$/;
    if (!validPackageRegExp.test(commandLineArgsMap.packagename)) {
        console.log('\'' + commandLineArgsMap.packagename + '\' is not a valid package name.');
        return false;
    }

    // Convert usesmartstore, if present.
    if (typeof commandLineArgsMap.usesmartstore !== 'undefined') {
        var trimmedVal = commandLineArgsMap.usesmartstore.trim();
        commandLineArgsMap.usesmartstore = (trimmedVal === 'true');
    }

    // Full path for targetdir.
    commandLineArgsMap.targetdir = path.resolve(commandLineArgsMap.targetdir);

    return true;
}

function configureInputAppProperties() {
    var inputProperties = {};
    switch (commandLineArgsMap.apptype) {
        case 'native':
            inputProperties.templateDir = path.join(sdkDir, 'native/TemplateApp');
            inputProperties.templateAppName = 'Template';
            inputProperties.templatePackageName = 'com.salesforce.samples.templateapp';
            inputProperties.bootConfigPath = path.join(commandLineArgsMap.targetdir, 'res', 'values', 'bootconfig.xml');
            break;
        case 'hybrid_local':
            inputProperties.templateDir = path.join(sdkDir, 'hybrid/SampleApps/ContactExplorer');
            inputProperties.templateAppName = 'ContactExplorer';
            inputProperties.templatePackageName = 'com.salesforce.samples.contactexplorer';
            inputProperties.bootConfigPath = path.join(commandLineArgsMap.targetdir, 'assets', 'www', 'bootconfig.json');
            break;
        case 'hybrid_remote':
            inputProperties.templateDir = path.join(sdkDir, 'hybrid/SampleApps/VFConnector');
            inputProperties.templateAppName = 'VFConnector';
            inputProperties.templatePackageName = 'com.salesforce.samples.vfconnector';
            inputProperties.bootConfigPath = path.join(commandLineArgsMap.targetdir, 'assets', 'www', 'bootconfig.json');
            break;
    }

    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);

    return inputProperties;
}

function makeContentReplacementPathsArray(appInputProperties) {
    var returnArray = [];
    returnArray.push(path.join(commandLineArgsMap.targetdir, 'AndroidManifest.xml'));
    returnArray.push(path.join(commandLineArgsMap.targetdir, '.project'));
    returnArray.push(path.join(commandLineArgsMap.targetdir, 'build.xml'));
    returnArray.push(path.join(commandLineArgsMap.targetdir, 'res', 'values', 'strings.xml'));
    returnArray.push(appInputProperties.bootConfigPath);
    var srcFilePaths = getTemplateSourceFilePaths(appInputProperties);
    srcFilePaths.forEach(function(srcFile) {
        returnArray.push(srcFile.path);
    });

    return returnArray;
}

function getTemplateSourceFilePaths(appInputProperties) {
    var srcFilesArray = [];
    var srcDir = path.join(commandLineArgsMap.targetdir, 'src', appInputProperties.templatePackageDir);
    fs.readdirSync(srcDir).forEach(function(srcFile) {
        if (/\.java$/.test(srcFile))
            srcFilesArray.push({ 'name': srcFile, 'path': path.join(srcDir, srcFile) });
    });

    return srcFilesArray;
}