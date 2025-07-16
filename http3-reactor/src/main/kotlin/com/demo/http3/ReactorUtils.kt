package com.demo.http3

import reactor.netty.DisposableServer

object ReactorUtils {
    fun DisposableServer.use(block: () -> Unit) {
        try {
            block()
        } finally {
            this.dispose()
        }
    }
}
