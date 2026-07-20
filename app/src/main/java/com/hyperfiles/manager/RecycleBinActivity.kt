package com.hyperfiles.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperfiles.manager.ui.FilesDevTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecycleBinActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        setContent { FilesDevTheme { RecycleBinScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RecycleBinScreen() {
        val ctx = this
        val scope = rememberCoroutineScope()
        var reload by remember { mutableIntStateOf(0) }
        var showEmptyConfirm by remember { mutableStateOf(false) }

        val entries by produceState(initialValue = emptyList<TrashBin.Entry>(), reload) {
            value = withContext(Dispatchers.IO) { TrashBin.list(ctx) }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Column {
                            Text("Recycle bin")
                            Text(
                                "${entries.size} items",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (entries.isNotEmpty()) {
                            IconButton(onClick = { showEmptyConfirm = true }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Empty bin")
                            }
                        }
                    }
                )
            }
        ) { pad ->
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.padding(pad).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Recycle bin is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(pad).fillMaxSize()) {
                    items(entries, key = { it.file.absolutePath }) { entry ->
                        TrashRow(
                            entry = entry,
                            onRestore = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { TrashBin.restore(ctx, entry) }
                                    Toast.makeText(ctx, if (ok) "Restored" else "Restore failed", Toast.LENGTH_SHORT).show()
                                    reload++
                                }
                            },
                            onOpen = { OpenHelper.open(ctx, entry.file) },
                            onDeleteForever = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { TrashBin.deleteForever(ctx, entry) }
                                    reload++
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showEmptyConfirm) {
            AlertDialog(
                onDismissRequest = { showEmptyConfirm = false },
                title = { Text("Empty recycle bin?") },
                text = { Text("All items will be permanently deleted.") },
                confirmButton = {
                    TextButton(onClick = {
                        showEmptyConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) { TrashBin.empty(ctx) }
                            reload++
                        }
                    }) { Text("Empty") }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }

    @Composable
    private fun TrashRow(
        entry: TrashBin.Entry,
        onRestore: () -> Unit,
        onOpen: () -> Unit,
        onDeleteForever: () -> Unit
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuOpen = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(FileTypes.iconFor(entry.file)),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.originalName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        entry.originalParent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Restore") }, onClick = { menuOpen = false; onRestore() })
                DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen() })
                DropdownMenuItem(text = { Text("Delete forever") }, onClick = { menuOpen = false; onDeleteForever() })
            }
        }
    }
}
