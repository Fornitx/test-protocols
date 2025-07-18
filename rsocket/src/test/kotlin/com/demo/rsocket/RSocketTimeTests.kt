package com.demo.rsocket

import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.resourceScope
import com.demo.constants.NET.PORT
import com.demo.data.StringData.randomText
import com.demo.rsocket.NettyUtils.CLIENT_SSL_CONTEXT
import com.demo.rsocket.NettyUtils.SERVER_SSL_CONTEXT
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import com.demo.test.StopWatchKt
import com.demo.test.printStats
import io.rsocket.ConnectionSetupPayload
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.SocketAcceptor
import io.rsocket.core.RSocketConnector
import io.rsocket.core.RSocketServer
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.ByteBufPayload
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.tcp.TcpClient
import reactor.netty.tcp.TcpServer
import reactor.test.test
import kotlin.test.assertEquals
import kotlin.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RSocketTimeTests {
    class ResponseHandler : SocketAcceptor {
        override fun accept(setup: ConnectionSetupPayload, sendingSocket: RSocket): Mono<RSocket> {
            return Mono.just(object : RSocket {
                override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
                    return Flux.from(payloads).concatMap { payload ->
                        val dataUtf8 = payload.dataUtf8
//                    ServerLogger.log(dataUtf8)
                        payload.release()
                        Mono.just(ByteBufPayload.create(dataUtf8))
                    }
                }

                override fun requestResponse(payload: Payload): Mono<Payload> {
                    val dataUtf8 = payload.dataUtf8
//                    ServerLogger.log(dataUtf8)
                    payload.release()
                    return Mono.just(ByteBufPayload.create(dataUtf8))
                }
            })
        }
    }

    private suspend fun ResourceScope.rSocketServer(tcpPort: Int): CloseableChannel = install(
        acquire = {
            val server = TcpServer.create().port(tcpPort).secure { it.sslContext(SERVER_SSL_CONTEXT) }
            val serverTransport = TcpServerTransport.create(server)
            RSocketServer.create(ResponseHandler()).bindNow(serverTransport)
        },
        release = { closeableChannel, _ ->
            closeableChannel.dispose()
            closeableChannel.onClose().block()
        },
    )

    private suspend fun ResourceScope.rSocketClient(tcpPort: Int): RSocket = install(
        acquire = {
            val client = TcpClient.create().port(tcpPort).secure { it.sslContext(CLIENT_SSL_CONTEXT) }
            val clientTransport = TcpClientTransport.create(client)
            RSocketConnector.connectWith(clientTransport).block()!!
        },
        release = { rSocket, _ ->
            rSocket.dispose()
            rSocket.onClose().block()
        },
    )

    @Test
    @Order(1)
    fun connectionOnlyTest() = runResourceTest {
        val client = TcpClient.create().port(PORT).secure { it.sslContext(CLIENT_SSL_CONTEXT) }
        val clientTransport = TcpClientTransport.create(client)

        val stopWatch = StopWatchKt.createUnstarted()
        val measurements = mutableListOf<Duration>()
        repeat(MEASUREMENTS) {
            stopWatch.reset()
            repeat(REPEATS) {
                val rSocket = stopWatch.record { RSocketConnector.connectWith(clientTransport).block()!! }
                rSocket.dispose()
                rSocket.onClose().block()
            }
            stopWatch.durationKt.also(measurements::add).also { println("timeTaken: $it") }
        }
        measurements.printStats()
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(2)
    fun onePacketTest(count: Int) = runResourceTest {
        val rSocket = rSocketClient(PORT)
        val stopWatch = StopWatchKt.createUnstarted()
        val measurements = mutableListOf<Duration>()
        repeat(MEASUREMENTS) {
            stopWatch.reset()
            repeat(REPEATS) {
                val randomText = randomText(count)
                stopWatch.start()
                rSocket.requestResponse(ByteBufPayload.create(randomText))
                    .doOnNext { stopWatch.stop() }
                    .test()
                    .assertNext { next -> assertEquals(randomText, next.dataUtf8) }
                    .verifyComplete()
            }
            stopWatch.durationKt.also(measurements::add).also { println("timeTaken: $it") }
        }
        measurements.printStats()
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(3)
    fun streamingTest(count: Int) = runResourceTest {
        val rSocket = rSocketClient(PORT)
        val stopWatch = StopWatchKt.createUnstarted()
        val measurements = mutableListOf<Duration>()
        repeat(MEASUREMENTS) {
            stopWatch.reset()
            val payloads = Flux.generate { sink -> sink.next(ByteBufPayload.create(randomText(count))) }
                .take(REPEATS.toLong())
            stopWatch.start()
            rSocket.requestChannel(payloads)
                .doOnNext { it.release() }
                .doFinally { stopWatch.stop() }
                .take(REPEATS.toLong())
                .test()
                .expectNextCount(REPEATS.toLong())
                .verifyComplete()

            stopWatch.durationKt.also(measurements::add).also { println("timeTaken: $it") }
        }
        measurements.printStats()
    }

    private fun runResourceTest(block: suspend ResourceScope.() -> Unit) = runTest {
        resourceScope {
            rSocketServer(PORT)
            block()
        }
    }
}
