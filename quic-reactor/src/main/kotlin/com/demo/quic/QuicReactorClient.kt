package com.demo.quic

import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.data.StringData
import com.demo.logging.ClientLogger
import com.demo.quic.ReactorUtils.CLIENT_SSL_CONTEXT
import io.netty.handler.codec.quic.QuicStreamType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.ByteBufMono
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
                .maxStreamDataBidirectionalRemote(10_000_000)
                .maxStreamDataUnidirectional(10_000_000)
                .maxStreamsBidirectional(100)
                .maxStreamsUnidirectional(100)
        }
        .connectNow()

    val latch = CountDownLatch(3)
    client.createStream(QuicStreamType.BIDIRECTIONAL) { `in`, out ->
        Mono.`when`(
            out.sendGroups(Flux.fromIterable(StringData.VALUES).map { ByteBufMono.fromString(Mono.just(it)) }),
            `in`.receive()
                .asString(Charsets.UTF_8)
                .doOnNext { str ->
                    ClientLogger.log(str)
                    latch.countDown()
                },
        )
    }.subscribe()

    latch.await()
}
