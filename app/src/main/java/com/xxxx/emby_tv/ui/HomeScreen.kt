package com.xxxx.emby_tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.*
import com.xxxx.emby_tv.data.model.BaseItemDto
import androidx.compose.ui.res.stringResource
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.ui.components.BuildItem
import com.xxxx.emby_tv.ui.components.JembyLibrarySplash
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.components.MenuDialog
import com.xxxx.emby_tv.ui.components.NoData
import com.xxxx.emby_tv.ui.components.TopStatusBar
import com.xxxx.emby_tv.ui.viewmodel.HomeViewModel
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import androidx.compose.foundation.lazy.items

private var jembySplashShownInProcess = false


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    mainViewModel: MainViewModel,
    navController: NavController,
    onSwitchAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showHomeSettings by remember { mutableStateOf(false) }
    val homePrefs = remember(context) { HomePreferences(context) }
    var selectedItem by remember { mutableStateOf<BaseItemDto?>(null) }

    // 获取 serverUrl
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""

    // 从 HomeViewModel 获取数据
    val resumeItems = homeViewModel.resumeItems
    val libraryLatestItems = homeViewModel.libraryLatestItems
    val favoriteItems = homeViewModel.favoriteItems
    val isLoading = homeViewModel.isLoading
    val errorMessage = homeViewModel.errorMessage

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
            homeViewModel.clearError()
        }
    }

    // 检查更新
    LaunchedEffect(Unit) {
        mainViewModel.checkUpdate()
    }

    // 菜单对话框
    if (showMenu) {
        MenuDialog(
            needUpdate = mainViewModel.needUpdate,
            onDismiss = { showMenu = false },
            onLogout = {
                mainViewModel.logout()
                showMenu = false
            },
            onUpdate = {
                mainViewModel.checkUpdate()
                showMenu = false
                navController.navigate("update")
            },
            onThemeChange = { themeColor ->
                mainViewModel.saveThemeId(themeColor.id)
            },
            onSwitchAccount = {
                showMenu = false
                onSwitchAccount()
            },
            onSearch = {
                showMenu = false
                navController.navigate("search")
            },
            onPlaybackSettings = {
                showMenu = false
                navController.navigate("playback_settings")
            },
            onHomeSettings = { showMenu = false; showHomeSettings = true },
            onProxySettings = {
                showMenu = false
                navController.navigate("proxy_settings")
            }
        )
    }

    if (showHomeSettings) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHomeSettings = false },
            title = { androidx.compose.material3.Text("J'Emby Home Settings") },
            text = {
                Column {
                    HomeSettingToggle("Hide watched titles", homePrefs.hideWatched, homePrefs::updateHideWatched)
                    HomeSettingToggle("Show Continue Watching", homePrefs.showContinue, homePrefs::updateShowContinue)
                    HomeSettingToggle("Show Favorites", homePrefs.showFavorites, homePrefs::updateShowFavorites)
                    HomeSettingToggle("Show libraries", homePrefs.showLibraries, homePrefs::updateShowLibraries)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Text("Poster size")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("small", "medium", "large").forEach { size ->
                            androidx.compose.material3.Button(onClick = { homePrefs.updatePosterSize(size) }) {
                                androidx.compose.material3.Text(if (homePrefs.posterSize == size) "Selected: " + size else size)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showHomeSettings = false }) {
                    androidx.compose.material3.Text("Done")
                }
            }
        )
    }

    fun goPlay(item: BaseItemDto) {
        val id = item.id ?: ""
        val userData = item.userData
        val position = userData?.playbackPositionTicks ?: 0L
        navController.navigate("player/$id?position=$position")
    }

    if (selectedItem != null) {
        val item = selectedItem!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { androidx.compose.material3.Text(item.name ?: "Item actions") },
            text = {
                Column {
                    androidx.compose.material3.TextButton(onClick = {
                        item.id?.let { homeViewModel.updatePlayed(it, true) }
                        selectedItem = null
                    }) { androidx.compose.material3.Text("Mark as played") }
                    androidx.compose.material3.TextButton(onClick = {
                        item.id?.let { homeViewModel.updatePlayed(it, false) }
                        selectedItem = null
                    }) { androidx.compose.material3.Text("Mark as unplayed") }
                    androidx.compose.material3.TextButton(onClick = {
                        item.id?.let { homeViewModel.updatePlayed(it, true) }
                        selectedItem = null
                    }) { androidx.compose.material3.Text("Remove from Continue Watching (mark played)") }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { selectedItem = null }) {
                    androidx.compose.material3.Text("Close")
                }
            }
        )
    }

    // Calculate User Info
    val currentAccountId = repository.currentAccountId
    val currentAccount = repository.savedAccounts.find { it.id == currentAccountId }
    val userInfo = if (currentAccount != null) {
        val domain = try {
            val uri = java.net.URI(currentAccount.serverUrl)
            uri.host ?: currentAccount.serverUrl
        } catch (e: Exception) {
            currentAccount.serverUrl
        }
        "${currentAccount.username}@$domain"
    } else {
        null
    }

    var showJembySplash by remember { mutableStateOf(!jembySplashShownInProcess) }
    var splashArtwork by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    LaunchedEffect(libraryLatestItems) {
        if (showJembySplash && libraryLatestItems != null) {
            // Wait for library artwork rather than flashing whichever API responds first.
            val candidates = (libraryLatestItems.flatMap { it.latestItems.orEmpty() }
                + resumeItems.orEmpty()).filter { !it.id.isNullOrBlank() }
                .distinctBy { it.id }
            // Rotate between launches; store the previous collage IDs locally.
            val splashPrefs = context.getSharedPreferences("jemby_splash", android.content.Context.MODE_PRIVATE)
            val accountKey = repository.currentAccountId ?: "default"
            val previous = splashPrefs.getString("previous_$accountKey", "")
                .orEmpty().split(",").filter { it.isNotBlank() }.toSet()
            val fresh = candidates.filter { it.id !in previous }.shuffled()
            val remaining = candidates.filter { it.id in previous }.shuffled()
            splashArtwork = (fresh + remaining).take(18)
            splashPrefs.edit().putString(
                "previous_$accountKey", splashArtwork.mapNotNull { it.id }.joinToString(",")
            ).apply()
            delay(4000)
            showJembySplash = false
            jembySplashShownInProcess = true
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xE90B1929))) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // 顶部状态栏
        TopStatusBar(
            currentVersion = mainViewModel.currentVersion,
            newVersion = mainViewModel.newVersion,
            needUpdate = mainViewModel.needUpdate,
            showSearchButton = true,
            userInfo = userInfo,
            onMenuClick = { showMenu = true },
            onSearchClick = {
                navController.navigate("search")
            },
            onUserInfoClick = {
                navController.navigate("account")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 数据未加载完成时显示 Loading 组件
//        if(isLoading){
//            Loading()
//        }
        if (libraryLatestItems != null || resumeItems != null || favoriteItems != null) {
            LazyColumn(
                modifier = Modifier.weight(1f)
                    .background(Color(0x7518293C), RoundedCornerShape(6.dp)),
                contentPadding = PaddingValues(bottom = 40.dp, top = 12.dp)
            ) {
                // Show libraries only when loaded; don't hold the entire
                // homepage behind the slowest Emby request.
                if (homePrefs.showLibraries && libraryLatestItems != null) {
                    item {
                        MediaSection(
                        title = stringResource(R.string.my_libraries),
                        items = libraryLatestItems,
                        isMyLibrary = true,
                        serverUrl = serverUrl,
                        onItemSelected = { item ->
                            val firstItem = item.latestItems?.firstOrNull()
                            val type = firstItem?.type ?: ""
                            val id = item.id ?: ""
                            val title = item.name ?: ""
                            navController.navigate("library/$id?libraryName=$title&type=$type")
                        },
                        onMenuPressed = { selectedItem = it },
                        posterSize = homePrefs.posterSize
                        )
                    }
                }

                // 继续观看
                if (homePrefs.showContinue && resumeItems != null && resumeItems.isNotEmpty()) {
                    item {
                        MediaSection(
                            title = stringResource(R.string.continue_watching),
                            items = resumeItems,
                            isShowImg17 = true,
                            isContinueWatching = true,
                            serverUrl = serverUrl,
                            onItemSelected = { item -> goPlay(item) },
                            onMenuPressed = { selectedItem = it },
                        posterSize = homePrefs.posterSize
                        )
                    }
                }

                // 收藏
                if (homePrefs.showFavorites && favoriteItems != null && favoriteItems.isNotEmpty()) {
                    item {
                        MediaSection(
                            title = stringResource(R.string.favorite),
                            items = if (homePrefs.hideWatched) favoriteItems.filter { it.userData?.played != true } else favoriteItems,
                            isShowImg17 = true,
                            serverUrl = serverUrl,
                            onItemSelected = { item -> goPlay(item) },
                            onMenuPressed = { selectedItem = it },
                        posterSize = homePrefs.posterSize
                        )
                    }
                }

                // 各库最新内容
                itemsIndexed(
                    if (homePrefs.showLibraries) libraryLatestItems ?: emptyList() else emptyList(),
                    key = { _, library -> library.id ?: library.hashCode() }
                ) { _, library ->
                    MediaSection(
                        title = library.name ?: "",
                        items = (library.latestItems ?: emptyList()).let { latest ->
                            if (homePrefs.hideWatched) latest.filter { it.userData?.played != true } else latest
                        },
                        serverUrl = serverUrl,
                        onItemSelected = { item ->
                            if (item.isSeries) {
                                val seriesId = item.id
                                if (!seriesId.isNullOrEmpty()) {
                                    homeViewModel.playNextUp(seriesId) { nextItem: BaseItemDto ->
                                        goPlay(nextItem)
                                    }
                                } else {
                                    goPlay(item)
                                }
                            } else {
                                goPlay(item)
                            }
                        },
                        onMenuPressed = { selectedItem = it },
                        posterSize = homePrefs.posterSize
                    )
                }
            }
        }
    }
    if (showJembySplash) {
        JembyLibrarySplash(serverUrl = serverUrl, artwork = splashArtwork)
    }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaSection(
    title: String,
    items: List<BaseItemDto>,
    isMyLibrary: Boolean = false,
    isShowImg17: Boolean = false,
    isContinueWatching: Boolean = false,
    serverUrl: String,
    onItemSelected: (BaseItemDto) -> Unit,
    onMenuPressed: (BaseItemDto) -> Unit,
    posterSize: String = "medium",
) {
    // A portrait-first, vertically navigable poster grid. Landscape Continue
    // Watching artwork stays 16:9, avoiding cropped episode thumbnails.
    val posterWidth = when (posterSize) {
        "small" -> if (isContinueWatching) 138.dp else 112.dp
        "large" -> if (isContinueWatching) 192.dp else 165.dp
        else -> if (isContinueWatching) 168.dp else 142.dp
    }
    val ratio = if (isContinueWatching) 16f / 9f else 2f / 3f
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, top = 18.dp, bottom = 12.dp)
        )
        if (items.isEmpty()) {
            NoData(modifier = Modifier.height(120.dp))
        } else {
            // Single, lazy horizontal strip per section; no truncation to ten.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items.size, key = { index -> items[index].id ?: index.toString() }) { index ->
                    val item = items[index]
                    BuildItem(
                        modifier = Modifier,
                        item = item,
                        aspectRatio = ratio,
                        imgWidth = posterWidth,
                        isShowImg17 = isContinueWatching,
                        isMyLibrary = isMyLibrary,
                        serverUrl = serverUrl,
                        onItemClick = { onItemSelected(item) },
                        onMenuClick = { onMenuPressed(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Text(label)
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}
