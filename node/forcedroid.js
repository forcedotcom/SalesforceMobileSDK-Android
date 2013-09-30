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
 // app templates and running sample apps.

var exec = require('child_process').exec;
var path = require('path');
var shellJs = require('shelljs');
var fs = require('fs');
var commandLineUtils = require('../external/shared/node/commandLineUtils');
var outputColors = require('../external/shared/node/outputColors');

var packageSdkRootDir = path.resolve(__dirname, '..');

var commandLineArgs = process.argv.slice(2, process.argv.length);
var command = commandLineArgs.shift();
if (typeof command !== 'string') {
    usage();
    process.exit(1);
}
var commandLineArgsMap = {};
if (command === 'samples') {
    var argProcessorList = samplesArgProcessorList();
    commandLineUtils.processArgsInteractive(commandLineArgs, argProcessorList, function (outputArgsMap) {
        var outputDir = outputArgsMap.targetdir;
        fetchSamples(outputDir);
    });
} else {

    // Set up the input argument processing / validation.
    var argProcessorList = createArgProcessorList();
    commandLineUtils.processArgsInteractive(commandLineArgs, argProcessorList, function (outputArgsMap) {
        commandLineArgsMap = outputArgsMap;
        switch  (command) {
            case 'create':
                create();
                break;
            default:
                console.log('Unknown option: \'' + command + '\'.');
                usage();
                process.exit(2);
        }
    });
}

