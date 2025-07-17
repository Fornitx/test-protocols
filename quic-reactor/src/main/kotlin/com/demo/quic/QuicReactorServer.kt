package com.demo.quic

import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import com.demo.quic.ReactorUtils.SERVER_SSL_CONTEXT
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.codec.quic.InsecureQuicTokenHandler
import reactor.netty.quic.QuicServer
import java.time.Duration

private val log = KotlinLogging.logger {}

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
                .maxStreamDataBidirectionalRemote(10_000_000)
                .maxStreamsBidirectional(100)
        }
        .handleStream { `in`, out ->
            out.sendString(
                `in`.receive().asString(Charsets.UTF_8).map { str ->
                    ServerLogger.log(str)
                    str.asResponse()
                }
            )
        }
        .bindNow()

    server.onDispose().block()
}
