Demo
====

Demo for Cordova plugin (Android).

This demo contains a small sample App for Cordova 2.x as well as browser page.

Prerequisites
=============

 * Cordova 2.x
 * Android device 4.1.x (officially only Samsung S3 is supported)
 * OpenTok Android SDK 2.0beta1


Installation
============

Preparation
--
Create your TokBox API-key, session-ID, and token for the demo app / test session.
See https://dashboard.tokbox.com/projects


Android App
--

1. Create a sample Cordova app (ver. 2.x)
2. Copy contents of demo/app/assets/*
   into the /assets folder of your demo app
3. Copy contents of src/*
   into the /src folder of your demo app
4. Copy contents of www/*
   into the assets/www/js folder of your demo app
5. Ensure that in your demo app the OpenTok Java implementation is loaded
   see the corresponding <feature> entry in demo/app/res/xml/config.xml
6. Ensure that your demo apps AndroidManifest.xml has the necessary permissions
   see demo/app/AndroidManifest.xml
7. Add OpenTok libraries for Android to your demo project (vers. 2.0beta1)
8. Enter your OpenTok API-key, session-ID and token in your app's assets/www/js/index.js
9. Run the Android app on a device (NOTE will not work in an emulator)


Web Page
--

1. publish the HTML page demo/webpage/helloWorld.html
  * ensure that the published web page is available with a public IP
2. open the web page and use query-parameters to set the OpenTok API-key, session-ID and token:
   .../helloWorld.html?apiKey=xx...xx&sessionId=xxx...xxx&sessionToken=xxx...xxx
   (use the same values as for the Android app)
