package com.vuravision.classroom

/** Overlapping pauses (background, rename dialog) stop time only once. */
class ActivityClock(private val time: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    private val reasons = mutableSetOf<String>()
    private var stoppedAt = 0L
    private var offset = 0L
    val paused get() = reasons.isNotEmpty()
    fun now() = (if (paused)stoppedAt else time()) - offset
    fun pause(reason: String, value: Boolean) {
        if (value) {
            if (reasons.isEmpty())stoppedAt = time()
            reasons.add(reason)
        } else if (reasons.remove(reason) && reasons.isEmpty()) {
            offset += time() - stoppedAt
        }
    }
}