function fetchSamples(outputDir) {
    var inputProperties = {};
    var projectDir;

    // Sets properties and copies over the 'FileExplorer' app.
    commandLineArgsMap.apptype = 'native';
    commandLineArgsMap.appname = 'FileExplorer';
    commandLineArgsMap.targetdir = outputDir;
    commandLineArgsMap.packagename = 'com.salesforce.samples.fileexplorer';
    commandLineArgsMap.boolusesmartstore = false;
    inputProperties.templateDir = path.join(packageSdkRootDir, 'native/SampleApps/FileExplorer');
    inputProperties.templateAppName = 'FileExplorer';
    inputProperties.templatePackageName = 'com.salesforce.samples.fileexplorer';
    projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    inputProperties.bootConfigPath = path.join(projectDir, 'res', 'values', 'bootconfig.xml');
    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);
    create(inputProperties);

    // Sets properties and copies over the 'NativeSqlAggregator' app.
    commandLineArgsMap.apptype = 'native';
    commandLineArgsMap.appname = 'NativeSqlAggregator';
    commandLineArgsMap.targetdir = outputDir;
    commandLineArgsMap.packagename = 'com.salesforce.samples.nativesqlaggregator';
    commandLineArgsMap.boolusesmartstore = true;
    inputProperties.templateDir = path.join(packageSdkRootDir, 'native/SampleApps/NativeSqlAggregator');
    inputProperties.templateAppName = 'NativeSqlAggregator';
    inputProperties.templatePackageName = 'com.salesforce.samples.nativesqlaggregator';
    projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    inputProperties.bootConfigPath = path.join(projectDir, 'res', 'values', 'bootconfig.xml');
    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);
    create(inputProperties);

    // Sets properties and copies over the 'RestExplorer' app.
    commandLineArgsMap.apptype = 'native';
    commandLineArgsMap.appname = 'RestExplorer';
    commandLineArgsMap.targetdir = outputDir;
    commandLineArgsMap.packagename = 'com.salesforce.samples.restexplorer';
    commandLineArgsMap.boolusesmartstore = true;
    inputProperties.templateDir = path.join(packageSdkRootDir, 'native/SampleApps/RestExplorer');
    inputProperties.templateAppName = 'RestExplorer';
    inputProperties.templatePackageName = 'com.salesforce.samples.restexplorer';
    projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    inputProperties.bootConfigPath = path.join(projectDir, 'res', 'values', 'bootconfig.xml');
    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);
    create(inputProperties);

    // Sets properties and copies over the 'AccountEditor' app.
    commandLineArgsMap.apptype = 'hybrid_local';
    commandLineArgsMap.appname = 'AccountEditor';
    commandLineArgsMap.targetdir = outputDir;
    commandLineArgsMap.packagename = 'com.salesforce.samples.accounteditor';
    commandLineArgsMap.boolusesmartstore = true;
    inputProperties.templateDir = path.join(packageSdkRootDir, 'hybrid/SampleApps/AccountEditor');
    inputProperties.templateAppName = 'AccountEditor';
    inputProperties.templatePackageName = 'com.salesforce.samples.accounteditor';
    projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    inputProperties.bootConfigPath = path.join(projectDir, 'assets', 'www', 'bootconfig.json');
    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);
    create(inputProperties);

    // Sets properties and copies over the 'ContactExplorer' app.
    commandLineArgsMap.apptype = 'hybrid_local';
    commandLineArgsMap.appname = 'ContactExplorer';
    commandLineArgsMap.targetdir = outputDir;
    commandLineArgsMap.packagename = 'com.salesforce.samples.contactexplorer';
    commandLineArgsMap.boolusesmartstore = false;
    inputProperties.templateDir = path.join(packageSdkRootDir, 'hybrid/SampleApps/ContactExplorer');
    inputProperties.templateAppName = 'ContactExplorer';
    inputProperties.templatePackageName = 'com.salesforce.samples.contactexplorer';
    projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    inputProperties.bootConfigPath = path.join(projectDir, 'assets', 'www', 'bootconfig.json');
    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);
    create(inputProperties);

    // Sets properties and copies over the 'HybridFileExplorer' app.
    commandLineArgsMap.apptype = 'hybrid_local';
    commandLineArgsMap.appname = 'HybridFileExplorer';
    commandLineArgsMap.targetdir = outputDir;
    commandLineArgsMap.packagename = 'com.salesforce.samples.hybridfileexplorer';
    commandLineArgsMap.boolusesmartstore = true;
    inputProperties.templateDir = path.join(packageSdkRootDir, 'hybrid/SampleApps/HybridFileExplorer');
    inputProperties.templateAppName = 'HybridFileExplorer';
    inputProperties.templatePackageName = 'com.salesforce.samples.hybridfileexplorer';
    projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    inputProperties.bootConfigPath = path.join(projectDir, 'assets', 'www', 'bootconfig.json');
    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);
    create(inputProperties);

    // Sets properties and copies over the 'SmartStoreExplorer' app.
    commandLineArgsMap.apptype = 'hybrid_local';
    commandLineArgsMap.appname = 'SmartStoreExplorer';
    commandLineArgsMap.targetdir = outputDir;
    commandLineArgsMap.packagename = 'com.salesforce.samples.smartstoreexplorer';
    commandLineArgsMap.boolusesmartstore = true;
    inputProperties.templateDir = path.join(packageSdkRootDir, 'hybrid/SampleApps/SmartStoreExplorer');
    inputProperties.templateAppName = 'SmartStoreExplorer';
    inputProperties.templatePackageName = 'com.salesforce.samples.smartstoreexplorer';
    projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    inputProperties.bootConfigPath = path.join(projectDir, 'assets', 'www', 'bootconfig.json');
    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);
    create(inputProperties);

    // Sets properties and copies over the 'VFConnector' app.
    commandLineArgsMap.apptype = 'hybrid_remote';
    commandLineArgsMap.appname = 'VFConnector';
    commandLineArgsMap.targetdir = outputDir;
    commandLineArgsMap.packagename = 'com.salesforce.samples.vfconnector';
    commandLineArgsMap.startpage = '/apex/BasicVFPage';
    commandLineArgsMap.boolusesmartstore = true;
    inputProperties.templateDir = path.join(packageSdkRootDir, 'hybrid/SampleApps/VFConnector');
    inputProperties.templateAppName = 'VFConnector';
    inputProperties.templatePackageName = 'com.salesforce.samples.vfconnector';
    projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    inputProperties.bootConfigPath = path.join(projectDir, 'assets', 'www', 'bootconfig.json');
    inputProperties.templateAppClassName = inputProperties.templateAppName + 'App';
    inputProperties.templatePackageDir = inputProperties.templatePackageName.replace(/\./g, path.sep);
    create(inputProperties);
}

