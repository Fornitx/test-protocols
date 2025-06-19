package com.demo.data

object StringData {
    val VALUES = listOf("foo", "bar", "baz")

    fun String.asResponse(): String {
        return List(3) { this.uppercase() }.joinToString("_")
    }
}
