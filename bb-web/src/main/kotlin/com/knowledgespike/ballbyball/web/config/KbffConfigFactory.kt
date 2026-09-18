package com.knowledgespike.ballbyball.web.config

import com.knowledgespike.feature.kbff.domain.model.KbffConfiguration
import io.ktor.server.config.ApplicationConfig

object KbffConfigFactory {
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

    fun defaultConfiguration(isDevelopment: Boolean = true): KbffConfiguration = KbffConfiguration().apply {
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
            endpoint("/api", "http://localhost:8081/api")
        }
        security {
            csrfHeaderName = "X-CSRF"
            csp = buildCsp(isDevelopment)
        }
    }

    fun fromConfig(config: ApplicationConfig, isDevelopment: Boolean = false): KbffConfiguration {
        val authority = config.propertyOrNull("kbff.oidc.authority")?.getString() ?: "https://ids.local:8443"
        val clientId = config.propertyOrNull("kbff.oidc.clientId")?.getString() ?: "bbweb"
        val clientSecret = config.propertyOrNull("kbff.oidc.clientSecret")?.getString() ?: "secret"
        val scopes = config.propertyOrNull("kbff.oidc.scopes")?.getList() ?: listOf("openid", "profile", "bb.api", "bb.api.read")
        val redirectUri = config.propertyOrNull("kbff.oidc.redirectUri")?.getString() ?: "http://localhost:8080/signin-oidc"
        val postLogoutRedirectUri = config.propertyOrNull("kbff.oidc.postLogoutRedirectUri")?.getString() ?: "http://localhost:8080/"
        val sslCertificatePath = config.propertyOrNull("kbff.oidc.sslCertificatePath")?.getString()

        val apiBaseUrl = config.propertyOrNull("api.baseUrl")?.getString()
            ?: config.propertyOrNull("kbff.api.baseUrl")?.getString()
            ?: "http://localhost:8081"

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
                endpoint("/api", "${apiBaseUrl.trimEnd('/')}/api")
            }
            security {
                csrfHeaderName = csrfHeader
                csp = buildCsp(isDevelopment)
            }
        }
    }
}
