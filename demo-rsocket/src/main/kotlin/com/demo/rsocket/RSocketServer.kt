package com.demo.rsocket

import com.demo.constants.PORT
import com.demo.constants.SERVER_KEY_MANAGER_FACTORY
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.SocketAcceptor
import io.rsocket.core.RSocketServer
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import reactor.core.publisher.Flux
import reactor.netty.tcp.TcpServer

private val log = KotlinLogging.logger {}

fun main() {
    val socketAcceptor = SocketAcceptor.forRequestChannel { requests ->
        Flux.from(requests).map { payload ->
            val dataUtf8 = payload.dataUtf8
            ServerLogger.log(dataUtf8)
            DefaultPayload.create(dataUtf8.asResponse())
        }
    }

    val serverTransport = TcpServerTransport.create(
        TcpServer.create().port(PORT).secure {
            it.sslContext(SslContextBuilder.forServer(SERVER_KEY_MANAGER_FACTORY).build())
        }
    )

    RSocketServer.create(socketAcceptor).bind(serverTransport).block()!!.onClose().block()
}
