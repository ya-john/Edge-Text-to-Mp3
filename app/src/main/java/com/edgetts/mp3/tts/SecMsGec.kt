package com.edgetts.mp3.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generates the `Sec-MS-GEC` anti-abuse token the Edge Read Aloud service requires on every
 * connection (token format v2: "<16 hex CS64>-<64 hex SHA-256 variant>").
 *
 * Port of `edge_tts/drm.py` from rany2/edge-tts 7.3.1. The service rejects the older plain
 * SHA-256 (v1) token. If Microsoft rotates the scheme again, this file and
 * [EdgeTtsClient.SEC_MS_GEC_VERSION] are the only places that need to change.
 *
 * All arithmetic is mod 2^32, which Kotlin's Int wraparound gives for free.
 */
object SecMsGec {
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val WIN_EPOCH = 11_644_473_600L

    /** Corrects for a device clock that disagrees with the server (set from a 403's Date header). */
    @Volatile
    var clockSkewSeconds: Long = 0L

    fun generate(unixSeconds: Long = System.currentTimeMillis() / 1000 + clockSkewSeconds): String {
        var ticks = unixSeconds + WIN_EPOCH
        ticks -= ticks % 300
        ticks *= 10_000_000L
        val data = "$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.US_ASCII)
        val digest = sha256Variant(data)
        val (c0, c1) = cs64(data, digest)
        val cs = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(c0).putInt(c1).array()
        return "${cs.toHex()}-${digest.toHex()}"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    // ---- SHA-256 with a pre-computed, tweaked first block ----

    private val SHA_INITIAL = intArrayOf(0x6A09E667.toInt(), 0xBB67AE85.toInt(), 0x3C6EF372.toInt(), 0xA54FF53A.toInt(), 0x510E527F.toInt(), 0x9B05688C.toInt(), 0x1F83D9AB.toInt(), 0x5BE0CD19.toInt())
    private val SHA_STATE = intArrayOf(0xFD9FBEA2.toInt(), 0x91243CE7.toInt(), 0xBE499094.toInt(), 0xCD2E1E0A.toInt(), 0x6C006F49.toInt(), 0x04BD35AB.toInt(), 0xFEB0A7E4.toInt(), 0x03EFFBD2.toInt())
    private val SHA_SCHEDULE = intArrayOf(0xEB24BF9E.toInt(), 0x1558691F.toInt(), 0xA0C09EE2.toInt(), 0x0CFA32BA.toInt(), 0xA622EE8C.toInt(), 0xD1DD9491.toInt(), 0xC0DD25A0.toInt(), 0xF9E70AB6.toInt(), 0xF0C31573.toInt(), 0x7058CE4E.toInt(), 0x7188E606.toInt(), 0x00E5AB33.toInt(), 0xD258B060.toInt(), 0x72766564.toInt())
    private val K = intArrayOf(0x428A2F98.toInt(), 0x71374491.toInt(), 0xB5C0FBCF.toInt(), 0xE9B5DBA5.toInt(), 0x3956C25B.toInt(), 0x59F111F1.toInt(), 0x923F82A4.toInt(), 0xAB1C5ED5.toInt(), 0xD807AA98.toInt(), 0x12835B01.toInt(), 0x243185BE.toInt(), 0x550C7DC3.toInt(), 0x72BE5D74.toInt(), 0x80DEB1FE.toInt(), 0x9BDC06A7.toInt(), 0xC19BF174.toInt(), 0xE49B69C1.toInt(), 0xEFBE4786.toInt(), 0x0FC19DC6.toInt(), 0x240CA1CC.toInt(), 0x2DE92C6F.toInt(), 0x4A7484AA.toInt(), 0x5CB0A9DC.toInt(), 0x76F988DA.toInt(), 0x983E5152.toInt(), 0xA831C66D.toInt(), 0xB00327C8.toInt(), 0xBF597FC7.toInt(), 0xC6E00BF3.toInt(), 0xD5A79147.toInt(), 0x06CA6351.toInt(), 0x14292967.toInt(), 0x27B70A85.toInt(), 0x2E1B2138.toInt(), 0x4D2C6DFC.toInt(), 0x53380D13.toInt(), 0x650A7354.toInt(), 0x766A0ABB.toInt(), 0x81C2C92E.toInt(), 0x92722C85.toInt(), 0xA2BFE8A1.toInt(), 0xA81A664B.toInt(), 0xC24B8B70.toInt(), 0xC76C51A3.toInt(), 0xD192E819.toInt(), 0xD6990624.toInt(), 0xF40E3585.toInt(), 0x106AA070.toInt(), 0x19A4C116.toInt(), 0x1E376C08.toInt(), 0x2748774C.toInt(), 0x34B0BCB5.toInt(), 0x391C0CB3.toInt(), 0x4ED8AA4A.toInt(), 0x5B9CCA4F.toInt(), 0x682E6FF3.toInt(), 0x748F82EE.toInt(), 0x78A5636F.toInt(), 0x84C87814.toInt(), 0x8CC70208.toInt(), 0x90BEFFFA.toInt(), 0xA4506CEB.toInt(), 0xBEF9A3F7.toInt(), 0xC67178F2.toInt())
    private const val SHA256_ADJUSTMENT = 0xC9DC06A7.toInt()
    private const val SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80
    private const val SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0
    private val K_FIRST_BLOCK = K.copyOf().also { it[14] = SHA256_ADJUSTMENT }

    private fun sigma0(x: Int) = Integer.rotateRight(x, 7) xor Integer.rotateRight(x, 18) xor (x ushr 3)
    private fun sigma1(x: Int) = Integer.rotateRight(x, 17) xor Integer.rotateRight(x, 19) xor (x ushr 10)

    private fun expand(w: IntArray, from: Int) {
        for (i in from until 64) w[i] = sigma1(w[i - 2]) + w[i - 7] + sigma0(w[i - 15]) + w[i - 16]
    }

    private fun rounds(state: IntArray, w: IntArray, firstRound: Int, k: IntArray): IntArray {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        for (i in firstRound until 64) {
            val s1 = Integer.rotateRight(e, 6) xor Integer.rotateRight(e, 11) xor Integer.rotateRight(e, 25)
            val ch = g xor (e and (f xor g))
            val t1 = h + s1 + ch + k[i] + w[i]
            val s0 = Integer.rotateRight(a, 2) xor Integer.rotateRight(a, 13) xor Integer.rotateRight(a, 22)
            val maj = (a and b) or (c and (a or b))
            val t2 = s0 + maj
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        return intArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun initState(prefix: ByteArray): IntArray {
        val w = IntArray(64)
        w[14] = ((prefix[0].toInt() and 0xFF) shl 16) or ((prefix[1].toInt() and 0xFF) shl 8) or (prefix[2].toInt() and 0xFF)
        w[15] = ByteBuffer.wrap(prefix, 3, 4).order(ByteOrder.BIG_ENDIAN).int
        for (i in 16..29) {
            var v = SHA_SCHEDULE[i - 16]
            if (i - 2 >= 14) v += if (i == 16) sigma1(w[i - 2]) xor SHA256_SIGMA1_ADJUSTMENT else sigma1(w[i - 2])
            if (i - 7 >= 14) v += w[i - 7]
            if (i - 15 >= 14) v += if (i == 29) sigma0(w[i - 15]) xor SHA256_SIGMA0_ADJUSTMENT else sigma0(w[i - 15])
            w[i] = v
        }
        w[30] = sigma1(w[28]) + w[23] + sigma0(w[15]) + w[14] + SHA256_ADJUSTMENT - K[14]
        expand(w, 31)
        val working = rounds(SHA_STATE, w, 14, K_FIRST_BLOCK)
        return IntArray(8) { SHA_INITIAL[it] + working[it] }
    }

    private fun sha256Variant(data: ByteArray): ByteArray {
        require(data.size >= 7)
        val length = 57 + data.size
        val zeros = Math.floorMod(55 - length, 64)
        val rest = data.copyOfRange(7, data.size)
        val padded = ByteBuffer.allocate(rest.size + 1 + zeros + 8).order(ByteOrder.BIG_ENDIAN)
        padded.put(rest).put(0x80.toByte()).put(ByteArray(zeros)).putLong(length.toLong() * 8)
        val bytes = padded.array()
        var state = initState(data.copyOfRange(0, 7))
        var offset = 0
        while (offset < bytes.size) {
            val w = IntArray(64)
            val bb = ByteBuffer.wrap(bytes, offset, 64).order(ByteOrder.BIG_ENDIAN)
            for (i in 0 until 16) w[i] = bb.int
            expand(w, 16)
            val working = rounds(state, w, 0, K)
            state = IntArray(8) { state[it] + working[it] }
            offset += 64
        }
        val out = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        state.forEach { out.putInt(it) }
        return out.array()
    }

    // ---- CS64 checksum keyed by the digest ----

    private val CS64_COEF = intArrayOf(0xE82E91B1.toInt(), 0x44DE24C6, 0x3BEB65BC, 0xC89C5933.toInt(), 0x8D1EC211.toInt(), 0x314EF5D5, 0xE92491B1.toInt(), 0xF0A5B9D5.toInt(), 0x06064CF3, 0xDA79FA60.toInt(), 0xBED20560.toInt(), 0x89F9C9F8.toInt(), 0x35FA38C6, 0xCB49E3C6.toInt())
    private val CS64_OFFSET = intArrayOf(0xB32700D0.toInt(), 0x88DFF51D.toInt(), 0x29514038, 0xB328CBDF.toInt(), 0xD405B4D5.toInt(), 0x26E55F3C, 0x607801C6, 0x82B2C1FB.toInt(), 0xFA51A3DA.toInt(), 0x207CC464, 0x3C3C74F3, 0xD2D14B61.toInt(), 0xBB04A2C1.toInt(), 0xC5626167.toInt())
    private const val CS64_FACTOR = 0x82893933.toInt()
    private const val CS64_ADJUSTMENT0 = 0xF2F90B1A.toInt()
    private const val CS64_ADJUSTMENT1 = 0x0B1A0000

    private fun rol16(v: Int) = Integer.rotateLeft(v, 16)

    private fun wordsLe4Even(data: ByteArray): IntArray {
        val padded = data.copyOf(data.size + Math.floorMod(-data.size, 4))
        if (padded.isEmpty()) return IntArray(0)
        val n = padded.size / 4
        val bb = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN)
        val words = IntArray(if (n % 2 == 1) n + 1 else n)
        for (i in 0 until n) words[i] = bb.int
        return words
    }

    private fun cs64(data: ByteArray, digest: ByteArray): Pair<Int, Int> {
        val kb = ByteBuffer.wrap(digest, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        val keyA = kb.int or 1
        val keyB = kb.int or 1
        val factors = intArrayOf(keyA * CS64_FACTOR, keyB * CS64_FACTOR)
        val terms = ArrayList<Int>()
        val offsets = ArrayList<Int>()
        CS64_COEF.forEachIndexed { i, c -> terms += c * factors[i % 2] }
        CS64_OFFSET.forEach { offsets += it }
        val words = wordsLe4Even(byteArrayOf(0) + data)
        words.forEachIndexed { i, word ->
            var term = word * (if (i % 2 == 0) keyA else keyB)
            var offset = rol16(word) * (if (i % 2 == 0) 0xEF0569FB.toInt() else 0xC31713DB.toInt())
            if (i == 0) {
                term += CS64_ADJUSTMENT0 * factors[0]
                offset += CS64_ADJUSTMENT1
            }
            terms += term
            offsets += offset
        }
        var swapAcc = 0; var swapLast = 0; var state = 0; var acc = 0
        var i = 0
        while (i < terms.size) {
            val x = terms[i]; val y = terms[i + 1]
            var t = rol16(offsets[i] + x)
            var q = t * 0x76F50F34
            swapAcc += rol16(q) * 0xC3EFEA97.toInt() + q
            t = rol16(offsets[i + 1] + y)
            q = t * 0x1881CE3C
            swapLast = rol16(q) * 0x35BD1EC9 + q
            swapAcc += swapLast

            state = state * keyA + x
            state = rol16(state) * 0xCF98B111.toInt()
            state = rol16(state) * 0x87085B9F.toInt()
            state = rol16(state) * 0x12CEB96D
            state = rol16(state) * 0x257E1D83
            acc += state
            t = state * keyB + y
            t = rol16(t) * 0xA27416F5.toInt()
            t = rol16(t) * 0xD38396FF.toInt()
            state = rol16(rol16(t) * 0x7C932B89) * 0xBFA49F69.toInt()
            acc += state
            i += 2
        }
        return Pair(swapLast xor state, swapAcc xor acc)
    }
}
