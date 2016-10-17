#!/usr/bin/env node

var version='4.1.0',
    execSync = require('child_process').execSync,
    path = require('path'),
    shelljs = require('shelljs'),
    commandLineUtils = require('../external/shared/node/commandLineUtils')
;

var outputColors = {
    'red': '\x1b[31;1m',
    'green': '\x1b[32;1m',
    'yellow': '\x1b[33;1m',
    'magenta': '\x1b[35;1m',
    'cyan': '\x1b[36;1m',
    'reset': '\x1b[0m'
}


// Calling main
main(process.argv);

// 
// Main function
// 
function main(args) {
    var commandLineArgs = process.argv.slice(2, args.length);
    var parsedArgs = commandLineUtils.parseArgs(commandLineArgs);

    if (parsedArgs.hasOwnProperty('usage')) {
        usage();
        process.exit(1);
    }
    else {
        var fork = parsedArgs.fork || 'forcedotcom';
        var branch = parsedArgs.branch || 'unstable';
        var pluginFork = parsedArgs.pluginFork || 'forcedotcom';
        var pluginBranch = parsedArgs.pluginBranch || 'unstable';
        var chosenAppTypes = parsedArgs.test || '';
        
        cleanup();
        var tmpDir = mkTmpDir();
        var repoDir = cloneRepo(tmpDir, 'https://github.com/' + fork + '/SalesforceMobileSDK-Android', branch);
        createDeployForcedroidPackage(repoDir, tmpDir);

        var nativeAppTypes = ['native', 'react_native'];
        for (var i = 0; i<nativeAppTypes.length; i++) {
            var appType = nativeAppTypes[i];
            if (chosenAppTypes.indexOf(appType) >= 0) createCompileApp(tmpDir, appType);
        }

        if (chosenAppTypes.indexOf('hybrid') >= 0) {
            var pluginRepoDir = clonePluginRepo(tmpDir, 'https://github.com/' + pluginFork + '/SalesforceMobileSDK-CordovaPlugin', pluginBranch);
            updatePluginRepo(tmpDir, pluginRepoDir, branch);
            var hybridAppTypes = ['hybrid_local', 'hybrid_remote'];
            for (var i = 0; i<hybridAppTypes.length; i++) {
                var appType = hybridAppTypes[i];
                if (chosenAppTypes.indexOf(appType) >= 0) createCompileHybridApp(tmpDir, appType);
            }
        }
    }
}

//
// Usage
//
function usage() {
    log('Usage:',  'cyan');
    log('  test_forcedroid.js --usage\n'
        + 'OR \n'
        + '  test_forcedroid.js\n'
        + '    [--fork=FORK (defaults to forcedotcom)]\n'
        + '    [--branch=BRANCH (defaults to unstable)]\n'
        + '    [--pluginFork=PLUGIN_FORK (defaults to forcedotcom)]\n'
        + '    [--pluginBranch=PLUGIN_BRANCH (defaults to unstable)]\n'
        + '    [--test=appType1,appType2,etc]\n'
        + '      where appTypes are in: native, react_native, hybrid_local, hybrid_remote\n'
        + '\n'
        + '  Clones https://github.com/FORK/SalesforceMobileSDK-Android at branch BRANCH\n'
        + '  Generates forcedroid package and deploys it to a temporary directory\n'
        + '  Creates and compile the application types selected\n'
        + '  For hybrid apps, it also\n'
        + '    clones https://github.com/PLUGIN_FORK/SalesforceMobileSDK-CordovaPlugin at branch PLUGIN_BRANCH\n'
        + '    runs ./tools/update.sh -b BRANCH to update clone of plugin repo\n'
        + '    edit node_modules/forcedroid/node/forcedroid.js to cordova plugin add from the local clone of the plugin repo\n'
        , 'magenta');
}

//
// Cleanup
//
function cleanup() {
    log('Cleaning up temp dirs', 'green');
    shelljs.rm('-rf', 'tmp');
}

//
// Make temp dir and return its path
//
function mkTmpDir() {
    var tmpDir = path.join('tmp', 'testforcedroid' + random(1000));
    log('Making temp dir:' + tmpDir, 'green');
    shelljs.mkdir('-p', tmpDir);
    return tmpDir;
}

//
// Clone Android repo and return its path
// 
function cloneRepo(tmpDir, repoUrl, branch) {
    log('Cloning ' + repoUrl + ' at ' + branch, 'green');
    var repoDir = path.join(tmpDir, 'SalesforceMobileSDK-Android');
    shelljs.mkdir('-p', repoDir);
    runProcess('git clone --branch ' + branch + ' --single-branch --depth 1 --recurse-submodules ' + repoUrl + ' ' + repoDir);
    return repoDir;
}

