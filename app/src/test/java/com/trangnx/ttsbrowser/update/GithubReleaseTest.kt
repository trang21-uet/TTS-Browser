package com.trangnx.ttsbrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GithubReleaseTest {

    private val full = """
        {
          "tag_name": "v0.3.0",
          "html_url": "https://github.com/trang21-uet/TTS-Browser/releases/tag/v0.3.0",
          "body": "### Added\n\n- Kiểm tra cập nhật\n",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 10},
            {"name": "TTS-Browser-0.3.0.apk", "browser_download_url": "https://github.com/x/y/releases/download/v0.3.0/TTS-Browser-0.3.0.apk", "size": 3049157}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesVersionNotesAndApkAsset() {
        val release = GithubRelease.parse(full)!!
        assertEquals(AppVersion(0, 3, 0), release.version)
        assertEquals("https://github.com/x/y/releases/download/v0.3.0/TTS-Browser-0.3.0.apk", release.apkUrl)
        assertEquals(3049157L, release.apkSize)
        assertEquals("https://github.com/trang21-uet/TTS-Browser/releases/tag/v0.3.0", release.pageUrl)
    }

    @Test
    fun releaseWithoutApkHasNoApkUrl() {
        val release = GithubRelease.parse("""{"tag_name": "v1.0.0", "body": null, "assets": []}""")!!
        assertNull(release.apkUrl)
        assertNull(release.apkSize)
        assertEquals("", release.notes)
        assertEquals(GithubRelease.RELEASES_PAGE, release.pageUrl)
    }

    @Test
    fun nonHttpsApkIsIgnored() {
        val json = """{"tag_name": "v1.0.0", "assets": [{"name": "a.apk", "browser_download_url": "http://example.com/a.apk", "size": 5}]}"""
        assertNull(GithubRelease.parse(json)!!.apkUrl)
    }

    @Test
    fun unusablePayloadsAreNull() {
        assertNull(GithubRelease.parse("not json"))
        assertNull(GithubRelease.parse("""{"tag_name": "nightly"}"""))
        assertNull(GithubRelease.parse("""{"name": "no tag"}"""))
        assertNotNull(GithubRelease.parse("""{"tag_name": "v1.0.0"}"""))
    }

    @Test
    fun displayNotesTurnsMarkdownIntoPlainLines() {
        assertEquals("Added\n\n• Kiểm tra cập nhật", GithubRelease.displayNotes("### Added\n\n- Kiểm tra cập nhật\n"))
        assertEquals("", GithubRelease.displayNotes(""))
    }
}
