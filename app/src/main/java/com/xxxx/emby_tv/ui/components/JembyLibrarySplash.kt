package com.xxxx.emby_tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto

/**
 * Cinematic branding panel made from this server's real library art.
 * Posters are requested at small sizes and use the application's shared Coil cache.
 */
@Composable
fun JembyLibrarySplash(serverUrl: String, artwork: List<BaseItemDto>) {
    Box(Modifier.fillMaxSize().background(Color(0xFF07101C))) {
        if (artwork.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(artwork.take(18), key = { it.id ?: it.hashCode() }) { item ->
                    AsyncImage(
                        model = Utils.getImageUrl(serverUrl, item, false),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.aspectRatio(0.68f)
                    )
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.70f)))
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("J'Emby", fontSize = 68.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(
                "YOUR MEDIA. YOUR WAY.",
                fontSize = 17.sp,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF8BCBFF)
            )
        }
    }
}