function usage() {
    console.log(outputColors.cyan + 'Usage:');
    console.log('\n');
    console.log(outputColors.magenta + 'forcedroid create');
    console.log('    --apptype=<Application Type> (native, hybrid_remote, hybrid_local)');
    console.log('    --appname=<Application Name>');
    console.log('    --targetdir=<Target App Folder>');
    console.log('    --packagename=<App Package Identifier> (com.my_company.my_app)');
    console.log('    --startpage=<Path to the remote start page> (/apex/MyPage — Only required/used for \'hybrid_remote\')');
    console.log('    [--usesmartstore=<Whether or not to use SmartStore> (\'true\' or \'false\'. false by default)]');
    console.log(outputColors.cyan + '\nOR\n');
    console.log(outputColors.magenta + 'forcedroid samples');
    console.log('    --targetdir=<Target Samples Folder>' + outputColors.reset);
}

function create(sampleAppInputProperties) {
    
    // The destination project directory, in the target directory.
    var projectDir = path.join(commandLineArgsMap.targetdir, commandLineArgsMap.appname);
    if (fs.existsSync(projectDir)) {
        console.log('App folder path \'' + projectDir + '\' already exists.  Cannot continue.');
        process.exit(3);
    }
    var appInputProperties;
    if (sampleAppInputProperties === undefined) {
        appInputProperties = configureInputAppProperties(projectDir);
    } else {
        appInputProperties = sampleAppInputProperties;
    }
    createApp(appInputProperties, projectDir);
}

