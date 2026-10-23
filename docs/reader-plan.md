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

## Phase 1: reading modes and layout (sections 1 and 5)

| Feature | Status | How |
|---|---|---|
| Paginated reading | [done] | |
| Single-page and two-page view | [done] | Two pages when the screen is wide. |
| Adaptive layout | [done] | **Pages** in the Display sheet: Auto, One page, Two pages. |
| Continuous scrolling (chapter or whole book) | [new] | Readium scroll mode, or a LazyColumn of paragraphs in our engine. |
| Page transitions: none, slide, curl | [done] | **Page turn** in the Display sheet, for both engines. |
| Scroll behavior: smooth, stepped, snap | [new] | Only in scroll mode. |
| Automatic scrolling with speed, pause on touch | [new] | Timer that scrolls N px per second, stops while a finger is down or text is selected. |
| Automatic page turns at an interval | [new] | Timer that calls the existing turn; pause while menus are open. |
| Multiple columns, column gap | [part] | **Pages** sets the column count in the new engine and the pages in the old one. **Space between pages** sets the gap. More than two columns isn't planned. |
| Margins: top, bottom, left, right separately | [done] | Sides, top and bottom. Left and right stay the same. |
| Reading width limit | [done] | **Shorter lines on wide screens**, about 65 characters. |
| Orientation: portrait, landscape, auto | [done] | |
| Full-screen reading | [done] | Tap the middle hides bars and system bars. |
| Fixed-layout and PDF controls: zoom, pan, fit width, fit page | [part] | PDF: pinch zoom, pan, double tap for fit width or fit page. Fixed-layout EPUB opens in the new engine with its own page fitting; **Pages** picks one page or a spread. |
| Brightness in the reader | [done] | |
| Screen-awake setting | [part] | Screen stays on today; add Off, While reading, Always. |
| Tap zones | [part] | Fixed thirds today; add an editor (left, middle, right, top, bottom areas). |
| Gestures: swipe, double tap, long press actions | [new] | Map gesture to action from a small list. |
| Hardware keys: volume keys, page buttons, remotes | [part] | Volume keys done; add Page Up/Down, arrows, Bluetooth page turners. |
| Keyboard shortcuts and remapping | [new] | Default shortcuts first, remapping later. |
| Mouse wheel: scroll or turn page | [new] | Setting. |
| Customizable toolbar | [new] | Choose and order the top bar actions. |
| Header and footer: title, chapter, page, percent, time, battery | [part] | Chapter and page shown; make each slot choosable. |
| Interface size separate from text | [new] | Scale factor applied to the reader's chrome only. |
| One-handed layout | [new] | Move controls to the left or right edge. |
| Accidental-input protection | [new] | Minimum swipe distance, ignore palm touches at the edges, confirm jumps of more than N pages. |

## Phase 2: page numbers and progress (section 2)

| Feature | Status | How |
|---|---|---|
| Screen page numbers | [done] | From current font, margins and screen. The new engine counts Readium positions instead, which stay the same for every screen. |
| Stable locations | [part] | The old engine stores chapter plus character offset. The new engine stores Readium Locators. |
| Publisher page labels | [new] | Read the EPUB page-list; label it "Print page 42" so its basis is clear. Hidden when the book has none. |
| Estimated standard pages | [new] | Fixed 1,024 characters per page, labelled "about 320 standard pages". |
| Book and chapter page counters | [part] | Book counter done; add "Page 4 of 18 in this chapter". |
| Book and chapter percentage | [part] | Book percent stored; show both. |
| Pages remaining (chapter, book) | [part] | Chapter pages left done; add book. |
| Time remaining | [new] | Measure the reader's own pages per minute, ignore idle time. |
| Progress bar styles: thin line, bar, segmented | [part] | Slider bar exists; add styles. |
| Chapter, bookmark, note and highlight markers on the bar | [new] | Ticks drawn at their positions. |
| Progress visibility: always, on tap, hidden | [part] | On tap today. |
| Progress scope: include or exclude front and back matter | [new] | Use the EPUB landmarks and guide to find body start and end. |

## Phase 3: fonts and typography (section 3)

| Feature | Status | How |
|---|---|---|
| Serif, sans | [done] | |
| Monospace and more reading fonts (for example Atkinson Hyperlegible, OpenDyslexic, Literata, Merriweather) | [new] | Bundle a few, offer more as downloads to keep the APK small. |
| Publisher fonts | [new] | Needs engine A or B. |
| Custom font import | [new] | Pick a .ttf or .otf file, copy into app storage. |
| Font size: slider, presets, exact number | [part] | Slider done; add presets and a number field. |
| Weight, letter spacing, word spacing | [new] | |
| Line spacing | [done] | |
| Paragraph spacing and first-line indent | [new] | |
| Alignment: left, right, center, justified | [new] | |
| Hyphenation (by book language) | [new] | Android hyphenation in our engine, CSS hyphens in Readium. |
| Ligatures on or off | [new] | |
| Headings styled separately | [new] | Needs the book's structure (engine A or B). |
| Drop caps and ornaments: keep or simplify | [new] | Needs engine A or B. |
| Font fallback for missing characters | [part] | System fallback works for system fonts; check bundled ones. |
| Selective overrides (change body text, keep italics and headings) | [new] | Readium "publisher styles" switch. |
| Reset to publisher defaults or my defaults | [new] | |

## Phase 4: colors and themes (section 4)

| Feature | Status | How |
|---|---|---|
| Light, Sepia, Dark (Night), True black | [done] | Day, Sepia, Night, Black. |
| Soft paper, Slate | [new] | Two more presets. |
| Custom: background, text, headings, links, selection | [new] | Color picker with HEX input. |
| Contrast preview | [new] | Show the WCAG contrast ratio and a warning below 4.5:1. |
| Highlight palette with opacity and names ("Important", "Question", "Quote") | [part] | 4 colors today; add names and opacity. |
| Progress, toolbar and footnote colors | [new] | |
| Images: original, dimmed, inverted in dark themes | [new] | Applies to EPUB images and PDF pages. |
| Paper texture with intensity | [new] | Subtle noise or paper image under the text. |
| Follow system light or dark | [done] | Auto theme. |
| Scheduled themes | [new] | Day theme by day, night theme from a set time or sunset. |
| Saved themes: create, duplicate, rename, import, export | [new] | Store as JSON; export and import as a file. |

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
| 7 | Controls: tap zones, gestures, keys, toolbar, header and footer, one-handed, protection | 2 weeks |

Each step ships as its own pull request with CI green before the next starts.
Sizes assume one developer and include translations for every new setting.

## Decisions needed from you

1. Engine: A (Readium), B (own renderer) or C (hybrid).
2. Which extra fonts to bundle in the app versus download.
3. Whether keyboard remapping, saved-theme export and scheduled themes are needed now or later.
