package com.demo.quic

import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.data.StringData
import com.demo.logging.ClientLogger
import com.demo.quic.ReactorUtils.CLIENT_SSL_CONTEXT
import io.netty.handler.codec.quic.QuicStreamType
import reactor.core.publisher.Flux
import reactor.netty.ByteBufFlux
import reactor.netty.quic.QuicClient
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch

fun main() {
    val client = QuicClient.create()
        .bindAddress { InetSocketAddress(0) }
        .remoteAddress { InetSocketAddress(HOST, PORT) }
        .secure(CLIENT_SSL_CONTEXT)
//        .wiretap(true)
        .idleTimeout(Duration.ofSeconds(5))
        .initialSettings { spec ->
            spec.maxData(10_000_000)
                .maxStreamDataBidirectionalLocal(10_000_000)
        }
        .connectNow()

    val latch = CountDownLatch(3)
    client.createStream(QuicStreamType.BIDIRECTIONAL) { `in`, out ->
        out.send(ByteBufFlux.fromString(Flux.fromIterable(StringData.VALUES)))
            .then(
                `in`.receive()
                    .asString(Charsets.UTF_8)
                    .doOnNext { str ->
                        ClientLogger.log(str)
                        latch.countDown()
                    }
                    .then()
            )
    }.subscribe()

    latch.await()
}
