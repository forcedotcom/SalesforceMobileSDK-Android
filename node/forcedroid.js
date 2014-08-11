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

//
// External modules
// 
var path = require('path');
var outputColors = require('../external/shared/node/outputColors');
var commandLineUtils = require('../external/shared/node/commandLineUtils');
var shelljs = require('shelljs') ;
var fs = require('fs'); 
var cordovaHelper = require('../external/shared/node/cordovaHelper');
var miscUtils = require('../external/shared/node/utils');

var minimumCordovaVersion = '3.5';

// Calling main
main(process.argv);

// 
// Main function
// 
function main(args) {
    var commandLineArgs = args.slice(2, args.length);
    var command = commandLineArgs.shift();

    var processorList = null;
    var commandHandler = null;

    switch (command || '') {
    case 'create': 
        processorList = createArgsProcessorList(); 
        commandHandler = createApp;
        break;
    case 'samples': 
        processorList = samplesArgsProcessorList(); 
        commandHandler = fetchSamples;
        break;
    default:
        usage();
        process.exit(1);
    };

    commandLineUtils.processArgsInteractive(commandLineArgs, processorList, commandHandler);
}

//
// Usage
//
function usage() {
    console.log(outputColors.cyan + 'Usage:');
    console.log('\n');
    console.log(outputColors.magenta + 'forcedroid create');
    console.log('    --apptype=<Application Type> (native, hybrid_remote, hybrid_local)');
    console.log('    --appname=<Application Name>');
    console.log('    --targetdir=<Target App Folder>');
    console.log('    --packagename=<App Package Identifier> (com.my_company.my_app)');
    console.log('    --targetandroidapi=<Target Api e.g. 19 for KitKat>');
    console.log('    --startpage=<Path to the remote start page> (/apex/MyPage — Only required/used for \'hybrid_remote\')');
    console.log('    [--usesmartstore=<Whether or not to use SmartStore> (\'true\' or \'false\'. false by default)]');
    console.log(outputColors.cyan + '\nOR\n');
    console.log(outputColors.magenta + 'forcedroid samples');
    console.log('    --targetdir=<Target Samples Folder>');
    console.log('    --targetandroidapi=<Target Api e.g. 19 for KitKat>' + outputColors.reset);
}

//
// Helper to 'samples' command
//
function fetchSamples(config) {
    // Map of sample app name to boolean indicating if it uses smartstore
    var sampleApps = {'FileExplorer':false, 'NativeSqlAggregator':true, 'RestExplorer': false};

    // Generating sample app projects
    for (var appname in sampleApps) {
        sampleNativeApp(config, appname, sampleApps[appname]);
    }

    // Inform the user of next steps.
    var nextStepsOutput = ['',
                           outputColors.green + 'Your sample applications projects are ready in ' + config.targetdir + '.',
                           '',
                           'To build from the command line do the following:' + outputColors.reset,
                           '   - cd ' + path.join(config.targetdir, '<SampleApp>'),
                           '   - ant clean debug',
                           '',
                           outputColors.cyan + 'To run the application, start an emulator or plug in your device and run:' + outputColors.reset,
                           '   - ant installd',
                           '',
                           outputColors.cyan + 'To use your new application in Eclipse, do the following:' + outputColors.reset,
                           '   - Set ' + config.targetdir + ' as your workspace',
                           '   - Go to Import -> Existing project and select ' + config.targetdir + ' as the root and import all the projects shown',
                           '   - Choose \'Build All\' from the Project menu',
                           '   - Run your application by choosing "Run as Android application"',
                           ''].join('\n');
    console.log(nextStepsOutput);
}

