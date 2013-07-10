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
 
var fs = require('fs');
var path = require('path');
var isWindows = (process.platform === "win32");
var pathSep = path.sep || (isWindows ? '\\' : '/');

module.exports.getSymLinkFiles = function() {
	var symlinkFilesPath = path.resolve(path.join(__dirname, '..', 'tools', 'symlink_files.txt'));

	var data;
	try {
		data = fs.readFileSync(symlinkFilesPath, 'utf8');
	} catch (err) {
		throw err;
	}
	var lines = data.split(/\r?\n/);
	var outLines = [];
	for (var i = 0; i < lines.length; i++) {
		var filesLine = lines[i];
		filesLine = filesLine.trim();
		if (filesLine !== '') {
			var sourceDestRegExp = /"([^"]+)"\s+"([^"]+)"/;
			var sourceFile = filesLine.replace(sourceDestRegExp, '$1').replace(/\\/g, pathSep);
			sourceFile = path.resolve(path.join(__dirname, '..', sourceFile));
			var destFile = filesLine.replace(sourceDestRegExp, '$2').replace(/\\/g, pathSep);
			destFile = path.resolve(path.join(__dirname, '..', destFile));
			outLines.push({ 'sourceFile': sourceFile, 'destFile': destFile });
		}
	}

	return outLines;
};