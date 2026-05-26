package eu.kanade.tachiyomi.ui.browse.extension.lua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
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
                if (!arg.istable()) return LuaValue.valueOf("{}")
                // Simplified serialization
                return LuaValue.valueOf(luaTableToJsonObject(arg.checktable()).toString())
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
        return luaTableToJsonObject(table.checktable()).toString()
    }

    private fun luaTableToJsonObject(table: LuaTable): JSONObject {
        val json = JSONObject()
        val keys = table.keys()
        for (key in keys) {
            val value = table.get(key)
            if (value.istable()) {
                json.put(key.tojstring(), luaTableToJsonObject(value.checktable()))
            } else if (value.isint()) {
                json.put(key.tojstring(), value.toint())
            } else if (value.isnumber()) {
                json.put(key.tojstring(), value.todouble())
            } else if (value.isboolean()) {
                json.put(key.tojstring(), value.toboolean())
            } else {
                json.put(key.tojstring(), value.tojstring())
            }
        }
        return json
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
                 return luaTableToJsonObject(result.checktable()).toString() // Needs proper array serialization in reality
            }
            return result.toString()
        }
        return "[]"
    }
}
