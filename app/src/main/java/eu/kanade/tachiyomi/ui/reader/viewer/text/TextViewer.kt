package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.FrameLayout
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tachiyomi.data.Database
import org.json.JSONException

class TextViewer(val activity: ReaderActivity) : Viewer {

    private val database: Database = Injekt.get()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var allPages: List<ReaderPage> = emptyList()
    private val loadedChapterIndices = mutableSetOf<Int>()

    private val webView: WebView = WebView(activity).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        @SuppressLint("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
        addJavascriptInterface(WebAppInterface(), "Android")
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun saveNote(chapterId: Long, pageIndex: Int, selectedText: String?, noteText: String, color: String?) {
            scope.launch {
                database.notesQueries.insertNote(
                    chapter_id = chapterId,
                    page_index = pageIndex.toLong(),
                    selected_text = selectedText,
                    note_text = noteText,
                    color = color,
                    created_at = System.currentTimeMillis()
                )
            }
            println("Saved note for chapter $chapterId")
        }

        @JavascriptInterface
        fun deleteNote(noteId: Long) {
            scope.launch {
                database.notesQueries.deleteNote(noteId)
            }
            println("Deleted note $noteId")
        }

        @JavascriptInterface
        fun onChapterChanged(index: Int) {
            activity.runOnUiThread {
                loadWindow(index)
            }
        }
    }

    init {
        try {
            val content = activity.assets.open("TextRendererPrototype.html").bufferedReader().use { it.readText() }
            webView.loadDataWithBaseURL("file:///android_asset/", content, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            e.printStackTrace()
            webView.loadData("<html><body>Prototype not found in assets.</body></html>", "text/html", "UTF-8")
        }
    }

    override fun getView(): View {
        return webView
    }

    override fun destroy() {
        webView.destroy()
    }

    override fun setChapters(chapters: ViewerChapters) {
        val currChapter = chapters.currChapter
        val pages = currChapter.pages
        if (pages.isNullOrEmpty()) return
        allPages = pages
        loadedChapterIndices.clear()

        val chapterId = currChapter.chapter.id ?: 0L
        
        scope.launch {
            val notes = database.notesQueries.getNotesByChapterId(chapterId).awaitAsList()
            
            val notesJsonArray = JSONArray()
            for (note in notes) {
                notesJsonArray.put(JSONObject().apply {
                    put("id", note._id)
                    put("chapterId", note.chapter_id)
                    put("pageIndex", note.page_index)
                    put("selectedText", note.selected_text)
                    put("noteText", note.note_text)
                    put("color", note.color)
                    put("createdAt", note.created_at)
                })
            }

            val jsBook = JSONObject().apply {
                put("title", currChapter.chapter.name)
                put("chapters", JSONArray().apply {
                    pages.forEachIndexed { i, page ->
                        val chTitle = if (page.url.isNotBlank() && page.url != page.index.toString()) page.url else "Chapter ${i + 1}"
                        put(JSONObject().apply {
                            put("id", chapterId) // Keeping same chapter id for notes
                            put("title", chTitle)
                            put("rawContent", "")
                            if (i == 0) put("notes", notesJsonArray)
                        })
                    }
                })
            }
            
            val js = "javascript:(function() { " +
                     "  BOOK = ${jsBook.toString()}; " +
                     "  if (typeof NOTES !== 'undefined' && BOOK.chapters.length > 0) { NOTES = BOOK.chapters[0].notes || []; } " +
                     "  invalidatePageCache(); " +
                     "  if (!S.scrollMode) { S.pages = getPagesFor(0); renderPage('next'); updateUI(); } " +
                     "  else { setMode(true); } " +
                     "})()"
                     
            activity.runOnUiThread {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    override fun moveToPage(page: ReaderPage) {
        val mainIdx = page.index
        loadWindow(mainIdx)
        
        val js = "javascript:go(${page.index}, 0, 'next');"
        webView.evaluateJavascript(js, null)
    }

    private fun loadWindow(mainIdx: Int) {
        if (allPages.isEmpty()) return

        val windowStart = maxOf(0, mainIdx - 3)
        val windowEnd = minOf(allPages.size - 1, mainIdx + 4)
        val windowIndices = (windowStart..windowEnd).toSet()

        // Unload pages outside window to save memory
        loadedChapterIndices.minus(windowIndices).forEach { idx ->
            val js = "javascript:(function() { if(BOOK && BOOK.chapters[$idx]) { BOOK.chapters[$idx].rawContent = ''; BOOK.chapters[$idx].html = '<p>(Empty content)</p>'; } invalidatePageCache(); })()"
            webView.evaluateJavascript(js, null)
        }

        // Load main chapter first
        if (!loadedChapterIndices.contains(mainIdx)) {
            loadChapterContent(mainIdx)
        }
        
        // Then load new chapters
        for (idx in mainIdx + 1..windowEnd) {
            if (!loadedChapterIndices.contains(idx)) {
                loadChapterContent(idx)
            }
        }
        
        // Then load old chapters if needed
        for (idx in mainIdx - 1 downTo windowStart) {
            if (!loadedChapterIndices.contains(idx)) {
                loadChapterContent(idx)
            }
        }

        loadedChapterIndices.clear()
        loadedChapterIndices.addAll(windowIndices)
    }

    private fun loadChapterContent(idx: Int) {
        val page = allPages.getOrNull(idx) ?: return
        scope.launch {
            val content = page.stream?.invoke()?.bufferedReader()?.use { it.readText() } ?: page.text ?: ""
            activity.runOnUiThread {
                try {
                    val escapedContent = JSONObject.quote(content)
                    val js = "javascript:(function() { if(BOOK && BOOK.chapters[$idx]) { BOOK.chapters[$idx].rawContent = $escapedContent; BOOK.chapters[$idx].html = processRaw(BOOK.chapters[$idx].rawContent); } invalidatePageCache(); })()"
                    webView.evaluateJavascript(js, null)
                    
                    // If we just loaded the currently visible chapter, re-render to show it
                    val reRenderJs = "javascript:(function() { if (S.chIdx === $idx) { if (S.scrollMode) { renderScrollMode(); } else { S.pages = getPagesFor($idx); renderPage('next'); } updateUI(); } })()"
                    webView.evaluateJavascript(reRenderJs, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        return false
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }
}