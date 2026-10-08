package com.ledga.app.data.update

import android.content.res.AssetManager
import com.ledga.core.update.NotesSection
import com.ledga.core.update.ReleaseNotes
import java.io.IOException

/** Spec §13.2: this build's own release notes, bundled at build time, for What's new (R141). */
fun interface BundledNotes {
    /** Empty when the build has none. */
    fun read(): List<NotesSection>

    companion object {
        /** Written by `bundle<Variant>ReleaseNotes` from `release-notes/<VERSION_NAME>.md`. */
        const val ASSET = "release-notes/current.md"
    }
}

class AssetNotes(private val assets: AssetManager) : BundledNotes {
    override fun read(): List<NotesSection> = try {
        assets.open(BundledNotes.ASSET).bufferedReader().use { ReleaseNotes.parse(it.readText()) }
    } catch (e: IOException) {
        emptyList()
    }
}
