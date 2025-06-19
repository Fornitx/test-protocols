package com.demo.quic

import io.github.oshai.kotlinlogging.KotlinLogging
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.log.SysOutLogger
import java.io.File
import java.net.URI
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

fun main() {
    val logger = SysOutLogger()
    logger.logInfo(true)
//    logger.logPackets(true)

    val connection = QuicClientConnection.newBuilder()
//        .noServerCertificateCheck()
        .uri(URI.create("https://localhost:$PORT"))
        .applicationProtocol(PROTOCOL)
        .customTrustStore(KeyStore.getInstance(CLIENT_KEYSTORE, PASSWORD))
        .logger(logger)
        .build()
    connection.connect()

    val quicStream = connection.createStream(true)

    val inputStream = quicStream.inputStream
    val outputStream = quicStream.outputStream

    thread(start = true, isDaemon = true) {
        while (true) {
            val available = inputStream.available()
            if (available > 0) {
                val response = inputStream.readNBytes(available).decodeToString()
                log.info { "QuicStream InputStream: $response" }
            }
            Thread.onSpinWait()
        }
    }

    outputStream.write("foo_".encodeToByteArray())
    outputStream.flush()

    TimeUnit.SECONDS.sleep(1)

    outputStream.write("bar_".encodeToByteArray())
    outputStream.flush()

    TimeUnit.SECONDS.sleep(1)

    connection.close()
}