function sampleNativeApp(config, appname, usesmartstore) {
   createNativeApp({targetdir: config.targetdir,
                     targetandroidapi: config.targetandroidapi,
                     apptype: 'native',
                     appname: appname,
                     packagename: 'com.salesforce.samples.' + appname.toLowerCase(),
                     usesmartstore: usesmartstore,
                     relativeTemplateDir: 'native/SampleApps/' + appname,
                     templateAppName: appname,
                     templatePackageName: 'com.salesforce.samples.' + appname.toLowerCase()},
                   false);
}


//
// Helper for 'create' command
//
function createApp(config) {
    // Native app creation
    if (config.apptype === 'native') {
        config.relativeTemplateDir = 'native/TemplateApp';
        config.templateAppName = 'Template';
        config.templatePackageName = 'com.salesforce.samples.templateapp';
        createNativeApp(config, true);
    }
    // Hybrid app creation
    else {
        createHybridApp(config);
    }
}

//
// Helper to create hybrid application
//
function createHybridApp(config) {
    config.projectDir = path.join(config.targetdir, config.appname);
    // console.log("Config:" + JSON.stringify(config, null, 2));

    // Make sure the Cordova CLI client exists.
    var cordovaCliVersion = cordovaHelper.getCordovaCliVersion();
    if (cordovaCliVersion === null) {
        console.log('cordova command line tool could not be found.  Make sure you install the cordova CLI from https://www.npmjs.org/package/cordova.');
        process.exit(6);
    }

    var minimumCordovaVersionNum = miscUtils.getVersionNumberFromString(minimumCordovaVersion);
    var cordovaCliVersionNum = miscUtils.getVersionNumberFromString(cordovaCliVersion);
    if (cordovaCliVersionNum < minimumCordovaVersionNum) {
        console.log('Installed cordova command line tool version (' + cordovaCliVersion + ') is less than the minimum required version (' + minimumCordovaVersion + ').  Please update your version of Cordova.');
        process.exit(7);
    }

    //console.log('cordova create ' + config.projectDir + ' ' + config.packagename + ' ' + config.appname);
    shelljs.exec('cordova create ' + config.projectDir + ' ' + config.packagename + ' ' + config.appname);
    shelljs.pushd(config.projectDir);
    shelljs.exec('cordova platform add android');
    shelljs.exec('cordova plugin add https://github.com/wmathurin/SalesforceMobileSDK-CordovaPlugin');
    //console.log('node plugins/com.salesforce/tools/postinstall-android.js ' + config.targetandroidapi + ' ' + config.usesmartstore)
    shelljs.exec('node plugins/com.salesforce/tools/postinstall-android.js ' + config.targetandroidapi + ' ' + config.usesmartstore);

    var bootconfig = {
        "remoteAccessConsumerKey": "3MVG9Iu66FKeHhINkB1l7xt7kR8czFcCTUhgoA8Ol2Ltf1eYHOU4SqQRSEitYFDUpqRWcoQ2.dBv_a1Dyu5xa",
        "oauthRedirectURI": "testsfdc:///mobilesdk/detect/oauth/done",
        "oauthScopes": ["web", "api"],
        "isLocal": config.apptype === 'hybrid_local',
        "startPage": config.startpage || 'index.html',
        "errorPage": "error.html",
        "shouldAuthenticate": true,
        "attemptOfflineLoad": false,
        "androidPushNotificationClientId": ""
    };
    // console.log("Bootconfig:" + JSON.stringify(bootconfig, null, 2));

    fs.writeFileSync('www/bootconfig.json', JSON.stringify(bootconfig, null, 2));
    shelljs.exec('cordova prepare android');
    shelljs.popd();

    // Inform the user of next steps.
    var nextStepsOutput =
        ['',
         outputColors.green + 'Your application project is ready in ' + config.targetdir + '.',
         '',
         outputColors.cyan + 'To build the new application, do the following:' + outputColors.reset,
         '   - cd ' + config.targetdir,
         '   - cordova build',
         '',
         outputColors.cyan + 'To run the application, start an emulator or plug in your device and run:' + outputColors.reset,
         '   - cordova run',
         '',
         outputColors.cyan + 'To use your new application in Eclipse, do the following:' + outputColors.reset,
         '   - Use ' + config.targetdir + ' as your workspace,',
         '   - Choose Import -> Android -> Existing Android Code into Workspace,',
         '   - Choose ' + config.targetdir + ' as the root directory',
         '   - Pick the following projects: platforms/android, platforms/android/CordovaLib, plugins/com.salesforce/android/hybrid/SmartStore and plugins/com.salesforce/android/native/SalesforceSDK',
         '   - For platforms/android, click on the project name, and change it to ' + config.appname,
         '   - Once everything is built, right-click on the ' + config.appname + ' project and choose "Run as Android application"',
         ''].join('\n');
    console.log(nextStepsOutput);
    console.log(outputColors.cyan + 'Before you ship, make sure to plug your OAuth Client ID,\nCallback URI, and OAuth Scopes into '
        + outputColors.magenta + 'www/bootconfig.json' + outputColors.reset);
}

