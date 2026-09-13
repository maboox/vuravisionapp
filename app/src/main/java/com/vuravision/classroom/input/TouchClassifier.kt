package com.vuravision.classroom.input

data class TouchSample(val major:Float,val minor:Float,val pressure:Float,val size:Float,val toolType:Int)
data class CalibrationProfile(val thinMaxMajor:Float=18f,val palmMinMajor:Float=55f,val fingerMinMajor:Float=19f)
enum class TouchClass{THIN_TIP,FINGER_OR_THICK,PALM,UNKNOWN}
class TouchClassifier(private val profile:CalibrationProfile){
    fun classify(s:TouchSample):TouchClass=when{
        s.major<=0f && s.size<=0f -> TouchClass.UNKNOWN
        s.major>=profile.palmMinMajor -> TouchClass.PALM
        s.major in 0.1f..profile.thinMaxMajor -> TouchClass.THIN_TIP
        s.major>=profile.fingerMinMajor -> TouchClass.FINGER_OR_THICK
        else -> TouchClass.UNKNOWN
    }
}
