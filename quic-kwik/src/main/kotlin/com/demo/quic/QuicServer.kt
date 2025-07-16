package com.demo.quic

import com.demo.constants.PASSWORD
import com.demo.constants.PORT
import com.demo.constants.PROTOCOL
import com.demo.constants.SERVER_ALIAS
import com.demo.constants.SERVER_KEY_STORE
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import tech.kwik.core.KwikVersion
import tech.kwik.core.QuicConnection
import tech.kwik.core.QuicStream
import tech.kwik.core.log.SysOutLogger
import tech.kwik.core.server.ApplicationProtocolConnection
import tech.kwik.core.server.ApplicationProtocolConnectionFactory
import tech.kwik.core.server.ServerConnectionConfig
import tech.kwik.core.server.ServerConnector
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

fun main() {
    val serverConnectionConfig = ServerConnectionConfig.builder()
        .maxIdleTimeoutInSeconds(30)
        .maxUnidirectionalStreamBufferSize(1_000_000)
        .maxBidirectionalStreamBufferSize(1_000_000)
        .maxConnectionBufferSize(10_000_000)
        .maxOpenPeerInitiatedUnidirectionalStreams(10)
        .maxOpenPeerInitiatedBidirectionalStreams(100)
        .retryRequired(true)
        .connectionIdLength(8)
        .build()

    val logger = SysOutLogger()
    logger.logInfo(true)
//    logger.logPackets(true)

    val serverConnector = ServerConnector.builder()
        .withPort(PORT)
        .withSupportedVersions(listOf(QuicConnection.QuicVersion.V1, QuicConnection.QuicVersion.V2))
        .withConfiguration(serverConnectionConfig)
        .withLogger(logger)
        .withKeyStore(SERVER_KEY_STORE, SERVER_ALIAS, PASSWORD)
        .build()

    serverConnector.registerApplicationProtocol(PROTOCOL, EchoApplicationProtocolConnectionFactory())
    serverConnector.start()
    log.info {
        "Kwik server ${KwikVersion.getVersion()} started; supported application protocols: ${serverConnector.registeredApplicationProtocols}"
    }
}

private class EchoApplicationProtocolConnectionFactory : ApplicationProtocolConnectionFactory {
    override fun createConnection(protocol: String, quicConnection: QuicConnection): ApplicationProtocolConnection {
        log.info { "Creating connection with $protocol" }
        return EchoApplicationProtocolConnection()
    }
}

private class EchoApplicationProtocolConnection : ApplicationProtocolConnection {
    override fun acceptPeerInitiatedStream(stream: QuicStream) {
        log.info { "acceptPeerInitiatedStream: streamId = ${stream.streamId}" }
        thread(start = true, isDaemon = true) {
            try {
                val inputStream = stream.inputStream
                val outputStream = stream.outputStream
                while (true) {
                    val available = inputStream.available()
                    if (available > 0) {
                        val request = inputStream.readNBytes(available).decodeToString()
                        ServerLogger.log(request)
                        outputStream.write(request.asResponse().encodeToByteArray())
                        outputStream.flush()
                    }
                    Thread.onSpinWait()
                }
            } catch (ex: Exception) {
                log.error(ex) {}
            }
        }
    }
}
