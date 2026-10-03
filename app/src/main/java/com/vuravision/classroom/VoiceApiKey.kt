package com.vuravision.classroom

/** A credential is opaque. Only Google can determine whether it is valid. */
object VoiceApiKey {
    // Bound memory/header size without assuming a provider prefix or token length.
    const val MAX_LENGTH = 8192
    private fun boundary(c:Char) = c.isWhitespace() || Character.isSpaceChar(c) ||
        c in "\u200B\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069\uFEFF"

    fun normalize(pasted:String):String {
        val value=pasted.trim(::boundary)
        if(value.length<2)return value
        val wrapped=when(value.first()){
            '\'' -> value.last()=='\''
            '"' -> value.last()=='"'
            '`' -> value.last()=='`'
            '\u201C' -> value.last()=='\u201D'
            '\u2018' -> value.last()=='\u2019'
            else -> false
        }
        return if(wrapped)value.substring(1,value.length-1).trim(::boundary) else value
    }

    /** Safe to put in an HTTP header; do not change any interior credential bytes. */
    fun isAcceptable(value:String):Boolean = value.isNotEmpty() && value.length<=MAX_LENGTH &&
        value.all{it.code in 0x21..0x7e} &&
        !Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(value) &&
        !value.startsWith("www.",ignoreCase=true)
}
