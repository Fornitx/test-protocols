package com.demo.logging

import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

object ClientLogger {
    fun log(any: Any?) {
        log.info { "Client received: '$any'" }
    }
}
