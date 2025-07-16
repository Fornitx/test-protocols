package com.demo.quic

import com.demo.constants.PORT
import com.demo.constants.PROTOCOL
import com.demo.data.StringData
import com.demo.logging.ClientLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ChannelInputShutdownReadComplete
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicClientCodecBuilder
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.incubator.codec.quic.QuicStreamType
import io.netty.util.NetUtil
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

fun main() {
    val context = QuicSslContextBuilder.forClient()
//        .keyManager(CLIENT_KEY_MANAGER_FACTORY, PASSWORD.concatToString())
//        .trustManager(TRUST_MANAGER_FACTORY)
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .applicationProtocols(PROTOCOL)
        .build()
    val group = NioEventLoopGroup(1)
    try {
        val codec = QuicClientCodecBuilder()
            .sslContext(context)
            .maxIdleTimeout(5_000, TimeUnit.MILLISECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(1_000_000)
            .build()

        val bs = Bootstrap()
        val channel = bs.group(group)
            .channel(NioDatagramChannel::class.java)
            .handler(codec)
            .bind(0)
            .sync()
            .channel()

        val quicChannel = QuicChannel.newBootstrap(channel)
            .streamHandler(object : ChannelInboundHandlerAdapter() {
                override fun channelActive(ctx: ChannelHandlerContext) {
                    ctx.close()
                }
            })
            .remoteAddress(InetSocketAddress(NetUtil.LOCALHOST4, PORT))
            .connect()
            .get()

        val streamChannel = quicChannel.createStream(
            QuicStreamType.BIDIRECTIONAL,
            object : ChannelInboundHandlerAdapter() {
                override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                    val byteBuf = msg as ByteBuf
                    ClientLogger.log(byteBuf.toString(Charsets.UTF_8))
                    byteBuf.release()
                }

                override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
                    log.info { "userEventTriggered: $evt" }
                    if (evt === ChannelInputShutdownReadComplete.INSTANCE) {
                        (ctx.channel().parent() as QuicChannel).close(
                            true, 0,
                            ctx.alloc().directBuffer(16)
                                .writeBytes(
                                    byteArrayOf(
                                        'k'.code.toByte(),
                                        't'.code.toByte(),
                                        'h'.code.toByte(),
                                        'x'.code.toByte(),
                                        'b'.code.toByte(),
                                        'y'.code.toByte(),
                                        'e'.code.toByte()
                                    )
                                )
                        )
                    }
                }
            }).sync().getNow()

        for ((index, value) in StringData.VALUES.withIndex()) {
            streamChannel.writeAndFlush(Unpooled.copiedBuffer(value, Charsets.UTF_8)).apply {
                if (index == StringData.VALUES.size - 1) {
                    addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                }
            }
            TimeUnit.SECONDS.sleep(1)
        }

        streamChannel.closeFuture().sync()
        quicChannel.closeFuture().sync()
        channel.close().sync()
    } finally {
        group.shutdownGracefully()
    }
}
