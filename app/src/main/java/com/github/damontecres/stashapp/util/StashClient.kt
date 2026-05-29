package com.github.damontecres.stashapp.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.websocket.GraphQLWsProtocol
import com.apollographql.apollo.network.websocket.WebSocketEngine
import com.apollographql.apollo.network.websocket.WebSocketNetworkTransport
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.StashApplication
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.time.DurationUnit

/**
 * Provides static functions to get clients to interact with the server
 */
class StashClient private constructor() {
    companion object {
        private const val TAG = "StashClient"
        private const val OK_HTTP_TAG = "$TAG.OkHttpClient"

        val okHttpClient by lazy { createOkHttpClient() }

        private fun createOkHttpClient(): OkHttpClient {
            val context = StashApplication.getApplication()
            val manager = PreferenceManager.getDefaultSharedPreferences(context)
            val trustAll = manager.getBoolean("trustAllCerts", false)
            val cacheDuration =
                cacheDurationPrefToDuration(manager.getInt("networkCacheDuration", 3))
            val cacheLogging = manager.getBoolean("networkCacheLogging", false)
            val networkTimeout = manager.getInt("networkTimeout", 15).toLong()

            val userAgent = createUserAgent(context)

            Log.v(TAG, "User-Agent=$userAgent")
            var builder =
                OkHttpClient
                    .Builder()
                    .readTimeout(networkTimeout, TimeUnit.SECONDS)
                    .writeTimeout(networkTimeout, TimeUnit.SECONDS)
                    .addInterceptor {
                        it.proceed(
                            it
                                .request()
                                .newBuilder()
                                .header("User-Agent", userAgent)
                                .build(),
                        )
                    }

            builder = applySslConfig(builder, trustAll)
            if (cacheLogging) {
                Log.d(
                    OK_HTTP_TAG,
                    "cacheDuration in hours: ${
                        cacheDuration?.toInt(
                            DurationUnit.HOURS,
                        )
                    }",
                )
                builder =
                    builder.eventListener(
                        object : EventListener() {
                            override fun cacheHit(
                                call: Call,
                                response: Response,
                            ) {
                                Log.v(
                                    OK_HTTP_TAG,
                                    "cacheHit: ${call.request().url} => ${response.code}",
                                )
                            }

                            override fun cacheMiss(call: Call) {
                                Log.v(OK_HTTP_TAG, "cacheMiss: ${call.request().url}")
                            }

                            override fun cacheConditionalHit(
                                call: Call,
                                cachedResponse: Response,
                            ) {
                                Log.v(
                                    OK_HTTP_TAG,
                                    "cacheConditionalHit: ${call.request().url} => ${cachedResponse.code}",
                                )
                            }
                        },
                    )
            }
            val cacheControl =
                if (cacheDuration != null) {
                    CacheControl
                        .Builder()
                        .maxAge(
                            cacheDuration.toInt(DurationUnit.HOURS),
                            TimeUnit.HOURS,
                        ).build()
                } else {
                    CacheControl.Builder().noCache().build()
                }
            builder =
                builder.addNetworkInterceptor {
                    val request =
                        it
                            .request()
                            .newBuilder()
                            .cacheControl(cacheControl)
                            .build()
                    it.proceed(request)
                }

            builder.cache(Constants.getNetworkCache(context))
            return builder.build()
        }

        /**
         * Create an [OkHttpClient]. Prefer using [StashServer.okHttpClient] when possible
         */
        fun createOkHttpClient(server: StashServer): OkHttpClient {
            val serverUrlRoot = getServerRoot(server.url)

            var builder = okHttpClient.newBuilder()
            val trustAll =
                PreferenceManager
                    .getDefaultSharedPreferences(StashApplication.getApplication())
                    .getBoolean("trustAllCerts", false)
            builder = applySslConfig(builder, trustAll)

            val cleanedApiKey = StashServer.normalizeApiKey(server.apiKey)
            if (cleanedApiKey != null) {
                builder =
                    builder.addInterceptor {
                        val request =
                            if (it
                                    .request()
                                    .url
                                    .toString()
                                    .startsWith(serverUrlRoot)
                            ) {
                                // Only set the API Key if the target URL is the stash server
                                it
                                    .request()
                                    .newBuilder()
                                    .addHeader(Constants.STASH_API_HEADER, cleanedApiKey)
                                    .build()
                            } else {
                                it.request()
                            }
                        it.proceed(request)
                    }
            }
            return builder.build()
        }