function createApp(appInputProperties, projectDir) {

    // Copy the template files to the destination directory.
    shellJs.mkdir('-p', projectDir);
    shellJs.cp('-R', path.join(appInputProperties.templateDir, '*'), projectDir);
    shellJs.cp(path.join(appInputProperties.templateDir, '.project'), projectDir);
    shellJs.cp(path.join(appInputProperties.templateDir, '.classpath'), projectDir);
    if (shellJs.error()) {
        console.log('There was an error copying the template files from \'' + appInputProperties.templateDir + '\' to \'' + projectDir + '\': ' + shellJs.error());
        process.exit(4);
    }

    // Copy the SDK into the app folder as well, if it's not already there.
    var destSdkDir = path.join(commandLineArgsMap.targetdir, path.basename(packageSdkRootDir));
    if (!fs.existsSync(destSdkDir)) {
        shellJs.cp('-R', packageSdkRootDir, commandLineArgsMap.targetdir);
        if (shellJs.error()) {
            console.log('There was an error copying the SDK directory from \'' + packageSdkRootDir + '\' to \'' + commandLineArgsMap.targetdir + '\': ' + shellJs.error());
            process.exit(5);
        }
    } else {
        console.log(outputColors.cyan + 'INFO:' + outputColors.reset + ' SDK directory \'' + destSdkDir + '\' already exists.  Skipping copy.');
    }

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
        var smartStoreProjectPropertyContent = 'android.library.reference.1=' + smartStorePathRelativeToProject + '\n';
        projectPropertiesContent = smartStoreProjectPropertyContent;
        projectPropertiesContent.to(projectPropertiesFilePath);

        console.log('Extending SalesforceSDKManagerWithSmartStore instead of SalesforceSDKManager.');
        shellJs.sed('-i', /SalesforceSDKManager/g, 'SalesforceSDKManagerWithSmartStore', appClassPath);
        shellJs.sed('-i',
            /com\.salesforce\.androidsdk\.app\.SalesforceSDKManagerWithSmartStore/g,
            'com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore',
            appClassPath);
    }

    // If it's a hybrid remote app, replace the start page.
    if (commandLineArgsMap.apptype === 'hybrid_remote') {
        console.log('Changing remote page reference in ' + appInputProperties.bootConfigPath + '.');
        var templateRemotePageRegExp = /\/apex\/BasicVFPage/g;
        shellJs.sed('-i', templateRemotePageRegExp, commandLineArgsMap.startpage, appInputProperties.bootConfigPath);
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

// -----
// Input argument validation / processing.
// -----

function createArgProcessorList() {
    var argProcessorList = new commandLineUtils.ArgProcessorList();

    // App type
    argProcessorList.addArgProcessor('apptype', 'Enter your application type (native, hybrid_remote, or hybrid_local):', function(appType) {
        appType = appType.trim();
        if (appType !== 'native' && appType !== 'hybrid_remote' && appType !== 'hybrid_local')
            return new commandLineUtils.ArgProcessorOutput(false, 'App type must be native, hybrid_remote, or hybrid_local.');

        return new commandLineUtils.ArgProcessorOutput(true, appType);
    });

    // App name
    argProcessorList.addArgProcessor('appname', 'Enter your application name:', function(appName) {
        if (appName.trim() === '')
            return new commandLineUtils.ArgProcessorOutput(false, 'Invalid value for app name: \'' + appName + '\'');
        
        return new commandLineUtils.ArgProcessorOutput(true, appName.trim());
    });

    // Target dir
    argProcessorList.addArgProcessor('targetdir', 'Enter the target directory of your app:', function(targetDir) {
        if (targetDir.trim() === '')
            return new commandLineUtils.ArgProcessorOutput(false, 'Invalid value for target dir: \'' + targetDir + '\'');
        
        return new commandLineUtils.ArgProcessorOutput(true, targetDir.trim());
    });

    // Package name
    argProcessorList.addArgProcessor('packagename', 'Enter the package name for your app (com.mycompany.my_app):', function(packageName) {
        if (packageName.trim() === '')
            return new commandLineUtils.ArgProcessorOutput(false, 'Invalid value for package name: \'' + packageName + '\'');

        packageName = packageName.trim();
        var validPackageRegExp = /^[a-z]+[a-z0-9_]*(\.[a-z]+[a-z0-9_]*)*$/;
        if (!validPackageRegExp.test(packageName)) {
            return new commandLineUtils.ArgProcessorOutput(false, '\'' + packageName + '\' is not a valid Java package name.');
        }
        
        return new commandLineUtils.ArgProcessorOutput(true, packageName);
    });

    // Start page
    argProcessorList.addArgProcessor(
        'startpage',
        'Enter the start page for your app (only applicable for hybrid_remote apps):',
        function(startPage, argsMap) {
            if (argsMap && argsMap.apptype === 'hybrid_remote') {
                if (startPage.trim() === '')
                    return new commandLineUtils.ArgProcessorOutput(false, 'Invalid value for start page: \'' + startPage + '\'');

                return new commandLineUtils.ArgProcessorOutput(true, startPage.trim());
            }

            // Unset any value here, as it doesn't apply for non-remote apps.
            return new commandLineUtils.ArgProcessorOutput(true, undefined);
        },
        function (argsMap) {
            return (argsMap['apptype'] === 'hybrid_remote');
        }
    );

    // Use SmartStore
    argProcessorList.addArgProcessor('usesmartstore', 'Do you want to use SmartStore in your app? [yes/NO] (\'No\' by default)', function(useSmartStore) {
        var boolUseSmartStore = (useSmartStore.trim().toLowerCase() === 'yes');
        return new commandLineUtils.ArgProcessorOutput(true, boolUseSmartStore);
    });

    return argProcessorList;
}

function samplesArgProcessorList() {
    var argProcessorList = new commandLineUtils.ArgProcessorList();

    // Target dir
    argProcessorList.addArgProcessor('targetdir', 'Enter the target directory of samples:', function(targetDir) {
        if (targetDir.trim() === '')
            return new commandLineUtils.ArgProcessorOutput(false, 'Invalid value for target dir: \'' + targetDir + '\'');
        
        return new commandLineUtils.ArgProcessorOutput(true, targetDir.trim());
    });

    return argProcessorList;
}
