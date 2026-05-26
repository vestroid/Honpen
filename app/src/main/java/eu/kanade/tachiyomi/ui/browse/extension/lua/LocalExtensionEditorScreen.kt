package eu.kanade.tachiyomi.ui.browse.extension.lua

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.io.File
import java.util.UUID

val STUB_LUA_CODE = """
-- example-extension.lua
SOURCE = {
    id = "my-novel-source",
    name = "My Novel Site",
    lang = "en",
    baseUrl = "https://example.com",
    
    -- Text-specific, not image URLs
    supportsText = true,
}

-- Required: search for novels
function SOURCE:search(query, page)
    return {
        {
            title = "Novel Title",
            url = "https://example.com/novel/123",
            coverUrl = "optional.jpg", -- or nil
            author = "Author Name",
            description = "Blurb...",
        }
    }
end

-- Required: get chapter list
function SOURCE:getChapterList(novelUrl)
    return {
        {
            name = "Chapter 1: The Beginning",
            url = "https://example.com/novel/123/chapter-1",
            dateUpload = 1700000000000, -- timestamp
            chapterNumber = 1,
        }
    }
end

-- REQUIRED NEW: get text content instead of image list
function SOURCE:getTextContent(chapterUrl)
    return {
        title = "Chapter 1: The Beginning",
        -- Plain text, HTML, or structured format?
        content = [[
            <p>First paragraph with <em>emphasis</em>.</p>
            <p>Second paragraph.</p>
        ]],
        -- OR plain text with your own markup
        -- content = "First paragraph.\n\nSecond paragraph.",
        
        -- Optional: pre-split pages for pagination
        pages = nil, -- let renderer handle it, or provide explicit breaks
    }
end
""".trimIndent()

class LocalExtensionEditorScreen(private val filePath: String?) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        
        var name by remember { mutableStateOf(if (filePath != null) File(filePath).nameWithoutExtension else "NewExtension") }
        var code by remember { mutableStateOf(if (filePath != null) File(filePath).readText() else STUB_LUA_CODE) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Local Extension") },
                    actions = {
                        IconButton(onClick = { navigator.push(LocalExtensionDocScreen()) }) {
                            Icon(Icons.Outlined.HelpOutline, contentDescription = "Documentation")
                        }
                        IconButton(onClick = {
                            val scriptsDir = File(context.filesDir, "lua_extensions").apply { mkdirs() }
                            val targetFile = if (filePath != null) File(filePath) else File(scriptsDir, "${name.replace(" ", "_")}.lua")
                            targetFile.writeText(code)
                            navigator.pop()
                        }) {
                            Icon(Icons.Outlined.Save, contentDescription = "Save")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Extension Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}
