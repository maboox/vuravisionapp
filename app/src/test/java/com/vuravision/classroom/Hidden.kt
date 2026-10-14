package com.vuravision.classroom

/**
 * Calls a non-public MainActivity function by name for tests, wherever it lives after the split into feature files
 * (item 11): a member (internal members carry a "$module" suffix on the JVM) or an extension in MainActivity*Kt.
 */
fun MainActivity.callHidden(name: String, vararg args: Any?): Any? {
    // The function itself, or its internal JVM name "name${'$'}module"; never Kotlin's synthetic "${'$'}lambda" / "${'$'}default" helpers.
    fun matches(n: String) = n == name || (n.startsWith("$name$") && n.count { it == '$' } == 1 && "lambda" !in n && "default" !in n)
    MainActivity::class.java.declaredMethods.firstOrNull { matches(it.name) && it.parameterCount == args.size }?.let {
        it.isAccessible = true; return it.invoke(this, *args)
    }
    for (file in listOf("Ai", "ClassTools", "Settings", "Files", "Pdf", "Voice")) {
        val type = Class.forName("com.vuravision.classroom.MainActivity${file}Kt")
        type.declaredMethods.firstOrNull { matches(it.name) && it.parameterCount == args.size + 1 }?.let {
            it.isAccessible = true; return it.invoke(null, this, *args)
        }
    }
    throw NoSuchMethodException(name)
}
