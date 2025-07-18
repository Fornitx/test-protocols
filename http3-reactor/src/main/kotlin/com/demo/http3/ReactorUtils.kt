package com.demo.http3

import com.demo.constants.TLS.PASSWORD
import com.demo.constants.TLS2
import io.netty.handler.ssl.ClientAuth
import reactor.netty.http.Http3SslContextSpec

object ReactorUtils {
    val SERVER_SSL_CONTEXT = Http3SslContextSpec
        .forServer(TLS2.SERVER_KEYMANAGERFACTORY, PASSWORD)
        .configure { quicSslContextBuilder ->
            quicSslContextBuilder
                .trustManager(TLS2.SERVER_TRUSTMANAGERFACTORY)
                .clientAuth(ClientAuth.REQUIRE)
        }
    val CLIENT_SSL_CONTEXT = Http3SslContextSpec
        .forClient()
        .configure { quicSslContextBuilder ->
            quicSslContextBuilder
                .keyManager(TLS2.CLIENT_KEYMANAGERFACTORY, PASSWORD)
                .trustManager(TLS2.CLIENT_TRUSTMANAGERFACTORY)
        }
}
