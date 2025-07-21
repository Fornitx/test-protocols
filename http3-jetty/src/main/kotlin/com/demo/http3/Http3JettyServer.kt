package com.demo.http3

import com.demo.constants.NET.PORT
import com.demo.constants.TLS.PASSWORD
import org.eclipse.jetty.http.MetaData
import org.eclipse.jetty.http3.api.Session
import org.eclipse.jetty.http3.api.Stream
import org.eclipse.jetty.http3.frames.HeadersFrame
import org.eclipse.jetty.http3.server.RawHTTP3ServerConnectionFactory
import org.eclipse.jetty.quic.server.QuicServerConnector
import org.eclipse.jetty.quic.server.ServerQuicConfiguration
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.ssl.SslContextFactory
import java.nio.file.Path

fun main() {
    // Create a Server instance.
    val server = Server(PORT)

    // HTTP/3 is always secure, so it always need a SslContextFactory.
    val sslContextFactory = SslContextFactory.Server()

    sslContextFactory.keyStorePath = "etc/keytool/server-keystore.p12"
    sslContextFactory.keyStoreType = "PKCS12"
    sslContextFactory.keyStorePassword = PASSWORD

    sslContextFactory.trustStorePath = "etc/keytool/server-truststore.p12"
    sslContextFactory.trustStoreType = "PKCS12"
    sslContextFactory.setTrustStorePassword(PASSWORD)

    sslContextFactory.needClientAuth = true

    // The listener for session events.
    val sessionListener: Session.Server.Listener = object : Session.Server.Listener {
        override fun onAccept(session: Session) {
            val remoteAddress = session.remoteSocketAddress
            println("Connection from $remoteAddress")
        }

        override fun onRequest(stream: Stream.Server, frame: HeadersFrame): Stream.Server.Listener {
            val request = frame.metaData as MetaData.Request

            // Demand to be called back when data is available.
            stream.demand()

            return object : Stream.Server.Listener {
                override fun onDataAvailable(stream: Stream.Server?) {
                    // Read a chunk of the request content.
                    val data = stream!!.readData()

                    if (data == null) {
                        // No data available now, demand to be called back.
                        stream.demand()
                    } else {
                        // Get the content buffer.
                        val buffer = data.byteBuffer

                        // Consume the buffer, here - as an example - just log it.
                        println("Consuming buffer $buffer")

                        // Tell the implementation that the buffer has been consumed.
                        data.release()

                        if (!data.isLast) {
                            // Demand to be called back.
                            stream.demand()
                        }
                    }
                }
            }
        }
    }

    val quicConfiguration = ServerQuicConfiguration(sslContextFactory, Path.of("tmp"))

    // Configure the max number of requests per QUIC connection.
    quicConfiguration.maxBidirectionalRemoteStreams = 100

    // Create and configure the RawHTTP3ServerConnectionFactory.
    val http3 = RawHTTP3ServerConnectionFactory(quicConfiguration, sessionListener)
    http3.httP3Configuration.streamIdleTimeout = 5000

    // Create and configure the QuicServerConnector.
    val connector = QuicServerConnector(server, quicConfiguration, http3)

    // Add the Connector to the Server.
    server.addConnector(connector)

    // Start the Server so it starts accepting connections from clients.
    server.start()
}
