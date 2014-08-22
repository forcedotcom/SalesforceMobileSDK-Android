#!/usr/bin/env node

var repoUtils = require('../external/shared/node/repoUtils');
var fs = require('fs');
var path = require('path');

var symlinkOutputFile = path.resolve(path.join(__dirname, 'symlink_files.txt'));
var repoRootPath = path.resolve(path.join(__dirname, '..'));
var symlinkFiles = repoUtils.getSymLinkFiles(repoRootPath);

// First, fix the paths and filenames.
for (var i = 0; i < symlinkFiles.length; i++) {
	var filesObj = symlinkFiles[i];
	filesObj.sourceFile...
}