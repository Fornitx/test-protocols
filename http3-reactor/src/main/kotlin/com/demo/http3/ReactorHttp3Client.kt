package com.demo.http3

import com.demo.constants.PORT
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import reactor.core.publisher.Mono
import reactor.netty.ByteBufFlux
import reactor.netty.http.Http3SslContextSpec
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import java.time.Duration

fun main() {
    val client = HttpClient.create()
        .protocol(HttpProtocol.HTTP3)
        .secure {
            val sslContext = Http3SslContextSpec.forClient()
                .configure { it.trustManager(InsecureTrustManagerFactory.INSTANCE) }
            it.sslContext(sslContext)
        }
        .http3Settings({ spec ->
            spec.idleTimeout(Duration.ofSeconds(5))
                .maxData(10000000)
                .maxStreamDataBidirectionalLocal(1000000)
        })

    val response = client.post()
        .uri("https://127.0.0.1:$PORT/")
        .send(ByteBufFlux.fromString(Mono.just("Hello World!")))
        .responseSingle({ res, bytes ->
            bytes.asString()
                .zipWith(Mono.just(res.responseHeaders()))
        })
        .block()!!

    println("Used stream ID: " + response.getT2().get("x-http3-stream-id"))
    println("Response: " + response.getT1())
}
