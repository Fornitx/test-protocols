package com.demo.quic

import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.resourceScope
import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.data.StringData.randomText
import com.demo.logging.ServerLogger
import com.demo.quic.ReactorUtils.CLIENT_SSL_CONTEXT
import com.demo.quic.ReactorUtils.SERVER_SSL_CONTEXT
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import com.demo.test.StopWatchKt
import com.demo.test.printStats
import io.netty.handler.codec.quic.InsecureQuicTokenHandler
import io.netty.handler.codec.quic.QuicStreamType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Mono
import reactor.netty.ByteBufMono
import reactor.netty.Connection
import reactor.netty.quic.QuicClient
import reactor.netty.quic.QuicConnection
import reactor.netty.quic.QuicServer
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CyclicBarrier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class QuicReactorTimeTests {
    private suspend fun ResourceScope.quicServer(udpPort: Int): Connection = install(
        acquire = {
            QuicServer.create()
                .host(HOST)
                .port(udpPort)
                .secure(SERVER_SSL_CONTEXT)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .idleTimeout(Duration.ofSeconds(5))
                .initialSettings { spec ->
                    spec.maxData(10_000_000)
                        .maxStreamDataBidirectionalLocal(10_000_000)
                        .maxStreamDataBidirectionalRemote(10_000_000)
                        .maxStreamDataUnidirectional(10_000_000)
                        .maxStreamsBidirectional(100)
                        .maxStreamsUnidirectional(100)
                }
                .handleStream { `in`, out ->
                    out.sendString(
                        `in`.receive().asString(Charsets.UTF_8).map { str ->
                            ServerLogger.log(str)
                            str.asResponse()
                        }
                    )
                }
                .bindNow()
        },
        release = { connection, _ ->
            connection.disposeNow()
            connection.onDispose().block()
        },
    )

    private suspend fun ResourceScope.quicClient(udpPort: Int): QuicConnection = install(
        acquire = { createClient(udpPort).connectNow() },
        release = { connection, _ ->
            connection.disposeNow()
            connection.onDispose().block()
        }
    )

    private fun createClient(udpPort: Int): QuicClient = QuicClient.create()
        .bindAddress { InetSocketAddress(0) }
        .remoteAddress { InetSocketAddress(HOST, udpPort) }
        .secure(CLIENT_SSL_CONTEXT)
        .idleTimeout(Duration.ofSeconds(5))
        .initialSettings { spec ->
            spec.maxData(10_000_000)
                .maxStreamDataBidirectionalLocal(10_000_000)
                .maxStreamDataBidirectionalRemote(10_000_000)
                .maxStreamDataUnidirectional(10_000_000)
                .maxStreamsBidirectional(100)
                .maxStreamsUnidirectional(100)
        }

    @Test
    @Order(1)
    fun connectionOnlyTest() = runResourceTest {
        val settings = createClient(PORT)
        val stopWatch = StopWatchKt.createUnstarted()
        val measurements = mutableListOf<kotlin.time.Duration>()
        repeat(MEASUREMENTS) {
            stopWatch.reset()
            repeat(REPEATS) {
                val connection = stopWatch.record { settings.connectNow() }
                connection.disposeNow()
                connection.onDispose().block()
            }
            stopWatch.durationKt.also(measurements::add).also { println("timeTaken: $it") }
        }
        measurements.printStats()
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(2)
    fun onePacketTest(count: Int) = runResourceTest {
        val connection = quicClient(PORT)

        val barrier = CyclicBarrier(2)

        val stopWatch = StopWatchKt.createUnstarted()
        val measurements = mutableListOf<kotlin.time.Duration>()
        repeat(MEASUREMENTS) {
            stopWatch.reset()
            repeat(REPEATS) {
                barrier.reset()
                val randomText = randomText(count)
                connection.createStream(QuicStreamType.BIDIRECTIONAL) { `in`, out ->
                    Mono.`when`(
                        out.send(ByteBufMono.fromString(Mono.just(randomText))),
                                `in`.receive().asString(Charsets.UTF_8).doOnNext { barrier.await() },
                    )
                }.subscribe()
                barrier.await()
                connection.disposeNow()
                connection.onDispose().block()
            }
            stopWatch.durationKt.also(measurements::add).also { println("timeTaken: $it") }
        }
        measurements.printStats()
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(3)
    fun streamingTest(count: Int) = runResourceTest {

    }

    private fun runResourceTest(block: suspend ResourceScope.() -> Unit) = runTest {
        resourceScope {
            quicServer(PORT)
            block()
        }
    }
}
