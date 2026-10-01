package io.justtrack.workManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal interface CoroutineTimeTicker {
    fun startTicking(updateInterval: Long, onTick: suspend (Long) -> Unit)
    fun stopTicking()
}

internal class DefaultCoroutineTimeTicker : CoroutineTimeTicker {
    private var job: Job? = null

    override fun startTicking(updateInterval: Long, onTick: suspend (Long) -> Unit) {
        job?.cancel()

        job = CoroutineScope(Dispatchers.IO).launch {
            var timeElapsed = 0L
            while (isActive) {
                delay(updateInterval)
                timeElapsed += updateInterval
                onTick(timeElapsed)
            }
        }
    }

    override fun stopTicking() {
        job?.cancel()
    }
}
