package com.txao.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One square thumbnail with video badge, favourite heart and selection check. */
@Composable
fun Thumb(
    item: MediaItem,
    px: Int,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    selected: Boolean? = null,
) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, item.uri, px) {
        value = withContext(Dispatchers.IO) { Thumbs.thumbnail(ctx, item, px) }
    }
    Box(modifier.background(Color(0xFF1C1C1C))) {
        bmp?.let {
            Image(it, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (item.isVideo) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(formatDuration(item.durationMs), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (favorite) {
            Icon(
                Icons.Default.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(14.dp),
            )
        }
        if (selected == true) {
            Box(Modifier.fillMaxSize().background(Color(0x66FFFFFF)))
        }
        if (selected != null) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp).clip(CircleShape)
                    .background(if (selected) Color.White else Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Pick one of the user's albums, or type a name to make a new one. */
@Composable
fun AlbumPickerDialog(names: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var fresh by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to album") },
        text = {
            Column {
                names.forEach { n ->
                    Text(
                        n, Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 10.dp),
                        fontSize = 15.sp,
                    )
                }
                OutlinedTextField(
                    value = fresh, onValueChange = { fresh = it }, singleLine = true,
                    label = { Text("New album") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (fresh.isNotBlank()) onPick(fresh.trim()) }, enabled = fresh.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
