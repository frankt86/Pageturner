### Does Pageturner support Android Auto?

Yes, Pageturner fully supports Android Auto!

Android Auto only shows apps installed from a trusted source. Pageturner is installed from GitHub, so follow
[Google's steps to allow unknown sources](https://developer.android.com/training/cars/testing#unknown-sources) in Android Auto.

### Why doesn't Pageturner support the xyz Media Format?

Pageturner relies on the media formats that are natively supported by the Android platform.

You can review the currently supported file extensions [here](https://developer.android.com/media/media3/exoplayer/supported-formats).
If a file that should be supported is not displayed, it is most likely either corrupted or incompatible with your Android version.

### Why isn't feature xyz available in the app?

Features are added when they make listening or reading better for many people. Each one has to be maintained for years, and
every new setting makes the app a bit harder to use. For what the reader can't do yet, see [Ebooks](ebooks.md#not-yet).

If you're missing something, [open an issue](https://github.com/frankt86/Pageturner/issues/new) and describe
what you'd like to do.

### Which Android version do I need?

Pageturner runs on Android 9 or newer.

### How do I resume playback after the sleep timer stops?

Once the sleep timer elapses, Pageturner pauses playback (after a brief fade-out). To keep listening, you have two options:

- **Shake to resume**: Shake your device within 30 seconds of pause to restart playback.
- **Open to resume**: Open the App and simply press on play again

!!! warning

    On some devices (e.g. Samsung S20fe) shake-to-resume may not work reliably.

### How do I continue a book on another device?

Pageturner keeps your place, your playback speed, your bookmarks and the titles, authors, series and parts you gave your
audiobooks in a hidden `.voice` folder inside each audiobook folder you added. Sync these folders between your devices with
a tool of your own, like
[Syncthing](https://syncthing.net), and when you open Pageturner on the other device, it picks up where you left off.

- Add the same folder on every device, and the same way, for example as "Every folder is a book" on both. Where the
  folder is on each device doesn't matter.
- The folder has to be on the device. Folders from cloud apps like Google Drive don't sync.
- The audio files of a book have to be the same on every device.
- Books you added as a single file don't sync. Add the folder they're in instead.
- Your sync tool has to copy hidden folders.
- A book that is playing stays where it is.
- Ebooks don't sync this way. Books from Komga keep your place in step with Komga, see
  [Ebook catalogs](opds.md#syncing-your-progress-with-komga).

If you share a folder with your family and don't want each other's place, names and bookmarks, let your sync tool
ignore `.voice`. In Syncthing, add `.voice` to the folder's ignore patterns.
