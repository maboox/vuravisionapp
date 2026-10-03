package com.vuravision.classroom

import java.net.URLEncoder

object GoogleSearchQuery {
    const val MAX_LENGTH=4096
    fun url(query:String):String {
        val value=query.trim()
        require(value.isNotEmpty() && value.length<=MAX_LENGTH){"Invalid search text"}
        return "https://www.google.com/search?q="+URLEncoder.encode(value,"UTF-8")
    }
}

data class SearchWindowBounds(val x:Int,val y:Int,val width:Int,val height:Int) {
    fun fit(hostWidth:Int,hostHeight:Int,minWidth:Int,minHeight:Int):SearchWindowBounds {
        val maxW=hostWidth.coerceAtLeast(1);val maxH=hostHeight.coerceAtLeast(1)
        val w=width.coerceIn(minWidth.coerceIn(1,maxW),maxW)
        val h=height.coerceIn(minHeight.coerceIn(1,maxH),maxH)
        return SearchWindowBounds(x.coerceIn(0,maxW-w),y.coerceIn(0,maxH-h),w,h)
    }
}
