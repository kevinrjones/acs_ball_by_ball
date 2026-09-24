package com.knowledgespike.ballbyball.web.adapter.out.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object HttpClientFactory {
    private val logger = LoggerFactory.getLogger(HttpClientFactory::class.java)

    const val DEFAULT_REQUEST_TIMEOUT_MILLIS: Long = 60_000L
    const val DEFAULT_CONNECT_TIMEOUT_MILLIS: Long = 30_000L
    const val DEFAULT_SOCKET_TIMEOUT_MILLIS: Long = 60_000L

    fun create(
        requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
        connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS,
        sslTrustAll: Boolean = false,
        sslCertificatePath: String? = null
    ): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            this.requestTimeoutMillis = requestTimeoutMillis
            this.connectTimeoutMillis = connectTimeoutMillis
            this.socketTimeoutMillis = socketTimeoutMillis
        }
        install(ContentNegotiation) { json() }

        when {
            sslTrustAll -> {
                logger.warn("Creating HttpClient with sslTrustAll=true")
                engine {
                    https {
                        trustManager = object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        }
                    }
                }
            }
            !sslCertificatePath.isNullOrBlank() -> {
                logger.info("Creating HttpClient with sslCertificatePath={}", sslCertificatePath)
                val trustManager = combinedTrustManager(sslCertificatePath)
                if (trustManager != null) {
                    engine {
                        https {
                            this.trustManager = trustManager
                        }
                    }
                }
            }
        }
    }

    fun fromConfig(config: ApplicationConfig): HttpClient {
        val requestTimeout = config.propertyOrNull("httpClient.requestTimeoutMillis")?.getString()?.toLongOrNull()
            ?: DEFAULT_REQUEST_TIMEOUT_MILLIS
        val connectTimeout = config.propertyOrNull("httpClient.connectTimeoutMillis")?.getString()?.toLongOrNull()
            ?: DEFAULT_CONNECT_TIMEOUT_MILLIS
        val socketTimeout = config.propertyOrNull("httpClient.socketTimeoutMillis")?.getString()?.toLongOrNull()
            ?: DEFAULT_SOCKET_TIMEOUT_MILLIS
        val isDevelopment = config.propertyOrNull("ktor.development")?.getString() == "true"
        val sslTrustAll = isDevelopment && (
            config.propertyOrNull("kbff.oidc.sslTrustAll")?.getString()?.toBoolean() == true
            )
        val sslCertificatePath = config.propertyOrNull("kbff.oidc.sslCertificatePath")?.getString()
            ?: config.propertyOrNull("OIDC_CERT_PATH")?.getString()
        return create(
            requestTimeoutMillis = requestTimeout,
            connectTimeoutMillis = connectTimeout,
            socketTimeoutMillis = socketTimeout,
            sslTrustAll = sslTrustAll,
            sslCertificatePath = sslCertificatePath
        )
    }

    private fun combinedTrustManager(certPath: String): X509TrustManager? {
        return try {
            val defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(null as KeyStore?)
            }
            val defaultTm = defaultTmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: return null

            val cf = CertificateFactory.getInstance("X.509")
            val cert = FileInputStream(certPath).use { cf.generateCertificate(it) as X509Certificate }
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("custom-cert", cert)
            }
            val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
            val customTm = customTmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: return defaultTm

            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    try {
                        customTm.checkClientTrusted(chain, authType)
                    } catch (_: Exception) {
                        defaultTm.checkClientTrusted(chain, authType)
                    }
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    try {
                        customTm.checkServerTrusted(chain, authType)
                    } catch (_: Exception) {
                        defaultTm.checkServerTrusted(chain, authType)
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    defaultTm.acceptedIssuers + customTm.acceptedIssuers
            }
        } catch (e: Exception) {
            logger.error("Failed to load custom certificate from $certPath, falling back to default trust manager", e)
            null
        }
    }
}
