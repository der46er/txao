package com.txao.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
            Row(
                Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(start = 3.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(formatDuration(item.durationMs), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp))
            }
        }
        if (favorite) {
            Icon(
                Icons.Default.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(14.dp),
            )
        }
        if (selected == true) {
            Box(Modifier.fillMaxSize().background(Color(0x55CDB8FF)))
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
