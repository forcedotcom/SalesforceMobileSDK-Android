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
