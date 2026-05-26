package eu.kanade.tachiyomi.ui.browse.extension.lua

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.LuaValue

class LuaSource(
    private val luaManager: LuaExtensionManager,
    private val script: String
) : CatalogueSource {

    private val sourceObj: LuaValue = luaManager.loadExtension(script)

    override val id: Long = sourceObj.get("id").toString().hashCode().toLong()
    override val name: String = sourceObj.get("name").toString()
    override val lang: String = sourceObj.get("lang").toString()
    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        return getSearchManga(page, "", FilterList())
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val json = luaManager.search(sourceObj, query, page)
        val array = JSONArray(json)
        val mangas = mutableListOf<SManga>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            mangas.add(SManga.create().apply {
                title = obj.getString("title")
                url = obj.getString("url")
                thumbnail_url = obj.optString("coverUrl")
                author = obj.optString("author")
                description = obj.optString("description")
            })
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        return MangasPage(emptyList(), false)
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        return manga
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val getChapterListFunc = sourceObj.get("getChapterList")
        if (getChapterListFunc.isfunction()) {
            val result = getChapterListFunc.call(sourceObj, LuaValue.valueOf(manga.url))
            val json = luaManager.tableToJsonString(result)
            val array = JSONArray(json)
            val chapters = mutableListOf<SChapter>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                chapters.add(SChapter.create().apply {
                    name = obj.getString("name")
                    url = obj.getString("url")
                    date_upload = obj.optLong("dateUpload", 0)
                    chapter_number = obj.optDouble("chapterNumber", -1.0).toFloat()
                })
            }
            return chapters
        }
        return emptyList()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val getTextContentFunc = sourceObj.get("getTextContent")
        if (getTextContentFunc.isfunction()) {
            val result = getTextContentFunc.call(sourceObj, LuaValue.valueOf(chapter.url))
            val json = luaManager.tableToJsonString(result)
            val obj = JSONObject(json)
            val content = obj.getString("content")
            
            // For a novel, we might return just one page with all text
            return listOf(Page(0, chapter.url, null).apply {
                text = content
            })
        }
        return emptyList()
    }

    override fun getFilterList(): FilterList = FilterList()
}