//
// Helper to create native application
//
function createNativeApp(config, showNextSteps) {
    // Computed config
    config.projectDir = path.join(config.targetdir, config.appname);
    config.bootConfigPath = path.join(config.projectDir, 'res', 'values', 'bootconfig.xml');
    config.templatePackageDir = config.templatePackageName.replace(/\./g, path.sep);
    var packageSdkRootDir = path.resolve(__dirname, '..');
    config.templateDir = path.join(packageSdkRootDir, config.relativeTemplateDir);
    config.templateAppClassName = config.templateAppName + 'App';
    
    // console.log("Config:" + JSON.stringify(config, null, 2));

    // Checking if config.projectDir already exists
    if (fs.existsSync(config.projectDir)) {
        console.log('App folder path \'' + config.projectDir + '\' already exists.  Cannot continue.');
        process.exit(3);
    }

    // Copy the template files to the destination directory.
    shelljs.mkdir('-p', config.projectDir);
    shelljs.cp('-R', path.join(config.templateDir, '*'), config.projectDir);
    shelljs.cp(path.join(config.templateDir, '.project'), config.projectDir);
    shelljs.cp(path.join(config.templateDir, '.classpath'), config.projectDir);
    if (shelljs.error()) {
        console.log('There was an error copying the template files from \'' + config.templateDir + '\' to \'' + config.projectDir + '\': ' + shelljs.error());
        process.exit(4);
    }

    var contentFilesWithReplacements = makeContentReplacementPathsArray(config);

    // Substitute app class name
    var appClassName = config.appname + 'App';
    console.log('Renaming application class to ' + appClassName + ' in source.');
    contentFilesWithReplacements.forEach(function(file) {
        var templateAppClassNameRegExp = new RegExp(config.templateAppClassName, 'g');
        shelljs.sed('-i', templateAppClassNameRegExp, appClassName, file);
    });

    // Substitute app name
    console.log('Renaming application to ' + config.appname + ' in source.');
    contentFilesWithReplacements.forEach(function(file) {
        var templateAppNameRegExp = new RegExp(config.templateAppName, 'g');
        shelljs.sed('-i', templateAppNameRegExp, config.appname, file);
    });

    // Substitute package name.
    console.log('Renaming package name to ' + config.packagename + ' in source.');
    contentFilesWithReplacements.forEach(function(file) {
        var templatePackageNameRegExp = new RegExp(config.templatePackageName.replace(/\./g, '\\.'), 'g');
        shelljs.sed('-i', templatePackageNameRegExp, config.packagename, file);
    });

    // Rename source package folders.
    console.log('Moving source files to proper package path.')
    var srcFilePaths = getTemplateSourceFilePaths(config);
    srcFilePaths.forEach(function(srcFile) {
        fs.renameSync(srcFile.path, path.join(config.projectDir, 'src', srcFile.name));  // Temporary location
    });
    shelljs.rm('-rf', path.join(config.projectDir, 'src', 'com'));
    var packageDir = config.packagename.replace(/\./g, path.sep);
    shelljs.mkdir('-p', path.resolve(config.projectDir, 'src', packageDir));
    srcFilePaths.forEach(function(srcFile) {
        fs.renameSync(path.join(config.projectDir, 'src', srcFile.name), path.resolve(config.projectDir, 'src', packageDir, srcFile.name));
    });

    // Rename the app class name.
    console.log('Renaming the app class filename to ' + appClassName + '.java.');
    var appClassDir = path.join(config.projectDir, 'src', packageDir);
    var templateAppClassPath = path.join(appClassDir, config.templateAppClassName) + '.java';
    var appClassPath = path.join(appClassDir, appClassName) + '.java';
    fs.renameSync(templateAppClassPath, appClassPath);

    // If SmartStore is configured, set it up.
    if (config.usesmartstore) {
        console.log('Adding SmartStore support.');
        shelljs.mkdir('-p', path.join(config.projectDir, 'assets'));  // May not exist for native.
        shelljs.cp(path.join(packageSdkRootDir, 'external', 'sqlcipher', 'assets', 'icudt46l.zip'), path.join(config.projectDir, 'assets', 'icudt46l.zip'));

        console.log('Extending SalesforceSDKManagerWithSmartStore instead of SalesforceSDKManager.');
        shelljs.sed('-i', /SalesforceSDKManager/g, 'SalesforceSDKManagerWithSmartStore', appClassPath);
        shelljs.sed('-i', /WithSmartStoreWithSmartStore/g, 'WithSmartStore', appClassPath); // undoing change if the template was already using SalesforceSDKManagerWithSmartStore
        shelljs.sed('-i',
            /com\.salesforce\.androidsdk\.app\.SalesforceSDKManagerWithSmartStore/g,
            'com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore',
            appClassPath);
    }

    // Copy SalesforceSDK library project into the app folder as well, if it's not already there.
    // copy <Android Package>/libs/SalesforceSDK -> <App Folder>/forcedroid/libs/SalesforceSDK
    var salesforceSDKRelativePath = path.join('libs', 'SalesforceSDK');
    copyFromSDK(packageSdkRootDir, config.targetdir, salesforceSDKRelativePath);
    shelljs.exec('android update project -p ' + path.join(config.targetdir, path.basename(packageSdkRootDir), salesforceSDKRelativePath));

    // Copy Cordova library project into the app folder as well, if it's not already there.
    // copy <Android Package>/external/cordova/framework <App Folder>/forcedroid/external/cordova/framework
    var cordovaRelativePath = path.join('external', 'cordova');
    var destCordovaDir = path.join(config.targetdir, path.basename(packageSdkRootDir), cordovaRelativePath);
    copyFromSDK(packageSdkRootDir, config.targetdir, path.join(cordovaRelativePath, 'framework'));
    shelljs.cp(path.join(packageSdkRootDir, cordovaRelativePath, 'VERSION'), destCordovaDir);
    console.log(destCordovaDir);
    shelljs.exec('android update project -p ' + path.join(destCordovaDir, 'framework'));
    console.log('update done');

    // Copy SmartStore library project into the app folder as well, if it's not already there - if required.
    // copy <Android Package>/libs/SmartStore <App Folder>/forcedroid/libs/SmartStore
    // copy <Android Package>/external/sqlcipher <App Folder>/forcedroid/external/sqlcipher
    if (config.usesmartstore) {
        var smartStoreRelativePath = path.join('libs', 'SmartStore');
        copyFromSDK(packageSdkRootDir, config.targetdir, smartStoreRelativePath);
        shelljs.exec('android update project -p ' + path.join(config.targetdir, path.basename(packageSdkRootDir), smartStoreRelativePath));
        copyFromSDK(packageSdkRootDir, config.targetdir, path.join('external', 'sqlcipher'));
    }

    // Library project reference
    console.log(outputColors.yellow + 'Fixing project.properties.');
    var projectPropertiesFilePath = path.join(config.projectDir, 'project.properties');
    shelljs.rm(projectPropertiesFilePath);
    var libProject = config.usesmartstore
        ? path.join('..', path.basename(packageSdkRootDir), smartStoreRelativePath)
        : path.join('..', path.basename(packageSdkRootDir), salesforceSDKRelativePath);
    shelljs.exec('android update project -p ' + config.projectDir + ' -t "android-' + config.targetandroidapi + '" -l ' + libProject);
    '\nmanifestmerger.enabled=true\n'.toEnd(projectPropertiesFilePath);

    // Inform the user of next steps if requested.
    if (showNextSteps) {
        var nextStepsOutput =
            ['',
             outputColors.green + 'Your application project is ready in ' + config.targetdir + '.',
             '',
             outputColors.cyan + 'To build the new application, do the following:' + outputColors.reset,
             '   - cd ' + config.projectDir,
             '   - ant clean debug',
             '',
             outputColors.cyan + 'To run the application, start an emulator or plug in your device and run:' + outputColors.reset,
             '   - ant installd',
             '',
             outputColors.cyan + 'To use your new application in Eclipse, do the following:' + outputColors.reset,
             '   - Import ' + path.basename(libProject) + ',',
             '     and the ' + outputColors.magenta + config.appname + outputColors.reset + ' project into your workspace',
             '   - Choose \'Build All\' from the Project menu',
             '   - Run your application by choosing "Run as Android application"',
             ''].join('\n');
        console.log(nextStepsOutput);
        var relativeBootConfigPath = path.relative(config.targetdir, config.bootConfigPath);
        console.log(outputColors.cyan + 'Before you ship, make sure to plug your OAuth Client ID,\nCallback URI, and OAuth Scopes into '
                    + outputColors.magenta + relativeBootConfigPath + '.' + outputColors.reset);
    }
}

