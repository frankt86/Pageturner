# Ebook catalogs (OPDS)

Many ebook servers share their books as a catalog in a standard format called OPDS. Calibre-Web, Komga, Kavita, the
Calibre content server and free libraries like Project Gutenberg all have one. Add a catalog to Pageturner, look through it and
download its EPUB, PDF and Kindle (MOBI and AZW3) books and its audiobooks into your library.

There is no Pageturner account and no Pageturner server in between. Pageturner only talks to the catalogs you add.

## Free catalogs

Pageturner comes with these free English catalogs. They need no account.

| Catalog | Books | Address |
|---------|-------|---------|
| Project Gutenberg | The most downloaded classics in English | `https://www.gutenberg.org/ebooks/search.opds/?query=l.en&sort_order=downloads` |
| Internet Archive | Over 21,000 LibriVox audiobooks in MP3, and public domain ebooks | `https://archive.org/services/opds` |
| Open Library | Free books from the Internet Archive | `https://openlibrary.org/opds/` |
| Unglue.it | Free books with open licenses, like Creative Commons | `https://unglue.it/api/opds/` |
| Wikisource | Proofread English texts from Wikisource | `https://ws-export.wmcloud.org/opds/en/Ready_for_export.xml` |
| OAPEN Library | Open access academic books | `https://library.oapen.org/opds` |
| Directory of Open Access Books | Open access academic books from many publishers | `https://directory.doabooks.org/opds` |
| LibriVox | Public domain audiobooks read by volunteers, in English | `https://librivox.org/` |

Pageturner adds each of them once. When you already have one of them, it isn't added again. Remove the ones you don't
need: they don't come back.

Open Library also lists books you can only borrow with an account. Pageturner only shows the books you can download.

On an update, Pageturner removes the Polish catalog Wolne Lektury that it added itself, once, and moves the Gutenberg
catalog it added to the address above. A Wolne Lektury you added yourself, or a Gutenberg address you changed, stays.

### Books in English

Every catalog shows books in English only, on its pages and in its search results:

- A book in another language doesn't show. A book in English and other languages shows.
- A book without a language doesn't show either, as Pageturner can't tell that it is in English. The exception is a
  page that the catalog itself limits to English: the Gutenberg pages with `l.en`, the English Wikisource
  (`/opds/en/`) and the Internet Archive search results. There, a book without a language shows.
- Shelves, authors and other links stay.
- When a whole page has no books in English, Pageturner loads the next pages, up to four.
- The search of Project Gutenberg, the Internet Archive and Open Library asks the catalog for books in English only.

Pageturner reads the language from `dc:language` and `dcterms:language` in OPDS 1, and from `metadata.language` in
OPDS 2. `en`, `eng`, `en-US`, `en-GB` and `English` all mean English.

### LibriVox

LibriVox has no OPDS catalog, so Pageturner reads it through the LibriVox API. Its catalog opens with the newest
audiobooks first, and more load as you scroll. The search finds books by the start of their title, like
`Count of Monte`, and by the last name of the author, like `Austen`.

Pageturner only shows the books in English that LibriVox finished. A book's page shows its description, author, readers,
length and the cover from the Internet Archive. **Download audiobook** downloads the MP3 file of each section, in order,
as one audiobook. Each section is a chapter.

You can't add LibriVox yourself or change its address. When you remove it, it doesn't come back.

## Adding a catalog

1. Open **Settings > Library > Ebook catalogs**. You also find it on the screen where you add books, under
   **Download from a catalog**.
2. Tap **Add catalog**.
3. Pick the kind of server:
    - **OPDS** for any catalog. Type the address of the catalog itself, like `books.example.com/opds` for Calibre-Web.
    - **Komga** for a Komga server. Type the address you open in your browser, like `komga.example.com`. Pageturner finds
      the catalog on its own.
4. Give it a name if you like. Without one, Pageturner takes the name the catalog gives itself.
5. If your catalog asks you to sign in, type your username and password.
6. Tap **Save**. Pageturner checks the catalog right away, so a typo or a wrong password shows before you save it.

Tap the menu next to a catalog to change it or to remove it. Books you downloaded from it stay in your library.

## Finding books

Tap a catalog to open it. Shelves, authors and series open one level deeper, and the back button takes you back up. More
books load as you scroll.

When the catalog can search, a search field sits at the top. Your results open like any other page of the catalog.

Tap a book to see its cover, author and description.

## Downloading

Tap **Download EPUB**, **Download PDF**, **Download AZW3** or **Download MOBI**. When a book comes in several of these,
Pageturner takes the EPUB, then the PDF, then the AZW3 and the MOBI last. You see how far the download
got, and **Stop download** stops it.

Once it's done, the book is in your library next to your other books, with the cover, title and author from the catalog.
It reads, bookmarks, highlights and reads aloud like any other ebook. A tick in the catalog shows the books you already
have, and **Read now** opens them.

Comics and other formats, like CBZ, CBR or FB2, can't be downloaded, as Pageturner reads EPUB, PDF, MOBI and AZW3 only.
A Kindle book with DRM downloads, but can't be opened, see [Kindle books](ebooks.md#kindle-books).

## Audiobooks

When a catalog offers a book as audio, its page shows **Download audiobook**, with the format and the size when the
catalog tells it, like `M4B · 312 MB`. A book that comes both as an ebook and as an audiobook shows both buttons, so you
can read it, listen to it, or both.

Audiobooks are large, so they download in the background: a notification shows how far the download got, and **Stop**
in the notification or **Stop download** on the book's page stops it. You can leave Pageturner in the meantime. The first
time, Pageturner asks to show notifications, so you can see the progress.

