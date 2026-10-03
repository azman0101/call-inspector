package net.slashetc.callinspector.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallLogRepositoryBenchmarkTest {

    private lateinit var repository: CallLogRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = CallLogRepository(context)
    }

    @Test
    fun benchmarkGenerateSampleCalls() = runBlocking {
        // Warm up
        repository.generateSampleCalls()

        val iterations = 50
        val totalTimeNanos = measureNanoTime {
            repeat(iterations) {
                repository.generateSampleCalls()
            }
        }

        val avgTimeMs = (totalTimeNanos / iterations.toDouble()) / 1_000_000.0
        println("BENCHMARK_RESULT: generateSampleCalls average time per call over $iterations iterations: %.3f ms".format(avgTimeMs))
    }
}
