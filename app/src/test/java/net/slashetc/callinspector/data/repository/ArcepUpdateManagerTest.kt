package net.slashetc.callinspector.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class ArcepUpdateManagerTest {

    @Test
    fun `sanitizeToken returns null for null or empty input`() {
        assertNull(ArcepUpdateManager.sanitizeToken(null, 20))
        assertNull(ArcepUpdateManager.sanitizeToken("", 20))
        assertNull(ArcepUpdateManager.sanitizeToken("   ", 20))
        assertNull(ArcepUpdateManager.sanitizeToken("\"\"", 20))
    }

    @Test
    fun `sanitizeToken trims whitespace, quotes, and removes control characters`() {
        val raw = "  \"ORANG\u0000\u0007\"  "
        val sanitized = ArcepUpdateManager.sanitizeToken(raw, 20)
        assertEquals("ORANG", sanitized)
    }

    @Test
    fun `sanitizeToken truncates string when length exceeds max limit`() {
        val longString = "A".repeat(50)
        val sanitized = ArcepUpdateManager.sanitizeToken(longString, 10)
        assertEquals("A".repeat(10), sanitized)
    }

    @Test
    fun `sanitizeNonNullableToken returns fallback string when token is null or empty`() {
        val result = ArcepUpdateManager.sanitizeNonNullableToken(null, 10, defaultIfEmpty = "DEFAULT")
        assertEquals("DEFAULT", result)

        val resultEmpty = ArcepUpdateManager.sanitizeNonNullableToken("   ", 10, defaultIfEmpty = "")
        assertEquals("", resultEmpty)
    }

    @Test
    fun `sanitizeNonNullableToken cleans and truncates valid token`() {
        val raw = " \"OPERATOR_NAME_VERY_LONG_1234567890\" "
        val result = ArcepUpdateManager.sanitizeNonNullableToken(raw, 15)
        assertEquals("OPERATOR_NAME_V", result)
    }

    @Test
    fun `hasTlsPinningFailure detects network security config pin failure`() {
        val handshake = SSLHandshakeException("handshake failed").apply {
            initCause(CertificateException("Pin verification failed"))
        }
        assertTrue(IOException("download failed", handshake).hasTlsPinningFailure())
    }

    @Test
    fun `hasTlsPinningFailure detects OkHttp certificate pinner failure`() {
        assertTrue(SSLPeerUnverifiedException("Certificate pinning failure!").hasTlsPinningFailure())
    }

    @Test
    fun `hasTlsPinningFailure ignores other TLS and network errors`() {
        val untrusted = SSLHandshakeException("handshake failed").apply {
            initCause(CertificateException("Trust anchor for certification path not found."))
        }
        assertFalse(untrusted.hasTlsPinningFailure())
        assertFalse(IOException("timeout").hasTlsPinningFailure())
    }

    @Test
    fun `readLimited returns the full content when under the limit`() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        assertArrayEquals(data, ArcepUpdateManager.readLimited(ByteArrayInputStream(data), data.size))
    }

    @Test(expected = IOException::class)
    fun `readLimited rejects content larger than the limit`() {
        ArcepUpdateManager.readLimited(ByteArrayInputStream(ByteArray(1_001)), 1_000)
    }

    @Test
    fun `isPlausibleArcepData rejects empty or truncated exports`() {
        assertTrue(ArcepUpdateManager.isPlausibleArcepData(20_655, 1_592))
        assertFalse(ArcepUpdateManager.isPlausibleArcepData(0, 0))
        assertFalse(ArcepUpdateManager.isPlausibleArcepData(20_655, 0))
        assertFalse(ArcepUpdateManager.isPlausibleArcepData(9_999, 1_592))
    }
}
