module.exports.makeRecursiveDir = function(dirPath, callback) {
    dirPath = dirPath.trim();
    if (dirPath === '') {
        callback(makeErrorResult('No path specified.'));
        return;
    }

    // Make every path absolute, and normalize it.
    dirPath = path.resolve(dirPath);
    dirPath = path.normalize(dirPath);

    var dirPathComponents = dirPath.split(path.sep);
    var startingDir;
    if (process.platform === 'win32') {
        // Absolute Windows path will have a drive letter at the beginning.
        startingDir = dirPathComponents.shift() + path.sep;
    } else {
        startingDir = path.sep;
        dirPathComponents.shift();
    }
    if (dirPathComponents[dirPathComponents.length - 1] === '') dirPathComponents.pop();  // Kill trailing slash.

    makeRecursiveDirHelper(startingDir, dirPathComponents, callback);
}

function makeRecursiveDirHelper(startingDir, dirPathComponents, callback) {
    // Check the existence of the starting dir.
    fs.lstat(startingDir, function(err, stats) {
        if (err) {
            // Assume the presence of 'err' means the folder does not exist.  Try to create it.
            fs.mkdir(startingDir, '0755', function(mkdirErr) {
                if (mkdirErr) {
                    callback(makeErrorResult('Could not create folder \'' + startingDir + '\''));
                } else {
                    // Success!
                    var nextDir = nextDirCreationPath(startingDir, dirPathComponents);
                    if (nextDir === null) {
                        // We're done.
                        callback(makeSuccessResult());
                    } else {
                        makeRecursiveDirHelper(nextDir, dirPathComponents, callback);
                    }
                }
            });
        } else {
            // File entity exists.  Is it valid, i.e. a directory?
            if (!stats.isDirectory()) {
                // No!
                callback(makeErrorResult('The file \'' + startingDir + '\' is not a directory.  Cannot create path.'));
            } else {
                // It's a directory.  Carry on.
                var nextDir = nextDirCreationPath(startingDir, dirPathComponents);
                if (nextDir === null) {
                    // We're done.
                    callback(makeSuccessResult());
                } else {
                    makeRecursiveDirHelper(nextDir, dirPathComponents, callback);
                }
            }
        }
    });
}

function nextDirCreationPath(lastDir, dirPathComponents) {
    if (dirPathComponents.length === 0) {
        // We're done.
        return null;
    } else {
        var nextComponent = dirPathComponents.shift();
        return path.join(lastDir, nextComponent);
    }
}

function makeErrorResult(errorDesc) {
    return makeResult(false, errorDesc);
}

function makeSuccessResult() {
    return makeResult(true, null);
}

function makeResult(success, errorDesc) {
    return { 'success': success, 'description': errorDesc };
}