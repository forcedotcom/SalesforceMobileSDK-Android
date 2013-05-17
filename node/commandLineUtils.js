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

// Helpder module for parsing the command line arguments to our package front-end.

module.exports.parseArgs = function(argsArray) {
    var argMap = {};
    for (var i = 0; i < argsArray.length; i++) {
        var fullArg = argsArray[i];
        var argSplitRegExp = /^--([^=]+)(=(.+))?$/;
        if (!argSplitRegExp.test(fullArg)) {
            console.log('Illegal argument: ' + fullArg);
            return null;
        }
        var argName = fullArg.replace(argSplitRegExp, "$1");
        argName = argName.toLocaleLowerCase();
        var argVal = fullArg.replace(argSplitRegExp, "$3");
        argMap[argName] = argVal;
    }

    return argMap;
};

module.exports.requiredArgsPresent = function(argMap, argNamesArray) {
    var allRequiredArgsPresent = true;
    for (var i = 0; i < argNamesArray.length; i++) {
        var argNameObj = argNamesArray[i];
        var valueRequired = true;
        var argName;
        if (typeof argNameObj === 'string') {
            argName = argNameObj;
        } else {
            // Additional options for arg parsing.
            if (!validateExtendedArgObject(argNameObj)) {
                return false;
            }
            argName = argNameObj.name;
            valueRequired = argNameObj.valueRequired;
        }

        var argVal = argMap[argName];
        if (typeof argVal === 'undefined') {
            console.log('The required argument \'' + argName + '\' is not present.');
            allRequiredArgsPresent = false;
        } else if ((argVal === null || argVal.trim() === '') && valueRequired) {
            console.log('The argument \'' + argName + '\' requires a value.');
            allRequiredArgsPresent = false;
        }
    }
    
    return allRequiredArgsPresent;
};

function validateExtendedArgObject(arg) {
    if (typeof arg !== 'object') {
        console.log('Extended arg is not an object (' + (typeof arg) + ').');
        return false;
    }
    if (typeof arg.name !== 'string') {
        console.log('Extended arg\'s \'name\' property must be a string (currently \'' + (typeof arg.name) + '\').');
        return false;
    }
    if (arg.name.trim() === '') {
        console.log('Arg name cannot be empty.');
        return false;
    }
    if (typeof arg.valueRequired !== 'boolean') {
        console.log('Extended arg\'s \'valueRequired\' property must be a boolean (currently \'' + (typeof arg.valueRequired) + '\').');
        return false;
    }

    return true;
}