function makeContentReplacementPathsArray(config) {
    var returnArray = [];
    returnArray.push(path.join(config.projectDir, 'AndroidManifest.xml'));
    returnArray.push(path.join(config.projectDir, '.project'));
    returnArray.push(path.join(config.projectDir, 'build.xml'));
    returnArray.push(path.join(config.projectDir, 'res', 'values', 'strings.xml'));
    returnArray.push(config.bootConfigPath);
    var srcFilePaths = getTemplateSourceFilePaths(config);
    srcFilePaths.forEach(function(srcFile) {
        returnArray.push(srcFile.path);
    });

    return returnArray;
}

function getTemplateSourceFilePaths(config) {
    var srcFilesArray = [];
    var srcDir = path.join(config.projectDir, 'src', config.templatePackageDir);
    fs.readdirSync(srcDir).forEach(function(srcFile) {
        if (/\.java$/.test(srcFile))
            srcFilesArray.push({ 'name': srcFile, 'path': path.join(srcDir, srcFile) });
    });

    return srcFilesArray;
}

function copyFromSDK(packageSdkRootDir, targetDir, srcDirRelative) {
    console.log('Copying ' + srcDirRelative + '.');
    var destDir = path.join(targetDir, path.basename(packageSdkRootDir), path.dirname(srcDirRelative));
    var srcDir = path.join(packageSdkRootDir, srcDirRelative);
    if (!fs.existsSync(path.join(destDir, path.basename(srcDirRelative)))) {
        shelljs.mkdir('-p', destDir);
        shelljs.cp('-R', srcDir, destDir);
        if (shelljs.error()) {
            console.log('There was an error copying ' + srcDirRelative + ' from \'' + packageSdkRootDir + '\' to \'' + destDir + '\': ' + shelljs.error());
            process.exit(5);
        }
    } else {
        console.log(outputColors.cyan + 'INFO:' + outputColors.reset + ' ' + srcDirRelative + ' already exists.  Skipping copy.');
    }
}

