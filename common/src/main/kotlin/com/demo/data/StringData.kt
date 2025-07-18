package com.demo.data

import org.apache.commons.lang3.RandomStringUtils

object StringData {
    val VALUES = listOf("foo", "bar", "baz")

    fun String.asResponse(): String {
        if (this.length > 3) {
            return this
        }
        return List(3) { this.uppercase() }.joinToString("_")
    }

    fun randomText(count: Int): String {
        return RandomStringUtils.insecure().nextAlphanumeric(count)
    }
}
