package com.arkhins.ctrlaps.data

/**
 * Whether [remote] is a newer version than [local].
 *
 * Versions are dotted numeric segments of any length ("0.0.0.0", "1.2",
 * "v2.0.1.7"); a missing segment counts as 0. A pre-release's suffix
 * ("0.2.1.0-beta", "0.2.1.0-rc.2") is cut off before the segments are read,
 * so its own dots can't pass for more of the version, and it counts as
 * older than the plain version it leads up to: a beta build is offered
 * 0.2.1.0 once that is out.
 */
fun isNewerVersion(remote: String, local: String): Boolean {
    val r = versionSegments(remote)
    val l = versionSegments(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val rv = r.getOrElse(i) { 0 }
        val lv = l.getOrElse(i) { 0 }
        if (rv != lv) return rv > lv
    }
    return isPreRelease(local) && !isPreRelease(remote)
}

/** The version without a leading "v" or a pre-release / build suffix ("-beta", "+abc"). */
private fun baseVersion(version: String): String =
    version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')

private fun isPreRelease(version: String): Boolean = '-' in version.trim()

fun versionSegments(version: String): List<Int> =
    baseVersion(version).split(".")
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
