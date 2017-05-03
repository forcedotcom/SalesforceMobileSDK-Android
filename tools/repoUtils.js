#!/usr/bin/env node

/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

var fs = require('fs');
var path = require('path');
var exec = require('child_process').exec;

var getSymLinkFiles = function(dir) {
	var linkFileList = [];
	var fileList = fs.readdirSync(dir);
	for (var i = 0; i < fileList.length; i++) {
		var destFilePath = path.resolve(path.join(dir, fileList[i]));
		var destFileStat = fs.lstatSync(destFilePath);
		if (destFileStat.isSymbolicLink()) {
			var linkSrcPath = path.resolve(dir, fs.readlinkSync(destFilePath));
			linkFileList.push({ 'sourceFile': linkSrcPath, 'destFile': destFilePath });
		} else if (destFileStat.isDirectory()) {
			linkFileList = linkFileList.concat(getSymLinkFiles(destFilePath));
		}
	}
	return linkFileList;
};

var resolveSymLinks = function(symLinkFileEntries, callback) {
	if (symLinkFileEntries.length === 0) {
		return callback(true, 'Successfully copied symlink files.');
	}

	// Remove the destination link, then copy the source file or directory to the destination file.
	var filesObj = symLinkFileEntries.shift();
	try {
		fs.unlinkSync(filesObj.destFile);
	} catch (err) {
		return callback(false, 'FATAL: Could not remove existing symlink file \'' + filesObj.destFile + '\'.');
	}

	console.log('Copying \'' + filesObj.sourceFile + '\' to \'' + filesObj.destFile + '\'.');
	exec('cp -R "' + filesObj.sourceFile + '" "' + filesObj.destFile + '"', function (error, stdout, stderr) {
		if (error) {
			return callback(false, 'FATAL: Could not copy \'' + filesObj.sourceFile + '\' to \'' + filesObj.destFile + '\'.');
		} else {
			// Next!
			resolveSymLinks(symLinkFileEntries, callback);
		}
	});
};

var revertSymLinks = function(symLinkEntries, repoRootPath, callback) {
	if (symLinkEntries.length === 0) {  // We're done.
		return callback();
	}

	process.chdir(repoRootPath);
	var destFilePath = symLinkEntries.shift().destFile;
	destFilePath = path.relative(repoRootPath, destFilePath);
	var destFileDir = path.dirname(destFilePath);
	var destFileName = path.basename(destFilePath);
	exec('git checkout -- "' + destFileName + '"', { 'cwd': destFileDir }, function (error, stdout, stderr) {
		if (error) {
			console.log('WARNING: Could not revert file \'' + destFilePath + '\' in git: ' + error);
		}
		revertSymLinks(symLinkEntries, repoRootPath, callback);
	});
};

var writeSymLinkOutput = function(symLinkEntries, outputPath) {
	fs.writeFileSync(outputPath, JSON.stringify(symLinkEntries), { 'encoding': 'utf8' });
};

var readSymLinkInput = function(inputPath) {
	if (!fs.existsSync(inputPath)) {
		throw new Error('Input file at ' + inputPath + ' does not exist.');
	}

	var symLinkFilesString = fs.readFileSync(inputPath, { 'encoding': 'utf8' });
	var symLinkFiles = JSON.parse(symLinkFilesString);
	return symLinkFiles;
};

module.exports.getSymLinkFiles = getSymLinkFiles;
module.exports.resolveSymLinks = resolveSymLinks;
module.exports.revertSymLinks = revertSymLinks;
module.exports.writeSymLinkOutput = writeSymLinkOutput;
module.exports.readSymLinkInput = readSymLinkInput;
