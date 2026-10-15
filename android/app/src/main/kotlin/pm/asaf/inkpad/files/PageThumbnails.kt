// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.core.model.Page

/**
 * Small pictures of pages (for the page panel), [width] pixels wide, rendered on [dispatcher]
 * (one thread: renderers aren't thread-safe) with a renderer from [newRenderer], and kept on
 * the main thread of [scope]. Pages never change, so a thumbnail is kept for the page object
 * it shows; an edited page is a new object, rendered anew.
 */
class PageThumbnails(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val newRenderer: () -> PageRenderer,
    private val width: Int = DEFAULT_WIDTH,
) {
    /** A page by identity: equal pages are not the same page. */
    private class Key(
        val page: Page,
    ) {
        override fun equals(other: Any?): Boolean = other is Key && other.page === page

        override fun hashCode(): Int = System.identityHashCode(page)
    }

    private val cache =
        object : LruCache<Key, Bitmap>(MAX_BYTES) {
            override fun sizeOf(
                key: Key,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    private val requested = HashSet<Key>()
    private var renderer: PageRenderer? = null
    private val renderedFlow = MutableStateFlow(0)

    /** Counts the thumbnails rendered, to draw again when one arrives. */
    val rendered: StateFlow<Int> = renderedFlow.asStateFlow()

    /** The thumbnail of [page], or null while it is rendered. Call on the main thread. */
    operator fun get(page: Page): Bitmap? {
        val key = Key(page)
        cache.get(key)?.let { return it }
        if (requested.add(key)) {
            scope.launch {
                val bitmap =
                    withContext(dispatcher) {
                        (renderer ?: newRenderer().also { renderer = it }).renderToBitmap(
                            page,
                            width / page.width.toFloat(),
                        )
                    }
                cache.put(key, bitmap)
                requested.remove(key)
                renderedFlow.value++
            }
        }
        return null
    }

    private companion object {
        const val DEFAULT_WIDTH = 240
        const val MAX_BYTES = 24 * 1024 * 1024
    }
}
