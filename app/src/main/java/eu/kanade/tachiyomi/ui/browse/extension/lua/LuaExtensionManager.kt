package eu.kanade.tachiyomi.ui.browse.extension.lua

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaTable
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LuaExtensionManager {

    private val globals: Globals = JsePlatform.standardGlobals()
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client

    init {
        exposeFunctions()
    }

    private fun exposeFunctions() {
        globals.set("httpGet", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val url = arg.checkjstring()
                val request = GET(url)
                val response = client.newCall(request).execute()
                return LuaValue.valueOf(response.body?.string() ?: "")
            }
        })
        
        globals.set("httpPost", object : TwoArgFunction() {
            override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
                val url = arg1.checkjstring()
                val bodyStr = arg2.checkjstring()
                val request = POST(url, body = bodyStr.toRequestBody("application/json".toMediaType()))
                val response = client.newCall(request).execute()
                return LuaValue.valueOf(response.body?.string() ?: "")
            }
        })

        globals.set("jsonToTable", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val jsonString = arg.checkjstring()
                return try {
                    if (jsonString.trim().startsWith("[")) {
                        jsonArrayToLuaTable(JSONArray(jsonString))
                    } else {
                        jsonObjectToLuaTable(JSONObject(jsonString))
                    }
                } catch (e: Exception) {
                    LuaValue.NIL
                }
            }
        })
        
        globals.set("tableToJson", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                return LuaValue.valueOf(tableToJsonString(arg))
            }
        })

        // New Functions
        globals.set("base64Encode", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                return LuaValue.valueOf(Base64.encodeToString(arg.checkjstring().toByteArray(), Base64.NO_WRAP))
            }
        })

        globals.set("base64Decode", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                return LuaValue.valueOf(String(Base64.decode(arg.checkjstring(), Base64.DEFAULT)))
            }
        })

        globals.set("jsoupParse", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val doc = Jsoup.parse(arg.checkjstring())
                val table = LuaTable()
                table.set("select", object : TwoArgFunction() {
                    override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
                        val elements = doc.select(arg2.checkjstring())
                        val elementsTable = LuaTable()
                        for (i in 0 until elements.size) {
                            val el = elements[i]
                            val elTable = LuaTable()
                            elTable.set("text", el.text())
                            elTable.set("html", el.html())
                            elTable.set("attr", object : OneArgFunction() {
                                override fun call(a: LuaValue): LuaValue = LuaValue.valueOf(el.attr(a.checkjstring()))
                            })
                            elementsTable.set(i + 1, elTable)
                        }
                        return elementsTable
                    }
                })
                return table
            }
        })
    }
    
    private fun jsonObjectToLuaTable(json: JSONObject): LuaTable {
        val table = LuaTable()
        json.keys().forEach { key ->
            val value = json.get(key)
            table.set(key, jsonValueToLuaValue(value))
        }
        return table
    }

    private fun jsonArrayToLuaTable(json: JSONArray): LuaTable {
        val table = LuaTable()
        for (i in 0 until json.length()) {
            table.set(i + 1, jsonValueToLuaValue(json.get(i))) // Lua arrays are 1-indexed
        }
        return table
    }

    private fun jsonValueToLuaValue(value: Any?): LuaValue {
        return when (value) {
            is JSONObject -> jsonObjectToLuaTable(value)
            is JSONArray -> jsonArrayToLuaTable(value)
            is String -> LuaValue.valueOf(value)
            is Int -> LuaValue.valueOf(value)
            is Double -> LuaValue.valueOf(value)
            is Boolean -> LuaValue.valueOf(value)
            else -> LuaValue.NIL
        }
    }
    
    fun tableToJsonString(table: LuaValue): String {
        if (!table.istable()) return "{}"
        return luaTableToJson(table.checktable()).toString()
    }

    private fun isArray(table: LuaTable): Boolean {
        if (table.length() == 0) return false
        val keys = table.keys()
        for (i in 1..table.length()) {
            if (table.get(i).isnil()) return false
        }
        return keys.size == table.length()
    }

    private fun luaTableToJson(table: LuaTable): Any {
        return if (isArray(table)) {
            luaTableToJsonArray(table)
        } else {
            luaTableToJsonObject(table)
        }
    }

    private fun luaTableToJsonObject(table: LuaTable): JSONObject {
        val json = JSONObject()
        val keys = table.keys()
        for (key in keys) {
            val value = table.get(key)
            json.put(key.tojstring(), luaValueToJsonValue(value))
        }
        return json
    }

    private fun luaTableToJsonArray(table: LuaTable): JSONArray {
        val json = JSONArray()
        for (i in 1..table.length()) {
            json.put(luaValueToJsonValue(table.get(i)))
        }
        return json
    }

    private fun luaValueToJsonValue(value: LuaValue): Any? {
        return when {
            value.istable() -> luaTableToJson(value.checktable())
            value.isint() -> value.toint()
            value.isnumber() -> value.todouble()
            value.isboolean() -> value.toboolean()
            value.isnil() -> null
            else -> value.tojstring()
        }
    }

    fun loadExtension(script: String): LuaValue {
        val chunk = globals.load(script)
        return chunk.call()
    }

    fun search(scriptObj: LuaValue, query: String, page: Int): String {
        val searchFunc = scriptObj.get("search")
        if (searchFunc.isfunction()) {
            val result = searchFunc.call(scriptObj, LuaValue.valueOf(query), LuaValue.valueOf(page))
            if (result.istable()) {
                 return tableToJsonString(result)
            }
            return result.toString()
        }
        return "[]"
    }
}
