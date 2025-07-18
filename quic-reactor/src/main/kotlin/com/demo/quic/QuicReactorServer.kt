package com.demo.quic

import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import com.demo.quic.ReactorUtils.SERVER_SSL_CONTEXT
import io.netty.handler.codec.quic.InsecureQuicTokenHandler
import reactor.core.publisher.Mono
import reactor.netty.ByteBufMono
import reactor.netty.quic.QuicServer
import java.time.Duration

fun main() {
    val server = QuicServer.create()
        .host(HOST)
        .port(PORT)
        .secure(SERVER_SSL_CONTEXT)
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
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
        .handleStream { `in`, out ->
            out.sendGroups(
                `in`.receive().asString(Charsets.UTF_8).map { str ->
                    ServerLogger.log(str)
                    ByteBufMono.fromString(Mono.just(str.asResponse()))
                }
            )
        }
        .bindNow()

    server.onDispose().block()
}
