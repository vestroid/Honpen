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
        val pagePaths = reader.getPagePaths()
        val pageTitles = reader.getPageTitles()
        if (pagePaths.isNotEmpty()) {
            return pagePaths.mapIndexed { i, path ->
                ReaderPage(index = i, url = pageTitles.getOrElse(i) { "Chapter ${i + 1}" }).apply {
                    text = "" // Dummy text to mark as text page and prevent image decoding
                    stream = { reader.getInputStream(path)!! }
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
