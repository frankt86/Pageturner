# Organizing Audiobooks

In Pageturner, an audiobook is always one folder or one file. All audio files in the folder of a book are its chapters,
also the files in folders inside it, in the order of their names.

When you add a folder, Pageturner looks at how the files in it are organized. It shows you the books it found before it
adds them. If the books don't look right, tap **Change how books are found**. The three options differ only in how deep
the books are:

| Option                     | A book is                            | Example                                |
|----------------------------|--------------------------------------|----------------------------------------|
| **One book**               | The folder you picked                | `The Hobbit/01.mp3`                    |
| **Every folder is a book** | Each folder in the folder you picked | `Audiobooks/The Hobbit/01.mp3`         |
| **Folders are authors**    | Each folder one level deeper         | `Audiobooks/Tolkien/The Hobbit/01.mp3` |

To change the option later, open **Settings > Library > Book folders** and tap the folder. Your progress and bookmarks
stay with your books.

EPUB, PDF and Kindle files in these folders show up as [ebooks](ebooks.md), however the folder is organized. A folder
can hold only ebooks, only audiobooks, or both.

## One book

The folder you picked is one book. Pick this option when you add a single book.

```
Pride and Prejudice/      <- the folder you picked
├─ CD 1/
│   ├─ 01.mp3
│   └─ 02.mp3
└─ CD 2/
    └─ 01.mp3
```

Pageturner finds one book, `Pride and Prejudice`, with three chapters.

## Every folder is a book

Each folder in the folder you picked is a book. So is each audio file directly in it, like an M4B file.

```
Audiobooks/               <- the folder you picked
├─ The Hobbit/
│   ├─ 01.mp3
│   └─ 02.mp3
├─ Moby Dick/
│   ├─ CD 1/
│   └─ CD 2/
└─ The Little Prince.m4b
```

Pageturner finds three books: `The Hobbit`, `Moby Dick` and `The Little Prince`.

## Folders are authors

Each folder in the folder you picked holds the books of one author, or of one series. Each folder or audio file in it is
a book.

```
Audiobooks/               <- the folder you picked
├─ Tolkien/
│   ├─ The Hobbit/
│   │   ├─ 01.mp3
│   │   └─ 02.mp3
│   └─ The Silmarillion.m4b
└─ Woolf/
    ├─ Mrs Dalloway/
    └─ To the Lighthouse/
```

Pageturner finds four books: `The Hobbit`, `The Silmarillion`, `Mrs Dalloway` and `To the Lighthouse`. The author
folders only tell Pageturner where the books are. The name of the author comes from the tags in your files.

## When your books don't look right

- **Several books became one long book**, with all their chapters in a row: your books are one folder deeper. Pick the
  next option down in the table above.
- **Every chapter became a book of its own**: your books are one folder higher. Pick the next option up.
- **"Looks like 7 books"** on a book in the list of books found: the folders in it look like books of their own. The
  next option down usually fits.
- **Your books are organized in different ways**, like an author folder with a series folder in it: Pageturner uses one
  option for the whole folder. Keep the books that are organized differently in a folder of their own, and add it with
  the option that fits it.

To add a book that is a single audio file, tap **Add a single file instead**.

## Series

Pageturner reads the series and the part of a book from the tags of its files. You can also set them yourself: long-press
the book and tap **Edit**. The books of a series stack together in the library, see [Series](ebooks.md#series).
