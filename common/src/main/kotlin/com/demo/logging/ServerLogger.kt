package com.demo.logging

import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

object ServerLogger {
    fun log(any: Any?) {
        log.info { "Server received: '$any'" }
    }
}
