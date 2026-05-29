package com.github.damontecres.stashapp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Unit tests for [CertPinning] — the Trust-On-First-Use per-certificate TLS pinning logic that
 * closes the residual MITM gap (the old self-signed fallback trusted any leaf for the host).
 *
 * These exercise the fingerprint compare and the trust decision directly with mocked certificates
 * and a mocked system trust manager, so no real TLS handshake is needed. (The full handshake +
 * persistence path is verified on-device.)
 */
class CertPinningTest {
    /** A mock leaf cert whose DER encoding is [der]. */
    private fun certWithDer(der: ByteArray): X509Certificate =
        mock<X509Certificate> {
            on { encoded } doReturn der
        }

    private fun sha256Pin(der: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        val hex = digest.joinToString(":") { "%02X".format(it) }
        return "sha256/$hex"
    }

    @Test
    fun fingerprint_isSha256OfDerEncoding_withSha256Prefix() {
        val der = byteArrayOf(1, 2, 3, 4, 5)
        val cert = certWithDer(der)
        assertTrue(CertPinning.fingerprint(cert).startsWith("sha256/"))
        // Matches an independently-computed SHA-256 of the same bytes.
        assertTrue(CertPinning.pinsMatch(CertPinning.fingerprint(cert), sha256Pin(der)))
    }

    @Test
    fun pinsMatch_ignoresPrefixWhitespaceAndCase() {
        val pin = "sha256/AB:CD:EF:01"
        assertTrue(CertPinning.pinsMatch(pin, "AB:CD:EF:01"))
        assertTrue(CertPinning.pinsMatch(pin, "ab:cd:ef:01"))
        assertTrue(CertPinning.pinsMatch(pin, " sha256/ab:cd:ef:01 "))
        // Colons are part of the canonical form (only whitespace is stripped), so a different
        // separator scheme does NOT accidentally match.
        assertFalse(CertPinning.pinsMatch(pin, "ABCDEF01"))
    }

    @Test
    fun pinsMatch_blankOrNullNeverMatches() {
        assertFalse(CertPinning.pinsMatch(null, null))
        assertFalse(CertPinning.pinsMatch("sha256/AB", null))
        assertFalse(CertPinning.pinsMatch(null, "sha256/AB"))
        assertFalse(CertPinning.pinsMatch("", ""))
        assertFalse(CertPinning.pinsMatch("   ", "sha256/AB"))
    }

    @Test
    fun pinsMatch_differentCertsDoNotMatch() {
        val a = CertPinning.fingerprint(certWithDer(byteArrayOf(1, 2, 3)))
        val b = CertPinning.fingerprint(certWithDer(byteArrayOf(9, 9, 9)))
        assertFalse(CertPinning.pinsMatch(a, b))
    }

    @Test
    fun certMatchesPin_trueOnlyForThePinnedCert() {
        val good = certWithDer(byteArrayOf(10, 20, 30))
        val evil = certWithDer(byteArrayOf(40, 50, 60))
        val pin = CertPinning.fingerprint(good)
        assertTrue(CertPinning.certMatchesPin(good, pin))
        assertFalse(CertPinning.certMatchesPin(evil, pin))
        assertFalse(CertPinning.certMatchesPin(good, null))
    }

    // --- Trust-decision logic ---

    /** System trust manager that accepts a chain iff its leaf DER equals [trustedDer]. */
    private fun systemTrustingOnly(trustedDer: ByteArray?): X509TrustManager =
        mock<X509TrustManager> {
            on { checkServerTrusted(any(), any()) } doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val chain = invocation.arguments[0] as Array<X509Certificate>
                if (trustedDer != null && chain.isNotEmpty() && chain[0].encoded.contentEquals(trustedDer)) {
                    null // trusted
                } else {
                    throw CertificateException("not chained to a system CA")
                }
            }
        }

    @Test
    fun trustManager_systemTrustedCert_isAcceptedWithoutNeedingAPin() {
        val systemDer = byteArrayOf(1, 1, 1)
        val system = systemTrustingOnly(systemDer)
        val tm = CertPinning.pinningTrustManager(system) { null }
        // System accepts it -> no exception, no pin consulted.
        tm.checkServerTrusted(arrayOf(certWithDer(systemDer)), "RSA")
    }

    @Test
    fun trustManager_pinnedSelfSignedCert_isAccepted() {
        val system = systemTrustingOnly(null) // system trusts nothing (pure self-signed server)
        val leafDer = byteArrayOf(7, 7, 7)
        val leaf = certWithDer(leafDer)
        val pin = CertPinning.fingerprint(leaf)
        val tm = CertPinning.pinningTrustManager(system) { pin }
        // Not system-trusted, but matches the pin -> accepted.
        tm.checkServerTrusted(arrayOf(leaf), "RSA")
    }

    @Test
    fun trustManager_unpinnedSelfSignedCert_isRejected() {
        val system = systemTrustingOnly(null)
        val leaf = certWithDer(byteArrayOf(7, 7, 7))
        val tm = CertPinning.pinningTrustManager(system) { null } // no pin stored
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(arrayOf(leaf), "RSA")
        }
    }

    @Test
    fun trustManager_swappedCert_isRejectedEvenWhenAPinExists() {
        val system = systemTrustingOnly(null)
        val pinnedLeaf = certWithDer(byteArrayOf(7, 7, 7))
        val pin = CertPinning.fingerprint(pinnedLeaf)
        // MITM presents a DIFFERENT self-signed cert for the same host.
        val swapped = certWithDer(byteArrayOf(8, 8, 8))
        val tm = CertPinning.pinningTrustManager(system) { pin }
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(arrayOf(swapped), "RSA")
        }
    }

    @Test
    fun trustManager_emptyChain_isRejected() {
        val system = systemTrustingOnly(null)
        val tm = CertPinning.pinningTrustManager(system) { "sha256/AB" }
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(emptyArray(), "RSA")
        }
    }

    @Test
    fun trustManager_clientAuth_isDelegatedToSystemOnly() {
        val client = certWithDer(byteArrayOf(3, 3, 3))
        val system =
            mock<X509TrustManager> {
                on { checkClientTrusted(any(), any()) } doThrow CertificateException("client not trusted")
            }
        val tm = CertPinning.pinningTrustManager(system) { CertPinning.fingerprint(client) }
        // Client certs are never pinned; a pin must NOT rescue an untrusted client cert.
        assertThrows(CertificateException::class.java) {
            tm.checkClientTrusted(arrayOf(client), "RSA")
        }
    }

    @Test
    fun trustManager_acceptedIssuers_comeFromSystem() {
        val issuers = arrayOf(certWithDer(byteArrayOf(5)))
        val system =
            mock<X509TrustManager> {
                on { acceptedIssuers } doReturn issuers
            }
        val tm = CertPinning.pinningTrustManager(system) { null }
        assertTrue(tm.acceptedIssuers.contentEquals(issuers))
    }
}
