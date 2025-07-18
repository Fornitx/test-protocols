package com.demo.http3

import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.resourceScope
import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.data.StringData.randomText
import com.demo.http3.ReactorUtils.CLIENT_SSL_CONTEXT
import com.demo.http3.ReactorUtils.SERVER_SSL_CONTEXT
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import com.demo.test.StopWatchKt
import com.demo.test.printStats
import io.netty.buffer.Unpooled
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import reactor.netty.DisposableServer
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import reactor.netty.http.server.HttpServer
import java.time.Duration
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Http3ReactorTimeTests {
    private val URI = "https://$HOST:$PORT/"

    private suspend fun ResourceScope.httpServer(udpPort: Int): DisposableServer = install(
        acquire = {
            HttpServer.create()
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
        },
        release = { disposableServer, _ ->
            disposableServer.disposeNow()
            disposableServer.onDispose().block()
        },
    )

    private fun createClient(): HttpClient = HttpClient.create()
        .protocol(HttpProtocol.HTTP3)
        .secure {
            it.sslContext(CLIENT_SSL_CONTEXT)
        }
        .http3Settings({ spec ->
            spec.idleTimeout(Duration.ofSeconds(5))
                .maxData(10_000_000)
                .maxStreamDataBidirectionalLocal(10_000_000)
                .maxStreamDataBidirectionalRemote(10_000_000)
                .maxStreamsBidirectional(100)
        })

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(2)
    fun onePacketTest(count: Int) = runResourceTest {
        val client = createClient()
        val stopWatch = StopWatchKt.createUnstarted()
        val measurements = mutableListOf<kotlin.time.Duration>()
        repeat(MEASUREMENTS) {
            stopWatch.reset()
            repeat(REPEATS) {
                val randomText = randomText(count)
                val byteBuf = Unpooled.wrappedBuffer(randomText.encodeToByteArray())
                stopWatch.start()
                client.post()
                    .uri(URI)
                    .send(Mono.just(byteBuf))
                    .responseSingle { response, byteBuf ->
                        stopWatch.stop()
                        byteBuf.asString(Charsets.UTF_8)
                            .doOnNext { assertEquals(randomText, it) }
                    }
                    .block()
            }
            stopWatch.durationKt.also(measurements::add).also { println("timeTaken: $it") }
        }
        measurements.printStats()
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(3)
    fun streamingTest(count: Int) = runResourceTest {
        val client = createClient()
        val stopWatch = StopWatchKt.createUnstarted()
        val measurements = mutableListOf<kotlin.time.Duration>()
        repeat(MEASUREMENTS) {
            stopWatch.reset()
            val data = Flux.range(0, REPEATS)
                .map { randomText(count) }
                .map { Unpooled.wrappedBuffer(it.encodeToByteArray()) }
                .map { Mono.just(it) }
            stopWatch.start()
            client.post()
                .uri(URI)
                .send { request, outbound -> outbound.sendGroups(data) }
                .response { response, byteBuf -> byteBuf.asString(Charsets.UTF_8) }
                .doOnNext { assertEquals(count, it.length) }
                .test()
                .expectNextCount(REPEATS.toLong())
                .verifyComplete()
            stopWatch.stop()
            stopWatch.durationKt.also(measurements::add).also { println("timeTaken: $it") }
        }
        measurements.printStats()
    }

    private fun runResourceTest(block: suspend ResourceScope.() -> Unit) = runTest {
        resourceScope {
            httpServer(PORT)
            block()
        }
    }
}