Once it's done, the audiobook is in your library next to your other audiobooks, with the title, author and cover from
the catalog and the chapters from the file. It plays, keeps your place and takes bookmarks like any other audiobook. A
tick in the catalog shows the books you already have, and **Listen now** plays them.

Supported formats:

| Format | How catalogs label it |
|--------|-----------------------|
| M4B, M4A, MP4 | `audio/mp4`, `audio/x-m4b`, `audio/m4b`, `audio/x-m4a`, and `video/mp4`, which some servers use for M4B |
| MP3 | `audio/mpeg`, `audio/mp3` |
| Ogg Vorbis, Opus | `audio/ogg`, `audio/opus` |
| FLAC | `audio/flac` |
| A zip of audio files | `application/audiobook+zip`, or `application/zip` when the catalog marks the book as an audiobook |
| A Readium audiobook manifest | `application/audiobook+json` |

When a catalog names no format, or only `application/octet-stream`, Pageturner goes by the file extension in the link or
in its title.

Some servers offer one file per chapter. Pageturner downloads them all as one book, in the order the catalog lists them.
The files of a zip play in the order of their names. When a book comes as M4B and as MP3, Pageturner takes the M4B, which
keeps its chapters in one file.

## Where the books are kept

Downloaded books go into a folder of your library, your **download folder**. They're plain files there, like the books
you copied yourself, so you can see them in your file manager, back them up or move them to another device.

The first time you download a book, Pageturner asks which folder to use. It lists the folders of your library that hold
several books, under **Settings > Library > Book folders**. Or tap **Choose another folder** to pick another one with
Android's folder picker. That folder joins your library as a folder where every folder or file is a book.

To change the download folder later, open **Settings > Library > Ebook catalogs** and tap **Download folder** at the top.
Books you already downloaded stay where they are. When the download folder leaves your library, or Pageturner can't
reach it, Pageturner asks again on the next download.

In the download folder:

- an ebook is a file named after its title, like `Persuasion.epub`;
- an audiobook is a folder named after its title, with its audio files and a `cover.jpg`.

When the folders in your download folder are authors, each book goes into the folder of its author, like
`Jane Austen/Persuasion.epub`. When a book with the same name is already there, the new one gets a number, like
`Persuasion (2).epub`. A book only shows up in the folder once it's completely downloaded.

Pageturner then looks through your library, finds the downloaded book like any other, and gives it the title, author and cover
from the catalog. The tick in the catalog and the progress sync with Komga keep working for it.

To take an ebook out of the library, long press it in the library and tap **Hide from library**. Its file stays in the
download folder. Download it again and the book comes back, with your place in it.

To remove a book from your phone, long press it in the library and tap **Delete Book**, like any other book. Its files
leave your phone, and the book leaves the library.

## Syncing your progress with Komga

For a Komga server, Pageturner keeps your place in step with Komga, in both directions. Read a few chapters in Pageturner,
open the book in Komga's own reader on your computer, and it opens where you stopped, and the other way round.

- **Opening a book:** Pageturner asks Komga where you are. When Komga has a newer place, the book opens there. Without a
  connection, the book opens where you left it in Pageturner.
- **While you read:** a few seconds after you turn a page, and when you close the book, Pageturner tells Komga your place.
- **Completed and Not started yet:** marking a book **Completed**, or reading to the end, marks it read in Komga too.
  **Not started yet** marks it unread.
- **Your library:** when you open Pageturner, it takes the progress of your Komga books, so the books you read in Komga
  show how far you got.

When there's no connection, nothing waits for it: your place stays in Pageturner and goes to Komga the next time it can.
When you read the same book in two places, the place you read last wins.

Syncing is on for every Komga server. To turn it off, tap the menu next to the catalog, tap **Edit** and turn off **Sync
reading progress**.

Audiobooks from Komga don't sync: their place stays in Pageturner.

PDF books sync to the exact page. EPUB books sync to about the page: Komga counts the pages of an EPUB differently than
Pageturner, so you might land a paragraph or two before where you stopped.

Kindle books don't sync: Komga doesn't serve them, and its pages wouldn't match the EPUB Pageturner makes from one, so
their place stays in Pageturner.

## Home servers, VPNs and certificates

Many servers are only reachable at home. Pageturner works with plain `http://` addresses in your network, with VPNs like
Tailscale or WireGuard, with reverse proxies in a subfolder and with self-signed certificates, once you install your
certificate authority in the Android settings.

You can leave out `http://` or `https://`. Pageturner tries both: plain `http://` first for addresses in your network, like
`192.168.1.20:25600` or `nas.local`. For a server on the internet, it only tries `https://` when you've entered a
password, so your password is never sent unencrypted by a guess. Type `http://` yourself if that server only speaks
plain http. For an OPDS catalog, the address of the server is enough too: Pageturner also looks for the catalog at
`/opds` and at Komga's path.

Your password only goes to the address of the catalog, never to other sites the catalog links to.

## FAQ

??? question "Does Pageturner keep my progress in step with Komga?"
    Yes, for books you downloaded from a Komga server. See [Syncing your progress with Komga](#syncing-your-progress-with-komga).
    Other OPDS catalogs have no standard way to keep a reading place, so their books only keep it in Pageturner.

??? question "My catalog uses a different sign in, like a token or single sign-on."
    Pageturner signs in with a username and password. Ask whoever runs the server whether your account can sign in that way.

??? question "Where do I find the address of my catalog?"
    Calibre-Web serves it at `/opds`, Kavita shows it in your user settings, and Komga under its own address. Many sites link to
    their catalog with an OPDS logo.
