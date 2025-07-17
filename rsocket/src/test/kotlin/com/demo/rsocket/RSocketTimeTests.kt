package com.demo.rsocket

import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.data.StringData.randomText
import com.demo.rsocket.NettyUtils.use
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import com.demo.test.measureTime
import io.rsocket.ConnectionSetupPayload
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.SocketAcceptor
import io.rsocket.core.RSocketConnector
import io.rsocket.core.RSocketServer
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.ByteBufPayload
import io.rsocket.util.DefaultPayload
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
                        Mono.just(ByteBufPayload.create(dataUtf8.asResponse()))
                    }
                }

                override fun requestResponse(payload: Payload): Mono<Payload> {
                    val dataUtf8 = payload.dataUtf8
//                    ServerLogger.log(dataUtf8)
                    payload.release()
                    return Mono.just(ByteBufPayload.create(dataUtf8.asResponse()))
                }
            })
        }
    }

    private fun startServer(tcpPort: Int): CloseableChannel {
        val serverTransport = TcpServerTransport.create(
            TcpServer.create().port(tcpPort).secure { it.sslContext(NettyUtils.SERVER_SSL_CONTEXT) }
        )

        return RSocketServer.create(ResponseHandler()).bindNow(serverTransport)
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
    fun connectionOnlyTest() = startServer(PORT).use {
        val clientTransport = startClient(PORT)

        measureTime(MEASUREMENTS) {
            repeat(REPEATS) {
                val rSocket = RSocketConnector.connectWith(clientTransport).block()!!
                rSocket.dispose()
                rSocket.onClose().block()
            }
        }
    }

    @Test
    @Order(2)
    fun onePacketTest() = startServer(PORT).use {
        val clientTransport = startClient(PORT)

        measureTime(MEASUREMENTS) {
            repeat(REPEATS) {
                val rSocket = RSocketConnector.connectWith(clientTransport).block()!!

                rSocket.requestResponse(DefaultPayload.create("abc"))
                    .test()
                    .assertNext { next -> assertEquals("ABC_ABC_ABC", next.dataUtf8) }
                    .verifyComplete()

                rSocket.dispose()
                rSocket.onClose().block()
            }
        }
    }

    @Test
    @Order(3)
    fun manySmallPacketsTest() = startServer(PORT).use {
        val clientTransport = startClient(PORT)

        measureTime(MEASUREMENTS) {
            val rSocket = RSocketConnector.connectWith(clientTransport).block()!!

            val payloads = Flux.range(0, REPEATS).map { DefaultPayload.create("abc") }
            rSocket.requestChannel(payloads)
                .doOnNext { next -> assertEquals("ABC_ABC_ABC", next.dataUtf8) }
                .take(REPEATS.toLong())
                .test()
                .expectNextCount(REPEATS.toLong())
                .verifyComplete()

            rSocket.dispose()
            rSocket.onClose().block()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000, 10_000, 100_000])
    @Order(4)
    fun bigDataTest(count: Int) = startServer(PORT).use {
        val clientTransport = startClient(PORT)

        measureTime(MEASUREMENTS) {
            repeat(REPEATS) {
                val rSocket = RSocketConnector.connectWith(clientTransport).block()!!

                val randomText = randomText(count)
                rSocket.requestResponse(DefaultPayload.create(randomText))
                    .test()
                    .assertNext { next -> assertEquals(randomText, next.dataUtf8) }
                    .verifyComplete()

                rSocket.dispose()
                rSocket.onClose().block()
            }
        }
    }
}
