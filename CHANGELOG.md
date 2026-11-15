# Changelog

All notable changes to Pageturner are in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [26.11.1] - 2026-11-15

The first stable release of Pageturner. Pageturner is a fork of
[Voice](https://github.com/PaulWoitaschek/Voice), the audiobook player by Paul Woitaschek. This list shows what
Pageturner adds to Voice.

### Added

#### Ebooks in the library

- EPUB, PDF and Kindle books (MOBI, AZW3, AZW and PRC) in your book folders show up next to your audiobooks, with their
  cover, title and author. A small book badge marks them. A folder can hold only ebooks.
- Ebooks sit in Current, Not started yet and Completed, like audiobooks.
- A Continue reading card takes you back to the ebook you read last.
- Long-press an ebook to mark it, edit its title, author, series and part, set a cover from the internet or from a file,
  hide it, or select it with other books.
- The series and part of an EPUB come from the book. Ebooks and audiobooks of one series share a stack and a series
  screen.
- The library search finds ebooks, and Browse lists them under their authors and series.
- Group by author, author pages, multi-select, hidden books, series actions and the folder chips work for ebooks too.

#### The reader

- Pages curl like paper and follow your finger. You can also let them slide, or turn at once.
- Two pages side by side on tablets, phones on their side and unfolded foldables. The right page curls over the spine.
- The Readium engine (the New book engine (beta) switch) shows EPUB books with their pictures, headings, links,
  footnotes and style.
- Themes: Day, Sepia, Night, Black, Soft paper, Slate and Auto. Your own colors with a contrast check, saved themes with
  export and import, day and night themes by time, paper texture, and dimmed or inverted images on dark pages.
- Text: Serif, Sans, Mono, Atkinson Hyperlegible, OpenDyslexic and your own fonts. Text size, line spacing, font weight,
  letter spacing, paragraph spacing, indent, alignment, hyphenation, ligatures and the style of the book.
- Layout: one or two pages, margins, space between pages, shorter lines on wide screens and a fixed orientation.
- Progress: a page bar to jump anywhere, pages of the chapter and the book, percent, pages left, time left from your
  reading speed, standard pages, print page numbers, bar styles, and marks for chapters, bookmarks and highlights.
- A scroll mode, automatic scrolling and automatic page turns.
- Controls: what each tap does, double tap, left-handed mode, volume keys, keyboard shortcuts, Bluetooth page turners,
  the mouse wheel, your own toolbar buttons, buttons at the bottom, bar size, a page header and footer with the time and
  battery, keeping the screen on, and protection against accidental touches.
- Bookmarks, highlights in four colors with notes and your own color names, search in the book, and word lookup in your
  dictionary app.
- Pinch to zoom on PDF pages.
- PDFs with a password open after you type it, and Pageturner keeps it. PDFs that the PDF reader of Android refuses open
  with PDFium on 64-bit phones.
- When a book can't open, the reader tells you why.
- Kindle books without DRM open like EPUB books.

#### Reading aloud

- A natural voice (Kokoro) that runs on your phone and works offline, after a download of about 130 MB.
- American and British English voices, and voices for Spanish, French, Hindi, Italian, Japanese, Portuguese and
  Chinese. Or the text-to-speech voice of your phone.
- The reader highlights the passage it reads and turns the pages along.

#### Catalogs and sync

- Browse and search OPDS 1 and OPDS 2 catalogs, like Calibre-Web, Komga and Kavita.
- Free English catalogs come with the app: Project Gutenberg, Internet Archive (with over 21,000 LibriVox audiobooks),
  Open Library, Wikisource, Unglue.it, OAPEN Library and the Directory of Open Access Books.
- LibriVox comes with the app: browse the newest English audiobooks, search them by title or author, and download them
  as MP3 audiobooks with a chapter for each section.
- Download EPUB, PDF, AZW3 and MOBI books, and audiobooks, into a download folder of your library.
- Your reading progress syncs both ways with Komga, and Completed and Not started yet mark books read or unread in Komga.
- Catalogs and Komga servers at home work with plain http, VPNs and the usual catalog paths.

#### Install and updates

- Stable releases signed with the release key, on GitHub Releases and on the website as `pageturner.apk`, with updates
  through Obtainium.
- A test build from every change, as `pageturner-debug.apk`.
- The stable app (`io.github.frankt86.pageturner`), the test app and Voice install side by side.

### Changed

- The app is called Pageturner, and its texts talk about books, not only audiobooks.
- Links for feedback and the privacy policy go to Pageturner.
- The website and the docs describe Pageturner, with an Install page.

### Fixed

- Audiobook downloads from a catalog show their real progress and don't stop at 50%.
- Ebooks open faster.

### From Voice

Pageturner includes the updates of Voice up to November 2026:

- A library that groups series into stacks, with a screen for each series, and can group by author, with author pages.
- Edit the title, author, series and part of a book, and sync them to your other devices through the library folder.
- Hide books, series, folders and Audiobookshelf libraries, select several books at once, and mark a whole series as
  completed or not started yet.
- A search that accepts one typo per word, and Android Auto with search and sections.
- Tablet layouts, a calmer player, and a switch to turn off cover colors.
- A sleep timer that starts again when you shake or press play right after it pauses.
- Traditional Chinese, and many fixes for the player, the scanner and Android Auto.
