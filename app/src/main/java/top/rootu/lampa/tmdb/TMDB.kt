package top.rootu.lampa.tmdb

import android.net.Uri
import androidx.core.net.toUri
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Helpers.debugLog
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.Prefs.tmdbApiUrl
import top.rootu.lampa.helpers.Prefs.tmdbImgUrl
import top.rootu.lampa.helpers.capitalizeFirstLetter
import top.rootu.lampa.tmdb.models.entity.Entities
import top.rootu.lampa.tmdb.models.entity.Entity
import top.rootu.lampa.tmdb.models.entity.Genre
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

object TMDB {
    const val APIURL = "https://api.themoviedb.org/3/"
    const val IMGURL = "https://image.tmdb.org/"
    const val APIKEY = "4ef0d7355d9ffb5151e987764708ce96"

    private var movieGenres: List<Genre?> = emptyList()
    private var tvGenres: List<Genre?> = emptyList()

    private val _genres by lazy {
        val ret = hashMapOf<Int, String>()
        populateGenres(movieGenres, ret)
        populateGenres(tvGenres, ret)
        ret
    }

    private val httpClient: OkHttpClient by lazy { startWithQuad9DNS() }

    /* return lowercase 2-digit lang tag */
    fun getLang(): String {
        val appLangCode = App.context.appLang
        val locale = if (appLangCode.isNotEmpty()) {
            Locale.forLanguageTag(appLangCode.replace("_", "-"))
        } else {
            Locale.getDefault()
        }

        val lang = locale.language.lowercase(Locale.ROOT)
        return when (lang) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            "lv" -> "en" // FIXME: Empty Genre Names on LV, so force EN for TMDB requests
            else -> lang
        }
    }

    fun initGenres() {
        try {
            movieGenres = fetchGenres("genre/movie/list") ?: emptyList()
            tvGenres = fetchGenres("genre/tv/list") ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchGenres(endpoint: String): List<Genre>? {
        return video(endpoint)?.genres
    }

    private fun populateGenres(genreList: List<Genre?>, ret: HashMap<Int, String>) {
        for (genre in genreList) {
            genre?.let {
                if (!genre.name.isNullOrEmpty()) {
                    ret[genre.id] = genre.name.capitalizeFirstLetter()
                }
            }
        }
    }

    val genres: Map<Int, String> get() = _genres

    fun startWithQuad9DNS(): OkHttpClient {
        val bootstrapClient = OkHttpClient.Builder().build()
        val dnsUrl = "https://dns.quad9.net/dns-query".toHttpUrlOrNull()

        val dns = dnsUrl?.let {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(it)
                .bootstrapDnsHosts(
                    InetAddress.getByName("9.9.9.9"),
                    InetAddress.getByName("149.112.112.112"),
                    Inet6Address.getByName("2620:fe::fe")
                )
                .build()
        } ?: Dns.SYSTEM

        return bootstrapClient.newBuilder()
            .connectTimeout(15000L, TimeUnit.MILLISECONDS)
            .dns(dns)
            .build()
    }

    private fun buildTmdbUriBuilder(endpoint: String): Uri.Builder {
        val apiUrl = App.context.tmdbApiUrl
        val apiUri = apiUrl.toUri()
        val authority = "${apiUri.host}${if (apiUri.port != -1) ":${apiUri.port}" else ""}"
        val basePath = apiUri.path?.removeSuffix("/") ?: "3"

        val builder = Uri.Builder()
            .scheme(apiUri.scheme)
            .encodedAuthority(authority)
            .path("$basePath/$endpoint")

        if (apiUrl != APIURL) {
            apiUri.queryParameterNames.forEach { name ->
                apiUri.getQueryParameter(name)?.let { value ->
                    builder.appendQueryParameter(name, value)
                }
            }
        }
        return builder
    }

    fun videos(endpoint: String, params: MutableMap<String, String>): Entities? {
        val urlBuilder = buildTmdbUriBuilder(endpoint)
        params["api_key"] = APIKEY
        params["language"] = getLang()
        params.forEach { (k, v) -> urlBuilder.appendQueryParameter(k, v) }

        val link = urlBuilder.build().toString()
        debugLog("TMDB videos($endpoint) link[$link]")

        return try {
            val request = Request.Builder().url(link).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val body = response.body?.string()
                if (body.isNullOrEmpty()) return null
                val entities = getJson(body, Entities::class.java)
                val ret = mutableListOf<Entity>()
                entities?.results?.forEach {
                    if (it.media_type == null) fixEntity(it)
                    if (it.media_type == "movie" || it.media_type == "tv") {
                        video("${it.media_type}/${it.id}")?.let { ent ->
                            fixEntity(ent)
                            ret.add(ent)
                        }
                    }
                }
                entities?.apply { results = ret }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun video(endpoint: String): Entity? {
        return videoDetail(endpoint, getLang())
    }

    private fun videoDetail(endpoint: String, lang: String = ""): Entity? {
        val urlBuilder = buildTmdbUriBuilder(endpoint)
        val params = mutableMapOf<String, String>().apply {
            put("api_key", APIKEY)
            put("language", lang.ifBlank { getLang() })
            put("append_to_response", "videos,images,alternative_titles")
            put("include_image_language", "${getLang()},ru,en,null")
        }
        params.forEach { (k, v) -> urlBuilder.appendQueryParameter(k, v) }

        val link = urlBuilder.build().toString()
        return try {
            val request = Request.Builder().url(link).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val body = response.body?.string()
                if (body.isNullOrEmpty()) return null
                getJson(body, Entity::class.java)?.also { fixEntity(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fixEntity(ent: Entity) {
        if (ent.title == null && ent.name == null) return

        if (ent.media_type.isNullOrEmpty()) {
            ent.media_type = if (ent.title.isNullOrEmpty()) "tv" else "movie"
        }

        if (ent.title.isNullOrEmpty()) ent.title = ent.name
        if (ent.original_title.isNullOrEmpty()) ent.original_title = ent.original_name

        val date = ent.release_date?.takeIf { it.isNotEmpty() } ?: ent.first_air_date
        date?.takeIf { it.length >= 4 }?.let { ent.year = it.substring(0, 4) }
        if (ent.release_date.isNullOrEmpty()) ent.release_date = ent.first_air_date

        ent.poster_path = imageUrl(ent.poster_path).replace("original", "w342")
        ent.backdrop_path = imageUrl(ent.backdrop_path).replace("original", "w1280")

        ent.images?.let { img ->
            img.backdrops.forEach { it.file_path = imageUrl(it.file_path).replace("original", "w1280") }
            img.posters.forEach { it.file_path = imageUrl(it.file_path).replace("original", "w342") }
        }
        ent.production_companies?.forEach { it.logo_path = imageUrl(it.logo_path).replace("original", "w185") }
        ent.seasons?.forEach { it.poster_path = imageUrl(it.poster_path).replace("original", "w342") }
    }

    fun imageUrl(path: String?): String {
        if (path.isNullOrEmpty()) return ""
        if (path.startsWith("http")) return path

        val imgUrl = App.context.tmdbImgUrl
        val imgUri = imgUrl.toUri()
        val authority = "${imgUri.host}${if (imgUri.port != -1) ":${imgUri.port}" else ""}"
        val basePath = imgUri.path?.removeSuffix("/") ?: ""

        val builder = Uri.Builder()
            .scheme(imgUri.scheme)
            .encodedAuthority(authority)
            .path("$basePath/t/p/original$path")

        if (imgUrl != IMGURL) {
            imgUri.queryParameterNames.forEach { name ->
                imgUri.getQueryParameter(name)?.let { value ->
                    builder.appendQueryParameter(name, value)
                }
            }
        }
        return builder.build().toString()
    }
}
