package com.txao.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SongRow(track: Track, playing: Boolean, onClick: () -> Unit, onPlayNext: () -> Unit, onEdit: () -> Unit, onArtist: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tint = if (playing) c.primary.copy(alpha = 0.16f) else null) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.uri, 52.dp, Modifier.size(52.dp), RoundedCornerShape(14.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                    color = if (playing) c.primary else c.onSurface,
                )
                Row {
                    // Tapping the artist opens their profile straight away.
                    Text(
                        track.artist, Modifier.weight(1f, fill = false).clickable(onClick = onArtist), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                    )
                    Text(" · ${Library.formatDuration(track.durationMs)}", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
            Box {
                GlassIconButton(Icons.Filled.MoreVert, "More", size = 44.dp) { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.PlaylistAdd, null) },
                        onClick = { menu = false; onPlayNext() })
                    DropdownMenuItem(text = { Text("Go to artist") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Person, null) },
                        onClick = { menu = false; onArtist() })
                    DropdownMenuItem(text = { Text("Edit info") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onEdit() })
                }
            }
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp)) {
            Artwork(album.tracks.first().uri, 180.dp, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(18.dp))
            Text(album.name, Modifier.padding(start = 4.dp, top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                "${album.artist} · ${album.tracks.size}", Modifier.padding(start = 4.dp, bottom = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val albums = artist.tracks.map { it.albumId }.distinct().size
    GlassCard(Modifier.fillMaxWidth(), radius = 24.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(artist.tracks.first().uri, 56.dp, Modifier.size(56.dp), CircleShape)
            Column(Modifier.padding(start = 14.dp)) {
                Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    "${artist.tracks.size} songs · $albums albums", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
        }
    }
}
