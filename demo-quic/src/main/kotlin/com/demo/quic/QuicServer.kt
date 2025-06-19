package com.demo.quic

import io.github.oshai.kotlinlogging.KotlinLogging
import tech.kwik.core.KwikVersion
import tech.kwik.core.QuicConnection
import tech.kwik.core.QuicStream
import tech.kwik.core.log.SysOutLogger
import tech.kwik.core.server.ApplicationProtocolConnection
import tech.kwik.core.server.ApplicationProtocolConnectionFactory
import tech.kwik.core.server.ServerConnectionConfig
import tech.kwik.core.server.ServerConnector
import java.io.File
import java.security.KeyStore
import kotlin.concurrent.thread

const val PORT = 8443
const val PROTOCOL = "echo"

val SERVER_KEYSTORE = File("etc/openssl/server-keystore.p12")
val TRUSTSTORE = File("etc/openssl/truststore.p12")
val PASSWORD = "123456".toCharArray()

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

    val keyStore = KeyStore.getInstance(SERVER_KEYSTORE, PASSWORD)

    val serverConnector = ServerConnector.builder()
        .withPort(PORT)
        .withSupportedVersions(listOf(QuicConnection.QuicVersion.V1, QuicConnection.QuicVersion.V2))
        .withConfiguration(serverConnectionConfig)
        .withLogger(logger)
        .withKeyStore(keyStore, "server", "123456".toCharArray())
        .build()

    serverConnector.registerApplicationProtocol(PROTOCOL, EchoApplicationProtocolConnectionFactory())
    serverConnector.start()
    log.info {
        "Kwik server ${KwikVersion.getVersion()} started; supported application protocols: ${serverConnector.registeredApplicationProtocols}"
    }
}

class EchoApplicationProtocolConnectionFactory : ApplicationProtocolConnectionFactory {
    override fun createConnection(protocol: String, quicConnection: QuicConnection): ApplicationProtocolConnection {
        log.info { "Creating connection with $protocol" }
        return EchoApplicationProtocolConnection()
    }
}

class EchoApplicationProtocolConnection : ApplicationProtocolConnection {
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
                        log.info { "QuicStream InputStream: $request" }
                        outputStream.write(request.repeat(3).encodeToByteArray())
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
