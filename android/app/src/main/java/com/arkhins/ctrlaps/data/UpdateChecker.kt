package com.arkhins.ctrlaps.data

import android.util.Log
import com.arkhins.ctrlaps.BuildConfig
import com.arkhins.ctrlaps.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The network could not be reached at all (no data, DNS failed, timed out). */
class NoConnectionException(cause: Throwable? = null) : IOException("No connection. Please try again.", cause)

/** The source answered, but not with a release. */
class HttpException(val code: Int, message: String) : IOException(message)

/** What an update check found. */
sealed interface Latest {
    /** A release exists (it may or may not be newer than this build). */
    data class Release(val info: AppVersionInfo) : Latest

    /** Every source answered, and none has published a release yet. */
    data object None : Latest
}

/**
 * Finds out what the latest release is.
 *
 * The CTR[L]APS server is asked first ([Config.UPDATE_URL]). If it cannot answer,
 * or has no release, GitHub's Releases API is asked directly for
 * [Config.GITHUB_REPO], so the update popup works from the very first
 * release, before the server is up. Both only ever report a full release:
 * a pre-release (a beta tag, "v0.2.1.0-beta") is left out by the server and
 * by GitHub's "latest release" alike.
 *
 * Each APK's expected SHA-256 from a check is remembered by its link
 * ([expectedSha256]), so [AppUpdater] can test the file it downloaded.
 */
class UpdateChecker {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    /**
     * The latest release, or [Latest.None] when there is none. Throws an
     * [IOException] with a message fit for the screen when no source could
     * be reached. [fresh] is the user asking outright and tells the server
     * to look past its own cache.
     */
    suspend fun latest(fresh: Boolean = false): Latest = withContext(Dispatchers.IO) {
        askSources(fresh).also { if (it is Latest.Release) remember(it.info) }
    }

    private fun askSources(fresh: Boolean): Latest {
        var serverFailure: IOException? = null
        var serverHasNone = false

        if (Config.UPDATE_URL.isNotBlank()) {
            try {
                return Latest.Release(fromServer(fresh))
            } catch (e: HttpException) {
                // 404: the server is up but has no release — or the site is not
                // deployed yet. Either way GitHub may still know better.
                if (e.code == 404) serverHasNone = true else serverFailure = e
                Log.w(TAG, "server update check failed: ${e.code} ${e.message}")
            } catch (e: IOException) {
                serverFailure = e
                Log.w(TAG, "server update check failed: ${e.message}", e.cause)
            }
        }

        if (Config.GITHUB_REPO.isNotBlank()) {
            try {
                return Latest.Release(fromGitHub())
            } catch (e: HttpException) {
                Log.w(TAG, "github update check failed: ${e.code} ${e.message}")
                // 404 from GitHub: the repository has no published release.
                if (e.code == 404) return Latest.None
                if (serverHasNone) return Latest.None
                throw serverFailure?.takeIf { it is HttpException } ?: e
            } catch (e: IOException) {
                Log.w(TAG, "github update check failed: ${e.message}", e.cause)
                if (serverHasNone) return Latest.None
                // The more specific message wins: an HTTP failure over "no connection".
                throw serverFailure?.takeIf { it is HttpException } ?: e
            }
        }

        if (serverHasNone) return Latest.None
        throw serverFailure ?: IOException("No update source is configured.")
    }

    private fun fromServer(fresh: Boolean): AppVersionInfo {
        val base = Config.UPDATE_URL
        val url = if (!fresh) base else base + (if ('?' in base) "&fresh=1" else "?fresh=1")
        return json.decodeFromString(AppVersionInfo.serializer(), get(url))
    }

    private fun fromGitHub(): AppVersionInfo {
        val url = "https://api.github.com/repos/${Config.GITHUB_REPO}/releases/latest"
        val release = json.decodeFromString(GitHubRelease.serializer(), get(url))
        // The checksums are a nicety here: a release without them, or a failure reading them, still updates.
        val sums = release.checksumsAsset()?.let { runCatching { parseChecksums(get(it.browserDownloadUrl)) }.getOrNull() }
        return release.toVersionInfo(sums.orEmpty())
    }

    /** SHA256SUMS ("<hex>  <file>" a line, as sha256sum writes it) by file name. */
    private fun parseChecksums(text: String): Map<String, String> = buildMap {
        val line = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+)$""")
        text.lineSequence().forEach { raw -> line.find(raw.trim())?.let { put(it.groupValues[2].trim(), it.groupValues[1].lowercase()) } }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, application/vnd.github+json")
            .header("User-Agent", "${Config.APP_NAME} Android/${BuildConfig.VERSION_NAME}")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .get()
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw NoConnectionException(e)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw HttpException(it.code, errorMessage(body, it.code))
            if (body.isBlank()) throw HttpException(it.code, "The update check came back empty.")
            return body
        }
    }

    /** The `error` field of a JSON error body, or a generic line with the status code. */
    private fun errorMessage(body: String, code: Int): String {
        val fromBody = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        return fromBody?.takeIf { it.isNotBlank() } ?: "Could not check for updates ($code)."
    }

    companion object {
        private const val TAG = "CtrlapsUpdate"

        /** APK link → its SHA-256, from every check this run; links carry the version, so old ones never collide. */
        private val checksums = ConcurrentHashMap<String, String>()

        private fun remember(info: AppVersionInfo) {
            listOfNotNull(info.apkUrl, *info.apks.values.toTypedArray()).forEach { url ->
                info.sha256For(url)?.let { checksums[url] = it }
            }
        }

        /** The SHA-256 the APK at [url] must have, or null when the release gave none (it is then installed unchecked). */
        fun expectedSha256(url: String): String? = checksums[url]
    }
}
