package com.demo.test

import com.google.common.base.Stopwatch
import org.apache.commons.math4.legacy.stat.descriptive.DescriptiveStatistics
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration
import kotlin.time.toKotlinDuration

const val MEASUREMENTS = 20
const val REPEATS = 100

class StopWatchKt(private val stopwatch: Stopwatch) {
    val duration: java.time.Duration
        get() = stopwatch.elapsed()
    val durationKt: kotlin.time.Duration
        get() = stopwatch.elapsed().toKotlinDuration()

    fun start(): StopWatchKt = this.also { stopwatch.start() }

    fun stop(): StopWatchKt = this.also { stopwatch.stop() }

    fun reset(): StopWatchKt = this.also { stopwatch.reset() }

    fun <T> record(block: () -> T): T {
        stopwatch.start()
        try {
            return block()
        } finally {
            stopwatch.stop()
        }
    }

    override fun toString(): String = stopwatch.toString()

    companion object {
        fun createUnstarted(): StopWatchKt = StopWatchKt(Stopwatch.createUnstarted())
//        fun createStarted(): StopWatchKt = StopWatchKt(Stopwatch.createStarted())
    }
}

@Deprecated("")
fun measureTime(repeats: Int, block: () -> Unit) {
    List(repeats) {
        measureTime {
            block()
        }.also { println("timeTaken: $it") }
    }.average().also { println("average time: $it") }
}

fun List<kotlin.time.Duration>.average(): kotlin.time.Duration {
    return this.asSequence()
        .map { it.toLong(DurationUnit.NANOSECONDS) }
        .average()
        .toDuration(DurationUnit.NANOSECONDS)
}

fun List<kotlin.time.Duration>.printStats() {
    val stats = DescriptiveStatistics()
    forEach { stats.addValue(it.toDouble(DurationUnit.NANOSECONDS)) }
    println("Mean: ${stats.mean.toDuration(DurationUnit.NANOSECONDS)}")
    println("Median: ${stats.getPercentile(50.0).toDuration(DurationUnit.NANOSECONDS)}")
}
