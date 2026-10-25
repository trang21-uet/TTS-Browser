// SPDX-License-Identifier: LGPL-3.0-only
// Token v2 port derived from rany2/edge-tts src/edge_tts/drm.py (2026-10-26).
// Copyright (c) rany <rany@riseup.net>. Kotlin port: TTS Browser contributors.
// Source: https://github.com/rany2/edge-tts/blob/master/src/edge_tts/drm.py
// See assets/licenses/edge-tts-LICENSE.txt and GPL-3.0.txt.
package com.trangnx.ttsbrowser.tts.edge

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Current v2 token: five-minute Windows ticks, a modified SHA-256 and CS64. */
object EdgeTokenGenerator {
    fun generate(unixMillis: Long = System.currentTimeMillis()): String {
        val seconds = Math.floorDiv(unixMillis, 1000L) + 11_644_473_600L
        val ticks = (seconds - Math.floorMod(seconds, 300L)) * 10_000_000L
        val salt = "$ticks${EdgeProtocol.TRUSTED_CLIENT_TOKEN}".toByteArray(Charsets.US_ASCII)
        val digest = digest(salt)
        val check = checksum(salt, digest)
        return "${hex(check)}-${hex(digest)}"
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }

    private fun small0(x: Int) = Integer.rotateRight(x, 7) xor Integer.rotateRight(x, 18) xor (x ushr 3)
    private fun small1(x: Int) = Integer.rotateRight(x, 17) xor Integer.rotateRight(x, 19) xor (x ushr 10)
    private fun swap(x: Int) = Integer.rotateLeft(x, 16)

    private fun expand(words: IntArray, start: Int = 16) {
        for (i in start..63) words[i] = small1(words[i - 2]) + words[i - 7] + small0(words[i - 15]) + words[i - 16]
    }

