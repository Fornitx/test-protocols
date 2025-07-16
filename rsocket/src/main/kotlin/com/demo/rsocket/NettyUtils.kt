package com.demo.rsocket

import com.demo.constants.CLIENT_KEY_MANAGER_FACTORY
import com.demo.constants.SERVER_KEY_MANAGER_FACTORY
import com.demo.constants.TRUST_MANAGER_FACTORY
import io.netty.handler.ssl.SslContextBuilder
import io.rsocket.transport.netty.server.CloseableChannel

object NettyUtils {
    val SERVER_SSL_CONTEXT = SslContextBuilder
        .forServer(SERVER_KEY_MANAGER_FACTORY)
        .trustManager(TRUST_MANAGER_FACTORY)
        .build()
    val CLIENT_SSL_CONTEXT = SslContextBuilder.forClient()
        .keyManager(CLIENT_KEY_MANAGER_FACTORY)
        .trustManager(TRUST_MANAGER_FACTORY)
        .build()

    fun CloseableChannel.use(block: () -> Unit) {
        try {
            block()
        } finally {
            this.dispose()
        }
    }
}
