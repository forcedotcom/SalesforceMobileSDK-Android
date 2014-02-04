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
        commandHandler = create;
        break;
    case 'samples': 
        processorList = samplesArgsProcessorList(); 
        commandHandler = samples;
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
    console.log('    --targetApi=<Target Api e.g. 19 for KitKat>');
    console.log('    --startpage=<Path to the remote start page> (/apex/MyPage — Only required/used for \'hybrid_remote\')');
    console.log('    [--usesmartstore=<Whether or not to use SmartStore> (\'true\' or \'false\'. false by default)]');
    console.log(outputColors.cyan + '\nOR\n');
    console.log(outputColors.magenta + 'forcedroid samples');
    console.log('    --targetdir=<Target Samples Folder>' + outputColors.reset);
    console.log('    --targetApi=<Target Api e.g. 19 for KitKat>');
}

//
// Helper to 'samples' command
//
function samples(config) {
    // Sets properties and copies over the 'FileExplorer' app.
    createNativeApp({targetdir: config.targetdir,
                     apptype: 'native',
                     appname: 'FileExplorer',
                     packagename: 'com.salesforce.samples.fileexplorer',
                     usesmartstore: false,
                     relativeTemplateDir: 'native/SampleApps/FileExplorer',
                     templateAppName: 'FileExplorer',
                     templatePackageName: 'com.salesforce.samples.fileexplorer'});

/* FIXME This one is broken (did it ever work?
    // Sets properties and copies over the 'NativeSqlAggregator' app.
    createNativeApp({targetdir: config.targetdir,
                     apptype: 'native',
                     appname: 'NativeSqlAggregator',
                     packagename: 'com.salesforce.samples.nativesqlaggregator',
                     usesmartstore: true,
                     relativeTemplateDir: 'native/SampleApps/NativeSqlAggregator',
                     templateAppName: 'NativeSqlAggregator',
                     templatePackageName: 'com.salesforce.samples.nativesqlaggregator'});
*/

    // Sets properties and copies over the 'RestExplorer' app.
    createNativeApp({targetdir: config.targetdir,
                     apptype: 'native',
                     appname: 'RestExplorer',
                     packagename: 'com.salesforce.samples.restexplorer',
                     usesmartstore: false,
                     relativeTemplateDir: 'native/SampleApps/RestExplorer',
                     templateAppName: 'RestExplorer',
                     templatePackageName: 'com.salesforce.samples.restexplorer'});
}

