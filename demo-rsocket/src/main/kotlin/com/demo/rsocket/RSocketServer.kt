package com.demo.rsocket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.SocketAcceptor
import io.rsocket.core.RSocketServer
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import reactor.core.publisher.Flux
import reactor.netty.tcp.TcpServer
import java.io.File
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

const val PORT = 8080

val SERVER_KEYSTORE = File("etc/openssl/server-keystore.p12")
val TRUSTSTORE = File("etc/openssl/truststore.p12")
val PASSWORD = "123456".toCharArray()

private val log = KotlinLogging.logger {}

fun main() {
    val socketAcceptor = SocketAcceptor.forRequestChannel { requests ->
        Flux.from(requests).map { payload ->
            val dataUtf8 = payload.dataUtf8
            log.info { "Request payload: $dataUtf8" }
            DefaultPayload.create(dataUtf8.repeat(3))
        }
    }
    val sslContext = SslContextBuilder.forServer(
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(KeyStore.getInstance(SERVER_KEYSTORE, PASSWORD), PASSWORD)
        }
    ).build()
    val serverTransport = TcpServerTransport.create(
        TcpServer.create().port(PORT).secure { it.sslContext(sslContext) }
    )
    RSocketServer.create(socketAcceptor).bind(serverTransport).block()!!.onClose().block()
}
