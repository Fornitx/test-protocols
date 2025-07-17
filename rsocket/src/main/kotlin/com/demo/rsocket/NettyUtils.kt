package com.demo.rsocket

import com.demo.constants.TLS
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.transport.netty.server.CloseableChannel

object NettyUtils {
    val SERVER_SSL_CONTEXT = SslContextBuilder
        .forServer(TLS.SERVER_KEYMANAGERFACTORY)
        .trustManager(TLS.SERVER_TRUSTMANAGERFACTORY)
        .clientAuth(ClientAuth.REQUIRE)
        .build()
    val CLIENT_SSL_CONTEXT = SslContextBuilder.forClient()
        .keyManager(TLS.CLIENT_KEYMANAGERFACTORY)
        .trustManager(TLS.CLIENT_TRUSTMANAGERFACTORY)
        .build()

    fun CloseableChannel.use(block: () -> Unit) {
        try {
            block()
        } finally {
            this.dispose()
            this.onClose().block()
        }
    }
}