//
// Processor list for 'create' command
//
function createArgsProcessorList() {
    var argProcessorList = new commandLineUtils.ArgProcessorList();

    // App type
    addProcessorFor(argProcessorList, 'apptype', 'Enter your application type (native, hybrid_remote, or hybrid_local):', 'App type must be native, hybrid_remote, or hybrid_local.', 
                    function(val) { return ['native', 'hybrid_remote', 'hybrid_local'].indexOf(val) >= 0; });

    // App name
    addProcessorFor(argProcessorList, 'appname', 'Enter your application name:', 'Invalid value for application name: \'$val\'.', /\S+/);

    // Target dir
    addProcessorFor(argProcessorList, 'targetdir', 'Enter the target directory of your app:', 'Invalid value for target dir: \'$val\'.',  /\S+/);

    // Target API 
    addProcessorForAndroidApi(argProcessorList);

    // Package name
    addProcessorFor(argProcessorList, 'packagename', 'Enter the package name for your app (com.mycompany.my_app):', '\'$val\' is not a valid Java package name.', /^[a-z]+[a-z0-9_]*(\.[a-z]+[a-z0-9_]*)*$/);

    // Start page
    addProcessorFor(argProcessorList, 'startpage', 'Enter the start page for your app (only applicable for hybrid_remote apps):', 'Invalid value for start page: \'$val\'.', /\S+/, 
                    function(argsMap) { return (argsMap['apptype'] === 'hybrid_remote'); });

    // Use SmartStore
    addProcessorFor(argProcessorList, 'usesmartstore', 'Do you want to use SmartStore in your app? [yes/NO] (\'No\' by default)', 'Use smartstore must be yes or no.',
                    function(val) {
                        if (val.trim() === '') return true;
                        return ['yes', 'no'].indexOf(val.toLowerCase()) >= 0;
                    },
                    undefined,
                    function(val) { return (val.toLowerCase() === 'yes'); });

    return argProcessorList;
}

