# Install

Pageturner is not on Google Play. You install it from an APK file. Pageturner needs Android 9 or newer.

## Download

[:material-download: &nbsp;Download Pageturner](https://frankt86.github.io/Pageturner/pageturner.apk){ .md-button .md-button--primary }

| Download | What it is |
|----------|------------|
| [pageturner.apk](https://frankt86.github.io/Pageturner/pageturner.apk) | The latest stable release, signed with the release key. |
| [GitHub Releases](https://github.com/frankt86/Pageturner/releases/latest) | The same APK, with the release notes. Older versions are on the [list of all releases](https://github.com/frankt86/Pageturner/releases). |
| [Obtainium](#get-updates-with-obtainium) | Gets each stable release for you. |
| [pageturner-debug.apk](https://frankt86.github.io/Pageturner/pageturner-debug.apk) | The [test app](#the-test-app), built from every change. |

## Install the APK

1. Open the download link in the browser of your phone, for example Chrome.
2. If Chrome warns that the file can be harmful, tap **Download anyway**.
3. Open the downloaded file. Android asks you to let your browser install unknown apps. Tap **Settings**, turn on
   **Allow from this source**, and go back.
4. Tap **Install**.

To update by hand, download the file again and install it over the app you have. Your library and your place in each
book stay.

## Get updates with Obtainium

[Obtainium](https://obtainium.imranr.dev/) installs apps from their GitHub releases and tells you about updates.

1. Install Obtainium and open it.
2. Tap **Add App**.
3. In **App Source URL**, enter `https://github.com/frankt86/Pageturner`.
4. Tap **Add**, then **Install**. If Android asks, let Obtainium install unknown apps.

Each release has two APK files, `pageturner-<version>.apk` and `pageturner.apk`. They are the same file. If Obtainium
asks which one to install, select either one.

Obtainium gets only the stable releases. It gets the test app only when you turn on **Include prereleases**.

## The test app

Every change to the code makes a test build:
[pageturner-debug.apk](https://frankt86.github.io/Pageturner/pageturner-debug.apk). It is also on the
[debug release page](https://github.com/frankt86/Pageturner/releases/tag/debug). It can have features before a stable
release, and it can have bugs.

## Side by side

The stable app (`io.github.frankt86.pageturner`) and the test app (`de.ph1b.audiobook.debug`) are two different apps.
You can install both side by side. Each one has its own library, settings and places in your books. An update of one
never changes the other.

Both also install next to Voice from Google Play or F-Droid, and do not touch its library.

## Android Auto

Android Auto shows only apps from a trusted source. For Pageturner, follow
[the steps of Google to allow unknown sources](https://developer.android.com/training/cars/testing#unknown-sources) in
Android Auto.