//
// Helper for 'create' command
//
function create(config) {
    // Native app creation
    if (config.apptype === 'native') {
        config.relativeTemplateDir = 'native/TemplateApp';
        config.templateAppName = 'Template';
        config.templatePackageName = 'com.salesforce.samples.templateapp';
        createNativeApp(config);
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

    shelljs.exec('cordova create ' + config.projectDir + ' ' + config.packagename + ' ' + config.appname);
    shelljs.pushd(config.projectDir);
    shelljs.exec('cordova platform add android');
    shelljs.exec('cordova plugin add https://github.com/wmathurin/SalesforceMobileSDK-CordovaPlugin');
    shelljs.exec('node plugins/com.salesforce/tools/postinstall.js 19 ' + config.usesmartstore);

    var bootconfig = {
        "remoteAccessConsumerKey": "3MVG9Iu66FKeHhINkB1l7xt7kR8czFcCTUhgoA8Ol2Ltf1eYHOU4SqQRSEitYFDUpqRWcoQ2.dBv_a1Dyu5xa",
        "oauthRedirectURI": "testsfdc:///mobilesdk/detect/oauth/done",
        "oauthScopes": ["web", "api"],
        "isLocal": config.apptype === 'hybrid_local',
        "startPage": config.startPage || 'index.html',
        "errorPage": "error.html",
        "shouldAuthenticate": true,
        "attemptOfflineLoad": false,
        "androidPushNotificationClientId": ""
    };
    // console.log("Bootconfig:" + JSON.stringify(bootconfig, null, 2));

    fs.writeFileSync('www/bootconfig.json', JSON.stringify(bootconfig, null, 2));
    shelljs.exec('cordova build');
    shelljs.popd();
}

//
// Helper to create native application
//
function createNativeApp(config) {
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

    // Copy the SDK into the app folder as well, if it's not already there.
    var destSdkDir = path.join(config.targetdir, path.basename(packageSdkRootDir));
    if (!fs.existsSync(destSdkDir)) {
        shelljs.cp('-R', packageSdkRootDir, config.targetdir);
        if (shelljs.error()) {
            console.log('There was an error copying the SDK directory from \'' + packageSdkRootDir + '\' to \'' + config.targetdir + '\': ' + shelljs.error());
            process.exit(5);
        }
    } else {
        console.log(outputColors.cyan + 'INFO:' + outputColors.reset + ' SDK directory \'' + destSdkDir + '\' already exists.  Skipping copy.');
    }

    var contentFilesWithReplacements = makeContentReplacementPathsArray(config);

    // Library project reference
    console.log(outputColors.yellow + 'Adjusting SalesforceSDK library project reference in project.properties.');
    var absNativeSdkPath = path.join(destSdkDir, 'native', 'SalesforceSDK');
    var nativeSdkPathRelativeToProject = path.relative(config.projectDir, absNativeSdkPath);
    var projectPropertiesFilePath = path.join(config.projectDir, 'project.properties');
    shelljs.sed('-i', /=.*SalesforceSDK/g, '=' + nativeSdkPathRelativeToProject, projectPropertiesFilePath);

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

        console.log('Adding SmartStore library reference in project.properties.');
        var projectPropertiesContent = shelljs.cat(projectPropertiesFilePath);
        var smartStoreAbsPath = path.join(destSdkDir, 'hybrid', 'SmartStore');
        var smartStorePathRelativeToProject = path.relative(config.projectDir, smartStoreAbsPath);
        projectPropertiesContent += 'android.library.reference.1=' + smartStorePathRelativeToProject + '\n';
        projectPropertiesContent.to(projectPropertiesFilePath);

        console.log('Extending SalesforceSDKManagerWithSmartStore instead of SalesforceSDKManager.');
        shelljs.sed('-i', /SalesforceSDKManager/g, 'SalesforceSDKManagerWithSmartStore', appClassPath);
        shelljs.sed('-i',
            /com\.salesforce\.androidsdk\.app\.SalesforceSDKManagerWithSmartStore/g,
            'com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore',
            appClassPath);
    }

    // Inform the user of next steps.
    var sdkLibrariesToIncludeMsg = 'the ' + outputColors.magenta + path.join(path.basename(destSdkDir), 'native', 'SalesforceSDK')
        + outputColors.reset + ' library project';
    if (config.usesmartstore) {
        sdkLibrariesToIncludeMsg += ', the ' + outputColors.magenta + path.join(path.basename(destSdkDir), 'hybrid', 'SmartStore')
        + outputColors.reset + ' library project';
    }
    var nextStepsOutput =
        ['',
         outputColors.green + 'Your application project is ready in ' + config.targetdir + '.',
         '',
         outputColors.cyan + 'To build the new application, do the following:' + outputColors.reset,
         '   - cd ' + config.projectDir,
         '   - $ANDROID_SDK_DIR/android update project -p .',
         '   - ant clean debug',
         '',
         outputColors.cyan + 'To run the application, start an emulator or plug in your device and run:' + outputColors.reset,
         '   - ant installd',
         '',
         outputColors.cyan + 'To use your new application in Eclipse, do the following:' + outputColors.reset,
         '   - Import ' + sdkLibrariesToIncludeMsg + ',',
         '     and the ' + outputColors.magenta + config.appname + outputColors.reset + ' project into your workspace',
         '   - Choose \'Build All\' from the Project menu',
         '   - Run your application by choosing "Run as Android application"',
         ''].join('\n');
    console.log(nextStepsOutput);
    var relativeBootConfigPath = path.relative(config.targetdir, config.bootConfigPath);
    console.log(outputColors.cyan + 'Before you ship, make sure to plug your OAuth Client ID,\nCallback URI, and OAuth Scopes into '
        + outputColors.magenta + relativeBootConfigPath + '.' + outputColors.reset);
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
    addProcessorFor(argProcessorList, 'targetapi', 'Enter the target api for your application (number between 8 (Froyo) and 19 (KitKat):', 'Target api must be a number between 8 and 19.', 
                    function(val) { var intVal = parseInt(val); return intVal >= 8 && intVal <= 19; });

    // Package name
    addProcessorFor(argProcessorList, 'packagename', 'Enter the package name for your app (com.mycompany.my_app):', '\'$val\' is not a valid Java package name.', /^[a-z]+[a-z0-9_]*(\.[a-z]+[a-z0-9_]*)*$/);

    // Start page
    addProcessorFor(argProcessorList, 'startpage', 'Enter the start page for your app (only applicable for hybrid_remote apps):', 'Invalid value for start page: \'$val\'.', /\S+/, 
                    function(argsMap) { return (argsMap['apptype'] === 'hybrid_remote'); });

    // Use SmartStore
    addProcessorFor(argProcessorList, 'usesmartstore', 'Do you want to use SmartStore in your app? [yes/NO] (\'No\' by default)', 'Use smartstore must be yes or no.',
                    function(val) { return ['yes', 'no'].indexOf(val.toLowerCase()) >= 0; },
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
