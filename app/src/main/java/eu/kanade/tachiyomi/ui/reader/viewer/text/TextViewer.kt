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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tachiyomi.data.Database

class TextViewer(val activity: ReaderActivity) : Viewer {

    private val database: Database = Injekt.get()
    private val scope = CoroutineScope(Dispatchers.IO)

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
    }

    init {
        // Load the HTML prototype from the project root. In a real app it would be in assets.
        val prototypeFile = File(activity.filesDir.parentFile?.parentFile?.parentFile, "TextRendererPrototype.html")
        if (prototypeFile.exists()) {
            val content = prototypeFile.readText()
            webView.loadDataWithBaseURL("file://", content, "text/html", "UTF-8", null)
        } else {
            // Fallback if not found locally
            webView.loadData("<html><body>Prototype not found at ${prototypeFile.absolutePath}</body></html>", "text/html", "UTF-8")
        }
    }

    override fun getView(): View {
        return webView
    }

    override fun destroy() {
        webView.destroy()
    }

    override fun setChapters(chapters: ViewerChapters) {
        // Here we would inject the chapter text from chapters into the webview
        val currChapter = chapters.currChapter
        val pages = currChapter.pages
        if (pages.isNullOrEmpty()) return

        val textPages = pages.filter { it.text != null }
        if (textPages.isNotEmpty()) {
            // Join the text or handle it
            val content = textPages.joinToString("\n") { it.text ?: "" }
            
            val chapterId = currChapter.chapter.id ?: 0L
            val notes = database.notesQueries.getNotesByChapterId(chapterId).executeAsList()
            
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

            // Create a JSON object representing the book and chapter
            val jsBook = JSONObject().apply {
                put("title", currChapter.chapter.name)
                put("chapters", JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", chapterId)
                        put("title", currChapter.chapter.name)
                        put("rawContent", content)
                        put("notes", notesJsonArray)
                    })
                })
            }
            
            // Inject into WebView
            val js = "javascript:(function() { " +
                     "  BOOK = ${jsBook.toString()}; " +
                     "  BOOK.chapters.forEach(ch => { ch.html = processRaw(ch.rawContent); }); " +
                     "  if (typeof NOTES !== 'undefined') { NOTES = BOOK.chapters[0].notes; } " +
                     "  invalidatePageCache(); " +
                     "  if (!S.scrollMode) { S.pages = getPagesFor(0); renderPage('next'); updateUI(); } " +
                     "  else { setMode(true); } " +
                     "})()"
            webView.evaluateJavascript(js, null)
        }
    }

    override fun moveToPage(page: ReaderPage) {
        val js = "javascript:go(0, ${page.index}, 'next');"
        webView.evaluateJavascript(js, null)
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        return false
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }
}
