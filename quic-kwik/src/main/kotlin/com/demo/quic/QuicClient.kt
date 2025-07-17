package com.demo.quic

import com.demo.constants.NET.PORT
import com.demo.constants.QUIC.PROTOCOL
import com.demo.constants.TLS
import com.demo.data.StringData
import com.demo.logging.ClientLogger
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.log.SysOutLogger
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun main() {
    val logger = SysOutLogger()
    logger.logInfo(true)
//    logger.logPackets(true)

    val connection = QuicClientConnection.newBuilder()
//        .noServerCertificateCheck()
        .uri(URI.create("https://localhost:$PORT"))
        .applicationProtocol(PROTOCOL)
        .customTrustStore(TLS.CLIENT_TRUSTSTORE)
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
