package com.demo.quic

import com.demo.constants.NET.PORT
import com.demo.constants.QUIC.PROTOCOL
import com.demo.constants.TLS
import com.demo.constants.TLS.PASSWORD
import com.demo.data.StringData.asResponse
import com.demo.logging.ServerLogger
import com.demo.quic.NettyUtils.use
import com.demo.test.MEASUREMENTS
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicClientCodecBuilder
import io.netty.incubator.codec.quic.QuicServerCodecBuilder
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.util.NetUtil
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime

private val log = KotlinLogging.logger {}

class QuicNettyTimeTests {
    private fun startServer(udpPort: Int): Pair<Channel, NioEventLoopGroup> {
        val sslContext = QuicSslContextBuilder.forServer(
            TLS.SERVER_KEYMANAGERFACTORY, PASSWORD
        )
            .trustManager(TLS.SERVER_TRUSTMANAGERFACTORY)
            .applicationProtocols(PROTOCOL)
            .build()
        val group = NioEventLoopGroup(1)
        val codec = QuicServerCodecBuilder().sslContext(sslContext)
            .maxIdleTimeout(5_000, TimeUnit.MILLISECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(1_000_000)
            .initialMaxStreamDataBidirectionalRemote(1_000_000)
            .initialMaxStreamsBidirectional(100)
            .initialMaxStreamsUnidirectional(100)
            .activeMigration(true)
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
                                ctx.writeAndFlush(buffer)
                            } finally {
                                byteBuf.release()
                            }
                        }
                    })
                }
            })
            .build()

//        try {
        val bs = Bootstrap()
        val channel = bs.group(group)
            .channel(NioDatagramChannel::class.java)
            .handler(codec)
            .bind(InetSocketAddress(udpPort)).sync().channel()
        return channel to group
//            channel.closeFuture().sync()
//        } finally {
//            group.shutdownGracefully()
//        }
    }

    @Test
    @Order(1)
    fun connectionOnlyTest() {
        startServer(PORT).use {
            val context = QuicSslContextBuilder.forClient()
                .keyManager(TLS.CLIENT_KEYMANAGERFACTORY, PASSWORD)
                .trustManager(TLS.CLIENT_TRUSTMANAGERFACTORY)
//                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(PROTOCOL)
                .build()
            val group = NioEventLoopGroup(1)
            repeat(MEASUREMENTS) {
                measureTime {
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

//                val streamChannel = quicChannel.createStream(
//                    QuicStreamType.BIDIRECTIONAL,
//                    object : ChannelInboundHandlerAdapter() {
//                        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
//                            val byteBuf = msg as ByteBuf
//                            ClientLogger.com.demo.quic.log(byteBuf.toString(Charsets.UTF_8))
//                            byteBuf.release()
//                        }
//
//                        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
//                            com.demo.quic.log.info { "userEventTriggered: $evt" }
//                            if (evt === ChannelInputShutdownReadComplete.INSTANCE) {
//                                (ctx.channel().parent() as QuicChannel).close(
//                                    true, 0,
//                                    ctx.alloc().directBuffer(16)
//                                        .writeBytes(
//                                            byteArrayOf(
//                                                'k'.code.toByte(),
//                                                't'.code.toByte(),
//                                                'h'.code.toByte(),
//                                                'x'.code.toByte(),
//                                                'b'.code.toByte(),
//                                                'y'.code.toByte(),
//                                                'e'.code.toByte()
//                                            )
//                                        )
//                                )
//                            }
//                        }
//                    }).sync().getNow()
//
//                for ((index, value) in StringData.VALUES.withIndex()) {
//                    streamChannel.writeAndFlush(Unpooled.copiedBuffer(value, Charsets.UTF_8)).apply {
//                        if (index == StringData.VALUES.size - 1) {
//                            addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
//                        }
//                    }
//                    TimeUnit.SECONDS.sleep(1)
//                }

//                streamChannel.closeFuture().sync()
                    quicChannel.close().sync()
                    channel.close().sync()
                }.also { println("timeTaken: $it") }
            }
            group.shutdownGracefully()
        }
    }
}
