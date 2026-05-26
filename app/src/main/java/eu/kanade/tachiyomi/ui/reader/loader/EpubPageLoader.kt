package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import mihon.core.archive.EpubReader

/**
 * Loader used to load a chapter from a .epub file.
 */
internal class EpubPageLoader(private val reader: EpubReader) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val textPages = reader.getTextFromPages()
        if (textPages.any { it.isNotBlank() }) {
            return textPages.mapIndexed { i, text ->
                ReaderPage(index = i, text = text).apply {
                    status = Page.State.Ready
                }
            }
        }

        return reader.getImagesFromPages().mapIndexed { i, path ->
            ReaderPage(index = i).apply {
                stream = { reader.getInputStream(path)!! }
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
