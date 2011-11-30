@ECHO OFF

:: *****************************************************************
:: *** ATTENTION: Windows-based Android developers               ***
:: *** Run this script *before* opening any projects in Eclipse, ***
:: *** to avoid errors due to missing symlinks.                  ***
:: *** From the Command line: fix_file_links.bat                 ***
:: *****************************************************************

:: -----------------------------------------------------------------
:: For Windows, this batch script copies source files to where their
:: symlink targets would be on Unix-based operating systems, as
:: symlinks are not well-supported for all Windows operating systems.
::
:: NOTE: This script depends on residing in its current location to
:: run properly.  While it can be executed from any working path, it
:: cannot be moved from its location and still function properly.
:: -----------------------------------------------------------------


:: ------------------------------------
:: Begin Main script
:: ------------------------------------

:: Clear any previous ERRORLEVEL setting.
%COMSPEC% /c

SET ROOT_REPO_DIR=%~dp0

CALL :CopyFile dist\SalesforceSDK-1.0.jar                            dist\SalesforceSDK.jar
CALL :CopyFile dist\SalesforceSDK.jar                                hybrid\SampleApps\ContactExplorer\libs\SalesforceSDK.jar
CALL :CopyFile dist\SalesforceSDK.jar                                hybrid\SampleApps\VFConnector\libs\SalesforceSDK.jar
CALL :CopyFile dist\SalesforceSDK.jar                                native\SampleApps\CloudTunes\libs\SalesforceSDK.jar
CALL :CopyFile external\phonegap-1.2.0.jar                           hybrid\SampleApps\ContactExplorer\libs\phonegap-1.2.0.jar
CALL :CopyFile external\phonegap-1.2.0.jar                           hybrid\SampleApps\VFConnector\libs\phonegap-1.2.0.jar
CALL :CopyFile external\phonegap-1.2.0.jar                           native\SalesforceSDK\libs\phonegap-1.2.0.jar
CALL :CopyFile external\phonegap-1.2.0.js                            hybrid\SampleApps\ContactExplorer\assets\www\phonegap-1.2.0.js
CALL :CopyFile external\phonegap-1.2.0.js                            hybrid\SampleApps\VFConnector\assets\www\phonegap-1.2.0.js
CALL :CopyFile external\shared\forcetk.js                            hybrid\SampleApps\ContactExplorer\assets\www\forcetk.js
CALL :CopyFile external\shared\jquery                                hybrid\SampleApps\ContactExplorer\assets\www\jquery
CALL :CopyFile external\shared\jquery                                hybrid\SampleApps\VFConnector\assets\www\jquery
CALL :CopyFile external\shared\PhoneGap\bootstrap.html               hybrid\SampleApps\ContactExplorer\assets\www\bootstrap.html
CALL :CopyFile external\shared\PhoneGap\bootstrap.html               hybrid\SampleApps\VFConnector\assets\www\bootstrap.html
CALL :CopyFile external\shared\PhoneGap\index.html                   hybrid\SampleApps\ContactExplorer\assets\www\index.html
CALL :CopyFile external\shared\PhoneGap\inline.js                    hybrid\SampleApps\ContactExplorer\assets\www\inline.js
CALL :CopyFile external\shared\PhoneGap\SalesforceOAuthPlugin.js     hybrid\SampleApps\ContactExplorer\assets\www\SalesforceOAuthPlugin.js
CALL :CopyFile external\shared\PhoneGap\SalesforceOAuthPlugin.js     hybrid\SampleApps\VFConnector\assets\www\SalesforceOAuthPlugin.js
CALL :CopyFile native\SalesforceSDK\res\drawable-hdpi\edit_icon.png  hybrid\SampleApps\ContactExplorer\res\drawable-hdpi\edit_icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-hdpi\edit_icon.png  hybrid\SampleApps\VFConnector\res\drawable-hdpi\edit_icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-hdpi\edit_icon.png  native\RestExplorer\res\drawable-hdpi\edit_icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-hdpi\edit_icon.png  native\SampleApps\CloudTunes\res\drawable-hdpi\edit_icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-hdpi\icon.png       hybrid\SampleApps\ContactExplorer\res\drawable-hdpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-hdpi\icon.png       hybrid\SampleApps\VFConnector\res\drawable-hdpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-hdpi\icon.png       native\RestExplorer\res\drawable-hdpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-hdpi\icon.png       native\SampleApps\CloudTunes\res\drawable-hdpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-ldpi\icon.png       hybrid\SampleApps\ContactExplorer\res\drawable-ldpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-ldpi\icon.png       hybrid\SampleApps\VFConnector\res\drawable-ldpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-ldpi\icon.png       native\RestExplorer\res\drawable-ldpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-ldpi\icon.png       native\SampleApps\CloudTunes\res\drawable-ldpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-mdpi\icon.png       hybrid\SampleApps\ContactExplorer\res\drawable-mdpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-mdpi\icon.png       hybrid\SampleApps\VFConnector\res\drawable-mdpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-mdpi\icon.png       native\RestExplorer\res\drawable-mdpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\drawable-mdpi\icon.png       native\SampleApps\CloudTunes\res\drawable-mdpi\icon.png
CALL :CopyFile native\SalesforceSDK\res\layout\custom_server_url.xml hybrid\SampleApps\ContactExplorer\res\layout\custom_server_url.xml
CALL :CopyFile native\SalesforceSDK\res\layout\custom_server_url.xml hybrid\SampleApps\VFConnector\res\layout\custom_server_url.xml
CALL :CopyFile native\SalesforceSDK\res\layout\custom_server_url.xml native\RestExplorer\res\layout\custom_server_url.xml
CALL :CopyFile native\SalesforceSDK\res\layout\custom_server_url.xml native\SampleApps\CloudTunes\res\layout\custom_server_url.xml
CALL :CopyFile native\SalesforceSDK\res\layout\login_header.xml      hybrid\SampleApps\ContactExplorer\res\layout\login_header.xml
CALL :CopyFile native\SalesforceSDK\res\layout\login_header.xml      hybrid\SampleApps\VFConnector\res\layout\login_header.xml
CALL :CopyFile native\SalesforceSDK\res\layout\login_header.xml      native\RestExplorer\res\layout\login_header.xml
CALL :CopyFile native\SalesforceSDK\res\layout\login_header.xml      native\SampleApps\CloudTunes\res\layout\login_header.xml
CALL :CopyFile native\SalesforceSDK\res\layout\login.xml             hybrid\SampleApps\ContactExplorer\res\layout\login.xml
CALL :CopyFile native\SalesforceSDK\res\layout\login.xml             hybrid\SampleApps\VFConnector\res\layout\login.xml
CALL :CopyFile native\SalesforceSDK\res\layout\login.xml             native\RestExplorer\res\layout\login.xml
CALL :CopyFile native\SalesforceSDK\res\layout\login.xml             native\SampleApps\CloudTunes\res\layout\login.xml
CALL :CopyFile native\SalesforceSDK\res\layout\passcode.xml          hybrid\SampleApps\ContactExplorer\res\layout\passcode.xml
CALL :CopyFile native\SalesforceSDK\res\layout\passcode.xml          hybrid\SampleApps\VFConnector\res\layout\passcode.xml
CALL :CopyFile native\SalesforceSDK\res\layout\passcode.xml          native\RestExplorer\res\layout\passcode.xml
CALL :CopyFile native\SalesforceSDK\res\layout\passcode.xml          native\SampleApps\CloudTunes\res\layout\passcode.xml
CALL :CopyFile native\SalesforceSDK\res\layout\server_picker.xml     hybrid\SampleApps\ContactExplorer\res\layout\server_picker.xml
CALL :CopyFile native\SalesforceSDK\res\layout\server_picker.xml     hybrid\SampleApps\VFConnector\res\layout\server_picker.xml
CALL :CopyFile native\SalesforceSDK\res\layout\server_picker.xml     native\RestExplorer\res\layout\server_picker.xml
CALL :CopyFile native\SalesforceSDK\res\layout\server_picker.xml     native\SampleApps\CloudTunes\res\layout\server_picker.xml
CALL :CopyFile native\SalesforceSDK\res\menu\clear_custom_url.xml    hybrid\SampleApps\ContactExplorer\res\menu\clear_custom_url.xml
CALL :CopyFile native\SalesforceSDK\res\menu\clear_custom_url.xml    hybrid\SampleApps\VFConnector\res\menu\clear_custom_url.xml
CALL :CopyFile native\SalesforceSDK\res\menu\clear_custom_url.xml    native\RestExplorer\res\menu\clear_custom_url.xml
CALL :CopyFile native\SalesforceSDK\res\menu\clear_custom_url.xml    native\SampleApps\CloudTunes\res\menu\clear_custom_url.xml
CALL :CopyFile native\SalesforceSDK\res\values\sdk.xml               hybrid\SampleApps\ContactExplorer\res\values\sdk.xml
CALL :CopyFile native\SalesforceSDK\res\values\sdk.xml               hybrid\SampleApps\VFConnector\res\values\sdk.xml
CALL :CopyFile native\SalesforceSDK\res\values\sdk.xml               native\RestExplorer\res\values\sdk.xml
CALL :CopyFile native\SalesforceSDK\res\values\sdk.xml               native\SampleApps\CloudTunes\res\values\sdk.xml
CALL :CopyFile native\SalesforceSDK\res\xml\authenticator.xml        hybrid\SampleApps\ContactExplorer\res\xml\authenticator.xml
CALL :CopyFile native\SalesforceSDK\res\xml\authenticator.xml        hybrid\SampleApps\VFConnector\res\xml\authenticator.xml
CALL :CopyFile native\SalesforceSDK\res\xml\authenticator.xml        native\RestExplorer\res\xml\authenticator.xml
CALL :CopyFile native\SalesforceSDK\res\xml\authenticator.xml        native\SampleApps\CloudTunes\res\xml\authenticator.xml

ECHO Successfully fixed all of the symlinks.
GOTO:EOF

:: ------------------------------------
:: End Main script
:: ------------------------------------

:: ------------------------------------
:: Begin CopyFile subroutine
:: ------------------------------------

:CopyFile

:: If previous errors, do not continue.
IF ERRORLEVEL 1 GOTO:EOF

:: Copy the source file to its symlink location
XCOPY "%ROOT_REPO_DIR%%1" "%ROOT_REPO_DIR%%2" /V /F /R /Y
IF ERRORLEVEL 1 GOTO ERRBLOCK
GOTO:EOF

:: ------------------------------------
:: End CopyFile subroutine
:: ------------------------------------

:: ------------------------------------
:: Begin Error handling subroutine
:: ------------------------------------

:ERRBLOCK
ECHO There was an error updating files.  Aborting the update process.
EXIT /B 1

:: ------------------------------------
:: End Error handling subroutine
:: ------------------------------------


