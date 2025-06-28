# Contributing
Contributions are greatly appreciated! There are many ways to contribute to this repository:
* [Submit Issues](#submit-issues)
* [Test Latest Versions](#test-latest-versions)
* [Translate](#translate)
* [Update the Wiki](#update-the-wiki)
* [Fix Bugs](#fix-bugs)
* [Add Features](#add-features)
## Submit Issues
A great bug report contains a description of the problem and steps to reproduce the problem. We need to know what we're looking for and where to look for it.
When reporting a bug, please make sure to provide the following information:
* Steps to reproduce the issue
* QUIK version
* Device / OS information
## Test Latest Versions
To do this, navigate to the release tab in this repository and click on releases. There are two types of releases in Quik. The first is the `Latest Release`.  This is the version that is distributed over the app stores. The other type is `Pre-Release`, which contains the latest features and may have bugs. To install this, download the apk from the release tab and install it on your phone. (Instructions can be found in the wiki for a more detailed explanation).
Once you have done this, simply use the app as normal, and report any bugs you come across.
## Translate
To translate this app join our [Weblate project](https://hosted.weblate.org/engage/quik/). From here you can begin editing strings on the web. We have a few guidelines for translations: 
* Insert a backslash `\` before any apostrophe `'` or quotes `"`.
* Use `` ` `` instead of single quotes.
* Follow the punctuation in the English strings as much as possible. For example don't add a `.` when there is none in the original strings.
* If you are unsure of a translation, consider using a suggestion rather than translating the string. That way other translators can review it.

We recommend checking out the [Weblate documentation](https://docs.weblate.org/en/latest/index.html), and if you need any help please open an issue.
## Update the Wiki 
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
## Helpful Tips
### Set Java Version
When building QUIK, make sure to download JDK 17 and specify an installation path for it. In Android Studio, you can do that in Settings > Build, Execution, Deployment > Build Tools > Gradle.
