package com.demo.quic

import com.demo.constants.PORT
import com.demo.constants.PROTOCOL
import com.demo.constants.TRUST_KEY_STORE
import com.demo.data.StringData
import com.demo.logging.ClientLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.log.SysOutLogger
import java.net.URI
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
        .customTrustStore(TRUST_KEY_STORE)
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
                ClientLogger.log(response)
            }
            Thread.onSpinWait()
        }
    }

    for (value in StringData.VALUES) {
        outputStream.write(value.encodeToByteArray())
        outputStream.flush()
        TimeUnit.SECONDS.sleep(1)
    }

    connection.close()
}
