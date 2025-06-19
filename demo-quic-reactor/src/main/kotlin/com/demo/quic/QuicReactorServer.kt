package com.demo.quic

import com.demo.constants.PASSWORD
import com.demo.constants.PORT
import com.demo.constants.PROTOCOL
import com.demo.constants.SERVER_KEY_MANAGER_FACTORY
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.codec.quic.InsecureQuicTokenHandler
import io.netty.handler.codec.quic.QuicSslContextBuilder
import reactor.core.publisher.Mono
import reactor.netty.quic.QuicServer
import java.time.Duration

private val log = KotlinLogging.logger {}

fun main() {
    val sslContext = QuicSslContextBuilder.forServer(
        SERVER_KEY_MANAGER_FACTORY, PASSWORD.concatToString()
    ).applicationProtocols(PROTOCOL).build()

    val server = QuicServer.create()
        .host("127.0.0.1")
        .port(PORT)
        .secure(sslContext)
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
        .wiretap(true)
        .idleTimeout(Duration.ofSeconds(5))
        .initialSettings { spec ->
            spec.maxData(10_000_000)
                .maxStreamDataBidirectionalRemote(1_000_000)
                .maxStreamsBidirectional(100)
        }
        .handleStream { `in`, out ->
            log.info { "handleStream" }
            `in`.receive().asString().concatMap { str ->
                ServerLogger.log(str)
                out.sendString(Mono.just(str.asResponse())).then()
            }
        }
        .bindNow()

    server.onDispose().block()
}
