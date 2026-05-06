package com.example.islandlyrics.data.lyric

internal object QqQrcDecrypter {
    private const val DECRYPT = 0
    private const val ENCRYPT = 1
    private val qqKey = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)

    fun decryptToCompressedBytes(encryptedLyrics: String): ByteArray {
        val encrypted = hexToBytes(encryptedLyrics)
        val output = ByteArray(encrypted.size)
        val schedule = Array(3) { Array(16) { ByteArray(6) } }
        tripleDesKeySetup(qqKey, schedule, DECRYPT)

        var offset = 0
        while (offset < encrypted.size) {
            tripleDesCrypt(encrypted, offset, output, offset, schedule)
            offset += 8
        }
        return output
    }

    private fun tripleDesKeySetup(key: ByteArray, schedule: Array<Array<ByteArray>>, mode: Int) {
        if (mode == ENCRYPT) {
            keySchedule(key, 0, schedule[0], mode)
            keySchedule(key, 8, schedule[1], DECRYPT)
            keySchedule(key, 16, schedule[2], mode)
        } else {
            keySchedule(key, 0, schedule[2], mode)
            keySchedule(key, 8, schedule[1], ENCRYPT)
            keySchedule(key, 16, schedule[0], mode)
        }
    }

    private fun tripleDesCrypt(input: ByteArray, inputOffset: Int, output: ByteArray, outputOffset: Int, key: Array<Array<ByteArray>>) {
        val temp = ByteArray(8)
        crypt(input, inputOffset, temp, 0, key[0])
        crypt(temp, 0, temp, 0, key[1])
        crypt(temp, 0, output, outputOffset, key[2])
    }

    private fun keySchedule(key: ByteArray, keyOffset: Int, schedule: Array<ByteArray>, mode: Int) {
        val keyRndShift = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
        val keyPermC = intArrayOf(56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35)
        val keyPermD = intArrayOf(62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3)
        val keyCompression = intArrayOf(13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31)

        var c = 0
        var d = 0
        var j = 31
        for (i in 0 until 28) {
            c = c or bitNum(key, keyOffset, keyPermC[i], j)
            j--
        }
        j = 31
        for (i in 0 until 28) {
            d = d or bitNum(key, keyOffset, keyPermD[i], j)
            j--
        }

        for (i in 0 until 16) {
            c = ((c shl keyRndShift[i]) or (c ushr (28 - keyRndShift[i]))) and 0xfffffff0.toInt()
            d = ((d shl keyRndShift[i]) or (d ushr (28 - keyRndShift[i]))) and 0xfffffff0.toInt()
            val toGen = if (mode == DECRYPT) 15 - i else i
            schedule[toGen].fill(0)

            for (k in 0 until 24) {
                schedule[toGen][k / 8] = (schedule[toGen][k / 8].toInt() or bitNumIntr(c, keyCompression[k], 7 - (k % 8)).toInt()).toByte()
            }
            for (k in 24 until 48) {
                schedule[toGen][k / 8] = (schedule[toGen][k / 8].toInt() or bitNumIntr(d, keyCompression[k] - 27, 7 - (k % 8)).toInt()).toByte()
            }
        }
    }

    private fun crypt(input: ByteArray, inputOffset: Int, output: ByteArray, outputOffset: Int, key: Array<ByteArray>) {
        val state = IntArray(2)
        ip(state, input, inputOffset)
        for (idx in 0 until 15) {
            val t = state[1]
            state[1] = f(state[1], key[idx]) xor state[0]
            state[0] = t
        }
        state[0] = f(state[1], key[15]) xor state[0]
        invIp(state, output, outputOffset)
    }

    private fun ip(state: IntArray, input: ByteArray, offset: Int) {
        state[0] = bitNum(input, offset, 57, 31) or bitNum(input, offset, 49, 30) or bitNum(input, offset, 41, 29) or bitNum(input, offset, 33, 28) or bitNum(input, offset, 25, 27) or bitNum(input, offset, 17, 26) or bitNum(input, offset, 9, 25) or bitNum(input, offset, 1, 24) or bitNum(input, offset, 59, 23) or bitNum(input, offset, 51, 22) or bitNum(input, offset, 43, 21) or bitNum(input, offset, 35, 20) or bitNum(input, offset, 27, 19) or bitNum(input, offset, 19, 18) or bitNum(input, offset, 11, 17) or bitNum(input, offset, 3, 16) or bitNum(input, offset, 61, 15) or bitNum(input, offset, 53, 14) or bitNum(input, offset, 45, 13) or bitNum(input, offset, 37, 12) or bitNum(input, offset, 29, 11) or bitNum(input, offset, 21, 10) or bitNum(input, offset, 13, 9) or bitNum(input, offset, 5, 8) or bitNum(input, offset, 63, 7) or bitNum(input, offset, 55, 6) or bitNum(input, offset, 47, 5) or bitNum(input, offset, 39, 4) or bitNum(input, offset, 31, 3) or bitNum(input, offset, 23, 2) or bitNum(input, offset, 15, 1) or bitNum(input, offset, 7, 0)
        state[1] = bitNum(input, offset, 56, 31) or bitNum(input, offset, 48, 30) or bitNum(input, offset, 40, 29) or bitNum(input, offset, 32, 28) or bitNum(input, offset, 24, 27) or bitNum(input, offset, 16, 26) or bitNum(input, offset, 8, 25) or bitNum(input, offset, 0, 24) or bitNum(input, offset, 58, 23) or bitNum(input, offset, 50, 22) or bitNum(input, offset, 42, 21) or bitNum(input, offset, 34, 20) or bitNum(input, offset, 26, 19) or bitNum(input, offset, 18, 18) or bitNum(input, offset, 10, 17) or bitNum(input, offset, 2, 16) or bitNum(input, offset, 60, 15) or bitNum(input, offset, 52, 14) or bitNum(input, offset, 44, 13) or bitNum(input, offset, 36, 12) or bitNum(input, offset, 28, 11) or bitNum(input, offset, 20, 10) or bitNum(input, offset, 12, 9) or bitNum(input, offset, 4, 8) or bitNum(input, offset, 62, 7) or bitNum(input, offset, 54, 6) or bitNum(input, offset, 46, 5) or bitNum(input, offset, 38, 4) or bitNum(input, offset, 30, 3) or bitNum(input, offset, 22, 2) or bitNum(input, offset, 14, 1) or bitNum(input, offset, 6, 0)
    }

    private fun invIp(state: IntArray, output: ByteArray, offset: Int) {
        output[offset + 3] = (bitNumIntr(state[1], 7, 7).toInt() or bitNumIntr(state[0], 7, 6).toInt() or bitNumIntr(state[1], 15, 5).toInt() or bitNumIntr(state[0], 15, 4).toInt() or bitNumIntr(state[1], 23, 3).toInt() or bitNumIntr(state[0], 23, 2).toInt() or bitNumIntr(state[1], 31, 1).toInt() or bitNumIntr(state[0], 31, 0).toInt()).toByte()
        output[offset + 2] = (bitNumIntr(state[1], 6, 7).toInt() or bitNumIntr(state[0], 6, 6).toInt() or bitNumIntr(state[1], 14, 5).toInt() or bitNumIntr(state[0], 14, 4).toInt() or bitNumIntr(state[1], 22, 3).toInt() or bitNumIntr(state[0], 22, 2).toInt() or bitNumIntr(state[1], 30, 1).toInt() or bitNumIntr(state[0], 30, 0).toInt()).toByte()
        output[offset + 1] = (bitNumIntr(state[1], 5, 7).toInt() or bitNumIntr(state[0], 5, 6).toInt() or bitNumIntr(state[1], 13, 5).toInt() or bitNumIntr(state[0], 13, 4).toInt() or bitNumIntr(state[1], 21, 3).toInt() or bitNumIntr(state[0], 21, 2).toInt() or bitNumIntr(state[1], 29, 1).toInt() or bitNumIntr(state[0], 29, 0).toInt()).toByte()
        output[offset] = (bitNumIntr(state[1], 4, 7).toInt() or bitNumIntr(state[0], 4, 6).toInt() or bitNumIntr(state[1], 12, 5).toInt() or bitNumIntr(state[0], 12, 4).toInt() or bitNumIntr(state[1], 20, 3).toInt() or bitNumIntr(state[0], 20, 2).toInt() or bitNumIntr(state[1], 28, 1).toInt() or bitNumIntr(state[0], 28, 0).toInt()).toByte()
        output[offset + 7] = (bitNumIntr(state[1], 3, 7).toInt() or bitNumIntr(state[0], 3, 6).toInt() or bitNumIntr(state[1], 11, 5).toInt() or bitNumIntr(state[0], 11, 4).toInt() or bitNumIntr(state[1], 19, 3).toInt() or bitNumIntr(state[0], 19, 2).toInt() or bitNumIntr(state[1], 27, 1).toInt() or bitNumIntr(state[0], 27, 0).toInt()).toByte()
        output[offset + 6] = (bitNumIntr(state[1], 2, 7).toInt() or bitNumIntr(state[0], 2, 6).toInt() or bitNumIntr(state[1], 10, 5).toInt() or bitNumIntr(state[0], 10, 4).toInt() or bitNumIntr(state[1], 18, 3).toInt() or bitNumIntr(state[0], 18, 2).toInt() or bitNumIntr(state[1], 26, 1).toInt() or bitNumIntr(state[0], 26, 0).toInt()).toByte()
        output[offset + 5] = (bitNumIntr(state[1], 1, 7).toInt() or bitNumIntr(state[0], 1, 6).toInt() or bitNumIntr(state[1], 9, 5).toInt() or bitNumIntr(state[0], 9, 4).toInt() or bitNumIntr(state[1], 17, 3).toInt() or bitNumIntr(state[0], 17, 2).toInt() or bitNumIntr(state[1], 25, 1).toInt() or bitNumIntr(state[0], 25, 0).toInt()).toByte()
        output[offset + 4] = (bitNumIntr(state[1], 0, 7).toInt() or bitNumIntr(state[0], 0, 6).toInt() or bitNumIntr(state[1], 8, 5).toInt() or bitNumIntr(state[0], 8, 4).toInt() or bitNumIntr(state[1], 16, 3).toInt() or bitNumIntr(state[0], 16, 2).toInt() or bitNumIntr(state[1], 24, 1).toInt() or bitNumIntr(state[0], 24, 0).toInt()).toByte()
    }

    private fun f(state: Int, key: ByteArray): Int {
        val largeState = ByteArray(6)
        val t1 = bitNumIntL(state, 31, 0) or ((state and 0xf0000000.toInt()) ushr 1) or bitNumIntL(state, 4, 5) or bitNumIntL(state, 3, 6) or ((state and 0x0f000000) ushr 3) or bitNumIntL(state, 8, 11) or bitNumIntL(state, 7, 12) or ((state and 0x00f00000) ushr 5) or bitNumIntL(state, 12, 17) or bitNumIntL(state, 11, 18) or ((state and 0x000f0000) ushr 7) or bitNumIntL(state, 16, 23)
        val t2 = bitNumIntL(state, 15, 0) or ((state and 0x0000f000) shl 15) or bitNumIntL(state, 20, 5) or bitNumIntL(state, 19, 6) or ((state and 0x00000f00) shl 13) or bitNumIntL(state, 24, 11) or bitNumIntL(state, 23, 12) or ((state and 0x000000f0) shl 11) or bitNumIntL(state, 28, 17) or bitNumIntL(state, 27, 18) or ((state and 0x0000000f) shl 9) or bitNumIntL(state, 0, 23)

        largeState[0] = ((t1 ushr 24) and 0xff).toByte()
        largeState[1] = ((t1 ushr 16) and 0xff).toByte()
        largeState[2] = ((t1 ushr 8) and 0xff).toByte()
        largeState[3] = ((t2 ushr 24) and 0xff).toByte()
        largeState[4] = ((t2 ushr 16) and 0xff).toByte()
        largeState[5] = ((t2 ushr 8) and 0xff).toByte()

        for (i in 0 until 6) {
            largeState[i] = (largeState[i].toInt() xor key[i].toInt()).toByte()
        }

        var result = (sbox1[sboxBit((largeState[0].toInt() ushr 2).toByte())] shl 28) or (sbox2[sboxBit((((largeState[0].toInt() and 0x03) shl 4) or ((largeState[1].toInt() and 0xff) ushr 4)).toByte())] shl 24) or (sbox3[sboxBit((((largeState[1].toInt() and 0x0f) shl 2) or ((largeState[2].toInt() and 0xff) ushr 6)).toByte())] shl 20) or (sbox4[sboxBit((largeState[2].toInt() and 0x3f).toByte())] shl 16) or (sbox5[sboxBit((largeState[3].toInt() ushr 2).toByte())] shl 12) or (sbox6[sboxBit((((largeState[3].toInt() and 0x03) shl 4) or ((largeState[4].toInt() and 0xff) ushr 4)).toByte())] shl 8) or (sbox7[sboxBit((((largeState[4].toInt() and 0x0f) shl 2) or ((largeState[5].toInt() and 0xff) ushr 6)).toByte())] shl 4) or sbox8[sboxBit((largeState[5].toInt() and 0x3f).toByte())]

        result = bitNumIntL(result, 15, 0) or bitNumIntL(result, 6, 1) or bitNumIntL(result, 19, 2) or bitNumIntL(result, 20, 3) or bitNumIntL(result, 28, 4) or bitNumIntL(result, 11, 5) or bitNumIntL(result, 27, 6) or bitNumIntL(result, 16, 7) or bitNumIntL(result, 0, 8) or bitNumIntL(result, 14, 9) or bitNumIntL(result, 22, 10) or bitNumIntL(result, 25, 11) or bitNumIntL(result, 4, 12) or bitNumIntL(result, 17, 13) or bitNumIntL(result, 30, 14) or bitNumIntL(result, 9, 15) or bitNumIntL(result, 1, 16) or bitNumIntL(result, 7, 17) or bitNumIntL(result, 23, 18) or bitNumIntL(result, 13, 19) or bitNumIntL(result, 31, 20) or bitNumIntL(result, 26, 21) or bitNumIntL(result, 2, 22) or bitNumIntL(result, 8, 23) or bitNumIntL(result, 18, 24) or bitNumIntL(result, 12, 25) or bitNumIntL(result, 29, 26) or bitNumIntL(result, 5, 27) or bitNumIntL(result, 21, 28) or bitNumIntL(result, 10, 29) or bitNumIntL(result, 3, 30) or bitNumIntL(result, 24, 31)
        return result
    }

    private fun bitNum(a: ByteArray, offset: Int, b: Int, c: Int): Int {
        val index = offset + (b / 32 * 4 + 3 - (b % 32 / 8))
        return (((a[index].toInt() and 0xff) ushr (7 - (b % 8))) and 0x01) shl c
    }

    private fun bitNumIntr(a: Int, b: Int, c: Int): Byte = (((a ushr (31 - b)) and 0x01) shl c).toByte()

    private fun bitNumIntL(a: Int, b: Int, c: Int): Int = ((a shl b) and Int.MIN_VALUE) ushr c

    private fun sboxBit(a: Byte): Int {
        val value = a.toInt() and 0xff
        return (value and 0x20) or ((value and 0x1f) ushr 1) or ((value and 0x01) shl 4)
    }

    private fun hexToBytes(value: String): ByteArray {
        require(value.length % 2 == 0) { "Invalid QQ QRC hex payload length." }
        return ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private val sbox1 = intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7, 0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8, 4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0, 15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13)
    private val sbox2 = intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10, 3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5, 0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15, 13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9)
    private val sbox3 = intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8, 13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1, 13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7, 1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12)
    private val sbox4 = intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15, 13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9, 10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4, 3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14)
    private val sbox5 = intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9, 14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6, 4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14, 11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3)
    private val sbox6 = intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11, 10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8, 9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6, 4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13)
    private val sbox7 = intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1, 13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6, 1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2, 6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12)
    private val sbox8 = intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7, 1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2, 7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8, 2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11)
}
