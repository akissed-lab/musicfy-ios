// OnlineSearchScreen.kt
//
// WP5d TODO stub. The upstream online suggestion screen depends on
// YouTube.searchSuggestions / YouTubeUrlParser, Android keyboard and menu
// locals, and Hilt. The KMP port only has the offline-backed
// OnlineSearchSuggestionViewModel, so this file keeps the public composable
// name/signature shape and renders a self-contained glass placeholder.
//
// TODO(search-online): replace the placeholder with the real suggestion list
// once the KMP innertube suggestion/link parser endpoints are ported.

package com.example.musicfy.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.viewmodels.OnlineSearchSuggestionViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.viewModelFactory

/** Entry point used by callers that do not own a view model instance. */
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above OnlineSearchScreen"
    }
    val viewModel = appViewModel<OnlineSearchSuggestionViewModel>(
        factory = viewModelFactory { OnlineSearchSuggestionViewModel(container) },
    )
    OnlineSearchScreen(
        query = query,
        onQueryChange = onQueryChange,
        navController = navController,
        onSearch = onSearch,
        onDismiss = onDismiss,
        pureBlack = pureBlack,
        viewModel = viewModel,
    )
}

/** Injectable overload kept for parity with upstream's Hilt-provided parameter. */
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel,
) {
    LaunchedEffect(query) {
        viewModel.query.value = query
        // TODO(search-online): collect viewModel.viewState.suggestions once the
        // innertube endpoint lands; today it is intentionally empty.
    }

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
                text = "Online search",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (query.isBlank()) {
                    "Type a query to search local results and history."
                } else {
                    "Online suggestions for “$query” are not ported to KMP yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.MutedContent,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Search anyway",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .glassSurface(
                        shape = RoundedCornerShape(16.dp),
                        style = GlassStyle(elevation = 0.dp),
                    )
                    .clickable { onSearch(query) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
