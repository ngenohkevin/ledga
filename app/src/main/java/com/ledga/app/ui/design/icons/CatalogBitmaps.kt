package com.ledga.app.ui.design.icons

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Catalog WebPs decoded off the main thread and kept (a 96 px icon is ~36 KB decoded; 256 of them ~9 MB). */
object CatalogBitmaps {
    private val cache = LruCache<String, ImageBitmap>(256)

    fun cached(key: String): ImageBitmap? = cache.get(key)

    /** The icon, or null for a key with no asset (a newer backup's, a hand-edited row's: R85). */
    suspend fun load(assets: AssetManager, key: String): ImageBitmap? = cache.get(key) ?: withContext(Dispatchers.IO) { decode(assets, key) }

    /** Decodes [keys] now, on this thread: screenshot tests draw every icon on the first frame. */
    fun preload(assets: AssetManager, keys: Collection<String>) = keys.forEach { decode(assets, it) }

    private fun decode(assets: AssetManager, key: String): ImageBitmap? =
        runCatching { assets.open(IconCatalog.assetPath(key)).use(BitmapFactory::decodeStream) }.getOrNull()
            ?.asImageBitmap()?.also { cache.put(key, it) }
}
