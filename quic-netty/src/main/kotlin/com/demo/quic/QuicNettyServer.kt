package com.demo.quic

import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import com.demo.quic.NettyUtils.SERVER_SSL_CONTEXT
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler
import io.netty.incubator.codec.quic.QuicServerCodecBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

fun main() {
    val group = NioEventLoopGroup(1)
    val codec = QuicServerCodecBuilder()
        .sslContext(SERVER_SSL_CONTEXT)
        .maxIdleTimeout(5_000, TimeUnit.MILLISECONDS)
        .initialMaxData(10_000_000)
        .initialMaxStreamDataBidirectionalLocal(10_000_000)
        .initialMaxStreamDataBidirectionalRemote(10_000_000)
        .initialMaxStreamDataUnidirectional(10_000_000)
        .initialMaxStreamsBidirectional(100)
        .initialMaxStreamsUnidirectional(100)
        .activeMigration(true)
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
//        .handler(object : ChannelInboundHandlerAdapter() {
//            override fun channelActive(ctx: ChannelHandlerContext) {
//                val channel = ctx.channel() as QuicChannel
//                channel.createStream(QuicStreamType.BIDIRECTIONAL, object : ChannelHandlerAdapter() {
//                })
//            }

//            override fun channelInactive(ctx: ChannelHandlerContext?) {
//                (ctx!!.channel() as QuicChannel).collectStats().addListener(GenericFutureListener { f ->
//                    if (f.isSuccess) {
//                        log.info("Connection closed: {}", f.getNow())
//                    }
//                })
//            }

//            override fun isSharable(): Boolean = true
//        })
        .streamHandler(object : ChannelInitializer<QuicStreamChannel>() {
            override fun initChannel(ch: QuicStreamChannel) {
                ch.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                        val byteBuf = msg as ByteBuf
                        try {
                            val request = byteBuf.toString(Charsets.UTF_8)
                            ServerLogger.log(request)
                            val buffer = ctx.alloc().directBuffer()
                            buffer.writeCharSequence(request.asResponse(), Charsets.UTF_8)
                            ctx.writeAndFlush(buffer).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                        } finally {
                            byteBuf.release()
                        }
                    }
                })
            }
        })
        .build()

    try {
        val bs = Bootstrap()
        val channel = bs.group(group)
            .channel(NioDatagramChannel::class.java)
            .handler(codec)
            .bind(InetSocketAddress(PORT))
            .sync()
            .channel()
        channel.closeFuture().sync()
    } finally {
        group.shutdownGracefully()
    }
}
