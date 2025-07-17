package com.demo.http3

import com.demo.constants.TLS
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import reactor.netty.DisposableServer
import reactor.netty.http.Http3SslContextSpec

object ReactorUtils {
    val SERVER_SSL_CONTEXT = Http3SslContextSpec
        .forServer(TLS.SERVER_KEYMANAGERFACTORY, TLS.PASSWORD)
        .configure { quicSslContextBuilder ->
            quicSslContextBuilder
                .trustManager(TLS.SERVER_TRUSTMANAGERFACTORY)
                .clientAuth(ClientAuth.REQUIRE)
        }
    val CLIENT_SSL_CONTEXT = Http3SslContextSpec
        .forClient()
        .configure { quicSslContextBuilder ->
            quicSslContextBuilder
                .keyManager(TLS.CLIENT_KEYMANAGERFACTORY, TLS.PASSWORD)
                // TODO InsecureTrustManagerFactory
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
//                .trustManager(TLS.CLIENT_TRUSTMANAGERFACTORY)
        }

    fun DisposableServer.use(block: () -> Unit) {
        try {
            block()
        } finally {
            this.dispose()
        }
    }
}