//
// Processor list for 'samples' command
//
function samplesArgsProcessorList() {
    var argProcessorList = new commandLineUtils.ArgProcessorList();
    addProcessorFor(argProcessorList, 'targetdir', 'Enter the target directory of samples:', 'Invalid value for target dir: \'$val\'.',  /\S+/);
    addProcessorForAndroidApi(argProcessorList);
    return argProcessorList;
}

//
// Add processor for target android api
// 
function addProcessorForAndroidApi(argProcessorList) { 
    // Target API 
    addProcessorFor(argProcessorList, 'targetandroidapi', 'Enter the target android api for your application (number between 8 (Froyo) and 19 (KitKat):', 'Target api must be a number between 8 and 19.', 
                    function(val) { var intVal = parseInt(val); return intVal >= 8 && intVal <= 19; });
}


//
// Helper function to add arg processor
// * argProcessorList: ArgProcessorList
// * argName: string, name of argument
// * prompt: string for prompt
// * error: string for error (can contain $val to print the value typed by the user in the error message)
// * validation: function or regexp or null (no validation)
// * preprocessor: function or null
// * postprocessor: function or null
// 
function addProcessorFor(argProcessorList, argName, prompt, error, validation, preprocessor, postprocessor) {
   argProcessorList.addArgProcessor(argName, prompt, function(val) {
       val = val.trim();

       // validation is either a function or a regexp
       if (typeof validation === 'function' && validation(val)
           || typeof validation === 'object' && typeof validation.test === 'function' && validation.test(val))
       {
           return new commandLineUtils.ArgProcessorOutput(true, typeof postprocessor === 'function' ? postprocessor(val) : val);
       }
       else {
           return new commandLineUtils.ArgProcessorOutput(false, error.replace('$val', val));
       }

   }, preprocessor);
}
