#!/usr/bin/env node

/*
 * Copyright (c) 2013-present, salesforce.com, inc.
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

var version = '5.0.0';
var targetApi = {'versionNumber': 25, 'versionName': 'Nougat'};
var minimumCordovaCliVersion = '5.4.0';
var cordovaPlatformVersion = '5.0.0';
var androidExePath;

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
    case 'version':
        console.log('forcedroid version ' + version);
        process.exit(0);
        break;
    case 'create': 
        processorList = createArgsProcessorList(); 
        commandHandler = createApp;
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
    console.log();
    console.log(outputColors.magenta + 'forcedroid create');
    console.log('    --apptype=<Application Type> (native, react_native, hybrid_remote, hybrid_local)');
    console.log('    --appname=<Application Name>');
    console.log('    --targetdir=<Target App Folder> (must be an existing empty folder)');
    console.log('    --packagename=<App Package Identifier> (com.my_company.my_app)');
    console.log('    --startpage=<Path to the remote start page> (/apex/MyPage — Only required/used for \'hybrid_remote\')');
    console.log('    [--usesmartstore=<Whether or not to use SmartStore/SmartSync> (\'yes\' or \'no\'. no by default — Only required/used for \'native\')]');
    console.log(outputColors.cyan + '\nOR\n');
    console.log(outputColors.magenta + 'forcedroid version' + outputColors.reset);
}

//
// Helper for 'create' command
//
function createApp(config) {

    // Verify necessary Android prerequisites.
    androidExePath = getAndroidSDKToolPath();
    if (androidExePath === null) {
        process.exit(8);
    }

    //If no target directory specified, default to current directory
    if(config.targetdir == undefined || config.targetdir.length == 0) {
        config.targetdir = config.appname;
    }

    // Native app creation
    if (config.apptype === 'native') {
        config.relativeTemplateDir = path.join('native', 'TemplateApp');
        config.templateAppName = 'Template';
        config.templatePackageName = 'com.salesforce.samples.templateapp';
        createNativeOrReactNativeApp(config);
    }

    // React Native app creation
    else if (config.apptype === 'react_native') {
        config.relativeTemplateDir = path.join('reactnative', 'ReactNativeTemplateApp');
        config.templateAppName = 'ReactNativeTemplate';
        config.templatePackageName = 'com.salesforce.samples.reactnativetemplateapp';
        config.usesmartstore = true;
        createNativeOrReactNativeApp(config);
    }

    // Hybrid app creation
    else {
        createHybridApp(config);
    }
}

function getAndroidSDKToolPath() {
    var androidHomeDir = process.env.ANDROID_HOME;
    if (typeof androidHomeDir !== 'string') {
        console.log(outputColors.red + 'You must set the ANDROID_HOME environment variable to the path of your installation of the Android SDK.' + outputColors.reset);
        return null;
    }
    var androidExePath = path.join(androidHomeDir, 'tools', 'android');
    var isWindows = (/^win/i).test(process.platform);
    if (isWindows) {
        androidExePath = androidExePath + '.bat';
    }
    if (!fs.existsSync(androidExePath)) {
        console.log(outputColors.red + 'The "android" utility does not exist at ' + androidExePath + '.  Make sure you\'ve properly installed the Android SDK.' + outputColors.reset);
        return null;
    }
    return androidExePath;
}

//
// Helper to create hybrid application
//
function createHybridApp(config) {
    config.projectDir = config.targetdir;

    // Make sure the Cordova CLI client exists.
    var cordovaCliVersion = cordovaHelper.getCordovaCliVersion();
    if (cordovaCliVersion === null) {
        console.log('cordova command line tool could not be found.  Make sure you install the cordova CLI from https://www.npmjs.org/package/cordova.');
        process.exit(6);
    }
    var minimumCordovaVersionNum = miscUtils.getVersionNumberFromString(minimumCordovaCliVersion);
    var cordovaCliVersionNum = miscUtils.getVersionNumberFromString(cordovaCliVersion);
    if (cordovaCliVersionNum < minimumCordovaVersionNum) {
        console.log('Installed cordova command line tool version (' + cordovaCliVersion + ') is less than the minimum required version (' + minimumCordovaCliVersion + ').  Please update your version of Cordova.');
        process.exit(7);
    }
    shelljs.exec('cordova create "' + config.projectDir + '" ' + config.packagename + ' ' + config.appname);
    shelljs.pushd(config.projectDir);
    shelljs.exec('cordova platform add android@' + cordovaPlatformVersion);
    shelljs.exec('cordova plugin add https://github.com/forcedotcom/SalesforceMobileSDK-CordovaPlugin#unstable');

    // Remove the default Cordova app.
    shelljs.rm('-rf', path.join('www', '*'));

    // Copy the sample app, if a local app was selected.
    if (config.apptype === 'hybrid_local') {
        var sampleAppFolder = path.join(__dirname, '..', 'external', 'shared', 'samples', 'userlist');
        shelljs.cp('-R', path.join(sampleAppFolder, '*'), 'www');
    }
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
    fs.writeFileSync(path.join('www', 'bootconfig.json'), JSON.stringify(bootconfig, null, 2));
    shelljs.exec('cordova prepare android');
    shelljs.popd();

    // Inform the user of next steps.
    var nextStepsOutput =
        ['',
         outputColors.green + 'Your application project is ready in ' + config.targetdir + '.',
         '',
         outputColors.cyan + 'To use your new application in Android Studio, do the following:' + outputColors.reset,
         '   - Launch Android Studio and select `Import project (Eclipse ADT, Gradle, etc.)` from the Welcome screen',
         '   - Navigate to the ' + outputColors.magenta + config.targetdir + '/' + config.appname + '/platforms/android' + outputColors.reset + ' folder, select it and click `Ok`',
         '   - From the dropdown that displays the available targets, choose the sample app you want to run and click the play button',
         ''].join('\n');
    console.log(nextStepsOutput);
    console.log(outputColors.cyan + 'Before you ship, make sure to plug your OAuth Client ID,\nCallback URI, and OAuth Scopes into '
        + outputColors.magenta + 'www/bootconfig.json' + outputColors.reset);
}

//
// Helper to create native or react native application
//
function createNativeOrReactNativeApp(config) {

    // Computed config
    config.projectDir = path.join(config.targetdir, config.appname);
    config.bootConfigPath = path.join(config.projectDir, 'res', 'values', 'bootconfig.xml');
    config.templatePackageDir = config.templatePackageName.replace(/\./g, path.sep);
    var packageSdkRootDir = path.resolve(__dirname, '..');
    config.templateDir = path.join(packageSdkRootDir, config.relativeTemplateDir);
    config.templateAppClassName = config.templateAppName + 'App';

    // Checking if config.projectDir already exists
    if (!fs.existsSync(config.projectDir)) {
        shelljs.mkdir('-p', config.projectDir);
    }

    // Copy the template files to the destination directory.
    shelljs.cp('-R', path.join(config.templateDir, '*'), config.projectDir);
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
        miscUtils.replaceTextInFile(file, templateAppClassNameRegExp, appClassName);
    });

    // Substitute app name
    console.log('Renaming application to ' + config.appname + ' in source.');
    contentFilesWithReplacements.forEach(function(file) {
        var templateAppNameRegExp = new RegExp(config.templateAppName, 'g');
        miscUtils.replaceTextInFile(file, templateAppNameRegExp, config.appname, file);
    });

    // Substitute package name.
    console.log('Renaming package name to ' + config.packagename + ' in source.');
    contentFilesWithReplacements.forEach(function(file) {
        var templatePackageNameRegExp = new RegExp(config.templatePackageName.replace(/\./g, '\\.'), 'g');
        miscUtils.replaceTextInFile(file, templatePackageNameRegExp, config.packagename);
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
        console.log('Adding SmartStore/SmartSync support.');
        console.log('Extending SmartSyncSDKManager instead of SalesforceSDKManager.');
        miscUtils.replaceTextInFile(appClassPath, 'SalesforceSDKManager', 'SmartSyncSDKManager');
        miscUtils.replaceTextInFile(appClassPath,
            'com.salesforce.androidsdk.app.SmartSyncSDKManager',
            'com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager',
            appClassPath);
        miscUtils.replaceTextInFile(appClassPath,
            'com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager.KeyInterface',
            'com.salesforce.androidsdk.app.SalesforceSDKManager.KeyInterface');
    }

    // Copy SalesforceSDK library project into the app folder as well, if it's not already there.
    // copy <Android Package>/libs/SalesforceSDK -> <App Folder>/forcedroid/libs/SalesforceSDK
    var salesforceSDKRelativePath = path.join('libs', 'SalesforceSDK');
    copyFromSDK(packageSdkRootDir, config.targetdir, salesforceSDKRelativePath);

    // Copy SalesforceAnalytics library project into the app folder as well, if it's not already there.
    // copy <Android Package>/libs/SalesforceAnalytics -> <App Folder>/forcedroid/libs/SalesforceAnalytics
    var salesforceAnalyticsRelativePath = path.join('libs', 'SalesforceAnalytics');
    copyFromSDK(packageSdkRootDir, config.targetdir, salesforceAnalyticsRelativePath);

    // Copy SmartStore and SmartSync library projects into the app folder as well, if it's not already there - if required.
    // copy <Android Package>/libs/SmartStore -> <App Folder>/forcedroid/libs/SmartStore
    // copy <Android Package>/libs/SmartSync -> <App Folder>/forcedroid/libs/SmartSync
    if (config.usesmartstore) {
        var smartStoreRelativePath = path.join('libs', 'SmartStore');
        copyFromSDK(packageSdkRootDir, config.targetdir, smartStoreRelativePath);
        var smartSyncRelativePath = path.join('libs', 'SmartSync');
        copyFromSDK(packageSdkRootDir, config.targetdir, smartSyncRelativePath);
    }

    // React native specific fixes
    if (config.apptype === 'react_native') {
        console.log('Changing name in package.json.');
        miscUtils.replaceTextInFile(path.join(config.projectDir, 'package.json'), config.templateAppName, config.appname);
        console.log('Changing app name in index.android.js.');
        miscUtils.replaceTextInFile(path.join(config.projectDir, 'js', 'index.android.js'), config.templateAppName, config.appname);

        // Copy SalesforceReact library project into the app folder as well.
        var salesforceReactRelativePath = path.join('libs', 'SalesforceReact');
        copyFromSDK(packageSdkRootDir, config.targetdir, salesforceReactRelativePath);

        // Moving up js directory and package.json
        console.log("Moving js directory up");
        shelljs.mv(path.join(config.projectDir, "js"), config.targetdir);
        console.log("Moving package.json up");
        shelljs.mv(path.join(config.projectDir, "package.json"), path.join(config.targetdir, 'package.json'))
    }

    createAppRootGradleFile(config);
    fixAppGradleFile(config);
    fixSdkGradleFiles(config);
    copyFromSDK(packageSdkRootDir, config.targetdir, "gradle.properties");
    copyFromSDK(packageSdkRootDir, config.targetdir, "gradlew.bat");
    copyFromSDK(packageSdkRootDir, config.targetdir, "gradlew");
    copyFromSDK(packageSdkRootDir, config.targetdir, "gradle");
    copyFromSDK(packageSdkRootDir, config.targetdir, "build.gradle");
    shelljs.mv(path.join(config.targetdir, "forcedroid", "gradle.properties"), path.join(config.targetdir, "gradle.properties"));
    shelljs.mv(path.join(config.targetdir, "forcedroid", "gradlew.bat"), path.join(config.targetdir, "gradlew.bat"));
    shelljs.mv(path.join(config.targetdir, "forcedroid", "gradlew"), path.join(config.targetdir, "gradlew"));
    shelljs.mv(path.join(config.targetdir, "forcedroid", "gradle"), path.join(config.targetdir, "gradle"));
    shelljs.mv(path.join(config.targetdir, "forcedroid", "build.gradle"), path.join(config.targetdir, "build.gradle"));
    miscUtils.replaceTextInFile(path.join(config.targetdir, "build.gradle"), 'group = \'com.salesforce.androidsdk\'', '');

    // Running npm install for react native apps
    if (config.apptype === 'react_native') {
        console.log("Running npm install to install npm packages required by react native.");
        shelljs.pushd(config.targetdir);
        shelljs.exec('npm install');
        shelljs.popd();
    }

    // Inform the user of next steps if requested.
    var nextStepsOutput =
        ['',
         outputColors.green + 'Your application project is ready in ' + config.targetdir + '.',
         '',
         outputColors.cyan + 'To use your new application in Android Studio, do the following:' + outputColors.reset,
         '   - Launch Android Studio and select `Import project (Eclipse ADT, Gradle, etc.)` from the Welcome screen ',
         '   - Navigate to the ' + outputColors.magenta + config.targetdir + outputColors.reset + ' folder, select it and click `Ok`',
         '   - From the dropdown that displays the available targets, choose the sample app you want to run and click the play button',
         '',
         outputColors.cyan + 'To work with your new project from the command line, use the following instructions:' + outputColors.reset,
         '   - cd ' + config.targetdir,
         '   - ./gradlew assembleDebug',
         ''].join('\n');
    console.log(nextStepsOutput);
    var relativeBootConfigPath = path.relative(config.targetdir, config.bootConfigPath);
    console.log(outputColors.cyan + 'Before you ship, make sure to plug your OAuth Client ID,\nCallback URI, and OAuth Scopes into '
                + outputColors.magenta + relativeBootConfigPath + '.' + outputColors.reset);
}

function makeContentReplacementPathsArray(config) {
    var returnArray = [];
    returnArray.push(path.join(config.projectDir, 'AndroidManifest.xml'));
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
// Creates a root level 'settings.gradle' file for the app
//
function createAppRootGradleFile(config) {
    console.log('Creating settings.gradle in ' + config.targetdir);
    var pathPrefix = (config.targetdir === '.' ? '' : config.targetdir);
    var salesforceSdkGradleSpec = "include 'forcedroid:libs:SalesforceSDK'\n";
    var smartStoreGradleSpec = "include 'forcedroid:libs:SmartStore'\n";
    var smartSyncGradleSpec = "include 'forcedroid:libs:SmartSync'\n";
    var salesforceReactGradleSpec = "include 'forcedroid:libs:SalesforceReact'\n";
    var appGradleSpec = "include '" + config.appname + "'";
    var salesforceAnalyticsGradleSpec = "include 'forcedroid:libs:SalesforceAnalytics'\n";

    var gradleSpec = salesforceSdkGradleSpec
        + salesforceAnalyticsGradleSpec
        + (config.usesmartstore ? smartStoreGradleSpec + smartSyncGradleSpec : "")
        + (config.apptype === 'react_native' ? salesforceReactGradleSpec : "")
        + appGradleSpec;

    fs.writeFileSync(path.join(config.targetdir, 'settings.gradle'), gradleSpec);
}

//
// Fixes library dependency references in the app's build.gradle files
//
function fixAppGradleFile(config) {
    if (config.apptype === 'react_native') {
        fixAppGradleFileHelper(config.targetdir, config.appname, "SalesforceReact", "SalesforceReact");
    }
    else {
        fixAppGradleFileHelper(config.targetdir, config.appname, "SalesforceSDK", config.usesmartstore ? "SmartSync" : "SalesforceSDK");
    }
}

function fixAppGradleFileHelper(appFolderName, appName, originalDependency, newDependency) {
    console.log('Tweaking build.gradle in ' + appFolderName + "/" + appName);
    var originalDependency = "compile project(':libs:" + originalDependency + "')";
    var newDependency = "compile project(':forcedroid:libs:" + newDependency + "')";
    miscUtils.replaceTextInFile(path.join(appFolderName, appName, "build.gradle"), originalDependency, newDependency);
}

//
// Fixes library dependency references in the SDK's build.gradle files
//
function fixSdkGradleFiles(config) {
    fixSdkGradleFileHelper(config.targetdir, "SalesforceSDK");
    if (config.usesmartstore) {
        fixSdkGradleFileHelper(config.targetdir, "SmartStore");
        fixSdkGradleFileHelper(config.targetdir, "SmartSync");
    }
    if (config.apptype === 'react_native') {
        fixSdkGradleFileHelper(config.targetdir, "SalesforceReact");
    }
}    

function fixSdkGradleFileHelper(appFolderName, lib) {
    console.log('Tweaking build.gradle for library ' + lib);
    miscUtils.replaceTextInFile(path.join(appFolderName, "forcedroid", "libs", lib, "build.gradle"), "compile project(':libs:", "compile project(':forcedroid:libs:"); 
}

//
// Processor list for 'create' command
//
function createArgsProcessorList() {
    var argProcessorList = new commandLineUtils.ArgProcessorList();

    // App type
    addProcessorFor(argProcessorList, 'apptype', 'Enter your application type (native, react_native, hybrid_remote, or hybrid_local):', 'App type must be native, react_native, hybrid_remote, or hybrid_local.', 
                    function(val) { return ['native', 'react_native', 'hybrid_remote', 'hybrid_local'].indexOf(val) >= 0; });

    // App name
    addProcessorFor(argProcessorList, 'appname', 'Enter your application name:', 'Invalid value for application name: \'$val\'.', /^\S+$/);

    // Target dir
    addProcessorForOptional(argProcessorList, 'targetdir', 'Enter the target directory of your app (defaults to current directory):');

    // Package name
    addProcessorFor(argProcessorList, 'packagename', 'Enter the package name for your app (com.mycompany.my_app):', '\'$val\' is not a valid Java package name.', /^[a-z]+[a-z0-9_]*(\.[a-z]+[a-z0-9_]*)*$/);

    // Start page
    addProcessorFor(argProcessorList, 'startpage', 'Enter the start page for your app (only applicable for hybrid_remote apps):', 'Invalid value for start page: \'$val\'.', /\S+/, 
                    function(argsMap) { return (argsMap['apptype'] === 'hybrid_remote'); });

    // Use SmartStore
    addProcessorFor(argProcessorList, 'usesmartstore', 'Do you want to use SmartStore or SmartSync in your app? [yes/NO] (\'No\' by default)', 'Use smartstore must be yes or no.',
                    function(val) {
                        if (val.trim() === '') return true;
                        return ['yes', 'no'].indexOf(val.toLowerCase()) >= 0;
                    },
                    function(argsMap) { return (argsMap['apptype'] === 'native'); },
                    function(val) { return (val.toLowerCase() === 'yes'); });

    return argProcessorList;
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

//
// Helper function to add arg processor for optional arg- should unset value when nothing is typed in
// * argProcessorList: ArgProcessorList
// * argName: string, name of argument
// * prompt: string for prompt
//
function addProcessorForOptional(argProcessorList, argName, prompt) {
    addProcessorFor(argProcessorList, argName, prompt, undefined, function() { return true;}, undefined, undefined);
}
