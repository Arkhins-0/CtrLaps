package com.arkhins.ctrlaps.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the update endpoint reports: the latest release of this app.
 *
 * GET <baseUrl>/api/app-version on the CTR[L]APS server returns exactly this
 * shape. [apkUrl] is null when the release carries no APK, in which case the
 * app can only open [releaseUrl] in the browser.
 */
@Serializable
data class AppVersionInfo(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String? = null,
    val notes: String = "",
    /** The notes by heading, each line with the roles it is for (see ReleaseNotes.kt). */
    val sections: List<NoteSection> = emptyList(),
    /** Smaller APKs for one processor type each ("arm64-v8a", …); [apkUrl] is the universal one. */
    val apks: Map<String, String> = emptyMap(),
) {
    /** The APK for this phone: its own processor type's when the release has one (about half the size), else the universal one. */
    fun apkFor(abis: Array<String>): String? = abis.firstNotNullOfOrNull { apks[it.lowercase()] } ?: apkUrl
}

/** The parts of GitHub's "latest release" response the fallback needs. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
) {
    /** Prefer the release APK over a debug one when both are attached. */
    fun toVersionInfo(): AppVersionInfo {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) && !it.name.contains("debug", ignoreCase = true) }
        // "CTRLAPS-v1.2.3.4.arm64-v8a.apk" is for one processor type; the one without is universal.
        val abi = Regex("""\.(arm64-v8a|armeabi-v7a|x86_64|x86)\.apk$""", RegexOption.IGNORE_CASE)
        val apk = apks.firstOrNull { !abi.containsMatchIn(it.name) }
        return AppVersionInfo(
            version = tagName.trim().removePrefix("v"),
            releaseUrl = htmlUrl,
            apkUrl = apk?.browserDownloadUrl,
            apks = apks.mapNotNull { a -> abi.find(a.name)?.groupValues?.get(1)?.lowercase()?.let { it to a.browserDownloadUrl } }.toMap(),
            notes = body.orEmpty(),
            sections = noteSections(body.orEmpty()),
        )
    }
}

@Serializable
internal data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
