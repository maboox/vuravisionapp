package com.vuravision.classroom

import org.junit.Test
import org.junit.Assert.*

class UnitConversionTest {
    @Test fun lengthMassAndPersianDigits(){
        assertEquals("12 in = 30.48 cm",UnitConversion.result("12 inch to cm"))
        assertEquals("12 in = 30.48 cm",UnitConversion.result("12 in to cm"))
        assertEquals("2 kg = 2000 g",UnitConversion.result("۲ کیلوگرم به گرم"))
        assertEquals("1.5 kg = 1500 g",UnitConversion.result("۱٫۵ kg to gram"))
        assertEquals("1 ft = 12 in",UnitConversion.result("1 foot to inches"))
    }
    @Test fun temperaturesUseOffsets(){
        assertEquals("32 °F = 0 °C",UnitConversion.result("32 F to C"))
        assertEquals("-40 °C = -40 °F",UnitConversion.result("-40 celsius to fahrenheit"))
        assertEquals("0 °C = 273.15 K",UnitConversion.result("0 C to K"))
    }
    @Test fun areaVolumeSpeedTime(){
        assertEquals("1 m² = 10000 cm²",UnitConversion.result("1 m2 to cm2"))
        assertEquals("2 L = 2000 ml",UnitConversion.result("2 liters to ml"))
        assertEquals("36 km/h = 10 m/s",UnitConversion.result("36 km/h to m/s"))
        assertEquals("1.5 h = 90 min",UnitConversion.result("1.5 hours to minutes"))
    }
    @Test fun invalidConversionsDoNotProducePlausibleAnswers(){
        for(value in listOf("12 kg to cm","12 nonsense to cm","-1 K to C","12 in","NaN kg to g")){
            assertThrows(IllegalArgumentException::class.java){UnitConversion.result(value)}
        }
    }
}
