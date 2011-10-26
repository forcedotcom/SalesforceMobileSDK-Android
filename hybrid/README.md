
This folder contains sample hybrid apps that combine the native SalesforceSDK jar files with
HTML5 and javascript.

- The ContactExplorer sample app uses phonegap (aka "callback") to retrieve local device contacts.  It also uses  forcetk.js to implement REST transactions with the Salesforce REST API. The app uses the OAuth2 support in SalesforceSDK to obtain oauth credentials, then propagates those credentials to forcetk by sending a javascript event. 

- The VFConnector sample app demonstrates how to wrap a VisualForce page in a native container.  This example assumes that your org has a VisualForce page called "BasicVFTest".  The app first obtains oauth login credentials using the  SalesforceSDK OAuth2 support, then uses those credentials to set appropriate webview cookies for accessing VisualForce pages.  

