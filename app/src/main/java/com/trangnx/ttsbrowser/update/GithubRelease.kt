package com.trangnx.ttsbrowser.update

import org.json.JSONException
import org.json.JSONObject

/** A published GitHub release, narrowed to what the update flow needs. */
data class GithubRelease(
    val version: AppVersion,
    /** The `## [x.y.z]` section of `docs/project-changelog.md`, copied in by `release-apk.yml`. May be empty. */
    val notes: String,
    val pageUrl: String,
    /** Null when the build step attached no `.apk`. */
    val apkUrl: String?,
    val apkSize: Long?,
) {
    companion object {
        const val RELEASES_PAGE = "https://github.com/trang21-uet/TTS-Browser/releases"

        /** Null when the payload has no parseable version tag: an unusable release is "nothing to update to". */
        fun parse(json: String): GithubRelease? = try {
            val root = JSONObject(json)
            val version = root.stringOrNull("tag_name")?.let(AppVersion::parse)
            if (version == null) null else {
                val apk = root.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                        .firstOrNull { it.stringOrNull("name").orEmpty().endsWith(".apk", ignoreCase = true) }
                }
                GithubRelease(
                    version = version,
                    notes = root.stringOrNull("body").orEmpty().trim(),
                    pageUrl = root.stringOrNull("html_url") ?: RELEASES_PAGE,
                    // Only https is ever fetched; anything else is treated as having no APK.
                    apkUrl = apk?.stringOrNull("browser_download_url")?.takeIf { it.startsWith("https://") },
                    apkSize = apk?.optLong("size", -1L)?.takeIf { it > 0 },
                )
            }
        } catch (_: JSONException) {
            null
        }

        /** The changelog is Markdown; show headings as plain lines and `- ` items as bullets. */
        fun displayNotes(notes: String): String = notes.lines()
            .map { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#") -> trimmed.trimStart('#').trim()
                    trimmed.startsWith("- ") -> "• " + trimmed.removePrefix("- ")
                    else -> trimmed
                }
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        // org.json's optString turns JSON null into the string "null".
        private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
    }
}
