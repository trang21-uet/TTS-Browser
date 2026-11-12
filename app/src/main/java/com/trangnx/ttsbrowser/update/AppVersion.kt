package com.trangnx.ttsbrowser.update

/** `MAJOR.MINOR.PATCH`, as in `versionName` and in release tags (`v0.2.0`). */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)""")

        /** Null for anything that is not a version, so a malformed tag means "no update", never a crash. */
        fun parse(raw: String): AppVersion? {
            val match = PATTERN.find(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(major.toIntOrNull() ?: return null, minor.toIntOrNull() ?: return null, patch.toIntOrNull() ?: return null)
        }
    }
}
