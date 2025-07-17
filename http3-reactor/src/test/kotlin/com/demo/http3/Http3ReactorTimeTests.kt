package com.demo.http3

import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.data.StringData.randomText
import com.demo.http3.ReactorUtils.CLIENT_SSL_CONTEXT
import com.demo.http3.ReactorUtils.SERVER_SSL_CONTEXT
import com.demo.http3.ReactorUtils.use
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import com.demo.test.measureTime
import io.netty.buffer.Unpooled
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
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import reactor.netty.http.server.HttpServer
import java.time.Duration
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Http3ReactorTimeTests {
    private fun startServer(udpPort: Int): DisposableServer {
        return HttpServer.create()
            .port(udpPort)
            .protocol(HttpProtocol.HTTP3)
            .secure({ it.sslContext(SERVER_SSL_CONTEXT) })
            .http3Settings({ spec ->
                spec.idleTimeout(Duration.ofSeconds(5))
                    .maxData(10_000_000)
                    .maxStreamDataBidirectionalLocal(10_000_000)
                    .maxStreamDataBidirectionalRemote(10_000_000)
                    .maxStreamsBidirectional(100)
            })
            .route { routes ->
                routes.post("/") { request, response ->
                    response.sendString(
                        request.receive().aggregate()
                            .map {
                                val dataUtf8 = it.toString(Charsets.UTF_8)
//                                ServerLogger.log(dataUtf8)
                                dataUtf8.asResponse()
                            }
                    )
                }
            }
            .bindNow()
    }

    private fun createClient(): HttpClient = HttpClient.create()
        .protocol(HttpProtocol.HTTP3)
        .secure {
            it.sslContext(CLIENT_SSL_CONTEXT)
        }
        .http3Settings({ spec ->
            spec.idleTimeout(Duration.ofSeconds(5))
                .maxData(10_000_000)
                .maxStreamDataBidirectionalLocal(10_000_000)
        })

    @Test
    @Order(2)
    fun onePacketTest() = startServer(PORT).use {
        val client = createClient()

        measureTime(MEASUREMENTS) {
            repeat(REPEATS) {
                client.post()
                    .uri("https://$HOST:$PORT/")
                    .send(ByteBufFlux.fromString(Mono.just("abc")))
                    .responseSingle({ res, bytes ->
                        bytes.asString()
                            .doOnNext {
                                assertEquals("ABC_ABC_ABC", it)
                            }
                    })
                    .block()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(4)
    fun bigDataTest(count: Int) = startServer(PORT).use {
        val client = createClient()

        measureTime(MEASUREMENTS) {
            repeat(REPEATS) {
                val randomText = randomText(count)
                client.post()
                    .uri("https://$HOST:$PORT/")
                    .send(Mono.just(Unpooled.wrappedBuffer(randomText.toByteArray(Charsets.UTF_8))))
                    .responseContent()
                    .aggregate()
                    .doOnNext {
                        assertEquals(randomText, it.toString(Charsets.UTF_8))
                    }
                    .block()
            }
        }
    }
}