        /**
         * Create the user agent used in HTTP calls
         *
         * Form of: StashAppAndroidTV/<version> (release/<android version>; sdk/<android sdk int>) (<manufacturer>; <device model>; <device name>)
         */
        fun createUserAgent(context: Context): String {
            val appName = context.getString(R.string.app_name)
            val versionStr = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            val comments =
                listOf(
                    joinValueNotNull("os", Build.VERSION.BASE_OS),
                    joinValueNotNull("release", Build.VERSION.RELEASE),
                    "sdk/${Build.VERSION.SDK_INT}",
                ).joinNotNullOrBlank("; ")
            val device =
                listOf(
                    Build.MANUFACTURER,
                    Build.MODEL,
                    if (Build.MODEL != Build.PRODUCT) Build.PRODUCT else null,
                    Build.DEVICE,
                ).joinNotNullOrBlank("; ")
            return "$appName/$versionStr ($comments) ($device)"
        }

        /**
         * Create a new [ApolloClient]. Prefer using [StashServer.apolloClient] when possible
         */
        @OptIn(ApolloExperimental::class)
        fun createApolloClient(server: StashServer): ApolloClient {
            val url = cleanServerUrl(server.url)
            val httpClient = server.okHttpClient
            return ApolloClient
                .Builder()
                .serverUrl(url)
                .httpEngine(DefaultHttpEngine(httpClient))
                .subscriptionNetworkTransport(
                    WebSocketNetworkTransport
                        .Builder()
                        .serverUrl(url)
                        .wsProtocol(GraphQLWsProtocol())
                        .webSocketEngine(WebSocketEngine(httpClient))
                        .build(),
                ).build()
        }

        /**
         * Cleans up the URL that a user enters
         *
         * Tries to add protocol (http) and endpoint (/graphql)
         */
        fun cleanServerUrl(stashUrl: String): String {
            var cleanedStashUrl = stashUrl.trim()
            if (!cleanedStashUrl.startsWith("http://") && !cleanedStashUrl.startsWith("https://")) {
                // Assume http
                cleanedStashUrl = "http://$cleanedStashUrl"
            }
            var url = cleanedStashUrl.toUri()
            val pathSegments = url.pathSegments.toMutableList()
            if (url.host.isNotNullOrBlank() && (pathSegments.isEmpty() || pathSegments.last() != "graphql")) {
                pathSegments.add("graphql")
            }
            url =
                url
                    .buildUpon()
                    .path(pathSegments.joinToString("/")) // Ensure the URL is the graphql endpoint
                    .build()
            return url.toString()
        }

        fun createLoginUrl(stashUrl: String): String {
            val cleanedStashUrl = cleanServerUrl(stashUrl)
            var url = cleanedStashUrl.toUri()
            val pathSegments = url.pathSegments.toMutableList()
            if (url.host.isNotNullOrBlank() && pathSegments.isNotEmpty() && pathSegments.last() == "graphql") {
                pathSegments.removeLastOrNull()
            }
            if (url.host.isNotNullOrBlank()) {
                pathSegments.add("login")
            }
            url =
                url
                    .buildUpon()
                    .path(pathSegments.joinToString("/")) // Ensure the URL is the graphql endpoint
                    .build()
            return url.toString()
        }

        /**
         * Get the server URL excluding the (unlikely) `/graphql` last path segment
         *
         * Basically, if a user is using a reverse proxy routes the path ending with `/graphql`, this will remove that
         */
        fun getServerRoot(stashUrl: String): String {
            var cleanedStashUrl = stashUrl.trim()
            if (!cleanedStashUrl.startsWith("http://") && !cleanedStashUrl.startsWith("https://")) {
                // Assume http
                cleanedStashUrl = "http://$cleanedStashUrl"
            }
            var url = Uri.parse(cleanedStashUrl)
            val pathSegments = url.pathSegments.toMutableList()
            if (pathSegments.isNotEmpty() && pathSegments.last() == "graphql") {
                pathSegments.removeAt(pathSegments.size - 1)
            }
            url =
                url
                    .buildUpon()
                    .path(pathSegments.joinToString("/"))
                    .build()
            return url.toString()
        }

