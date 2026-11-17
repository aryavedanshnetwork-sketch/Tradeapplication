package com.example.growwlock

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Standard 6-digit TOTP (RFC 6238, SHA-1, 30 seconds). Same as pyotp.TOTP(secret).now(). */
object Totp {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32Decode(input: String): ByteArray {
        val clean = input.trim().replace(" ", "").replace("=", "").uppercase()
        var buffer = 0
        var bits = 0
        val out = ByteArrayOutputStream()
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "API secret has a character that is not valid base32" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun code(secret: String, nowMs: Long = System.currentTimeMillis()): String {
        val key = base32Decode(secret)
        val counter = nowMs / 1000L / 30L
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val h = mac.doFinal(msg)
        val off = h[h.size - 1].toInt() and 0x0F
        val bin = ((h[off].toInt() and 0x7F) shl 24) or
            ((h[off + 1].toInt() and 0xFF) shl 16) or
            ((h[off + 2].toInt() and 0xFF) shl 8) or
            (h[off + 3].toInt() and 0xFF)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }
}
