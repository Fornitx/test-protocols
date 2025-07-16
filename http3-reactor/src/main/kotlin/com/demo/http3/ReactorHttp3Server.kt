package com.demo.http3

import com.demo.constants.PASSWORD
import com.demo.constants.PORT
import com.demo.constants.SERVER_KEY_MANAGER_FACTORY
import com.demo.constants.TRUST_MANAGER_FACTORY
import com.demo.logging.ServerLogger
import reactor.core.publisher.Mono
import reactor.netty.http.Http3SslContextSpec
import reactor.netty.http.HttpProtocol
import reactor.netty.http.server.HttpServer
import java.time.Duration

fun main() {
    val sslContext = Http3SslContextSpec.forServer(SERVER_KEY_MANAGER_FACTORY, PASSWORD).configure {
        it.trustManager(TRUST_MANAGER_FACTORY)
    }

    val server = HttpServer.create()
        .port(PORT)
        .protocol(HttpProtocol.HTTP3)
        .secure({ spec -> spec.sslContext(sslContext) })
        .http3Settings({ spec ->
            spec.idleTimeout(Duration.ofSeconds(5))
                .maxData(10000000)
                .maxStreamDataBidirectionalLocal(1000000)
                .maxStreamDataBidirectionalRemote(1000000)
                .maxStreamsBidirectional(100)
        })
        .handle({ request, response ->
            request.receive()
                .doOnNext { content -> ServerLogger.log(content.toString(Charsets.UTF_8)) }
                .then(
                    response.header("server", "reactor-netty")
                        .sendString(Mono.just("hello"))
                        .then()
                )
        })
        .bindNow()

    server.onDispose().block()
}