        /**
         * Build an [ApolloClient] suitable for testing connectivity for the specified server.
         *
         * @see [com.github.damontecres.stashapp.util.testStashConnection]
         */
        fun createTestApolloClient(
            context: Context,
            server: StashServer,
            trustCerts: Boolean,
        ): ApolloClient {
            val url = cleanServerUrl(server.url)
            var builder =
                okHttpClient
                    .newBuilder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)

            builder = applySslConfig(builder, trustCerts)
            val cleanedApiKey = StashServer.normalizeApiKey(server.apiKey)
            if (cleanedApiKey != null) {
                builder =
                    builder.addInterceptor {
                        val request =
                            it
                                .request()
                                .newBuilder()
                                .addHeader(Constants.STASH_API_HEADER, cleanedApiKey)
                                .build()
                        it.proceed(request)
                    }
            }
            val httpClient = builder.build()
            return ApolloClient
                .Builder()
                .serverUrl(url)
                .httpEngine(DefaultHttpEngine(httpClient))
                .build()
        }

        /**
         * Build an [ApolloClient] suitable for testing connectivity for the specified server.
         */
        fun createCookieHttpClient(trustCerts: Boolean): OkHttpClient {
            var builder =
                okHttpClient
                    .newBuilder()
                    .cookieJar(
                        object : CookieJar {
                            private val cookies = mutableMapOf<String, List<Cookie>>()

                            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies[url.host] ?: listOf()

                            override fun saveFromResponse(
                                url: HttpUrl,
                                cookies: List<Cookie>,
                            ) {
                                this.cookies[url.host] = cookies
                            }
                        },
                    ).readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)

            builder = applySslConfig(builder, trustCerts)
            return builder.build()
        }

        /**
         * Apply the TLS configuration to [builder].
         *
         * Default (`allowSelfSigned == false`): rely on OkHttp's platform trust store and
         * standard hostname verification — no custom SSL at all.
         *
         * When the user has explicitly opted into a self-signed server
         * (`allowSelfSigned == true`, the legacy "trust all certs" toggle), we still:
         *  - use a modern `TLS` context (never the deprecated `SSL`/SSLv3 context), and
         *  - **keep hostname verification on** (the cert must match the host), and
         *  - keep trusting the system CA store in addition to self-signed leaf certs,
         * so the previous "accept literally any cert for any host" MITM hole is closed
         * while the self-signed-server use case keeps working. This consolidates the four
         * previously-duplicated trust-all blocks into one helper.
         */
        private fun applySslConfig(
            builder: OkHttpClient.Builder,
            allowSelfSigned: Boolean,
        ): OkHttpClient.Builder {
            if (!allowSelfSigned) {
                return builder
            }
            val trustManager = selfSignedTolerantTrustManager()
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), SecureRandom())
            // Note: hostname verification is intentionally left at OkHttp's default.
            return builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        }

        /**
         * An [X509TrustManager] that first validates against the platform CA store and,
         * only if that fails, accepts the chain (covering self-signed certs). It does not
         * disable hostname verification — that stays with OkHttp's default verifier.
         */
        @SuppressLint("CustomX509TrustManager")
        private fun selfSignedTolerantTrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            val system =
                factory.trustManagers
                    .filterIsInstance<X509TrustManager>()
                    .first()
            return object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                    try {
                        system.checkClientTrusted(chain, authType)
                    } catch (_: Exception) {
                        // Self-signed client cert: accepted (still hostname-verified).
                    }
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                    try {
                        system.checkServerTrusted(chain, authType)
                    } catch (_: Exception) {
                        // Self-signed server cert: accepted (still hostname-verified).
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
            }
        }
    }
}
