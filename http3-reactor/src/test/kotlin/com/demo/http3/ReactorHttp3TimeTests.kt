package com.demo.http3

import com.demo.constants.PASSWORD
import com.demo.constants.PORT
import com.demo.constants.SERVER_KEY_MANAGER_FACTORY
import com.demo.constants.TRUST_MANAGER_FACTORY
import com.demo.data.StringData.asResponse
import com.demo.data.StringData.randomText
import com.demo.http3.ReactorUtils.use
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import io.netty.buffer.Unpooled
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Mono
import reactor.netty.ByteBufFlux
import reactor.netty.DisposableServer
import reactor.netty.http.Http3SslContextSpec
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import reactor.netty.http.server.HttpServer
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.time.measureTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ReactorHttp3TimeTests {
    private fun startServer(udpPort: Int): DisposableServer {
        val sslContext = Http3SslContextSpec.forServer(SERVER_KEY_MANAGER_FACTORY, PASSWORD).configure {
            it.trustManager(TRUST_MANAGER_FACTORY)
        }

        return HttpServer.create()
            .port(PORT)
            .protocol(HttpProtocol.HTTP3)
            .secure({ spec -> spec.sslContext(sslContext) })
            .http3Settings({ spec ->
                spec.idleTimeout(Duration.ofSeconds(5))
                    .maxData(10_000_000)
                    .maxStreamDataBidirectionalLocal(1_000_000)
                    .maxStreamDataBidirectionalRemote(1_000_000)
                    .maxStreamsBidirectional(100)
            })
            .route { routes ->
                routes.post("/") { request, response ->
                    response.send(
                        request.receive().retain().aggregate()
                            .map {
                                if (it.capacity() > 10)
                                    it
                                else
                                    Unpooled.wrappedBuffer(it.toString(Charsets.UTF_8).asResponse().encodeToByteArray())
                            }
                    )
                }
            }
            .bindNow()
    }

    private fun createClient(): HttpClient = HttpClient.create()
        .protocol(HttpProtocol.HTTP3)
        .secure {
            val sslContext = Http3SslContextSpec.forClient()
                .configure { it.trustManager(InsecureTrustManagerFactory.INSTANCE) }
            it.sslContext(sslContext)
        }
        .http3Settings({ spec ->
            spec.idleTimeout(Duration.ofSeconds(5))
                .maxData(10_000_000)
                .maxStreamDataBidirectionalLocal(1_000_000)
        })

    @Test
    @Order(1)
    fun onePacketTest() = startServer(PORT).use {
        val client = createClient()

        repeat(MEASUREMENTS) {
            measureTime {
                repeat(REPEATS) {
                    client.post()
                        .uri("https://127.0.0.1:$PORT/")
                        .send(ByteBufFlux.fromString(Mono.just("abc")))
                        .responseSingle({ res, bytes ->
                            bytes.asString()
                                .doOnNext {
                                    assertEquals("ABC_ABC_ABC", it)
                                }
                        })
                        .block()
                }
            }.also { println("timeTaken: $it") }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(4)
    fun bigDataTest(count: Int) = startServer(PORT).use {
        val client = createClient()

        repeat(MEASUREMENTS) {
            measureTime {
                repeat(REPEATS) {
                    val randomText = randomText(count)
                    client.post()
                        .uri("https://127.0.0.1:$PORT/")
                        .send(Mono.just(Unpooled.wrappedBuffer(randomText.toByteArray(Charsets.UTF_8))))
                        .responseContent()
                        .aggregate()
                        .doOnNext {
                            assertEquals(randomText, it.toString(Charsets.UTF_8))
                        }
                        .block()
                }
            }.also { println("timeTaken: $it") }
        }
    }
}
