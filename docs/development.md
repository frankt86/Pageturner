# Development

## Project Setup

To run the project, open it in the latest Version of Android Studio and build the project as usual.
The default local development variant is `freeDebug`, which does not require Google services credentials.
Play variants include Firebase, Crashlytics, Remote Config and Analytics. Pageturner publishes only the free variant.
Play builds require `app/src/play/google-services.json`.

By default, there is not enough memory configured for gradle. You can fix this by running.

```sh
scripts/gradle_bootstrap.sh
```

This will configure your global `~/.gradle/gradle.properties` to use more memory, depending on your machine.

Example of the generated properties, check the `gradle_bootstrap.sh` script for exact details.

```properties
# Begin: Gradle JVM bootstrap-generated properties
org.gradle.jvmargs=-Dfile.encoding=UTF-8 -XX:+ExitOnOutOfMemoryError -Xms4g -Xmx16g
kotlin.daemon.jvm.options=-Dfile.encoding=UTF-8 -XX:+ExitOnOutOfMemoryError -Xms4g -Xmx16g
# End: Gradle JVM bootstrap-generated properties
```

## Tests

To run the unit tests, run the following command:

```sh
./gradlew voiceUnitTest
```

## Developer Menu

The developer menu is hidden by default.

To unlock it:

1. Open `Settings`.
2. Tap the app version entry 13 times.
3. Open the developer menu from `Settings`.

## Opening a Screen Directly

Debug builds can start on any back stack of `Destination.Compose`s, passed as JSON in the `debugBackStack` extra.
The emulator skill's script builds it for you:

```sh
ANDROID_SERIAL=<serial from adb devices> .agents/skills/emulator/scripts/ui.py open BookOverview Settings DeveloperSettings
```

Agents use this through the emulator skill in `.agents/skills/emulator`, which also gives every agent its own emulator.

## Formatting

Pageturner formats Kotlin with **ktfmt** in its Google style, through Spotless.

- Check for formatting issues:

```sh
./gradlew spotlessCheck
```

- Auto-fix formatting:

```sh
./gradlew spotlessApply
```

