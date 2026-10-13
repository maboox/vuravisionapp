package com.vuravision.classroom

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Lab captions and slider names were English even in the Persian interface. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class LabTextTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test @Config(qualifiers="fa") fun persianReplacesWordsAndKeepsNumbersAndFormulas(){
        assertEquals("جمع دو تاس سالم · 300 پرتاب مستقل",LabText.t(context,"Sum of two fair dice · 300 independent throws"))
        assertEquals("تعداد توپ",LabText.t(context,"Balls"))
        val roots=LabText.t(context,"Discriminant = -3: no real roots");assertTrue(roots,roots.startsWith("دلتا = -3"));assertFalse(roots,roots.contains("roots"))
        assertTrue(LabText.t(context,"g (m/s²)")=="g (m/s²)")
    }
    @Test @Config(qualifiers="en") fun englishIsUnchanged(){
        assertEquals("Sum of two fair dice · 300 independent throws",LabText.t(context,"Sum of two fair dice · 300 independent throws"))
    }
    /** Every slider name of every lab has a Persian name, unless it is only a symbol or unit (L (m), g (m/s²), n). */
    @Test @Config(qualifiers="fa") fun everySliderWithWordsIsTranslated(){
        val words=Regex("[A-Za-z]{3,}")
        Labs.keys.filter{it!="periodic_table"}.flatMap{Labs.controls(it)}.map{it.label}.distinct().forEach{label->
            val shown=LabText.t(context,label)
            assertFalse("untranslated slider '$label' → '$shown'",words.findAll(shown).any{it.value !in setOf("NaOH","HCl","atm","mol")})
        }
    }
}
