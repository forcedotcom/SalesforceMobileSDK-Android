#!/usr/bin/env node

var exec = require('child_process').exec;
var commandLineUtils = require('./commandLineUtils');
var path = require('path');

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
    
    // Start the ant app build process chain.
    exec('ant -version', postAntVersion);
}

function postAntVersion(error, stdout, stderr) {
    if (error !== null) {
        console.log('Could not validate access to ant.  You must have ant installed to use this app.');
        process.exit(4);
    } else {
        // Ant install is validated.  Proceed with app creation.
        var transposedArgArray = convertCommandLineArgs();
        var buildFilePath = path.join(__dirname, '..', 'build.xml');
        exec('ant -buildfile ' + buildFilePath + ' ' + transposedArgArray.join(' '), postCreateApp);
    }
}

function postCreateApp(error, stdout, stderr) {
    if (stdout) console.log(stdout);
    if (stderr) console.log(stderr);
    if (error !== null) {
        console.log('There was an error creating the app.');
        usage();
        process.exit(5);
    } else {
        console.log('Congratulations!  You have successfully created your app.');
    }
}

function convertCommandLineArgs() {
    var antCommandLineArgs = [];
    switch (commandLineArgsMap.apptype) {
        case 'native':
            antCommandLineArgs.push('create_native');
            break;
        case 'hybrid_remote':
            antCommandLineArgs.push('create_hybrid_vf');
            break;
        case 'hybrid_local':
            antCommandLineArgs.push('create_hybrid_local');
            break;
    }
    antCommandLineArgs.push('-Dapp.name=' + commandLineArgsMap.appname);
    antCommandLineArgs.push('-Dtarget.dir=' + commandLineArgsMap.targetdir);
    antCommandLineArgs.push('-Dpackage.name=' + commandLineArgsMap.packagename);
    if (typeof commandLineArgsMap.usesmartstore !== 'undefined')
        antCommandLineArgs.push('-Duse.smartstore=' + commandLineArgsMap.usesmartstore);
    if (typeof commandLineArgsMap.apexpage !== 'undefined')
        antCommandLineArgs.push('-Dapex.page=' + commandLineArgsMap.apexpage);

    return antCommandLineArgs;
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

    return true;
}