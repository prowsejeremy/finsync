package com.jpd.hz.tags

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * For the device checklist: how long one read takes, per format. It never fails; read the
 * results with `adb logcat -d -s hztags-timing`.
 */
@RunWith(AndroidJUnit4::class)
class TagLibTimingTest {

    companion object {
        private const val LOG_TAG = "hztags-timing"
        private const val READS = 21
        private const val NANOS_PER_MILLI = 1_000_000.0
    }

    private val files = SampleFiles()

    @Before
    fun setUp() = files.reset()

    @After
    fun tearDown() {
        files.directory.deleteRecursively()
    }

    @Test
    fun logsTheMedianReadTimePerFormat() {
        for (sample in SAMPLES) {
            val path = files.copy(sample.name).path
            TagLibBridge.read(path, sample.extension)
            val times = List(READS) {
                val start = System.nanoTime()
                TagLibBridge.read(path, sample.extension)
                System.nanoTime() - start
            }.sorted()
            val medianMs = times[READS / 2] / NANOS_PER_MILLI
            Log.i(LOG_TAG, "${sample.name}: median %.2f ms over $READS reads".format(medianMs))
        }
    }
}
