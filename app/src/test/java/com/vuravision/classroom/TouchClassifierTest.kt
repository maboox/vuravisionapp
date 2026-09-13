package com.vuravision.classroom
import com.vuravision.classroom.input.*
import org.junit.Assert.assertEquals
import org.junit.Test
class TouchClassifierTest{
 private val c=TouchClassifier(CalibrationProfile())
 @Test fun classifiesThinAndPalm(){assertEquals(TouchClass.THIN_TIP,c.classify(TouchSample(10f,8f,1f,.1f,0)));assertEquals(TouchClass.PALM,c.classify(TouchSample(80f,50f,1f,.8f,0)))}
}
