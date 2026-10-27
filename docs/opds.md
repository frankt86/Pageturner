# Ebook catalogs (OPDS)

Many ebook servers share their books as a catalog in a standard format called OPDS. Calibre-Web, Komga, Kavita, the
Calibre content server and sites like Standard Ebooks all have one. Add a catalog to Pageturner, look through it and
download its EPUB and PDF books into your library.

There is no Pageturner account and no Pageturner server in between. Pageturner only talks to the catalogs you add.

## Adding a catalog

1. Open **Settings » Library » Ebook catalogs**. You also find it on the screen where you add books, under
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

Tap **Download EPUB** or **Download PDF**. When a book comes in both, Pageturner takes the EPUB. You see how far the download
got, and **Stop download** stops it.

Once it's done, the book is in your library next to your other books, with the cover, title and author from the catalog.
It reads, bookmarks, highlights and reads aloud like any other ebook. A tick in the catalog shows the books you already
have, and **Read now** opens them.

Comics and other formats, like CBZ, CBR or MOBI, can't be downloaded, as Pageturner reads EPUB and PDF only.

## Where the books are kept

Downloaded books live in Pageturner's own storage on your phone, not in one of your book folders. That way they work
without picking a folder, and nothing gets mixed up with the files you manage yourself.

To remove a book, long press it in the library and tap **Remove from library**. Download it again and your place in the
book is still there.

## Home servers, VPNs and certificates

Many servers are only reachable at home. Like with Audiobookshelf, Pageturner works with plain `http://` addresses in your
network, with VPNs like Tailscale or WireGuard, with reverse proxies in a subfolder and with self-signed certificates, once
you install your certificate authority in the Android settings.

Your password only goes to the address of the catalog, never to other sites the catalog links to.

## FAQ

??? question "Does Pageturner keep my progress in step with Komga?"
    Not yet. For now, books from Komga are downloaded and read in Pageturner. Syncing the reading progress with Komga is planned.

??? question "My catalog uses a different sign in, like a token or single sign-on."
    Pageturner signs in with a username and password. Ask whoever runs the server whether your account can sign in that way.

??? question "Where do I find the address of my catalog?"
    Calibre-Web serves it at `/opds`, Kavita shows it in your user settings, and Komga under its own address. Many sites link to
    their catalog with an OPDS logo.
