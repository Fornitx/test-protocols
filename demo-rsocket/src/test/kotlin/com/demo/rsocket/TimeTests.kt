package com.demo.rsocket

import com.demo.constants.PORT
import com.demo.data.StringData.asResponse
import com.demo.data.StringData.randomText
import com.demo.rsocket.NettyUtils.use
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import io.rsocket.SocketAcceptor
import io.rsocket.core.RSocketConnector
import io.rsocket.core.RSocketServer
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Flux
import reactor.netty.tcp.TcpClient
import reactor.netty.tcp.TcpServer
import reactor.test.test
import kotlin.test.assertEquals
import kotlin.time.measureTime

//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TimeTests {
    private fun startServer(tcpPort: Int): CloseableChannel {
        val socketAcceptor = SocketAcceptor.forRequestChannel { requests ->
            Flux.from(requests).map { payload ->
                val dataUtf8 = payload.dataUtf8
//                ServerLogger.log(dataUtf8)
                DefaultPayload.create(dataUtf8.asResponse())
            }
        }

        val serverTransport = TcpServerTransport.create(
            TcpServer.create().port(tcpPort).secure { it.sslContext(NettyUtils.SERVER_SSL_CONTEXT) }
        )

        return RSocketServer.create(socketAcceptor).bindNow(serverTransport)
    }

    private fun startClient(tcpPort: Int): ClientTransport {
        return TcpClientTransport.create(
            TcpClient.create().port(tcpPort).secure {
                it.sslContext(NettyUtils.CLIENT_SSL_CONTEXT)
            }
        )
    }

    @Test
    @Order(1)
    fun connectionOnlyTest() {
        startServer(PORT).use {
            val clientTransport = startClient(PORT)

            repeat(MEASUREMENTS) {
                measureTime {
                    repeat(REPEATS) {
                        val rSocket = RSocketConnector.connectWith(clientTransport).block()!!
                        rSocket.dispose()
                    }
                }.also { println("timeTaken: $it") }
            }
        }
    }

    @Test
    @Order(2)
    fun onePacketTest() {
        startServer(PORT).use {
            val clientTransport = startClient(PORT)

            repeat(MEASUREMENTS) {
                measureTime {
                    repeat(REPEATS) {
                        val rSocket = RSocketConnector.connectWith(clientTransport).block()!!

                        rSocket.requestChannel(Flux.just(DefaultPayload.create("abc")))
                            .take(1)
                            .test()
                            .assertNext { next -> assertEquals("ABC_ABC_ABC", next.dataUtf8) }
                            .verifyComplete()

                        rSocket.dispose()
                    }
                }.also { println("timeTaken: $it") }
            }
        }
    }

    @Test
    @Order(3)
    fun manySmallPacketsTest() {
        startServer(PORT).use {
            val clientTransport = startClient(PORT)

            repeat(MEASUREMENTS) {
                measureTime {
                    val rSocket = RSocketConnector.connectWith(clientTransport).block()!!

                    rSocket.requestChannel(Flux.range(0, REPEATS).map { DefaultPayload.create("abc") })
                        .doOnNext { next -> assertEquals("ABC_ABC_ABC", next.dataUtf8) }
                        .take(100)
                        .test()
                        .expectNextCount(100)
                        .verifyComplete()

                    rSocket.dispose()
                }.also { println("timeTaken: $it") }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(4)
    fun bigDataTest(count: Int) {
        startServer(PORT).use {
            val clientTransport = startClient(PORT)

            repeat(MEASUREMENTS) {
                measureTime {
                    repeat(REPEATS) {
                        val rSocket = RSocketConnector.connectWith(clientTransport).block()!!

                        val randomText = randomText(count)
                        rSocket.requestChannel(Flux.just(DefaultPayload.create(randomText)))
                            .doOnNext { next -> assertEquals(randomText, next.dataUtf8) }
                            .take(1)
                            .test()
                            .expectNextCount(1)
                            .verifyComplete()

                        rSocket.dispose()
                    }
                }.also { println("timeTaken: $it") }
            }
        }
    }
}
