package com.demo.http3

import com.demo.constants.NET.PORT
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.incubator.codec.http3.DefaultHttp3DataFrame
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame
import io.netty.incubator.codec.http3.Http3
import io.netty.incubator.codec.http3.Http3DataFrame
import io.netty.incubator.codec.http3.Http3HeadersFrame
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.util.CharsetUtil
import io.netty.util.ReferenceCountUtil
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

val CONTENT = "Hello World!\r\n".toByteArray(CharsetUtil.UTF_8)

fun main() {
    val group = NioEventLoopGroup(1)
    val cert = SelfSignedCertificate()
    val sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
        .applicationProtocols(*Http3.supportedApplicationProtocols()).build()
    val codec = Http3.newQuicServerCodecBuilder()
        .sslContext(sslContext)
        .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
        .initialMaxData(10000000)
        .initialMaxStreamDataBidirectionalLocal(1000000)
        .initialMaxStreamDataBidirectionalRemote(1000000)
        .initialMaxStreamsBidirectional(100)
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
        .handler(object : ChannelInitializer<QuicChannel>() {
            override fun initChannel(ch: QuicChannel) {
                // Called for each connection
                ch.pipeline().addLast(
                    Http3ServerConnectionHandler(
                        object : ChannelInitializer<QuicStreamChannel>() {
                            // Called for each request-stream,
                            override fun initChannel(ch: QuicStreamChannel) {
                                ch.pipeline().addLast(object : Http3RequestStreamInboundHandler() {
                                    override fun channelRead(
                                        ctx: ChannelHandlerContext?, frame: Http3HeadersFrame?
                                    ) {
                                        ReferenceCountUtil.release(frame)
                                    }

                                    override fun channelRead(
                                        ctx: ChannelHandlerContext?, frame: Http3DataFrame?
                                    ) {
                                        ReferenceCountUtil.release(frame)
                                    }

                                    override fun channelInputClosed(ctx: ChannelHandlerContext) {
                                        val headersFrame: Http3HeadersFrame = DefaultHttp3HeadersFrame()
                                        headersFrame.headers().status("404")
                                        headersFrame.headers().add("server", "netty")
                                        headersFrame.headers().addInt("content-length", CONTENT.size)
                                        ctx.write(headersFrame)
                                        ctx.writeAndFlush(
                                            DefaultHttp3DataFrame(
                                                Unpooled.wrappedBuffer(CONTENT)
                                            )
                                        )
                                            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                                    }
                                })
                            }
                        })
                )
            }
        }).build()
    try {
        val bs = Bootstrap()
        val channel = bs.group(group)
            .channel(NioDatagramChannel::class.java)
            .handler(codec)
            .bind(InetSocketAddress(PORT)).sync().channel()
        channel.closeFuture().sync()
    } finally {
        group.shutdownGracefully()
    }
}
