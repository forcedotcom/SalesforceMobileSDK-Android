/**
 * Utility functionality for hybrid apps.
 * Note: This JS module assumes the inclusion of a) the PhoneGap JS libraries and
 * b) the jQuery libraries.
 */

if (SFHybridApp == undefined) {

var SFHybridApp = {

appStartTime: new Date(),  // Used for debug timing measurements.

/**
 * Logs debug messages to a "debug console" section of the page.  Only
 * shows when debugMode (above) is set to true.
 *   txt - The text (html) to log to the console.
 */
logToConsole: function(txt) {
    if ((typeof debugMode !== "undefined") && (debugMode === true)) {
        jQuery("#console").css("display", "block");
        SFHybridApp.log("#console", txt);
    }
},

/**
 * Use to log error messages to an "error console" section of the page.
 *   txt - The text (html) to log to the console.
 */
logError: function(txt) {
    jQuery("#errors").css("display", "block");
    SFHybridApp.log("#errors", txt);
},

/**
 * Logs text to a given section of the page.
 *   section - HTML section (CSS-identified) to log to.
 *   txt - The text (html) to log.
 */
log: function(section, txt) {
    console.log("jslog: " + txt);
    var now = new Date();
    var fullTxt = "<p><i><b>* At " + (now.getTime() - SFHybridApp.appStartTime.getTime()) + "ms:</b></i> " + txt + "</p>";
    jQuery(section).append(fullTxt);
},

/**
 * Creates the local URL to load.
 *   page - The local page value used to create the URL.
 *
 * Returns:
 *   The local URL start page for the app.
 */
buildLocalUrl: function(page) {
    if (navigator.device.platform == "Android") {
        return SFHybridApp.buildAppUrl("file:///android_asset/www", page);
    }
    else {
        return page;
    }
},

/**
 * Creates a fullly qualified URL from server and page information.
 * Example:
 *   var fullUrl = SFHybridApp.buildAppUrl("https://na1.salesforce.com", "apex/MyVisualForcePage");
 *
 *   server - The server URL prefix.
 *   page   - The page information to append to the server.
 * Returns:
 *   Full URL to the user's page, e.g. https://na1.salesforce.com/apex/MyVisualForcePage.
 */
buildAppUrl: function(server, page) {
    var trimmedServer = jQuery.trim(server);
    var trimmedPage = jQuery.trim(page);
    if (trimmedServer === "")
        return trimmedPage;
    else if (trimmedPage === "")
        return trimmedServer;

    // Manage '/' between server and page URL on the page var side.
    if (trimmedServer.charAt(trimmedServer.length-1) === '/')
        trimmedServer = trimmedServer.substr(0, trimmedServer.length-1);

    if (trimmedPage === "" || trimmedPage === "/")
        return trimmedServer + "/";
    if (trimmedPage.charAt(0) !== '/')
        trimmedPage = "/" + trimmedPage;

    return trimmedServer + trimmedPage;
},

/**
 * Creates the initial entry page to the service, for pages that are hosted on Salesforce.
 * This function pretty much assumes OAuth authentication has occurred, as it requires
 * data that has been returned as part of the authentication process.
 *
 * instanceServer - Required - The instance where the page will be hosted.
 * startPage      - Required - The start page portion of the URL (e.g. /apex/MyPage).
 * accessToken    - Required - The access / SID token used to authenticate the user.
 *
 * Returns: The full URL required to load the requested start page on the service.
 */
buildFrontDoorUrl: function(instanceServer, startPage, accessToken) {
    var baseUrl = SFHybridApp.buildAppUrl(instanceServer, "/secur/frontdoor.jsp");
    var fullUrl = baseUrl + "?sid=" + encodeURIComponent(accessToken) + "&retURL=" + encodeURIComponent(startPage);
    return fullUrl;
},

/**
 * Load the given URL, using PhoneGap on Android, and loading directly on other platforms.
 *   fullAppUrl       - The URL to load.
 */
loadUrl: function(fullAppUrl) {
    if (navigator.device.platform == "Android") {
        navigator.app.loadUrl(fullAppUrl , {clearHistory:true});
    }
    else {
        location.href = fullAppUrl;
    }
},

/**
 * Determine whether the device is online.
 */
deviceIsOnline: function() {
    var connType;
    if (navigator && navigator.network && navigator.network.connection) {
        connType = navigator.network.connection.type;
        SFHybridApp.logToConsole("deviceIsOnline connType: " + connType);
    } else {
        SFHybridApp.logToConsole("deviceIsOnline connType is undefined.");
    }

    if (typeof connType !== 'undefined') {
        // PhoneGap's connection object.  May be more accurate?
        return (connType != null && connType != Connection.NONE && connType != Connection.UNKNOWN);
    } else {
        // Default to browser facility.
        return navigator.onLine;
    }
},

/**
 * Sanitizes a URL for logging, based on an array of querystring parameters whose
 * values should be sanitized.  The value of each querystring parameter, if found
 * in the URL, will be changed to '[redacted]'.  Useful for getting rid of secure
 * data on the querystring, so it doesn't get persisted in an app log for instance.
 *
 * origUrl            - Required - The URL to sanitize.
 * sanitizeParamArray - Required - An array of querystring parameters whose values
 *                                 should be sanitized.
 * Returns: The sanitzed URL.
 */
sanitizeUrlParamsForLogging: function(origUrl, sanitizeParamArray) {
    var trimmedOrigUrl = jQuery.trim(origUrl);
    if (trimmedOrigUrl === '')
        return trimmedOrigUrl;

    if ((typeof sanitizeParamArray !== "object") || (sanitizeParamArray.length === 0))
        return trimmedOrigUrl;

    var redactedUrl = trimmedOrigUrl;
    for (var i = 0; i < sanitizeParamArray.length; i++) {
        var paramRedactRegexString = "^(.*[\\?&]" + sanitizeParamArray[i] + "=)([^&]+)(.*)$";
        var paramRedactRegex = new RegExp(paramRedactRegexString, "i");
        if (paramRedactRegex.test(redactedUrl))
            redactedUrl = redactedUrl.replace(paramRedactRegex, "$1[redacted]$3");
    }

    return redactedUrl;
},

/**
 * RemoteAppStartData data object - Represents the data associated with bootstrapping a
 * 'remote' app, i.e. a hybrid app with its content managed as a traditional server-side
 * web app, such as a Visualforce app.
 *
 *   appStartUrl        - Required - The "start page" of the hybrid application.
 *   isAbsoluteUrl      - Optional - Whether or not the start URL is a fully-qualified URL.
 *                                   Defaults to false.
 *   shouldAuthenticate - Optional - Whether or not to authenticate prior to loading the
 *                                   application.  Defaults to true.
 */
RemoteAppStartData: function(appStartUrl, isAbsoluteUrl, shouldAuthenticate) {
    if (typeof appStartUrl !== "string" || jQuery.trim(appStartUrl) === "") {
        SFHybridApp.logError("appStartUrl cannot be empty");
        return;
    }
    this.appStartUrl = appStartUrl;
    this.isRemoteApp = true;
    this.isAbsoluteUrl = (typeof isAbsoluteUrl !== "boolean" ? false : isAbsoluteUrl);
    this.shouldAuthenticate = (typeof shouldAuthenticate !== "boolean" ? true : shouldAuthenticate);
},

/**
 * LocalAppStartData data object - Represents the data associated with bootstrapping a
 * 'local' app, i.e. a hybrid app with its content managed through a local web page,
 * such as a traditional PhoneGap app.
 *
 *   appStartUrl        - Optional - The local "start page" of the hybrid application.
 *                                   Defaults to "index.html".
 *   shouldAuthenticate - Optional - Whether or not to authenticate prior to loading the
 *                                   application.  Defaults to true.
 */
LocalAppStartData: function(appStartUrl, shouldAuthenticate) {
    this.appStartUrl = (typeof appStartUrl !== "string" || jQuery.trim(appStartUrl) === "" ? "index.html" : appStartUrl);
    this.isRemoteApp = false;
    this.isAbsoluteUrl = false;
    this.shouldAuthenticate = (typeof shouldAuthenticate !== "boolean" ? true : shouldAuthenticate);
}

};

}
