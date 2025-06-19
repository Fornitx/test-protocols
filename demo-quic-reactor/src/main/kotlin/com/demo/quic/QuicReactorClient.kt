package com.demo.quic

import com.demo.constants.PORT
import com.demo.constants.PROTOCOL
import com.demo.data.StringData
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.codec.quic.QuicSslContextBuilder
import io.netty.handler.codec.quic.QuicStreamType
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import reactor.core.publisher.Flux
import reactor.netty.quic.QuicClient
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch

private val log = KotlinLogging.logger {}

fun main() {
    val clientCtx = QuicSslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .applicationProtocols(PROTOCOL)
        .build()

    val client = QuicClient.create()
        .bindAddress { InetSocketAddress(0) }
        .remoteAddress { InetSocketAddress("127.0.0.1", PORT) }
        .secure(clientCtx)
        .wiretap(true)
        .idleTimeout(Duration.ofSeconds(5))
        .initialSettings { spec ->
            spec.maxData(10_000_000).maxStreamDataBidirectionalLocal(1_000_000)
        }
        .connectNow()

    val latch = CountDownLatch(2)
    client.createStream(QuicStreamType.BIDIRECTIONAL) { `in`, out ->
        out.sendString(Flux.fromIterable(StringData.VALUES))
            .then(
                `in`.receive()
                    .asString()
                    .doOnNext { s ->
                        log.info { "CLIENT RECEIVED: $s" }
                        latch.countDown()
                    }
                    .then()
            )
    }.subscribe()

    latch.await()
}
