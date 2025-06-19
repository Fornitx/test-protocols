package com.demo.rsocket

import com.demo.constants.PORT
import com.demo.constants.TRUST_MANAGER_FACTORY
import com.demo.data.StringData
import com.demo.logging.ClientLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.core.RSocketConnector
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.util.DefaultPayload
import reactor.core.publisher.Flux
import reactor.netty.tcp.TcpClient
import java.time.Duration

private val log = KotlinLogging.logger {}

fun main() {
    val clientTransport = TcpClientTransport.create(
        TcpClient.create().port(PORT).secure {
            it.sslContext(SslContextBuilder.forClient().trustManager(TRUST_MANAGER_FACTORY).build())
        }
    )

    val rSocket = RSocketConnector.connectWith(clientTransport).block()!!

    rSocket.requestChannel(
        Flux.fromIterable(StringData.VALUES)
            .map(DefaultPayload::create)
            .delayElements(Duration.ofSeconds(1))
    )
        .doOnNext { next -> ClientLogger.log(next.dataUtf8) }
        .take(StringData.VALUES.size.toLong())
        .blockLast()

    rSocket.dispose()
}