    /** Int overflow is intentional: every operation is modulo 2^32. */
    private fun rounds(state: IntArray, words: IntArray, start: Int = 0): IntArray {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        for (i in start..63) {
            val sigmaE = Integer.rotateRight(e, 6) xor Integer.rotateRight(e, 11) xor Integer.rotateRight(e, 25)
            val sigmaA = Integer.rotateRight(a, 2) xor Integer.rotateRight(a, 13) xor Integer.rotateRight(a, 22)
            val constant = if (start == 14 && i == 14) 0xC9DC06A7.toInt() else roundConstants[i]
            val t1 = h + sigmaE + (g xor (e and (f xor g))) + constant + words[i]
            val t2 = sigmaA + ((a and b) or (c and (a or b)))
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        return intArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun digest(salt: ByteArray): ByteArray {
        val words = IntArray(64)
        words[14] = ((salt[0].toInt() and 255) shl 16) or ((salt[1].toInt() and 255) shl 8) or (salt[2].toInt() and 255)
        words[15] = ByteBuffer.wrap(salt, 3, 4).int
        for (i in 16..29) {
            var value = initialSchedule[i - 16]
            if (i - 2 >= 14) value += small1(words[i - 2]) xor (if (i == 16) 0x000B92C0 else 0)
            if (i - 7 >= 14) value += words[i - 7]
            if (i - 15 >= 14) value += small0(words[i - 15]) xor (if (i == 29) 0x059C0B80 else 0)
            words[i] = value
        }
        words[30] = small1(words[28]) + words[23] + small0(words[15]) + words[14] + 0xC9DC06A7.toInt() - roundConstants[14]
        expand(words, 31)
        var state = rounds(initialWorking, words, 14).mapIndexed { i, value -> value + initial[i] }.toIntArray()
        val length = 57 + salt.size
        val padding = Math.floorMod(55 - length, 64)
        val tail = ByteBuffer.allocate(salt.size - 7 + 1 + padding + 8)
            .put(salt, 7, salt.size - 7).put(0x80.toByte()).put(ByteArray(padding)).putLong(length * 8L).array()
        for (offset in tail.indices step 64) {
            val buffer = ByteBuffer.wrap(tail, offset, 64)
            for (i in 0..15) words[i] = buffer.int
            expand(words)
            state = rounds(state, words).mapIndexed { i, value -> value + state[i] }.toIntArray()
        }
        return ByteBuffer.allocate(32).apply { state.forEach { putInt(it) } }.array()
    }

    private fun checksum(salt: ByteArray, digest: ByteArray): ByteArray {
        val digestBuffer = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN)
        val keys = intArrayOf(digestBuffer.int or 1, digestBuffer.int or 1)
        val factors = keys.map { it * 0x82893933.toInt() }
        val terms = coefficients.mapIndexed { i, pair -> pair.first * factors[i % 2] }.toMutableList()
        val offsets = coefficients.map { it.second }.toMutableList()
        val saltWords = ByteBuffer.allocate(((salt.size + 1 + 7) / 8) * 8).order(ByteOrder.LITTLE_ENDIAN)
            .put(0.toByte()).put(salt).apply { rewind() }
        var i = 0
        while (saltWords.hasRemaining()) {
            val word = saltWords.int
            val term = word * keys[i % 2] + if (i == 0) 0xF2F90B1A.toInt() * factors[0] else 0
            val offset = swap(word) * (if (i % 2 == 0) 0xEF0569FB.toInt() else 0xC31713DB.toInt()) +
                if (i == 0) 0x0B1A0000 else 0
            terms += term
            offsets += offset
            i++
        }
        var swapAcc = 0; var swapLast = 0; var state = 0; var acc = 0
        for (n in terms.indices step 2) {
            val q1 = swap(offsets[n] + terms[n]) * 0x76F50F34
            swapAcc += swap(q1) * 0xC3EFEA97.toInt() + q1
            val q2 = swap(offsets[n + 1] + terms[n + 1]) * 0x1881CE3C
            swapLast = swap(q2) * 0x35BD1EC9 + q2
            swapAcc += swapLast
            state = state * keys[0] + terms[n]
            for (multiplier in intArrayOf(0xCF98B111.toInt(), 0x87085B9F.toInt(), 0x12CEB96D, 0x257E1D83)) {
                state = swap(state) * multiplier
            }
            acc += state
            var t = state * keys[1] + terms[n + 1]
            for (multiplier in intArrayOf(0xA27416F5.toInt(), 0xD38396FF.toInt(), 0x7C932B89, 0xBFA49F69.toInt())) {
                t = swap(t) * multiplier
            }
            state = t
            acc += state
        }
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(swapLast xor state).putInt(swapAcc xor acc).array()
    }

    private val initial = ints("6A09E667 BB67AE85 3C6EF372 A54FF53A 510E527F 9B05688C 1F83D9AB 5BE0CD19")
    private val initialWorking = ints("FD9FBEA2 91243CE7 BE499094 CD2E1E0A 6C006F49 04BD35AB FEB0A7E4 03EFFBD2")
    private val initialSchedule = ints("EB24BF9E 1558691F A0C09EE2 0CFA32BA A622EE8C D1DD9491 C0DD25A0 F9E70AB6 F0C31573 7058CE4E 7188E606 00E5AB33 D258B060 72766564")
    private val roundConstants = ints(
        "428A2F98 71374491 B5C0FBCF E9B5DBA5 3956C25B 59F111F1 923F82A4 AB1C5ED5 " +
            "D807AA98 12835B01 243185BE 550C7DC3 72BE5D74 80DEB1FE 9BDC06A7 C19BF174 " +
            "E49B69C1 EFBE4786 0FC19DC6 240CA1CC 2DE92C6F 4A7484AA 5CB0A9DC 76F988DA " +
            "983E5152 A831C66D B00327C8 BF597FC7 C6E00BF3 D5A79147 06CA6351 14292967 " +
            "27B70A85 2E1B2138 4D2C6DFC 53380D13 650A7354 766A0ABB 81C2C92E 92722C85 " +
            "A2BFE8A1 A81A664B C24B8B70 C76C51A3 D192E819 D6990624 F40E3585 106AA070 " +
            "19A4C116 1E376C08 2748774C 34B0BCB5 391C0CB3 4ED8AA4A 5B9CCA4F 682E6FF3 " +
            "748F82EE 78A5636F 84C87814 8CC70208 90BEFFFA A4506CEB BEF9A3F7 C67178F2",
    )
    private val coefficients = ints(
        "E82E91B1 B32700D0 44DE24C6 88DFF51D 3BEB65BC 29514038 C89C5933 B328CBDF " +
            "8D1EC211 D405B4D5 314EF5D5 26E55F3C E92491B1 607801C6 F0A5B9D5 82B2C1FB " +
            "06064CF3 FA51A3DA DA79FA60 207CC464 BED20560 3C3C74F3 89F9C9F8 D2D14B61 " +
            "35FA38C6 BB04A2C1 CB49E3C6 C5626167",
    ).toList().chunked(2).map { it[0] to it[1] }

    private fun ints(hex: String) = hex.split(' ').map { it.toLong(16).toInt() }.toIntArray()
}
