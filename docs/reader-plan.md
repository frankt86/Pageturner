# Pageturner reader plan

Plan for the reading modes, page numbers, typography, themes and controls you listed.
Status key: [done] already in the app, [part] partly there, [new] to build.

## Step 0: the engine decision (do this first)

Today the reader turns each EPUB chapter into plain text and draws it with our own Compose code.
That gives us the page curl and full control, but it throws away the book's own formatting:
headings, italics, images, links, footnotes, embedded fonts, page lists and fixed layout.
About half of your list needs that formatting, so the engine choice decides the cost of everything else.

| Option | What it means | Pros | Cons |
|---|---|---|---|
| A. Readium Kotlin toolkit (recommended) | Open-source EPUB engine used by many readers (BSD license). Renders the real XHTML/CSS in a WebView. | Reflowable and fixed-layout EPUB, publisher styles and fonts, images, links, footnotes, page lists, stable locations (Locators), columns, hyphenation, ligatures, scroll and paginated modes, all built in. Months of work saved. | Page curl must be rebuilt on top of it (turn the visible page into an image, curl the image, then swap). Bigger APK (a few MB). WebView quirks. |
| B. Extend our own renderer | Parse XHTML into styled text ourselves. | Keeps the current curl as is. Full control. | Every item above (images, tables, embedded fonts, fixed layout, CSS) is ours to build and maintain. Fixed layout alone is a large project. |
| C. Hybrid | Readium for fixed-layout books and our renderer for plain novels. | Curl stays for novels. | Two engines to maintain, features differ by book. |

Recommendation: A, with the curl kept as a page transition that animates a snapshot of the page.
PDFs keep the current Android PdfRenderer path (zoom and pan added in phase 1).

Decision: A. Readium 3.4.0 is in the app as **New book engine (beta)** in the Display sheet, off by default.

### Where the new engine stands

| Feature | Status |
|---|---|
| Book layout: formatting, images, links, footnotes | [done] |
| Theme, font, text size, line spacing, margins | [done] |
| Fonts, added fonts, spacing, alignment, hyphenation, the book's own style | [done] |
| Page turns: side taps, swipes, volume keys; middle tap shows the bars | [done] |
| Place kept as a Readium Locator, progress in the library | [done] |
| Contents | [done] |
| Page bar with "page X of Y" from Readium positions | [done] |
| Bookmarks | [done] |
| Links to websites open in the browser | [done] |
| Highlights and notes | [done] |
| Search | [done] |
| Reading aloud, with the natural voice or the phone's | [done] |
| Page curl, slide or no animation | [done] | The curl turns a picture of the page; two-page spreads curl as a whole. |
| Scroll mode and automatic scrolling | [done] | One chapter at a time; a sideways swipe or the end of the chapter goes on to the next. |
| Controls: taps, keys, mouse wheel, buttons, header, footer, screen, accidental touches | [done] | As in the old engine. The shortest swipe doesn't apply to Readium's own slide. |

## Phase 1: reading modes and layout (sections 1 and 5)

