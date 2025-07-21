package com.demo.http3

import com.demo.constants.NET.PORT
import com.demo.http3.NettyUtils.CLIENT_SSL_CONTEXT
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame
import io.netty.incubator.codec.http3.Http3
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler
import io.netty.incubator.codec.http3.Http3DataFrame
import io.netty.incubator.codec.http3.Http3HeadersFrame
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.util.CharsetUtil
import io.netty.util.NetUtil
import io.netty.util.ReferenceCountUtil
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

fun main() {
    val group = NioEventLoopGroup(1)
    try {
        val codec = Http3.newQuicClientCodecBuilder()
            .sslContext(CLIENT_SSL_CONTEXT)
            .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(10_000_000)
            .initialMaxStreamDataBidirectionalRemote(10_000_000)
            .initialMaxStreamDataUnidirectional(10_000_000)
            .initialMaxStreamsBidirectional(100)
            .initialMaxStreamsUnidirectional(100)
            .build()

        val bs = Bootstrap()
        val channel = bs.group(group)
            .channel(NioDatagramChannel::class.java)
            .handler(codec)
            .bind(0)
            .sync()
            .channel()

        val quicChannel = QuicChannel.newBootstrap(channel)
            .handler(Http3ClientConnectionHandler())
            .remoteAddress(InetSocketAddress(NetUtil.LOCALHOST4, PORT))
            .connect()
            .get()

        val streamChannel = Http3.newRequestStream(
            quicChannel,
            object : Http3RequestStreamInboundHandler() {
                override fun channelRead(ctx: ChannelHandlerContext, frame: Http3HeadersFrame) {
                    ReferenceCountUtil.release(frame)
                }

                override fun channelRead(ctx: ChannelHandlerContext, frame: Http3DataFrame) {
                    System.err.print(frame.content().toString(CharsetUtil.UTF_8))
                    ReferenceCountUtil.release(frame)
                }

                override fun channelInputClosed(ctx: ChannelHandlerContext) {
                    ctx.close()
                }
            }).sync().getNow()

        // Write the Header frame and send the FIN to mark the end of the request.
        // After this its not possible anymore to write any more data.
        val frame: Http3HeadersFrame = DefaultHttp3HeadersFrame()
        frame.headers().method("GET").path("/")
            .authority(NetUtil.LOCALHOST4.hostAddress + ":" + PORT)
            .scheme("https")
        streamChannel.writeAndFlush(frame)
            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync()

        // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
        // After this is done we will close the underlying datagram channel.
        streamChannel.closeFuture().sync()

        // After we received the response lets also close the underlying QUIC channel and datagram channel.
        quicChannel.close().sync()
        channel.close().sync()
    } finally {
        group.shutdownGracefully()
    }
}
