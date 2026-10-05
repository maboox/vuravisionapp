package com.vuravision.classroom

import android.content.Context
import android.graphics.Canvas
import android.view.View

/** ListView binds only visible page previews. No full-document bitmap allocation. */
class PageThumbnail(context:Context,private val renderer:Renderer):View(context){
    var page:Page?=null
        set(value){field=value;invalidate()}
    override fun onDraw(canvas:Canvas){page?.let{renderer.page(canvas,it,width,height,false)}}
}
