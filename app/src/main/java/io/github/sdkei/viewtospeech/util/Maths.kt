package io.github.sdkei.viewtospeech.util

import kotlin.math.floor

/**
 * [this] に [other] を掛けて端数を切り捨てた値を返す。
 */
fun Int.timesFloor(other: Float): Int = floor(this * other).toInt()
