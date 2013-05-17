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

 // This prepublish script (and therefore package publishing) is only compatible
 // with node.js on Unix-based OSes at this point.  Platform agnostic functionality will be
 // somewhere down the line, if ever.
 if (/^win/i.test(process.platform)) {
 	console.log('Publishing of this package is incompatible with Windows.');
 	process.exit(1);
 }

var fs = require('fs');
var path = require('path');
var exec = require('child_process').exec;
var publishUtils = require('./publishutils');

// Make hard copies of symlink files.  npm does not pack symlinks.
var symLinkFileEntries = publishUtils.getSymLinkFiles();
resolveSymLinks(symLinkFileEntries, function(success, msg) {
	if (msg) console.log(msg);
	if (!success) {
		process.exit(2);
	}
});

function resolveSymLinks(symLinkFileEntries, callback) {
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
}