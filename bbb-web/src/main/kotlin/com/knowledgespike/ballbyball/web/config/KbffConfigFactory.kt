package com.knowledgespike.ballbyball.web.config

import com.knowledgespike.feature.kbff.domain.model.KbffConfiguration
import io.ktor.server.config.ApplicationConfig

object KbffConfigFactory {
    const val DEFAULT_REGISTRATION_URL = "https://ids.local:8443/identity/account/register"
    fun buildCsp(isDevelopment: Boolean): String {
        val defaultSrc = "default-src 'self'"
        val scriptSrc = "script-src 'self' 'unsafe-inline'"
        val scriptSrcElem = "script-src-elem * data: blob: 'unsafe-inline' 'unsafe-eval'"
        val styleSrc = "style-src 'self' 'unsafe-inline' fonts.googleapis.com https://fonts.googleapis.com"
        val fontSrc = "font-src 'self' data: fonts.gstatic.com https://fonts.gstatic.com"
        val imgSrc = "img-src 'self' data: https:"
        var connectSrc = "connect-src 'self'"
        val objectSrc = "object-src 'none'"
        val frameAncestors = "frame-ancestors 'none'"

        if (isDevelopment) {
            connectSrc = "connect-src 'self' ws: wss: http: https:"
        }

        val cspParts = listOf(
            defaultSrc,
            scriptSrc,
            scriptSrcElem,
            styleSrc,
            fontSrc,
            imgSrc,
            objectSrc,
            connectSrc,
            frameAncestors
        )
        return cspParts.joinToString("; ")
    }

    fun defaultConfiguration(isDevelopment: Boolean = true, apiBaseUrl: String = "http://localhost:8081"): KbffConfiguration {
        val cleanedBaseUrl = apiBaseUrl.trimEnd('/')
        return KbffConfiguration().apply {
            environment(isProduction = !isDevelopment)
            oidc {
                authority = "https://ids.local:8443"
                clientId = "bbweb"
                clientSecret = "secret"
                scopes = listOf("openid", "profile", "bb.api", "bb.api.read")
                redirectUri = "http://localhost:8080/signin-oidc"
                postLogoutRedirectUri = "http://localhost:8080/"
                sslTrustAll = false
            }
            proxy {
                endpoint("/api", "$cleanedBaseUrl/api")
            }
            security {
                csrfHeaderName = "X-CSRF"
                csp = buildCsp(isDevelopment)
            }
        }
    }

    fun resolveApiBaseUrl(config: ApplicationConfig): String =
        (config.propertyOrNull("api.baseUrl")?.getString()
            ?: config.propertyOrNull("kbff.api.baseUrl")?.getString()
            ?: "http://localhost:8081").trimEnd('/')

    fun fromConfig(config: ApplicationConfig, isDevelopment: Boolean = false): KbffConfiguration {
        val authority = requiredConfig(config, "kbff.oidc.authority", isDevelopment, "https://ids.local:8443")
        val clientId = requiredConfig(config, "kbff.oidc.clientId", isDevelopment, "bbweb")
        val clientSecret = requiredConfig(config, "kbff.oidc.clientSecret", isDevelopment, "secret")
        val scopes = config.propertyOrNull("kbff.oidc.scopes")?.getList()?.takeIf { it.isNotEmpty() }
            ?: if (isDevelopment) listOf("openid", "profile", "bb.api", "bb.api.read")
            else error("kbff.oidc.scopes must be configured")
        val redirectUri = requiredConfig(config, "kbff.oidc.redirectUri", isDevelopment, "http://localhost:8080/signin-oidc")
        val postLogoutRedirectUri = requiredConfig(config, "kbff.oidc.postLogoutRedirectUri", isDevelopment, "http://localhost:8080/")
        val sslCertificatePath = config.propertyOrNull("kbff.oidc.sslCertificatePath")?.getString()

        val apiBaseUrl = resolveApiBaseUrl(config)
        val csrfHeader = config.propertyOrNull("kbff.security.csrfHeaderName")?.getString() ?: "X-CSRF"

        return KbffConfiguration().apply {
            environment(isProduction = !isDevelopment)
            oidc {
                this.authority = authority
                this.clientId = clientId
                this.clientSecret = clientSecret
                this.scopes = scopes
                this.redirectUri = redirectUri
                this.postLogoutRedirectUri = postLogoutRedirectUri
                this.sslTrustAll = false
                this.sslCertificatePath = sslCertificatePath
            }
            proxy {
                endpoint("/api", "$apiBaseUrl/api")
            }
            security {
                csrfHeaderName = csrfHeader
                csp = buildCsp(isDevelopment)
            }
        }
    }

    private fun requiredConfig(
        config: ApplicationConfig,
        path: String,
        isDevelopment: Boolean,
        developmentDefault: String
    ): String = config.propertyOrNull(path)?.getString()?.takeIf(String::isNotBlank)
        ?: if (isDevelopment) developmentDefault else error("$path must be configured")
}

fun ApplicationConfig.resolveRegistrationUrl(): String =
    propertyOrNull("kbff.oidc.registrationUrl")?.getString()?.takeIf(String::isNotBlank)
        ?: KbffConfigFactory.DEFAULT_REGISTRATION_URL

val KbffConfiguration.apiBaseUrl: String
    get() = proxy.endpoints.firstOrNull { it.path == "/api" }?.targetUrl?.removeSuffix("/api")?.trimEnd('/')
        ?: "http://localhost:8081"
