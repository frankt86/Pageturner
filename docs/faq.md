# FAQ

### Where do I download Pageturner?

Download [pageturner.apk](https://frankt86.github.io/Pageturner/pageturner.apk) on your phone, or get it from the
[GitHub Releases page](https://github.com/frankt86/Pageturner/releases/latest). [Install](install.md) gives the steps,
and tells you how to get updates with Obtainium.

### Can I keep Voice and the test app next to Pageturner?

Yes. The stable app (`io.github.frankt86.pageturner`), the test app and Voice are three different apps. Each one keeps
its own library, settings and places in your books.

### Does Pageturner support Android Auto?

Yes. Android Auto shows only apps from a trusted source. Pageturner comes from GitHub, so follow
[the steps of Google to allow unknown sources](https://developer.android.com/training/cars/testing#unknown-sources) in
Android Auto.

Android Auto shows your audiobooks. Ebooks are not in Android Auto.

### Why doesn't Pageturner play my audio format?

Pageturner plays the media formats that Android supports. See the
[list of supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats). If a supported
file doesn't show up, the file is probably damaged, or your Android version can't play it.

Audiobooks from stores that lock them to their own app with DRM, like Audible, can't play in Pageturner. Books without
DRM work.

### Which ebook formats can Pageturner open?

EPUB, PDF and Kindle books (MOBI, AZW3, AZW and PRC) without DRM. See [Ebooks and the Reader](ebooks.md).

### Why isn't feature xyz in the app?

A feature comes in when it makes listening or reading better for many people. Each one must be kept working for years,
and each setting makes the app a bit harder to use. For what the reader can't do, see
[Ebooks and the Reader](ebooks.md#not-yet).

If you miss something, [open an issue](https://github.com/frankt86/Pageturner/issues/new) and describe what you want to
do.

### Which Android version do I need?

Pageturner runs on Android 9 or newer.

### How do I resume playback after the sleep timer stops?

Before the sleep timer ends, the sound fades out for a few seconds. Then Pageturner pauses playback. To keep listening,
you have two options:

- **Shake to keep listening**: shake your device while the sound fades out, or within 30 seconds of the pause. The sleep
  timer starts again.
- **Press play**: press play within 30 seconds of the pause, in the app, in the notification or on your headphones.
  The sleep timer starts again. If you press play later, Pageturner plays on without a sleep timer.

!!! warning

    On some devices (for example the Samsung S20 FE), shake to keep listening doesn't always work.

### How do I continue a book on another device?

Pageturner keeps your place, your playback speed, your bookmarks, the titles, authors, series and parts you gave your
audiobooks, and the books you hid from your library in a hidden `.voice` folder. Each audiobook folder you added has
one. Sync these folders between your devices with a tool of your own, like [Syncthing](https://syncthing.net). When you
open Pageturner on the other device, it continues where you stopped.

- Add the same folder on every device, in the same way. For example, add it as "Every folder is a book" on both. Where
  the folder is on each device is not important.
- The folder must be on the device. Folders from cloud apps like Google Drive don't sync.
- The audio files of a book must be the same on every device.
- Books you added as a single file don't sync. Add the folder they are in.
- Your sync tool must copy hidden folders.
- A book that plays stays where it is.
- Ebooks don't sync this way. Books from Komga keep your place in sync with Komga, see
  [Ebook catalogs](opds.md#syncing-your-progress-with-komga).

Do you share a folder with your family, and want to keep your places, names, hidden books and bookmarks apart? Then let
your sync tool ignore `.voice`. In Syncthing, add `.voice` to the ignore patterns of the folder.

### Where do I report a bug?

[Open an issue](https://github.com/frankt86/Pageturner/issues/new) on GitHub.
