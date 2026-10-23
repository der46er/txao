package com.txao.gallery

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Albums : Route
    data class Grid(val title: String, val source: Source) : Route
    data class Viewer(val items: List<MediaItem>, val index: Int, val standalone: Boolean = false) : Route
}

sealed interface Source {
    data object All : Source
    data class OfAlbum(val albumId: Long) : Source
    data object Favorites : Source
    data object Trash : Source
    data object Search : Source
}

class MainActivity : ComponentActivity() {
    private var externalUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        externalUri = viewUri(intent)
        setContent { TxaoTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        externalUri = viewUri(intent)
    }

    private fun viewUri(i: Intent?): Uri? = if (i?.action == Intent.ACTION_VIEW) i.data else null

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun hasAccess() = neededPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    @Composable
    private fun Root() {
        val prefs = remember { Prefs(this) }
        val repo = remember { MediaRepo(this) }
        val scope = rememberCoroutineScope()

        var granted by remember { mutableStateOf(hasAccess()) }
        val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = hasAccess()
        }

        var all by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
        var trashed by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
        var favorites by remember { mutableStateOf(prefs.favorites) }
        var hidden by remember { mutableStateOf(prefs.hiddenAlbums) }
        var sort by remember { mutableStateOf(prefs.sort) }
        var columns by remember { mutableStateOf(prefs.columns) }
        var loaded by remember { mutableStateOf(false) }
        val stack = remember { mutableStateOf<List<Route>>(listOf(Route.Albums)) }

        fun refresh() {
            scope.launch {
                val (a, t) = withContext(Dispatchers.IO) { repo.load() to repo.load(trashed = true) }
                all = a; trashed = t; loaded = true
            }
        }
        LaunchedEffect(granted) { if (granted) refresh() }

        // Pending confirmation flow (system delete / trash dialogs).
        var onConfirmed by remember { mutableStateOf<(() -> Unit)?>(null) }
        val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) onConfirmed?.invoke()
            onConfirmed = null
            refresh()
        }
        fun launch(sender: IntentSender, then: (() -> Unit)? = null) {
            onConfirmed = then
            confirm.launch(IntentSenderRequest.Builder(sender).build())
        }

        val actions = remember(repo) {
            object : GalleryActions {
                override fun trash(items: List<MediaItem>, trash: Boolean, after: () -> Unit) {
                    val uris = items.map { it.uri }
                    if (Build.VERSION.SDK_INT >= 30) {
                        launch(repo.trashRequest(uris, trash), after)
                    } else if (trash) {
                        deleteLegacy(items, after)
                    }
                }

                override fun deleteForever(items: List<MediaItem>, after: () -> Unit) {
                    if (Build.VERSION.SDK_INT >= 30) launch(repo.deleteRequest(items.map { it.uri }), after)
                    else deleteLegacy(items, after)
                }

                fun deleteLegacy(items: List<MediaItem>, after: () -> Unit) {
                    val uris = items.map { it.uri }
                    scope.launch {
                        val sender = withContext(Dispatchers.IO) { runCatching { repo.legacyDelete(uris) }.getOrNull() }
                        if (sender != null) launch(sender) { scope.launch { withContext(Dispatchers.IO) { repo.legacyDelete(uris) }; after() } }
                        else { refresh(); after() }
                    }
                }
            }
        }

        val external = externalUri
        if (external != null) {
            val item = remember(external) { repo.fromUri(external, intent?.type) }
            BackHandler { externalUri = null; if (!isTaskRoot) finish() }
            ViewerScreen(
                items = listOf(item), startIndex = 0, favorites = favorites,
                onFavorite = {}, actions = actions, onClose = { externalUri = null; if (!granted) finish() },
                canDelete = false,
            )
            return
        }

        if (!granted) {
            PermissionGate { askPermission.launch(neededPermissions()) }
            return
        }

        val route = stack.value.last()
        fun push(r: Route) { stack.value = stack.value + r }
        fun pop() { if (stack.value.size > 1) stack.value = stack.value.dropLast(1) }
        BackHandler(enabled = stack.value.size > 1) { pop() }

        fun toggleFavorite(id: Long) {
            favorites = if (id in favorites) favorites - id else favorites + id
            prefs.favorites = favorites
        }

        val visible = remember(all, hidden) { all.filter { it.albumId !in hidden } }

        when (route) {
            Route.Albums -> AlbumsScreen(
                albums = remember(all, hidden, sort) { groupAlbums(all, hidden, sort) },
                hiddenAlbums = remember(all, hidden) { groupAlbums(all, emptySet(), sort).filter { it.id in hidden } },
                loaded = loaded,
                sort = sort, columns = columns,
                onSort = { sort = it; prefs.sort = it },
                onColumns = { columns = it; prefs.columns = it },
                onAlbum = { push(Route.Grid(it.name, Source.OfAlbum(it.id))) },
                onAll = { push(Route.Grid("All media", Source.All)) },
                onFavorites = { push(Route.Grid("Favorites", Source.Favorites)) },
                onTrash = { push(Route.Grid("Trash", Source.Trash)) },
                onSearch = { push(Route.Grid("Search", Source.Search)) },
                onHide = { hidden = hidden + it.id; prefs.hiddenAlbums = hidden },
                onUnhide = { hidden = hidden - it.id; prefs.hiddenAlbums = hidden },
                trashSupported = Build.VERSION.SDK_INT >= 30,
            )

            is Route.Grid -> {
                val base = when (route.source) {
                    Source.All, Source.Search -> visible
                    is Source.OfAlbum -> all.filter { it.albumId == route.source.albumId }
                    Source.Favorites -> visible.filter { it.id in favorites }
                    Source.Trash -> trashed
                }
                GridScreen(
                    title = route.title, source = route.source, base = base, sort = sort, columns = columns,
                    favorites = favorites,
                    onBack = ::pop,
                    onOpen = { list, i -> push(Route.Viewer(list, i)) },
                    onColumns = { columns = it; prefs.columns = it },
                    onSort = { sort = it; prefs.sort = it },
                    actions = actions,
                )
            }

            is Route.Viewer -> ViewerScreen(
                items = route.items, startIndex = route.index, favorites = favorites,
                onFavorite = ::toggleFavorite, actions = actions, onClose = ::pop,
                canDelete = true,
            )
        }
    }
}

interface GalleryActions {
    fun trash(items: List<MediaItem>, trash: Boolean, after: () -> Unit = {})
    fun deleteForever(items: List<MediaItem>, after: () -> Unit = {})
}

@Composable
private fun PermissionGate(onAsk: () -> Unit) {
    Box(Modifier.fillMaxSize().background(GlassBg), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TXAO GALLERY", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
            Spacer(Modifier.height(12.dp))
            Text(
                "Allow access to your photos and videos so the gallery can show them. Nothing leaves your phone.",
                color = Color(0xB3FFFFFF),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAsk,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) { Text("Allow access", fontWeight = FontWeight.Bold) }
        }
    }
}