| Feature | Status | How |
|---|---|---|
| Paginated reading | [done] | |
| Single-page and two-page view | [done] | Two pages when the screen is wide. |
| Adaptive layout | [done] | **Pages** in the Display sheet: Auto, One page, Two pages. |
| Continuous scrolling (chapter or whole book) | [done] | **Reading mode**: Pages or Scroll. The old engine scrolls the whole book as a list of paragraphs, PDF as a list of pages. The new engine uses Readium's scroll mode, one chapter at a time. |
| Page transitions: none, slide, curl | [done] | **Page turn** in the Display sheet, for both engines. Hidden in the scroll mode. |
| Scroll behavior: smooth, stepped, snap | [part] | **Scrolling**: Smooth, Instant (a screen at once) and By screen (each swipe ends on a screen). The new engine has no By screen, its web view can't snap. |
| Automatic scrolling with speed, pause on touch | [done] | **Scroll automatically** in the top bar and **Scrolling speed**. Waits while a finger is down, text is selected, the bars or a sheet are open or the book is read aloud. The new engine scrolls its web view with a script. |
| Automatic page turns at an interval | [done] | **Turn pages automatically** in the top bar and **Time per page**, 10 to 120 seconds. Waits while the bars or a sheet are open or the book is read aloud; a turn by hand starts the time anew. |
| Multiple columns, column gap | [part] | **Pages** sets the column count in the new engine and the pages in the old one. **Space between pages** sets the gap. More than two columns isn't planned. |
| Margins: top, bottom, left, right separately | [done] | Sides, top and bottom. Left and right stay the same. |
| Reading width limit | [done] | **Shorter lines on wide screens**, about 65 characters. |
| Orientation: portrait, landscape, auto | [done] | |
| Full-screen reading | [done] | Tap the middle hides bars and system bars. |
| Fixed-layout and PDF controls: zoom, pan, fit width, fit page | [part] | PDF: pinch zoom, pan, double tap for fit width or fit page. Fixed-layout EPUB opens in the new engine with its own page fitting; **Pages** picks one page or a spread. |
| Brightness in the reader | [done] | |
| Screen-awake setting | [done] | **Keep the screen on** in **Controls**: System, While reading (as before) and Until idle, which lets the screen sleep after 5, 10 or 30 minutes without a touch or key, but not while reading aloud or turning by itself. |
| Tap zones | [done] | **Taps** in **Controls**: Previous page, Next page, Show or hide bars or Nothing for the left, middle and right third, and **Left-handed**. Both engines and the scroll mode. Top and bottom areas aren't planned. |
| Gestures: swipe, double tap, long press actions | [done] | **Double tap**: Nothing, Show or hide bars or Bookmark. Long press stays selection and swipes stay page turns. |
| Hardware keys: volume keys, page buttons, remotes | [done] | Page Up and Down, the arrow keys, space and Shift+space, so Bluetooth page turners that send these keys work. Arrows follow right-to-left books in the new engine. |
| Keyboard shortcuts and remapping | [part] | Ctrl+F search, Ctrl+B bookmark, Ctrl+D display and Esc back. Remapping is left out. |
| Mouse wheel: scroll or turn page | [done] | **Mouse wheel**: Scroll or Turn pages, a page or in the scroll mode a screen per step. |
| Customizable toolbar | [done] | **Buttons** in **Controls**: show, hide and move each top bar button. Display always stays. |
| Header and footer: title, chapter, page, percent, time, battery | [done] | **Page footer** also has Pages left and Nothing; **Show the time**, **Show the battery** and **Page header** (Title or Chapter). PDF pages show none of these. |
| Interface size separate from text | [done] | **Size of bars and menus**, 80 to 140 %, scales the bars, the page bar, the selection menu and the sheets. |
| One-handed layout | [done] | **Buttons at the bottom** moves the top bar's buttons to a bar at the bottom. |
| Accidental-input protection | [done] | **Shortest swipe to turn a page**, **Ignore touches at the edges** and **Ask before jumping over more than** 10, 50 or 100 pages. The shortest swipe doesn't apply to the new engine's slide, which Readium handles itself. |

## Phase 2: page numbers and progress (section 2)

| Feature | Status | How |
|---|---|---|
| Screen page numbers | [done] | From current font, margins and screen. The new engine counts Readium positions instead, which stay the same for every screen. |
| Stable locations | [part] | The old engine stores chapter plus character offset. The new engine stores Readium Locators. |
| Publisher page labels | [part] | New engine: "Print page 42" in the page bar from the EPUB page-list, placed by where each page's anchor is in its file's text. Hidden when the book has none. The old engine keeps only plain text, so it has none. |
| Estimated standard pages | [done] | Fixed 1,024 characters per page, "About 320 standard pages" in the page bar. |
| Book and chapter page counters | [done] | "Page 4 of 18" under "This chapter" in the page bar. The new engine counts a chapter as one file of the book. |
| Book and chapter percentage | [done] | Both in the page bar; the book's can be the page footer. |
| Pages remaining (chapter, book) | [done] | |
| Time remaining | [done] | Characters per minute over page turns forward, leaving out breaks over 2 minutes and jumps; one average over the last hour, for all books. Not for PDF. |
| Progress bar styles: thin line, bar, segmented | [done] | **Progress bar**: Line, Bar, Chapters. |
| Chapter, bookmark, note and highlight markers on the bar | [done] | One switch: chapter ticks, bookmark ribbons and highlight dots (notes are highlights). |
| Progress visibility: always, on tap, hidden | [done] | **Show progress**: Always adds a thin line, and in the new engine a footer with the chapter. |
| Progress scope: include or exclude front and back matter | [part] | New engine: **Count only the main text** uses the bodymatter and backmatter landmarks (or the EPUB 2 guide). The old engine has no book structure. |

## Phase 3: fonts and typography (section 3)

