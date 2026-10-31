# Pageturner

[![CI](https://github.com/frankt86/Pageturner/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/frankt86/Pageturner/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE.md)

**A clean audiobook player and ebook reader for Android.**

Pageturner is based on [Voice](https://github.com/PaulWoitaschek/Voice) by Paul Woitaschek, and keeps merging its updates.
It adds ebooks, a full reader, reading aloud and ebook catalogs on top.

<p align="center">
  <img alt="Library" width="30%" src="docs/images/library.png" />
  <img alt="Player" width="30%" src="docs/images/player.png" />
  <img alt="Reader" width="30%" src="docs/images/reader-folded.png" />
</p>

## Download

**[Download Pageturner](https://frankt86.github.io/Pageturner/pageturner.apk)** on your phone, open the file and tap
**Install**. It's always the newest build, and installs next to Voice from Google Play or F-Droid without touching its
library. See [Install](docs/install.md) for the steps.

## Features

### Audiobooks

- Add a folder once and Pageturner finds your M4B, MP3, M4A, OGG, OGA and OPUS audiobooks. See
  [Organizing Audiobooks](docs/organizing.md).
- Resume where you stopped, chapters, bookmarks, a sleep timer with fade-out, playback speed, silence skipping, volume boost
  and auto-rewind after pauses.
- Android Auto and widgets.
- Your place, speed, bookmarks and book names travel to your other devices with the library folder, using a sync tool of your
  own. See the [FAQ](docs/faq.md#how-do-i-continue-a-book-on-another-device).

### Ebooks

- EPUB and PDF books in your folders sit next to your audiobooks, with their covers and a small book badge.
- An EPUB's series and its part in it come from the book.
- Long-press an ebook to mark it read or unread, edit its title and author, set a cover from the internet or from a file, or
  remove it.
- The library search finds ebooks too, forgives typos, and browses them by author and series.
- A **Continue reading** card takes you back to the book you read last.

### The reader

- Two engines: the original one with a real page curl, and the **New book engine (beta)**, built on
  [Readium](https://readium.org), which shows EPUB books with their pictures, headings, links and footnotes.
- Pages curl, slide or turn at once. Two pages side by side on tablets and unfolded foldables.
- Themes: presets, your own colors with a contrast check, saved themes, day and night themes by time, paper texture and
  dimmed or inverted images on dark pages.
- Typography: bundled fonts, your own .ttf or .otf fonts, size, spacing, alignment, hyphenation and the book's own style.
- Layout: one or two pages, four margins, shorter lines on wide screens, orientation, and zoom for PDF pages.
- Progress: chapter and book pages, print page numbers, standard pages, time left, bar styles and marks for chapters,
  bookmarks and highlights.
- A scroll mode, scrolling by itself and turning pages by itself.
- Controls: tap zones, double tap, volume and keyboard keys, Bluetooth page turners, the mouse wheel, your own toolbar,
  buttons at the bottom, interface size, a page header and footer with the time and battery, keeping the screen on and
  protection against accidental touches.
- Bookmarks, highlights with notes, search and word lookup.

### Reading aloud

- A natural voice that runs on your phone, offline, with American and British English voices and voices for several other
  languages. Or your phone's own voice.

### Catalogs and servers

- Browse and search OPDS catalogs like Calibre-Web, Komga and Kavita, and download EPUB and PDF books and audiobooks into a
  download folder of your library. See [Ebook catalogs](docs/opds.md).
- Your reading progress syncs both ways with Komga.
- Stream and download audiobooks from [Audiobookshelf](docs/audiobookshelf.md), with your position and bookmarks in sync.

### And

- No account, no ads, no forced cloud sync.
- In many languages, including Traditional Chinese.

<p align="center">
  <img alt="Two pages side by side on an unfolded foldable" width="90%" src="docs/images/reader-unfolded.png" />
</p>

## Learn more

The [docs](https://frankt86.github.io/Pageturner/) cover installing, organizing your library, [ebooks and the
reader](docs/ebooks.md), [ebook catalogs](docs/opds.md), [Audiobookshelf](docs/audiobookshelf.md) and the
[FAQ](docs/faq.md).

## Contributing

To find your way around the code, start with [Development](docs/development.md) and [Architecture](docs/architecture.md).

## License

Pageturner, like Voice, is licensed under [GNU GPLv3](LICENSE.md). By contributing, you agree to license your code under the
same terms.