- To format from Android Studio, install the [ktfmt plugin](https://plugins.jetbrains.com/plugin/14912-ktfmt) and choose
  "Enable for This Project" when it asks. It picks up the project's settings from `.idea/ktfmt.xml`.

- To check the formatting on every commit, turn on the pre-commit hook in `.githooks`. When the check fails, it formats
  the code and stops the commit, so you can stage the changes and commit again. Claude Code sessions turn it on for you.
  Otherwise, run this once per clone, it covers all worktrees:

```sh
git config core.hooksPath .githooks
```

Compose rules come from Slack's [compose-lints](https://slackhq.github.io/compose-lints/) and run as part of Android Lint.

## Releasing

Every push to `main` builds a test APK with the debug app id (`.debug` suffix) and the debug key. CI publishes it as the
numbered `test-<number>` release, marked latest, and as `pageturner-debug.apk` on the website. Its app id is
`de.ph1b.audiobook.debug`, so it installs next to the stable app and next to Voice.

Stable releases are signed with the release key and published on
[GitHub Releases](https://github.com/frankt86/Pageturner/releases). The website's `pageturner.apk` is the latest stable
release. When GitHub has no stable release, the site serves the debug build under both names.

### Cutting a release

1. Find the next version name. It is `YY.M.RELEASE` (see [Versioning](#versioning)), for example `26.11.1` for the
   first release in November 2026, or `26.11.2` when `26.11.1` exists.
2. In `CHANGELOG.md`, add a section for that version below `## [Unreleased]`, with the heading
   `## [26.11.1] - 2026-11-20`. Move the notes from `[Unreleased]` into it. Merge this change into `main`.
3. Dispatch the [Release Workflow](https://github.com/frankt86/Pageturner/actions/workflows/release.yml) on `main`.

The workflow picks the version name and code, and takes the release notes from the changelog section of that version. If
the section is missing, it stops before the build and names the heading it expects. Then it builds `freeRelease` with
the app id `io.github.frankt86.pageturner` (`-Pvoice.applicationId`), signs it with the release key, and publishes a
release marked as latest. The release has the tag `YY.M.RELEASE-CODE` and two copies of the APK:
`pageturner-<version>.apk` and `pageturner.apk`. So
`https://github.com/frankt86/Pageturner/releases/latest/download/pageturner.apk` always gives the latest stable APK.
After the release, the website deploys again with that APK.

The workflow does not start from a pushed tag. It makes the tag itself, because the tag holds the version code.

The release needs these repository secrets. If one is missing, the workflow stops at its first step and names it.

| Secret                      | Purpose                                     |
|-----------------------------|---------------------------------------------|
| `GH_SIGNING_KEYSTORE`       | Release keystore (PKCS12), base64 encoded.  |
| `GH_SIGNING_STORE_PASSWORD` | Release keystore password.                  |
| `GH_SIGNING_KEY_ALIAS`      | Release key alias (`pageturner`).           |
| `GH_SIGNING_KEY_PASSWORD`   | Release key password.                       |

CI signs through Gradle's injected signing properties. `scripts/ci_signing_setup.sh free` writes the keystore from
`GH_SIGNING_KEYSTORE`. The script also takes `play` and `google_services` for the Play build, which no workflow of
Pageturner does.

Keep the release key safe. Android only installs an update that has the same key as the installed app.

## Play Store Artwork

Play Store artwork is generated from the `artwork/` Node project and written to `fastlane/metadata/android/*/images`.

Use:

```sh
./scripts/generate_artworks.sh
```

The generator uses:

- `artwork/public/logo.svg` for the Play Store icon.
- `artwork/public/raw/phone/*.png` for raw app screenshots, captured at 1080x2424 on a Pixel 9 emulator with the
  system UI demo mode on.
- `artwork/public/raw/feature/1.png` for the phone in the feature graphic.
- `artwork/src/templates/` and `artwork/src/styles/` for the rendered Play Store images. `PHONE_TONES` in
  `template.ts` gives each screenshot a background that matches the colors of its screen.
- `fastlane/metadata/android/*/marketing.yml` for localized feature graphic text and screenshot captions.

Only locales with `marketing.yml` get localized generated images. The script removes old generated screenshot folders before writing the current output, so deleted tablet screenshots do not remain in the repository.

To take the raw screenshots again, run `./scripts/take_store_screenshots.py`. It needs `ffmpeg` and starts an agent
emulator of its own (see the emulator skill), so your devices and library stay untouched. It adds a generated library
with the books of the kiosk mode, and uses kiosk mode for the library, bookmarks, history, and feature graphic.

The `artwork/package-lock.json` file is intentionally not checked in. CI and local generation use `npm install` from `artwork/package.json` to keep this small tool out of regular lockfile update churn.

## Versioning

Pageturner, like Voice, uses [calendar versioning (CalVer)](https://calver.org/).

- Version name format: `YY.M.RELEASE`
  - `YY`: release year, last 2 digits (2025 -> 25)
  - `M`: release month (1-12)
  - `RELEASE`: counter for releases within the same month (starting at 1)
- Tag format (used by CI): `YY.M.RELEASE-CODE`
  - `CODE` is the Android versionCode, see below.

Examples:

- First release in December 2026 -> version name `26.12.1`
- Built on 2026-12-01 at 09:00 UTC -> tag `26.12.1-5436263`

### Version code

The Android `versionCode` counts the minutes since 2026-11-13 20:00 UTC, starting at `5411003`:

`CODE = 5411003 + minutes since 2026-11-13 20:00 UTC`

So every release gets a higher code than all releases before it, and Android installs it as an update.
`determine_release_tag.main.kts` refuses to run when the clock is before the start or after 2039, because a code from a
wrong clock can't be taken back.

Codes below `5411003` came from the version name, like `5309001` for `25.9.1`.

`determine_release_tag.main.kts` puts this code in the git tag, and the Release workflow builds with it.

Builds without `-Pvoice.versionCode` take the code of the newest release tag. So a local build installs over that
release, and the next release replaces it.

## Pages Deployment

The [website](https://frankt86.github.io/Pageturner/) uses GitHub Pages and MkDocs and deploys on every push to `main`
that touches `docs/`, after each debug APK build and after each release. It puts the latest stable APK next to the site
as `pageturner.apk`, and the debug APK as `pageturner-debug.apk`.
To deploy it by hand, dispatch the workflow manually.

[Dispatch the workflow](https://github.com/frankt86/Pageturner/actions/workflows/deploy_pages.yml)

The `website/` folder holds upstream Voice's Astro website. Pageturner doesn't build or deploy it.