//
// Create and deploy forcedroid
//
function createDeployForcedroidPackage(repoDir, tmpDir) {
    log('Generating forcedroid package', 'green');
    runProcess('ant -f ' + path.join(repoDir, 'build_npm.xml'));
    runProcess('npm install --prefix ' + tmpDir + ' ' + path.join(repoDir, 'forcedroid-' + version + '.tgz'));
}

//
// Create and compile non-hybrid app 
//
function createCompileApp(tmpDir, appType) {
    log('Creating ' + appType + ' app ', 'green');
    var appName = appType + 'App';
    var targetDir = path.join(tmpDir, appName);
    shelljs.mkdir('-p', targetDir);
    var appId = '3MVG9Iu66FKeHhINkB1l7xt7kR8czFcCTUhgoA8Ol2Ltf1eYHOU4SqQRSEitYFDUpqRWcoQ2.dBv_a1Dyu5xa';
    var callbackUri = 'testsfdc:///mobilesdk/detect/oauth/done';
    var forcedroidPath = path.join(tmpDir, 'node_modules', '.bin', 'forcedroid');
    var forcedroidArgs = 'create '
        + ' --apptype=' + appType
        + ' --appname=' + appName
        + ' --packagename=com.mycompany'
        + ' --targetdir=' + targetDir
        + ' --usesmartstore=yes';
    runProcess(forcedroidPath + ' ' + forcedroidArgs);

    shelljs.pushd(targetDir);
    runProcess('./gradlew');    
    shelljs.popd();
}

//
// Clone cordova plugin repo and return its path
// 
function clonePluginRepo(tmpDir, pluginRepoUrl, branch) {
    log('Cloning ' + pluginRepoUrl + ' at ' + branch, 'green');
    var pluginRepoDir = path.join(tmpDir, 'SalesforceMobileSDK-CordovaPlugin');
    shelljs.mkdir('-p', pluginRepoDir);
    runProcess('git clone --branch ' + branch + ' --single-branch --depth 1 --recurse-submodules ' + pluginRepoUrl + ' ' + pluginRepoDir);
    return pluginRepoDir;
}

//
// Update cordova plugin repo and point forcedroid to it
//
function updatePluginRepo(tmpDir, pluginRepoDir, branch) {
    log('Updating cordova plugin at ' + branch, 'green');
    shelljs.pushd(pluginRepoDir);
    runProcess(path.join('tools', 'update.sh') + ' -b ' + branch);    
    shelljs.popd();

    // Pointing to pluginRepoDir in forcedroid.js
    shelljs.sed('-i', /'cordova plugin add .*'/g, '\'cordova plugin add ../SalesforceMobileSDK-CordovaPlugin\'', path.join(tmpDir, 'node_modules', 'forcedroid', 'node', 'forcedroid.js'));
}    

//
// Create and compile hybrid app 
//
function createCompileHybridApp(tmpDir, appType) {
    log('Creating ' + appType + ' app ', 'green');
    var appName = appType + 'App';
    var appId = '3MVG9Iu66FKeHhINkB1l7xt7kR8czFcCTUhgoA8Ol2Ltf1eYHOU4SqQRSEitYFDUpqRWcoQ2.dBv_a1Dyu5xa';
    var callbackUri = 'testsfdc:///mobilesdk/detect/oauth/done';
    var forcedroidPath = path.join(tmpDir, 'node_modules', '.bin', 'forcedroid');
    var forcedroidArgs = 'create '
        + ' --apptype=' + appType
        + ' --appname=' + appName
        + ' --packagename=com.mycompany'
        + ' --targetdir=' + tmpDir;
    if (appType === 'hybrid_remote') {
        forcedroidArgs += ' --startpage=/apex/testPage';
    }

    runProcess(forcedroidPath + ' ' + forcedroidArgs);
    shelljs.pushd(path.join(tmpDir, appName));
    runProcess('cordova build');    
    shelljs.popd();
}


//
// Helper to run arbitrary shell command
//
function runProcess(cmd) {
    log('Running: ' + cmd);
    try {
        execSync(cmd);
    } catch (err) {
        log('!Failed!', 'red');
        console.error(err.stderr.toString());
    }
}

//
// Print important information
//
function log(msg, color) {
    if (color) {
        console.log(outputColors[color] + msg + outputColors.reset);
    }
    else {
        console.log(msg);
    }
}


//
// Return random number between n/10 and n
//
function random(n) {
    return (n/10)+Math.floor(Math.random()*(9*n/10));
}

