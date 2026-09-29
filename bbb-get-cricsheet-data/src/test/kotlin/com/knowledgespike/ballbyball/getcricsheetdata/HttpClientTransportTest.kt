package com.knowledgespike.ballbyball.getcricsheetdata

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class HttpClientTransportTest {
    @Test
    fun `given transient server errors when requested then request is retried`() {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/page") { exchange ->
            val attempt = requests.incrementAndGet()
            val body = if (attempt < 3) "temporary failure" else "<html>ready</html>"
            exchange.sendResponseHeaders(if (attempt < 3) 503 else 200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val transport = JdkHttpClientTransport(
                maxAttempts = 3,
                retryDelay = { java.time.Duration.ZERO },
                sleeper = {}
            )
            val response = transport.getText(URI("http://localhost:${server.address.port}/page"))

            expectThat(response).isEqualTo("<html>ready</html>")
            assertEquals(3, requests.get())
        } finally {
            server.stop(0)
        }
    }
}