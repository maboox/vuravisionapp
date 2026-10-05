package com.vuravision.classroom

import java.util.Locale
import kotlin.math.abs

object DisplayNumbers {
    fun one(value:Double):String {
        if(!value.isFinite())return "—"
        val n=if(abs(value)<.05)0.0 else value
        return String.format(Locale.ROOT,"%.1f",n).removeSuffix(".0")
    }
}
