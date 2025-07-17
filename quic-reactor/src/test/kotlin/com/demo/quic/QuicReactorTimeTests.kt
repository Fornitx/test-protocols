package com.demo.quic

import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import com.demo.quic.ReactorUtils.CLIENT_SSL_CONTEXT
import com.demo.quic.ReactorUtils.SERVER_SSL_CONTEXT
import com.demo.quic.ReactorUtils.use
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import com.demo.test.measureTime
import io.netty.handler.codec.quic.InsecureQuicTokenHandler
import io.netty.handler.codec.quic.QuicStreamType
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Mono
import reactor.netty.ByteBufFlux
import reactor.netty.Connection
import reactor.netty.quic.QuicClient
import reactor.netty.quic.QuicServer
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class QuicReactorTimeTests {
    private fun startServer(udpPort: Int): Connection {
        return QuicServer.create()
            .host(HOST)
            .port(udpPort)
            .secure(SERVER_SSL_CONTEXT)
            .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            .idleTimeout(Duration.ofSeconds(5))
            .initialSettings { spec ->
                spec.maxData(10_000_000)
                    .maxStreamDataBidirectionalRemote(10_000_000)
                    .maxStreamsBidirectional(100)
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
    }

    @Test
    @Order(1)
    fun connectionOnlyTest() = startServer(PORT).use {
        val settings = QuicClient.create()
            .bindAddress { InetSocketAddress(0) }
            .remoteAddress { InetSocketAddress(HOST, PORT) }
            .secure(CLIENT_SSL_CONTEXT)
            .idleTimeout(Duration.ofSeconds(5))
            .initialSettings { spec ->
                spec.maxData(10_000_000)
                    .maxStreamDataBidirectionalLocal(10_000_000)
            }
        measureTime(MEASUREMENTS) {
            repeat(REPEATS) {
                val connection = settings.connectNow()
                connection.disposeNow()
            }
        }
    }

    @Test
    @Order(2)
    fun onePacketTest() = startServer(PORT).use {
        val settings = QuicClient.create()
            .bindAddress { InetSocketAddress(0) }
            .remoteAddress { InetSocketAddress(HOST, PORT) }
            .secure(CLIENT_SSL_CONTEXT)
            .idleTimeout(Duration.ofSeconds(5))
            .initialSettings { spec ->
                spec.maxData(10_000_000)
                    .maxStreamDataBidirectionalLocal(10_000_000)
            }

        var latch = CountDownLatch(1)

        measureTime(MEASUREMENTS) {
            repeat(REPEATS) {
                val connection = settings.connectNow()
                connection.createStream(QuicStreamType.BIDIRECTIONAL) { `in`, out ->
                    out.send(ByteBufFlux.fromString(Mono.just("abc")))
                        .then(`in`.receive().asString(Charsets.UTF_8).doOnNext { latch.countDown() }.then())
                }
                latch.await()
                connection.disposeNow()
            }
            latch = CountDownLatch(1)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(4)
    fun bigDataTest(count: Int) = startServer(PORT).use {

    }
}
