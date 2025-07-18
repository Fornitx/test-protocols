package com.demo.quic

import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.resourceScope
import com.demo.constants.NET.PORT
import com.demo.data.StringData.asResponse
import com.demo.logging.ClientLogger
import com.demo.logging.ServerLogger
import com.demo.quic.NettyUtils.CLIENT_SSL_CONTEXT
import com.demo.quic.NettyUtils.SERVER_SSL_CONTEXT
import com.demo.test.MEASUREMENTS
import com.demo.test.REPEATS
import com.demo.test.StopWatchKt
import com.demo.test.average
import com.demo.test.printStats
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ChannelInputShutdownReadComplete
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicClientCodecBuilder
import io.netty.incubator.codec.quic.QuicServerCodecBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.incubator.codec.quic.QuicStreamType
import io.netty.util.NetUtil
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private val log = KotlinLogging.logger {}

typealias ServerResources = Pair<Channel, NioEventLoopGroup>
typealias ClientResources = Pair<Channel, NioEventLoopGroup>

class QuicNettyTimeTests {
    private suspend fun ResourceScope.quicServer(udpPort: Int): ServerResources = install(
        acquire = {
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
//            .handler(object : ChannelInboundHandlerAdapter() {
//
//            })
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

            val bs = Bootstrap()
            val channel = bs.group(group)
                .channel(NioDatagramChannel::class.java)
                .handler(codec)
                .bind(InetSocketAddress(udpPort))
                .sync()
                .channel()
            channel to group
        },
        release = { serverResources, _ ->
            val (channel, group) = serverResources
            channel.close().sync()
            group.shutdownGracefully().sync()
        },
    )

    @Test
    @Order(1)
    fun connectionOnlyTest() = runResourceTest {
        val measurements = mutableListOf<Duration>()
        val stopWatch = StopWatchKt.createUnstarted()
        repeat(MEASUREMENTS) {
            stopWatch.reset()
            repeat(REPEATS) {
                val group = NioEventLoopGroup(1)
                val codec = QuicClientCodecBuilder()
                    .sslContext(CLIENT_SSL_CONTEXT)
                    .maxIdleTimeout(5_000, TimeUnit.MILLISECONDS)
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

                stopWatch.start()
                val quicChannel = QuicChannel.newBootstrap(channel)
                    .streamHandler(object : ChannelInboundHandlerAdapter() {
                        override fun channelActive(ctx: ChannelHandlerContext) {
                            ctx.close()
                        }
                    })
                    .remoteAddress(InetSocketAddress(NetUtil.LOCALHOST4, PORT))
                    .connect()
                    .get()
                stopWatch.stop()

                quicChannel.close().sync()
                channel.close().sync()
                group.shutdownGracefully()
            }
            stopWatch.durationKt.also(measurements::add).also { println("timeTaken: $it") }
        }
        measurements.printStats()
    }

    @ParameterizedTest
    @ValueSource(ints = [1_000/*, 10_000, 100_000*/])
    @Order(2)
    fun onePacketTest(count: Int) = runResourceTest {
        val group = NioEventLoopGroup(1)
        val codec = QuicClientCodecBuilder()
            .sslContext(CLIENT_SSL_CONTEXT)
            .maxIdleTimeout(5_000, TimeUnit.MILLISECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(10_000_000)
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

        val measurements = mutableListOf<Duration>()
        repeat(MEASUREMENTS) {
            val stopWatch = StopWatchKt.createUnstarted()
            repeat(REPEATS) {
                val streamChannel =
                    quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, object : ChannelInboundHandlerAdapter() {
                        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                            val byteBuf = msg as ByteBuf
                            ClientLogger.log(byteBuf.toString(Charsets.UTF_8))
                            stopWatch.stop()
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
                stopWatch.start()
                streamChannel.writeAndFlush(Unpooled.copiedBuffer("abc", Charsets.UTF_8))
                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                streamChannel.closeFuture().sync()
//                streamChannel.close().sync()
            }
            val timeTaken = stopWatch.durationKt
            println("timeTaken: $timeTaken")
            measurements.add(timeTaken)
        }
        println("average time: ${measurements.average()}")
        quicChannel.close().sync()
        channel.close().sync()
        group.shutdownGracefully()
    }

    private fun runResourceTest(block: ResourceScope.() -> Unit) = runTest {
        resourceScope {
            quicServer(PORT)
            block()
        }
    }
}
