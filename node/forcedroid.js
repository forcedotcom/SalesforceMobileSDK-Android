#!/usr/bin/env node

/*
 * Copyright (c) 2013, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

 // node.js application for working with projects that use the Salesforce Mobile
 // SDK for Android.  Currently supports the creation of new apps from different
 // app templates.

var exec = require('child_process').exec;
var path = require('path');
var shellJs = require('shelljs');
var fs = require('fs');
var commandLineUtils = require('./commandLineUtils');

var outputColors = {
    'green': '\x1b[32;1m',
    'yellow': '\x1b[33;1m',
    'magenta': '\x1b[35;1m',
    'cyan': '\x1b[36;1m',
    'reset': '\x1b[0m'
}

var packageSdkRootDir = path.resolve(__dirname, '..');

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
    console.log('    --packagename=<App Package Identifier> (com.my_company.my_app)');
    console.log('    --apexpage=<Path to Apex start page> (/apex/MyPage — Only required/used for \'hybrid_remote\'');
    console.log('    [--usesmartstore=<Whether or not to use SmartStore> (--usesmartstore=true — false by default)]');
}

function createApp() {
    var argsAreValid = validateCreateAppArgs();
    if (!argsAreValid) {
        console.log('Args for creating an app are not valid.');
        usage();
        process.exit(3);
    }

    // The destination project directory, in the target directory.
    var projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);

    var appInputProperties = configureInputAppProperties(projectDir);

    // Copy the template files to the destination directory.
    shellJs.mkdir('-p', projectDir);
    shellJs.cp('-R', path.join(appInputProperties.templateDir, '*'), projectDir);
    shellJs.cp(path.join(appInputProperties.templateDir, '.project'), projectDir);
    shellJs.cp(path.join(appInputProperties.templateDir, '.classpath'), projectDir);
    if (shellJs.error()) {
        console.log('There was an error copying the template files from \'' + appInputProperties.templateDir + '\' to \'' + projectDir + '\': ' + shellJs.error());
        process.exit(4);
    }

    // Copy the SDK into the app folder as well.
    shellJs.cp('-R', packageSdkRootDir, commandLineArgsMap.targetdir);
    if (shellJs.error()) {
        console.log('There was an error copying the SDK directory from \'' + packageSdkRootDir + '\' to \'' + commandLineArgsMap.targetdir + '\': ' + shellJs.error());
        process.exit(5);
    }
    var destSdkDir = path.join(commandLineArgsMap.targetdir, path.basename(packageSdkRootDir));

    var contentFilesWithReplacements = makeContentReplacementPathsArray(appInputProperties, projectDir);

    // Library project reference
    console.log(outputColors.yellow + 'Adjusting SalesforceSDK library project reference in project.properties.');
    var absNativeSdkPath = path.join(destSdkDir, 'native', 'SalesforceSDK');
    var nativeSdkPathRelativeToProject = path.relative(projectDir, absNativeSdkPath);
    var projectPropertiesFilePath = path.join(projectDir, 'project.properties');
    shellJs.sed('-i', /=.*SalesforceSDK/g, '=' + nativeSdkPathRelativeToProject, projectPropertiesFilePath);

    // Substitute app class name
    var appClassName = commandLineArgsMap.appname + 'App';
    console.log('Renaming application class to ' + appClassName + ' in source.');
    contentFilesWithReplacements.forEach(function(file) {
        var templateAppClassNameRegExp = new RegExp(appInputProperties.templateAppClassName, 'g');
        shellJs.sed('-i', templateAppClassNameRegExp, appClassName, file);
    });

    // Substitute app name
    console.log('Renaming application to ' + commandLineArgsMap.appname + ' in source.');
    contentFilesWithReplacements.forEach(function(file) {
        var templateAppNameRegExp = new RegExp(appInputProperties.templateAppName, 'g');
        shellJs.sed('-i', templateAppNameRegExp, commandLineArgsMap.appname, file);
    });

    // Substitute package name.
    console.log('Renaming package name to ' + commandLineArgsMap.packagename + ' in source.');
    contentFilesWithReplacements.forEach(function(file) {
        var templatePackageNameRegExp = new RegExp(appInputProperties.templatePackageName.replace(/\./g, '\\.'), 'g');
        shellJs.sed('-i', templatePackageNameRegExp, commandLineArgsMap.packagename, file);
    });

    // Rename source package folders.
    console.log('Moving source files to proper package path.')
    var srcFilePaths = getTemplateSourceFilePaths(appInputProperties, projectDir);
    srcFilePaths.forEach(function(srcFile) {
        fs.renameSync(srcFile.path, path.join(projectDir, 'src', srcFile.name));  // Temporary location
    });
    shellJs.rm('-rf', path.join(projectDir, 'src', 'com'));
    var packageDir = commandLineArgsMap.packagename.replace(/\./g, path.sep);
    shellJs.mkdir('-p', path.resolve(projectDir, 'src', packageDir));
    srcFilePaths.forEach(function(srcFile) {
        fs.renameSync(path.join(projectDir, 'src', srcFile.name), path.resolve(projectDir, 'src', packageDir, srcFile.name));
    });

    // Rename the app class name.
    console.log('Renaming the app class filename to ' + appClassName + '.java.');
    var appClassDir = path.join(projectDir, 'src', packageDir);
    var templateAppClassPath = path.join(appClassDir, appInputProperties.templateAppClassName) + '.java';
    var appClassPath = path.join(appClassDir, appClassName) + '.java';
    fs.renameSync(templateAppClassPath, appClassPath);

    // If SmartStore is configured, set it up.
    if (commandLineArgsMap.usesmartstore) {
        console.log('Adding SmartStore support.');
        shellJs.mkdir('-p', path.join(projectDir, 'assets'));  // May not exist for native.
        shellJs.cp(path.join(packageSdkRootDir, 'external', 'sqlcipher', 'assets', 'icudt46l.zip'), path.join(projectDir, 'assets', 'icudt46l.zip'));

        console.log('Adding SmartStore library reference in project.properties.');
        var projectPropertiesContent = shellJs.cat(projectPropertiesFilePath);
        var smartStoreAbsPath = path.join(destSdkDir, 'hybrid', 'SmartStore');
        var smartStorePathRelativeToProject = path.relative(projectDir, smartStoreAbsPath);
        var smartStoreProjectPropertyContent = 'android.library.reference.2=' + smartStorePathRelativeToProject + '\n';
        projectPropertiesContent = projectPropertiesContent + smartStoreProjectPropertyContent;
        projectPropertiesContent.to(projectPropertiesFilePath);

        console.log('Extending SalesforceSDKManagerWithSmartStore instead of SalesforceSDKManager.');
        shellJs.sed('-i', /SalesforceSDKManager/g, 'SalesforceSDKManagerWithSmartStore', appClassPath);
        shellJs.sed('-i',
            /com\.salesforce\.androidsdk\.app\.SalesforceSDKManagerWithSmartStore/g,
            'com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore',
            appClassPath);
    }

    // If it's a hybrid remote app, replace the Apex page.
    if (commandLineArgsMap.apptype === 'hybrid_remote') {
        console.log('Changing Visualforce page reference in ' + appInputProperties.bootConfigPath + '.');
        var templateVfPageRegExp = /\/apex\/BasicVFPage/g;
        shellJs.sed('-i', templateVfPageRegExp, commandLineArgsMap.apexpage, appInputProperties.bootConfigPath);
    }

    // Inform the user of next steps.
    var sdkLibrariesToIncludeMsg = 'the ' + outputColors.magenta + path.join(path.basename(destSdkDir), 'native', 'SalesforceSDK')
        + outputColors.reset + ' library project';
    if (commandLineArgsMap.usesmartstore) {
        sdkLibrariesToIncludeMsg += ', the ' + outputColors.magenta + path.join(path.basename(destSdkDir), 'hybrid', 'SmartStore')
        + outputColors.reset + ' library project';
    }
    var nextStepsOutput =
        ['',
         outputColors.green + 'Your application project is ready in ' + commandLineArgsMap.targetdir + '.',
         '',
         outputColors.cyan + 'To build the new application, do the following:' + outputColors.reset,
         '   - cd ' + projectDir,
         '   - $ANDROID_SDK_DIR/android update project -p .',
         '   - ant clean debug',
         '',
         outputColors.cyan + 'To run the application, start an emulator or plug in your device and run:' + outputColors.reset,
         '   - ant installd',
         '',
         outputColors.cyan + 'To use your new application in Eclipse, do the following:' + outputColors.reset,
         '   - Import ' + sdkLibrariesToIncludeMsg + ',',
         '     and the ' + outputColors.magenta + commandLineArgsMap.appname + outputColors.reset + ' project into your workspace',
         '   - Choose \'Build All\' from the Project menu',
         '   - Run your application by choosing "Run as Android application"',
         ''].join('\n');
    console.log(nextStepsOutput);
    var relativeBootConfigPath = path.relative(commandLineArgsMap.targetdir, appInputProperties.bootConfigPath);
    console.log(outputColors.cyan + 'Before you ship, make sure to plug your OAuth Client ID,\nCallback URI, and OAuth Scopes into '
        + outputColors.magenta + relativeBootConfigPath + '.' + outputColors.reset);
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

function configureInputAppProperties(projectDir) {
    var inputProperties = {};
    switch (commandLineArgsMap.apptype) {
        case 'native':
            inputProperties.templateDir = path.join(packageSdkRootDir, 'native/TemplateApp');
            inputProperties.templateAppName = 'Template';
            inputProperties.templatePackageName = 'com.salesforce.samples.templateapp';
            inputProperties.bootConfigPath = path.join(projectDir, 'res', 'values', 'bootconfig.xml');
            break;
        case 'hybrid_local':
            inputProperties.templateDir = path.join(packageSdkRootDir, 'hybrid/SampleApps/ContactExplorer');
            inputProperties.templateAppName = 'ContactExplorer';
            inputProperties.templatePackageName = 'com.salesforce.samples.contactexplorer';
            inputProperties.bootConfigPath = path.join(projectDir, 'assets', 'www', 'bootconfig.json');
            break;
        case 'hybrid_remote':
            inputProperties.templateDir = path.join(packageSdkRootDir, 'hybrid/SampleApps/VFConnector');
            inputProperties.templateAppName = 'VFConnector';
            inputProperties.templatePackageName = 'com.salesforce.samples.vfconnector';
            inputProperties.bootConfigPath = path.join(projectDir, 'assets', 'www', 'bootconfig.json');
            break;
    }

    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);

    return inputProperties;
}

function makeContentReplacementPathsArray(appInputProperties, projectDir) {
    var returnArray = [];
    returnArray.push(path.join(projectDir, 'AndroidManifest.xml'));
    returnArray.push(path.join(projectDir, '.project'));
    returnArray.push(path.join(projectDir, 'build.xml'));
    returnArray.push(path.join(projectDir, 'res', 'values', 'strings.xml'));
    returnArray.push(appInputProperties.bootConfigPath);
    var srcFilePaths = getTemplateSourceFilePaths(appInputProperties, projectDir);
    srcFilePaths.forEach(function(srcFile) {
        returnArray.push(srcFile.path);
    });

    return returnArray;
}

function getTemplateSourceFilePaths(appInputProperties, projectDir) {
    var srcFilesArray = [];
    var srcDir = path.join(projectDir, 'src', appInputProperties.templatePackageDir);
    fs.readdirSync(srcDir).forEach(function(srcFile) {
        if (/\.java$/.test(srcFile))
            srcFilesArray.push({ 'name': srcFile, 'path': path.join(srcDir, srcFile) });
    });

    return srcFilesArray;
}
