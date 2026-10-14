package com.vuravision.classroom.labs
import android.content.Context
import com.vuravision.classroom.ui.tr

object LabCatalog {
    data class Topic(val id: String, val subject: String, val en: String, val fa: String, val ids: Set<String>)
    val topics = listOf(
        Topic("mechanics", "physics", "Mechanics", "مکانیک", "orbit pendulum projectile spring collision incline freefall energy".split(" ").toSet()),
        Topic("waves", "physics", "Waves and sound", "موج و صدا", "standing doppler interference".split(" ").toSet()),
        Topic("optics", "physics", "Light and relativity", "نور و نسبیت", "lens lightclock".split(" ").toSet()),
        Topic("matter", "physics", "Matter, heat and pressure", "ماده، گرما و فشار", "gas buoyancy heat pressure".split(" ").toSet()),
        Topic("electricity", "physics", "Electricity", "الکتریسیته", "circuit".split(" ").toSet()),
        Topic("atomic", "chem", "Atoms and bonding", "اتم و پیوند", "molecule atom periodic periodic_table bonding".split(" ").toSet()),
        Topic("solutions", "chem", "Solutions and transport", "محلول و انتقال", "states ph solubility titration diffusion osmosis density dilution".split(" ").toSet()),
        Topic("reactions", "chem", "Reactions", "واکنش‌ها", "reaction halflife electrolysis flame equilibrium catalyst".split(" ").toSet()),
        Topic("geometry", "math", "Geometry", "هندسه", "circle fractal golden pythagoras polypi koch triangle_area trig".split(" ").toSet()),
        Topic("functions", "math", "Functions and waves", "تابع و موج", "waves quadratic tangent fourier lissajous series".split(" ").toSet()),
        Topic("numbers", "math", "Numbers and algorithms", "اعداد و الگوریتم", "primes pascal collatz modcircle sorting".split(" ").toSet()),
        Topic("probability", "stats", "Probability", "احتمال", "coins dice montecarlo monty birthday bayes streaks ruin".split(" ").toSet()),
        Topic("data", "stats", "Data analysis", "تحلیل داده", "clt regression benford median sampling simpson statistics".split(" ").toSet()),
        Topic("random", "stats", "Random processes", "فرایندهای تصادفی", "galton walk markov".split(" ").toSet())
    )
    val subjects = listOf("physics", "chem", "math", "stats")

    /**
     * Rough school level, for the gallery filter: "middle" (grades 7-9: one quantity at a time, no trigonometry or
     * calculus) or "high" (grades 10-12). Anything not listed is high school. A guide for finding things, not a syllabus.
     */
    private val middle = (
        "states density ph atom buoyancy pressure heat circuit lens diffusion flame electrolysis solubility " +
            "pythagoras primes coins dice median collatz modcircle sorting fractal golden polypi streaks freefall periodic periodic_table " +
            "osmosis energy triangle_area statistics koch"
        ).split(" ").toSet()
    fun level(key: String) = if (key.removePrefix("native_") in middle)"middle" else "high"
    fun levelName(context: Context, level: String) = if (level == "middle")context.tr("Grades 7–9", "پایهٔ هفتم تا نهم")else context.tr("Grades 10–12", "پایهٔ دهم تا دوازدهم")
    fun topic(key: String) = topics.first { key.removePrefix("native_") in it.ids }
    fun name(context: Context, subject: String) = when (subject) {
        "physics" -> context.tr("Physics", "فیزیک")
        "chem" -> context.tr("Chemistry", "شیمی")
        "math" -> context.tr("Mathematics", "ریاضی")
        else -> context.tr("Statistics", "آمار")
    }
    fun topicName(context: Context, topic: Topic) = context.tr(topic.en, topic.fa)
}
