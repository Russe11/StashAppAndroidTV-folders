package com.github.damontecres.stashapp.util

import android.annotation.SuppressLint
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.X509TrustManager

/**
 * Trust-On-First-Use (TOFU) per-certificate TLS pinning.
 *
 * Closes the residual MITM gap Stream E left: the consolidated `applySslConfig` self-signed
 * fallback trusted *any* self-signed leaf for the matching host. With pinning, a server whose
 * leaf does not chain to the system store is accepted only if its leaf SHA-256 fingerprint
 * matches the pin the user accepted on first connect — so a silent MITM cert swap is rejected.
 *
 * The fingerprint compare and the trust decision live here as pure functions so they can be
 * unit-tested directly (no Android framework, no real TLS handshake required).
 */
object CertPinning {
    /** Display/storage form of the SHA-256 fingerprint, e.g. `sha256/AB:CD:...`. */
    private const val PIN_PREFIX = "sha256/"

    /**
     * The SHA-256 fingerprint of a leaf certificate's DER encoding, as uppercase
     * colon-separated hex prefixed with [PIN_PREFIX] (e.g. `sha256/AB:CD:EF:...`).
     *
     * This is the value persisted as the per-server pin and shown to the user to accept.
     */
    fun fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val hex =
            digest.joinToString(":") { byte ->
                "%02X".format(byte)
            }
        return PIN_PREFIX + hex
    }

    /**
     * Whether two pin strings name the same certificate. Comparison is case-insensitive and
     * tolerant of an absent `sha256/` prefix and stray whitespace, so a hand-typed or
     * legacy-stored pin still matches. Blank/empty pins never match (no pin => reject).
     */
    fun pinsMatch(
        a: String?,
        b: String?,
    ): Boolean {
        val na = normalizePin(a) ?: return false
        val nb = normalizePin(b) ?: return false
        return na == nb
    }

    /**
     * Whether [cert]'s fingerprint matches the stored [pin]. False when there is no pin.
     */
    fun certMatchesPin(
        cert: X509Certificate,
        pin: String?,
    ): Boolean = pinsMatch(fingerprint(cert), pin)

    /**
     * Canonical comparison form: strip the `sha256/` prefix, drop whitespace, uppercase.
     * Returns null for a blank/absent pin (which must never match anything).
     */
    private fun normalizePin(pin: String?): String? {
        if (pin.isNullOrBlank()) return null
        val trimmed = pin.trim()
        val withoutPrefix =
            if (trimmed.startsWith(PIN_PREFIX, ignoreCase = true)) {
                trimmed.substring(PIN_PREFIX.length)
            } else {
                trimmed
            }
        val cleaned = withoutPrefix.replace(Regex("\\s"), "").uppercase(Locale.ROOT)
        return cleaned.ifBlank { null }
    }

    /**
     * Build an [X509TrustManager] that:
     *  1. trusts the system CA store FIRST (delegating to [system]); and
     *  2. only if that fails, accepts the chain iff the leaf cert's SHA-256 fingerprint matches
     *     the pin returned by [pinForHost] for the connection host — otherwise rejects.
     *
     * Hostname verification stays with OkHttp's default verifier (this manager does not touch it).
     *
     * [pinForHost] is invoked lazily per handshake so a freshly-accepted pin takes effect without
     * rebuilding the client; it receives nothing and returns the pin for the single server this
     * manager is built for (clients are per-server).
     */
    @SuppressLint("CustomX509TrustManager")
    fun pinningTrustManager(
        system: X509TrustManager,
        pinForHost: () -> String?,
    ): X509TrustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<X509Certificate>,
                authType: String,
            ) {
                // Client (mutual-TLS) certs: only the system store decides. We never pin these.
                system.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String,
            ) {
                try {
                    system.checkServerTrusted(chain, authType)
                    return
                } catch (systemFailure: CertificateException) {
                    // Not chained to a system CA — fall through to the pin check.
                    if (chain.isEmpty()) {
                        throw systemFailure
                    }
                    val leaf = chain[0]
                    val pin = pinForHost()
                    if (!certMatchesPin(leaf, pin)) {
                        throw CertificateException(
                            "Server certificate is not trusted by the system store and its " +
                                "fingerprint (${fingerprint(leaf)}) does not match the pinned " +
                                "certificate for this server. Possible MITM, or the certificate " +
                                "was rotated and needs to be re-pinned.",
                            systemFailure,
                        )
                    }
                    // Leaf matches the user-accepted pin: trust exactly this cert.
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
        }
}