| Feature | Status | How |
|---|---|---|
| Serif, sans | [done] | |
| Monospace and more reading fonts (for example Atkinson Hyperlegible, OpenDyslexic, Literata, Merriweather) | [done] | Mono, Atkinson Hyperlegible (bundled, about 220 KB with its license next to it) and OpenDyslexic (comes with Readium). Literata, the app's serif, is the old engine's Serif and its own choice in the new one. No downloads. |
| Publisher fonts | [done] | New engine: **Book's own style** turns on Readium's publisher styles; the text size and colors still change. |
| Custom font import | [done] | **Add font** copies a .ttf or .otf into the app's fonts folder. The new engine gets it from the book's own address, so the web view loads it. Added fonts can't be removed yet. |
| Font size: slider, presets, exact number | [done] | Slider with minus, plus and the size in between. |
| Weight, letter spacing, word spacing | [done] | Word spacing only in the new engine; Compose text has none. |
| Line spacing | [done] | |
| Paragraph spacing and first-line indent | [done] | The old engine lays the paragraphs out apart once either is set, and keeps one blank line otherwise. |
| Alignment: left, right, center, justified | [done] | Readium can't center, so the new engine has left, right and justified. |
| Hyphenation (by book language) | [done] | Old engine: Android's hyphenation in the phone's language, the old engine has no book language. New engine: the book's language. |
| Ligatures on or off | [done] | Readium's switch only covers Arabic script. |
| Headings styled separately | [part] | **Book's own style** keeps the book's headings. Readium has no separate heading settings, so there are none. |
| Drop caps and ornaments: keep or simplify | [part] | Kept with **Book's own style**. |
| Font fallback for missing characters | [part] | System fallback works for system fonts; check bundled ones. |
| Selective overrides (change body text, keep italics and headings) | [part] | **Book's own style** keeps all of the book's styling. Nothing in between yet. |
| Reset to publisher defaults or my defaults | [done] | **Reset text settings** puts all of these back. Saved "my defaults" aren't planned. |

## Phase 4: colors and themes (section 4)

| Feature | Status | How |
|---|---|---|
| Light, Sepia, Dark (Night), True black | [done] | Day, Sepia, Night, Black. |
| Soft paper, Slate | [done] | Two more presets. |
| Custom: background, text, headings, links, selection | [part] | **Colors and themes**: hue, saturation and brightness sliders, a few colors to start from and a HEX field. Links only in the new engine; the old one has no links. Readium has no heading color, so there is none. |
| Contrast preview | [done] | The WCAG ratio of text on page, with a warning below 4.5:1. |
| Highlight palette with opacity and names ("Important", "Question", "Quote") | [done] | Names for the 4 colors, used in the selection menu and highlight sheet, and **Highlight strength**. |
| Progress, toolbar and footnote colors | [new] | Left out: the bars follow the app colors and the progress line the page's text. |
| Images: original, dimmed, inverted in dark themes | [done] | Readium's image filter in the new engine and a color filter on PDF pages. The old engine shows no images. |
| Paper texture with intensity | [done] | A fine grain in the text color, under the text in the old engine and over the pages in the new one. |
| Follow system light or dark | [done] | Auto theme. |
| Scheduled themes | [done] | **By time**: a day and a night theme, with the night's start and end in half hours. Sunset isn't planned, it needs the location. |
| Saved themes: create, duplicate, rename, import, export | [done] | Kept in the reader settings; export and import as a JSON file. |

## Order and rough size

| # | Work | Size |
|---|---|---|
| 0 | Engine decision, then Readium spike on 3 real books including one fixed-layout | 1 week |
| 1 | Readium integration with curl, slide and instant transitions | 2 to 3 weeks |
| 2 | Layout controls: adaptive setting, columns, four margins, width limit, orientation | 1 week |
| 3 | Progress: counters, page labels, standard pages, time left, bar styles and markers | 1 to 2 weeks |
| 4 | Typography: fonts, import, spacing, alignment, hyphenation, overrides, reset | 1 to 2 weeks |
| 5 | Themes: new presets, custom colors, contrast, highlight names, schedules, saved themes | 1 to 2 weeks |
| 6 | Scroll mode, auto scroll, auto page turn | 1 week |
| 7 | Controls: tap zones, gestures, keys, toolbar, header and footer, one-handed, protection | 2 weeks, done |

Each step ships as its own pull request with CI green before the next starts.
Sizes assume one developer and include translations for every new setting.

## Decisions needed from you

1. Engine: A (Readium), B (own renderer) or C (hybrid).
2. Which extra fonts to bundle in the app versus download.
3. Whether keyboard remapping, saved-theme export and scheduled themes are needed now or later.
