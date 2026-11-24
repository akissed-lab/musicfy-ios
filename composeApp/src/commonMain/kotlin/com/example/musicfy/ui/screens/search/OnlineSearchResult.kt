// OnlineSearchResult.kt
//
// WP5d TODO stub. Upstream's OnlineSearchViewModel (YouTube search pages,
// filters, radio queues and menu sheet) has no KMP equivalent yet, so this
// file keeps the public composable name with a self-contained glass placeholder.
//
// TODO(search-online): port the result grid/rows once OnlineSearchViewModel and
// the corresponding innertube search pages are available in commonMain.

package com.example.musicfy.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.glassSurface

/**
 * Simplified KMP signature: upstream also accepted a Hilt-created
 * `OnlineSearchViewModel`, which is not ported. The integrator can call this
 * with just the nav controller and `pureBlack` until that view model lands.
 */
@Composable
fun OnlineSearchResult(
    navController: NavController,
    pureBlack: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) GlassTokens.Base else Color.Transparent)
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(
                    shape = RoundedCornerShape(22.dp),
                    style = GlassStyle(elevation = 0.dp),
                )
                .padding(20.dp),
        ) {
            Text(
                text = "Online results",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "YouTube search results are not ported to KMP yet. Local library results are available from the search results screen.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.MutedContent,
            )
        }
    }
}
