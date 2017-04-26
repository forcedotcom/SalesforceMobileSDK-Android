#!/usr/bin/env node

var repoUtils = require('./repoUtils');
var fs = require('fs');
var path = require('path');

var symlinkOutputFile = path.resolve(path.join(__dirname, 'symlink_files.txt'));
var repoRootPath = path.resolve(path.join(__dirname, '..'));
var symlinkFiles = repoUtils.getSymLinkFiles(repoRootPath);

// First, fix the paths and filenames.
for (var i = 0; i < symlinkFiles.length; i++) {
	var filesObj = symlinkFiles[i];
	filesObj.sourceFile = path.relative(repoRootPath, filesObj.sourceFile);
	filesObj.destFile = path.relative(repoRootPath, filesObj.destFile);
	filesObj.sourceFile = filesObj.sourceFile.replace(/\//g, '\\');
	filesObj.destFile = filesObj.destFile.replace(/\//g, '\\');
}

// Now sort them.
symlinkFiles.sort(function(filesObj1, filesObj2) {
	if (filesObj1.sourceFile < filesObj2.sourceFile)
		return -1;
	else if (filesObj1.sourceFile > filesObj2.sourceFile)
		return 1;
	else {
		if (filesObj1.destFile < filesObj2.destFile)
			return -1;
		else if (filesObj1.destFile > filesObj2.destFile)
			return 1;
		else
			return 0;
	}
});

// Build and write output.
var outputString = '';
for (var i = 0; i < symlinkFiles.length; i++) {
	var filesObj = symlinkFiles[i];
	outputString += '"' + filesObj.sourceFile + '" "' + filesObj.destFile + '"\r\n';
}
fs.writeFileSync(symlinkOutputFile, outputString, { 'encoding' : 'utf8' });
