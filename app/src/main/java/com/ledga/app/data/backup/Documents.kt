package com.ledga.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/** A file the person chose in Android's picker (R124: save; R122: restore). */
interface Documents {
    fun write(uri: Uri): OutputStream

    fun read(uri: Uri): InputStream
}

class AndroidDocuments(private val resolver: ContentResolver) : Documents {
    // "wt": a file picked again is replaced, not written over its start.
    override fun write(uri: Uri): OutputStream = resolver.openOutputStream(uri, "wt") ?: throw IOException("can't write there")

    override fun read(uri: Uri): InputStream = resolver.openInputStream(uri) ?: throw IOException("can't read it")
}

/** You's "Android backup" row (R125): when this phone's snapshot was last written. */
fun interface BackupStatus {
    fun savedAt(): Instant?
}
