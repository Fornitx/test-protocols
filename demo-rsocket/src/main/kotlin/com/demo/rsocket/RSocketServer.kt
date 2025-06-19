package com.demo.rsocket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.rsocket.SocketAcceptor
import io.rsocket.core.RSocketServer
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import reactor.core.publisher.Flux

const val PORT = 8080

private val log = KotlinLogging.logger {}

fun main() {
    RSocketServer.create(SocketAcceptor.forRequestChannel { requests ->
        Flux.from(requests)
            .map { payload ->
                val dataUtf8 = payload.dataUtf8
                log.info { "Request payload: $dataUtf8" }
                DefaultPayload.create(dataUtf8.repeat(3))
            }
    })
        .bind(TcpServerTransport.create(PORT))
        .block()!!
        .onClose()
        .block()
}
