package com.xxxx.emby_tv.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.xxxx.emby_tv.BuildConfig
import com.xxxx.emby_tv.data.repository.EmbyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class Credit(val title: String, val year: Int?, val character: String, val poster: String?, val type: String, val localId: String?)
private data class ActorData(val name: String, val bio: String, val birthday: String, val birthplace: String, val portrait: String?, val credits: List<Credit>)

private fun getJson(path: String): JSONObject {
    val connection = URL("https://api.themoviedb.org/3/$path").openConnection() as HttpURLConnection
    connection.connectTimeout = 10000
    connection.readTimeout = 15000
    connection.setRequestProperty("Accept", "application/json")
    try {
        if (connection.responseCode !in 200..299) error("TMDB HTTP ${connection.responseCode}")
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally { connection.disconnect() }
}

private fun tmdb(path: String): JSONObject = getJson("$path${if ('?' in path) '&' else '?'}api_key=${URLEncoder.encode(BuildConfig.TMDB_API_KEY, "UTF-8")}")
private fun JSONObject.str(key: String) = optString(key).takeUnless { it.isNullOrBlank() || it == "null" } ?: ""

@Composable
fun ActorProfileScreen(name: String, onOpenItem: (String, Boolean) -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { EmbyRepository.getInstance(context) }
    var actor by remember(name) { mutableStateOf<ActorData?>(null) }
    var error by remember(name) { mutableStateOf<String?>(null) }
    LaunchedEffect(name) {
        try {
            if (BuildConfig.TMDB_API_KEY.isBlank()) error("TMDB_API_KEY is missing from the signed build")
            actor = withContext(Dispatchers.IO) {
                val search = tmdb("search/person?query=${URLEncoder.encode(name, "UTF-8")}").getJSONArray("results")
                if (search.length() == 0) error("No TMDB profile found")
                val candidate = (0 until search.length()).map { search.getJSONObject(it) }
                    .firstOrNull { it.str("name").equals(name, true) } ?: search.getJSONObject(0)
                val id = candidate.getInt("id")
                val detail = tmdb("person/$id")
                val creditsJson = tmdb("person/$id/combined_credits").getJSONArray("cast")
                val localItems = mutableListOf<com.xxxx.emby_tv.data.model.BaseItemDto>()
                for (kind in listOf("Movie", "Series")) {
                    var offset = 0
                    while (true) {
                        val (page, total) = repository.getLibraryList("", kind, offset, 500)
                        localItems.addAll(page)
                        offset += page.size
                        if (page.isEmpty() || offset >= total) break
                    }
                }
                val libraryIndex = localItems.groupBy { it.type to it.name?.trim()?.lowercase() }
                val credits = (0 until creditsJson.length()).mapNotNull { index ->
                    val item = creditsJson.getJSONObject(index)
                    val type = item.str("media_type")
                    if (type != "movie" && type != "tv") return@mapNotNull null
                    val title = item.str(if (type == "movie") "title" else "name")
                    if (title.isBlank()) return@mapNotNull null
                    val date = item.str(if (type == "movie") "release_date" else "first_air_date")
                    val year = date.take(4).toIntOrNull()
                    val local = libraryIndex[(if (type == "movie") "Movie" else "Series") to title.trim().lowercase()]
                        ?.firstOrNull { year == null || it.productionYear == year }
                    Credit(title, year, item.str("character"), item.str("poster_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w342$it" }, type, local?.id)
                }.distinctBy { Triple(it.type, it.title, it.year) }.sortedWith(compareByDescending<Credit> { it.localId != null }.thenByDescending { it.year ?: 0 })
                ActorData(detail.str("name"), detail.str("biography"), detail.str("birthday"), detail.str("place_of_birth"), detail.str("profile_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" }, credits)
            }
        } catch (e: Exception) { error = e.message ?: "Unable to load actor profile" }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF101820)).padding(28.dp)) {
        Surface(onClick = onBack) { Text("‹ Back", modifier = Modifier.padding(10.dp)) }
        Spacer(Modifier.height(16.dp))
        if (error != null) Text(error!!, color = Color.White)
        else if (actor == null) CircularProgressIndicator()
        else {
            val info = actor!!
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.height(230.dp)) {
                AsyncImage(model = info.portrait, contentDescription = info.name, modifier = Modifier.width(155.dp).fillMaxHeight(), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(info.name, color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    Text("Born: ${info.birthday}  •  ${info.birthplace}", color = Color.LightGray)
                    Spacer(Modifier.height(10.dp))
                    Text(info.bio.ifBlank { "Biography unavailable" }, color = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Filmography  •  In your library highlighted", color = Color.White, style = MaterialTheme.typography.titleLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(info.credits) { credit ->
                    Surface(onClick = { credit.localId?.let { onOpenItem(it, credit.type == "tv") } },
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (credit.localId != null) Color(0xFF284C60) else Color(0xFF252A30)),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = credit.poster, contentDescription = null, modifier = Modifier.size(45.dp, 65.dp), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("${credit.title} ${credit.year?.let { "($it)" } ?: ""}${if (credit.localId != null) "  ✓ IN LIBRARY" else ""}", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(credit.character, color = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
