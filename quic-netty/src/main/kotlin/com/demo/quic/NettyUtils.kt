package com.demo.quic

import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup

object NettyUtils {
    fun Pair<Channel, NioEventLoopGroup>.use(block: () -> Unit) {
        try {
            block()
        } finally {
            this.first.close().sync()
            this.second.shutdownGracefully().sync()
        }
    }
}
