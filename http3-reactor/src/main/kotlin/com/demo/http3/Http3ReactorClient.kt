package com.demo.http3

import com.demo.constants.NET.HOST
import com.demo.constants.NET.PORT
import com.demo.http3.ReactorUtils.CLIENT_SSL_CONTEXT
import reactor.core.publisher.Mono
import reactor.netty.ByteBufMono
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import java.time.Duration

fun main() {
    val client = HttpClient.create()
        .protocol(HttpProtocol.HTTP3)
        .secure { it.sslContext(CLIENT_SSL_CONTEXT) }
        .http3Settings({ spec ->
            spec.idleTimeout(Duration.ofSeconds(5))
                .maxData(10000000)
                .maxStreamDataBidirectionalLocal(1000000)
        })

    val response = client.post()
        .uri("https://$HOST:$PORT/")
        .send(ByteBufMono.fromString(Mono.just("Hello World!")))
        .responseSingle({ res, bytes ->
            bytes.asString()
                .zipWith(Mono.just(res.responseHeaders()))
        })
        .block()!!

    println("Used stream ID: " + response.getT2().get("x-http3-stream-id"))
    println("Response: " + response.getT1())
}
