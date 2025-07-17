package com.demo.test

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration

const val MEASUREMENTS = 20
const val REPEATS = 100

fun measureTime(repeats: Int, block: () -> Unit) {
    List(repeats) {
        measureTime {
            block()
        }.also { println("timeTaken: $it") }
    }.average().also { println("average time: $it") }
}

private fun List<Duration>.average(): Duration {
    return this.asSequence()
        .map { it.toLong(DurationUnit.NANOSECONDS) }
        .average()
        .toDuration(DurationUnit.NANOSECONDS)
}
