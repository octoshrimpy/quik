# Contributing
Contributions are greatly appreciated! There are many ways to contribute to this repository:
* [Submitting Issues](#submitting-Issues)
* [Testing Latest Versions](#testing-latest-versions)
* [Translating](#translating)
* [Updating the Wiki](#updating-the-wiki)
* [Fix Bugs](#fix-bugs)
* [Add Features](#add-features)
## Submitting Issues
A great bug report contains a description of the problem and steps to reproduce the problem. We need to know what we're looking for and where to look for it.
When reporting a bug, please make sure to provide the following information:
* Steps to reproduce the issue
* QUIK version
* Device / OS information
## Testing Latest Versions
To do this, navigate to the release tab in this repository and click on releases. There are two types of releases in Quik. The first is the `Latest Release`.  It is colored green and is the version that goes out over the app stores. The other type is `Pre-Release`, which contains the latest features and may have bugs. To install this, download the apk from the release tab and install it on your phone. (Instructions can be found in the wiki for a more detailed explanation).
Once you have done this, simply use the app as normal, and report any bugs you come across.
## Translating 
To translate this app navigate to `presentation/src/main/res` and find or make a values file with the language you wish to edit. Then copy over the `strings.xml` file from `presentation/src/main/res/values` and translate it. Please do not translate any string with the `translatable=false` attribute. Also, remember to make sure to insert a backslash `\` before any apostrophe `'` or quotes `"`.
<!--
## Translations

If you'd like to add translations to QUIK, please join the project on [Crowdin](https://crowdin.com/project/qksms). Translations that are committed directly to source files will not be accepted.
-->
## Updating the Wiki 
To update the wiki, simply navigate to the [wiki tab](https://github.com/octoshrimpy/quik/wiki) in this repository, find the file that you need to edit, and then click `Edit Page`. Then you can edit and commit your change. For more sizeable changes, and for feedback and questions please comment in [this discussion](https://github.com/octoshrimpy/quik/discussions/174).
## Fix Bugs 
1. Find a bug that needs fixing, please check the issues tab, and if the bug isn't reported, please do so before fixing it.
2. Fork the repository.
3. Create a new branch.
4. Make your change.
5. Test your change, either by building an apk or running on an emulator.
6. Submit a pull request with your change.
**We have a build action on each pull request, if this build fails, please edit the pull request in order to make the build succeed.**
## Add Features 
1. Submit a feature request to the issue tab, or search through the issues for a feature request that has already been submitted.
2. Fork the repository.
3. Create a new branch.
4. Make your change. **Note: Please make sure to provide detailed comments within your code to make reviewers and future contributors lives easier.**
5. Test your change, either by building an apk or running on an emulator.
6. Submit a pull request with your change.
**We have a build action on each pull request, if this build fails, please edit the pull request in order to make the build succeed.**
