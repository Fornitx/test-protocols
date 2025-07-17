package com.demo.quic

import com.demo.constants.QUIC.PROTOCOL
import com.demo.constants.TLS
import io.netty.handler.codec.quic.QuicSslContextBuilder
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import reactor.netty.Connection

object ReactorUtils {
    val SERVER_SSL_CONTEXT = QuicSslContextBuilder.forServer(TLS.SERVER_KEYMANAGERFACTORY, TLS.PASSWORD)
        .trustManager(TLS.SERVER_TRUSTMANAGERFACTORY)
        .clientAuth(ClientAuth.REQUIRE)
        .applicationProtocols(PROTOCOL)
        .build()
    val CLIENT_SSL_CONTEXT = QuicSslContextBuilder.forClient()
        .keyManager(TLS.CLIENT_KEYMANAGERFACTORY, TLS.PASSWORD)
        // TODO InsecureTrustManagerFactory
//        .trustManager(TLS.CLIENT_TRUSTMANAGERFACTORY)
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .applicationProtocols(PROTOCOL)
        .build()

    fun Connection.use(block: () -> Unit) {
        try {
            block()
        } finally {
            this.dispose()
            this.onDispose().block()
        }
    }
}
