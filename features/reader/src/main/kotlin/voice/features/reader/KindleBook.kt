package voice.features.reader

import android.content.Context
import android.net.Uri
import voice.core.ebook.mobi.KindleDrmException
import voice.core.ebook.mobi.kindleEpub

/** A MOBI or AZW3 book opens as the EPUB converted from it, so both engines read it like any EPUB. */
internal fun kindleEpubUri(
  context: Context,
  uri: Uri,
): Uri = Uri.fromFile(kindleEpub(context, uri))

internal val Exception.isDrmProtection: Boolean get() = this is KindleDrmException
