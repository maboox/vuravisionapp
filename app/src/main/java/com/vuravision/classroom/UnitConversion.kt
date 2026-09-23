package com.vuravision.classroom

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

/** Offline dimensional conversion, independent of the handwriting provider. */
object UnitConversion {
    private data class UnitDef(val name:String,val dimension:String,val scale:Double,val offset:Double=0.0)
    private val units=mutableMapOf<String,UnitDef>()
    private fun add(name:String,dimension:String,scale:Double,aliases:String,offset:Double=0.0){
        val unit=UnitDef(name,dimension,scale,offset)
        (aliases.split('|')+name).forEach{units[it.lowercase(Locale.ROOT)]=unit}
    }
    init {
        add("mm","length",.001,"millimeter|millimeters|millimetre|millimetres|میلی متر|میلی‌متر")
        add("cm","length",.01,"centimeter|centimeters|centimetre|centimetres|سانتی متر|سانتی‌متر")
        add("m","length",1.0,"meter|meters|metre|metres|متر")
        add("km","length",1000.0,"kilometer|kilometers|kilometre|kilometres|کیلومتر")
        add("in","length",.0254,"inch|inches|اینچ|\"")
        add("ft","length",.3048,"foot|feet|فوت|'")
        add("yd","length",.9144,"yard|yards|یارد")
        add("mi","length",1609.344,"mile|miles|مایل")
        add("mg","mass",.000001,"milligram|milligrams|میلی گرم|میلی‌گرم")
        add("g","mass",.001,"gram|grams|gr|گرم")
        add("kg","mass",1.0,"kilogram|kilograms|کیلوگرم|کیلو گرم")
        add("t","mass",1000.0,"tonne|tonnes|metric ton|تن")
        add("lb","mass",.45359237,"lbs|pound|pounds|پوند")
        add("oz","mass",.028349523125,"ounce|ounces|اونس")
        add("ml","volume",.001,"milliliter|milliliters|millilitre|millilitres|میلی لیتر|میلی‌لیتر")
        add("L","volume",1.0,"liter|liters|litre|litres|لیتر")
        add("m³","volume",1000.0,"m3|cubic meter|cubic meters|متر مکعب")
        add("s","time",1.0,"sec|second|seconds|ثانیه")
        add("min","time",60.0,"minute|minutes|دقیقه")
        add("h","time",3600.0,"hr|hour|hours|ساعت")
        add("day","time",86400.0,"days|روز")
        add("m²","area",1.0,"m2|square meter|square meters|متر مربع")
        add("cm²","area",.0001,"cm2|square centimeter|square centimeters")
        add("km²","area",1000000.0,"km2|square kilometer|square kilometers")
        add("ha","area",10000.0,"hectare|hectares|هکتار")
        add("m/s","speed",1.0,"متر بر ثانیه")
        add("km/h","speed",1.0/3.6,"kph|کیلومتر بر ساعت")
        add("mph","speed",.44704,"miles per hour")
        add("°C","temperature",1.0,"c|celsius|سانتیگراد|سلسیوس",273.15)
        add("°F","temperature",5.0/9,"f|fahrenheit|فارنهایت",273.15-32*5.0/9)
        add("K","temperature",1.0,"kelvin|کلوین")
    }
    fun result(input:String):String {
        val normalized=input.trim().map { c ->
            when(c){in '۰'..'۹'->'0'+(c-'۰');in '٠'..'٩'->'0'+(c-'٠');'٫'->'.';'−'->'-';else->c}
        }.joinToString("").lowercase(Locale.ROOT)
        val match=Regex("""^([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*(.+?)\s*(?:\bto\b|به|→|=)\s*(.+?)\s*\??$""").matchEntire(normalized)
            ?:throw IllegalArgumentException("Example: 12 inch to cm · 2 kg to g · 32 F to C")
        val value=match.groupValues[1].toDouble()
        require(value.isFinite()){"Value is too large"}
        val from=units[match.groupValues[2].trim()]?:throw IllegalArgumentException("Unknown source unit: ${match.groupValues[2]}")
        val to=units[match.groupValues[3].trim()]?:throw IllegalArgumentException("Unknown target unit: ${match.groupValues[3]}")
        require(from.dimension==to.dimension){"Units must measure the same quantity"}
        val base=value*from.scale+from.offset
        require(from.dimension!="temperature" || base>=-1e-9){"Temperature is below absolute zero"}
        val answer=(base-to.offset)/to.scale
        require(answer.isFinite()){"Result is too large"}
        fun format(n:Double)=if(kotlin.math.abs(n)<1e-12)"0" else BigDecimal.valueOf(n).round(MathContext(12)).stripTrailingZeros().toPlainString()
        return "${format(value)} ${from.name} = ${format(answer)} ${to.name}"
    }
}
