package com.demo.quic

import com.demo.constants.NET.PORT
import com.demo.constants.QUIC.PROTOCOL
import com.demo.constants.TLS
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import tech.kwik.core.KwikVersion
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.QuicConnection
import tech.kwik.core.QuicStream
import tech.kwik.core.log.NullLogger
import tech.kwik.core.server.ApplicationProtocolConnection
import tech.kwik.core.server.ApplicationProtocolConnectionFactory
import tech.kwik.core.server.ServerConnectionConfig
import tech.kwik.core.server.ServerConnector
import java.net.URI
import kotlin.concurrent.thread
import kotlin.time.measureTime

private val log = KotlinLogging.logger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class QuicKwikTimeTests {
    private fun startServer(udpPort: Int): ServerConnector {
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

        val logger = NullLogger()
//        val logger = SysOutLogger()
//        logger.logInfo(true)
//    logger.logPackets(true)

        val serverConnector = ServerConnector.builder()
            .withPort(udpPort)
            .withSupportedVersions(listOf(QuicConnection.QuicVersion.V1, QuicConnection.QuicVersion.V2))
            .withConfiguration(serverConnectionConfig)
            .withLogger(logger)
            .withKeyStore(TLS.SERVER_KEYSTORE, TLS.SERVER_ALIAS, TLS.PASSWORD_CHARS)
            // TODO NO MTLS !!!
            .build()

        serverConnector.registerApplicationProtocol(PROTOCOL, EchoApplicationProtocolConnectionFactory2())
        serverConnector.start()
        log.info {
            "Kwik server ${KwikVersion.getVersion()} started; supported application protocols: ${serverConnector.registeredApplicationProtocols}"
        }
        return serverConnector
    }

    private class EchoApplicationProtocolConnectionFactory2 : ApplicationProtocolConnectionFactory {
        override fun createConnection(protocol: String, quicConnection: QuicConnection): ApplicationProtocolConnection {
            return EchoApplicationProtocolConnection2()
        }
    }

    private class EchoApplicationProtocolConnection2 : ApplicationProtocolConnection {
        override fun acceptPeerInitiatedStream(stream: QuicStream) {
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

    @Test
    @Order(1)
    fun connectionOnlyTest() = startServer(PORT).use {
        repeat(MEASUREMENTS) {
            measureTime {
                repeat(REPEATS / 10) {
                    val connection = QuicClientConnection.newBuilder()
                        .uri(URI.create("https://localhost:$PORT"))
                        .applicationProtocol(PROTOCOL)
                        .clientKeyManager(TLS.CLIENT_KEYSTORE)
                        .clientKey(TLS.PASSWORD)
                        .customTrustStore(TLS.CLIENT_TRUSTSTORE)
                        .logger(NullLogger())
                        .build()
                    connection.connect()
                    connection.closeAndWait()
                }
            }.also { println("timeTaken: $it") }
        }
    }
}
