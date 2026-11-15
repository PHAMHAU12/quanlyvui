package com.example.domain.model

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic non-PII QR payload protocol:
 * Payload format: "QRA1:<qrToken>.<hmacTag>"
 * - Contains ZERO student personal data (no name, no DOB, no class name, no phone).
 * - Verified against the class's secret key, studentId, qrToken, and qrVersion.
 */
object QrSecurity {
    private const val PREFIX = "QRA1:"
    private const val HMAC_ALGO = "HmacSHA256"
    private const val BASE64_URL_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val secureRandom = SecureRandom()

    fun generateRandomSecretHex(byteLength: Int = 24): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateRandomQrToken(): String {
        val bytes = ByteArray(14)
        secureRandom.nextBytes(bytes)
        return "tok_" + encodeBase64UrlNoPad(bytes)
    }

    fun buildQrPayload(
        classId: String,
        studentId: String,
        qrToken: String,
        qrVersion: Long,
        classQrSecret: String
    ): String {
        val tag = computeHmacTag(
            classQrSecret = classQrSecret,
            message = "$classId:$studentId:$qrToken:$qrVersion"
        )
        return "$PREFIX$qrToken.$tag"
    }

    sealed interface ParseResult {
        data class ValidFormat(val qrToken: String, val hmacTag: String) : ParseResult
        data object InvalidFormat : ParseResult
    }

    fun parsePayload(rawValue: String): ParseResult {
        val trimmed = rawValue.trim()
        if (!trimmed.startsWith(PREFIX)) return ParseResult.InvalidFormat
        val body = trimmed.removePrefix(PREFIX)
        val dotIdx = body.indexOf('.')
        if (dotIdx <= 0 || dotIdx >= body.length - 1) return ParseResult.InvalidFormat
        val token = body.substring(0, dotIdx)
        val tag = body.substring(dotIdx + 1)
        if (token.length < 16 || tag.length < 10) return ParseResult.InvalidFormat
        return ParseResult.ValidFormat(qrToken = token, hmacTag = tag)
    }

    fun verifySignature(
        parsed: ParseResult.ValidFormat,
        classroom: Classroom,
        student: Student
    ): Boolean {
        if (student.classId != classroom.id) return false
        if (student.qrToken != parsed.qrToken) return false
        if (!student.active) return false
        val expectedTag = computeHmacTag(
            classQrSecret = classroom.qrSecret,
            message = "${classroom.id}:${student.id}:${student.qrToken}:${student.qrVersion}"
        )
        return MessageDigest.isEqual(
            expectedTag.toByteArray(Charsets.UTF_8),
            parsed.hmacTag.toByteArray(Charsets.UTF_8)
        )
    }

    private fun computeHmacTag(classQrSecret: String, message: String): String {
        val mac = Mac.getInstance(HMAC_ALGO)
        val keySpec = SecretKeySpec(classQrSecret.toByteArray(Charsets.UTF_8), HMAC_ALGO)
        mac.init(keySpec)
        val raw = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return encodeBase64UrlNoPad(raw.copyOfRange(0, 10))
    }

    private fun encodeBase64UrlNoPad(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0
            val triplet = (b0 shl 16) or (b1 shl 8) or b2

            sb.append(BASE64_URL_ALPHABET[(triplet shr 18) and 0x3F])
            sb.append(BASE64_URL_ALPHABET[(triplet shr 12) and 0x3F])
            if (hasB1) sb.append(BASE64_URL_ALPHABET[(triplet shr 6) and 0x3F])
            if (hasB2) sb.append(BASE64_URL_ALPHABET[triplet and 0x3F])
            i += 3
        }
        return sb.toString()
    }
}

object QrCodeBitmapGenerator {
    fun generateBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
