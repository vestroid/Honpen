package eu.kanade.tachiyomi.ui.browse.extension.lua

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import java.io.File

@Composable
fun Screen.localExtensionsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    
    // In a real implementation this would be backed by a ScreenModel and StateFlow
    val scriptsDir = remember { File(context.filesDir, "lua_extensions").apply { mkdirs() } }
    val scripts = remember { scriptsDir.listFiles()?.filter { it.extension == "lua" } ?: emptyList() }

    return TabContent(
        titleRes = MR.strings.label_local, // Or a custom string "Local"
        actions = persistentListOf(),
        content = { contentPadding, _ ->
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                if (scripts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No local extensions. Click + to create one.")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(scripts) { file ->
                            ListItem(
                                headlineContent = { Text(file.nameWithoutExtension) },
                                modifier = Modifier.clickable {
                                    navigator.push(LocalExtensionEditorScreen(file.absolutePath))
                                }
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        navigator.push(LocalExtensionEditorScreen(null))
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Extension")
                }
            }
        },
    )
}
