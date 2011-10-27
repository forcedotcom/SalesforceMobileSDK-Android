# Introduction

This folder contains sample hybrid apps that combine the native Salesforce SDK functionality with HTML5 and javascript, to create web-based apps that can make use of extended device capabilities for enhanced app functionality.

* The ContactExplorer sample app uses PhoneGap (aka "callback") to retrieve local device contacts.  It also uses the forcetk.js toolkit to implement REST transactions with the Salesforce REST API.  The app uses the OAuth2 support in Salesforce SDK to obtain OAuth credentials, then propagates those credentials to forcetk by sending a javascript event.

* The VFConnector sample app demonstrates how to wrap a VisualForce page in a native container.  This example assumes that your org has a VisualForce page called "BasicVFTest".  The app first obtains OAuth login credentials using the Salesforce SDK OAuth2 support, then uses those credentials to set appropriate webview cookies for accessing VisualForce pages.  


# Setting up your development environment

Please follow the instructions for setting up your development environment from the README file at the root of this repo, prior to working with the Salesforce SDK.

# Creating hybrid (PhoneGap) apps that utilize the Salesforce SDK

In order to create PhoneGap-based hybrid apps, you can take one of two basic approaches:

* Create an app with a local index.html file in the app itself, which dynamically pulls content into the app through AJAX requests.  Look at the ContactExplorer sample app mentioned above, which is an example of this approach, leveraging REST API calls to retrieve the content for its app.
* Create an app that references external content from a website, essentially enhancing an existing web application (such as a VisualForce application) with device capabilities.  In this case, you'll want to look at the VFConnector sample app described above, for an example of using this approach.
