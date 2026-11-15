package com.ta3.downloader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ta3.downloader.MainViewModel
import com.ta3.downloader.UiState
import kotlinx.coroutines.launch

/**
 * Full-screen JOJ show page: hero backdrop + synopsis up top, fading as the episode list
 * (which slides up over the image) is scrolled — same pattern as [MovieDetailScreen].
 */
@Composable
fun JojShowDetailScreen(state: UiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val show = state.jojSelectedShow ?: return
    BackHandler(onBack = { viewModel.backToJojShows() })

    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.background
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val listState = rememberLazyListState()
    val detail = state.jojShowDetail
    // Not falling back to show.posterUrl here on purpose: it's a differently-cropped portrait
    // poster, so swapping it for the real (landscape) backdrop once it loads looked like the
    // image was being resized. A plain placeholder until the real backdrop arrives reads better.
    val backdropUrl = detail?.backdropUrl
    val description = detail?.description?.takeIf { it.isNotBlank() } ?: ""

    Box(modifier.fillMaxSize().background(bg)) {
        val fadePx = with(LocalDensity.current) { (screenHeight * 0.5f).toPx() }
        val imageAlpha by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) 0f
                else (1f - listState.firstVisibleItemScrollOffset / fadePx).coerceIn(0f, 1f)
            }
        }
        // Keyed on the show so Coil's AsyncImagePainter (which otherwise keeps showing the
        // previous image while the next one loads) is fully reset when the show changes.
        key(show.id) {
            Box(Modifier.fillMaxWidth().height(screenHeight * 0.75f).graphicsLayer { alpha = imageAlpha }) {
                if (backdropUrl != null) {
                    AsyncImage(
                        model = backdropUrl, contentDescription = show.title,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to bg)))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item { Spacer(Modifier.height(screenHeight * 0.38f)) }

            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(show.title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground)
                    if (description.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(description, fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Episode list panel, slides up over the image
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(bg)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Box(
                        Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).width(40.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                    Text("Epizódy", fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                }
            }

            if (state.jojSeasons.size > 1) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().background(bg).padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.jojSeasons.indices.toList(), key = { it }) { index ->
                            FilterChip(
                                selected = state.jojSeasonIndex == index,
                                onClick = { viewModel.selectJojSeason(index) },
                                label = { Text(state.jojSeasons[index].label, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            when {
                state.jojEpisodeLoadError != null && state.jojEpisodes.isEmpty() -> item {
                    Column(
                        Modifier.fillMaxWidth().background(bg).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.jojEpisodeLoadError, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadMoreJojEpisodes() }) { Text("Opakovať") }
                    }
                }
                state.jojEpisodes.isEmpty() && state.jojLoadingEpisodes -> item {
                    Box(Modifier.fillMaxWidth().background(bg).padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.jojEpisodes.isEmpty() -> item {
                    Text("Žiadne epizódy", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().background(bg).padding(20.dp))
                }
                else -> {
                    items(state.jojEpisodes, key = { it.url }) { episode ->
                        Box(Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 5.dp)) {
                            EpisodeCard(
                                episode = episode,
                                isDownloaded = state.downloadedFiles.any { it.episodeUrl == episode.url },
                                activeDownload = state.activeDownloads[episode.url],
                                onDownload = { viewModel.startDownload(episode) },
                                onPlay = { viewModel.playEpisode(context, episode) },
                                onPlayLocal = { state.downloadedFiles.firstOrNull { it.episodeUrl == episode.url }?.let { openFileWithPlayer(context, it) } },
                                onCancelDownload = { viewModel.cancelDownload(episode.url) },
                                onShare = { viewModel.shareEpisode(context, episode) }
                            )
                        }
                    }
                    if (state.jojHasMoreEpisodes) {
                        item {
                            LaunchedEffect(state.jojEpisodes.size) { viewModel.loadMoreJojEpisodes() }
                            Box(Modifier.fillMaxWidth().background(bg).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                if (state.jojLoadingEpisodes) CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Keeps the status bar legible once the list scrolls underneath it
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
        if (scrolled) {
            Box(
                Modifier.fillMaxWidth().height(96.dp)
                    .background(Brush.verticalGradient(0f to bg, 0.6f to bg.copy(alpha = 0.85f), 1f to Color.Transparent))
            )
        }

        // Scroll-to-top, once a few episodes down — same FAB as the shows grid
        val far by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
        androidx.compose.animation.AnimatedVisibility(
            visible = far,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
        ) {
            val scope = rememberCoroutineScope()
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Hore")
            }
        }

        IconButton(
            onClick = { viewModel.backToJojShows() },
            modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x99000000))
        ) { Icon(Icons.Default.ArrowBack, "Späť", tint = Color.White) }
    }
}
