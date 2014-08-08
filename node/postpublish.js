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

var exec = require('child_process').exec;
var path = require('path');
var publishUtils = require('./publishutils');

// Move the original README back into place.
var readmePath = path.resolve(path.join(__dirname, '..', 'README.md'));
var readmeBackupPath = readmePath + '.orig';
console.log('Moving original repo README file back into place.');
exec('mv "' + readmeBackupPath + '" "' + readmePath + '"', function (error, stdout, stderr) {
	if (error) {
		console.log('WARNING: Could not move ' + readmeBackupPath + ' to ' + readmePath + ': ' + error);
	}

	// Revert symlinks from the root of the git repo.
	var absGitRepoPath = path.resolve(__dirname, '..');
	process.chdir(absGitRepoPath);

	var symLinkEntries = publishUtils.getSymLinkFiles();
	gitRevertSymLinks(symLinkEntries, function() {
		console.log('Finished reverting symlink files in git.');
	});
});

function gitRevertSymLinks(symLinkEntries, callback) {
	if (symLinkEntries.length === 0) {  // We're done.
		return callback();
	}

	var destFilePath = symLinkEntries.shift().destFile;
	destFilePath = path.relative(process.cwd(), destFilePath);
	var destFileDir = path.dirname(destFilePath);
	var destFileName = path.basename(destFilePath);
	exec('git checkout -- "' + destFileName + '"', { 'cwd': destFileDir }, function (error, stdout, stderr) {
		if (error) {
			console.log('WARNING: Could not revert file \'' + destFilePath + '\' in git: ' + error);
		}
		gitRevertSymLinks(symLinkEntries, callback);
	});
}
