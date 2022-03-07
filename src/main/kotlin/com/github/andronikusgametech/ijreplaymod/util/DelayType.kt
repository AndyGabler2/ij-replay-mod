package com.github.andronikusgametech.ijreplaymod.util

import java.util.concurrent.TimeUnit
import java.util.function.Function

enum class DelayType(
    val typeLabel: String,
    val converter: Function<Long, Long>
) {
    SECOND("second", { millis -> TimeUnit.MILLISECONDS.toSeconds(millis) }),
    MILLISECOND("millisecond", { millis -> TimeUnit.MILLISECONDS.toMillis(millis) })
}
