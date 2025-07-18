package com.demo.rsocket

import com.demo.constants.NET
import com.demo.data.StringData
import com.demo.logging.ClientLogger
import io.rsocket.core.RSocketConnector
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.util.ByteBufPayload
import reactor.core.publisher.Flux
import reactor.netty.tcp.TcpClient
import java.time.Duration

fun main() {
    val clientTransport = TcpClientTransport.create(
        TcpClient.create().port(NET.PORT).secure {
            it.sslContext(NettyUtils.CLIENT_SSL_CONTEXT)
        }
    )

    val rSocket = RSocketConnector.connectWith(clientTransport).block()!!

    rSocket.requestChannel(
        Flux.fromIterable(StringData.VALUES)
            .map(ByteBufPayload::create)
            .delayElements(Duration.ofSeconds(1))
    )
        .doOnNext { next -> ClientLogger.log(next.dataUtf8) }
        .take(StringData.VALUES.size.toLong())
        .blockLast()

    rSocket.dispose()
}
