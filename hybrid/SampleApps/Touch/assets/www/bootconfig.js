//-----------------------------------------------------------------
// Replace the values below with your own app configuration values.
//-----------------------------------------------------------------
 
// When debugMode is true, logToConsole() messages will be written to a
// "debug console" section of the page.
var debugMode = false;
 
// The client ID value specified for your remote access object that defines
// your application in Salesforce.
var remoteAccessConsumerKey = "3MVG9WQsPp5nH_EpM_KnrLdttEzcHigVLNjQNb7PkdJ1EFBUAyYCd4zYXQX8BJv3Zx3wVKXq9hlZPpt1ePy52";
 
// The redirect URI value specified for your remote access object that defines
// your application in Salesforce.
var oauthRedirectURI = "sfdc-touch:///services/callback";
 
// The authorization/access scope(s) you wish to define for your application.
var oauthScopes = ["web", "api"];
 
// The start data associated with the application.  Use SFHybridApp.LocalAppStartData for a "local"
// PhoneGap-based application, and SFHybridApp.RemoteAppStartData for a Visualforce-based
// application.  The default representations are below, or you can look at the data
// classes in SFHybridApp.js to see how you can further customize your options.
//var startData = new SFHybridApp.LocalAppStartData();  // Used for local REST-based "index.html" PhoneGap apps.
var startData = new SFHybridApp.RemoteAppStartData("/lumen?lumen.tag=mobile:mobileContainer&lumen.format=HTML&lumen.deftype=APPLICATION"); // Used for Visualforce-based apps.
 
 
// Whether the container app should automatically refresh our oauth session on app foreground:
// generally a good idea.
var autoRefreshOnForeground = true;
 
// Whether the container app should automatically refresh our oauth session periodically
var autoRefreshPeriodically = true;
   
 
//-----------------------------------------------------------------
// End configuration block
//----------------------------------------------------------------- 
