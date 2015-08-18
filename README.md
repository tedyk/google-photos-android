# google-photos-android

Android example app demonstrating integration with Google Photos, using the Drive REST API.

## Prerequisites
1. You need to first register your Project at Google Developers Console and create an API key and Client ID. Make sure Client ID is same as your app's package name, else you'll receive unauthorized error when you fire the API later.
2. If you are unfamiliar with the process, [this page](https://developers.google.com/drive/android/get-started) has instructions to guide you.
3. Once you have your API key, open Constants.java and replace `XXXXXXXXXXXXXXXXXXXXXX` with your API key
4. I have used Roboguice for dependency injection, and Picasso for image handling in the example - they are not a necessity for integrating with Google Photos - they are there just to make life easier.
5. I haven't used any other google sdks other than the play-services one; using the sdks like DriveAPI etc would provide much more convenience; however, my purpose for this app was to demonstrate the usage of Drive's REST APIs, and it holds true to its purpose.