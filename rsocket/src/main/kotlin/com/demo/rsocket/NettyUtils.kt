package com.demo.rsocket

import com.demo.constants.TLS
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContextBuilder

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
}
