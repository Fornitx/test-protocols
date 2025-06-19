package com.demo.rsocket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.core.RSocketConnector
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.util.DefaultPayload
import reactor.core.publisher.Flux
import reactor.netty.tcp.TcpClient
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

private val log = KotlinLogging.logger {}

fun main() {
    val sslContext = SslContextBuilder.forClient()
        .trustManager(TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(KeyStore.getInstance(TRUSTSTORE, PASSWORD))
        })
        .build()
    val clientTransport = TcpClientTransport.create(
        TcpClient.create().port(PORT).secure { it.sslContext(sslContext) }
    )
    val rSocket = RSocketConnector.connectWith(clientTransport).block()!!
    rSocket.requestChannel(Flux.just(DefaultPayload.create("foo_"), DefaultPayload.create("bar_")))
        .doOnNext { next ->
            log.info { "Got next: ${next.dataUtf8}" }
        }
        .take(2)
        .blockLast()

    rSocket.dispose()
}
