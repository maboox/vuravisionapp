package com.vuravision.classroom

import kotlin.math.max
import kotlin.math.min

object MindMap {
    /** Ink belongs to a node when its first sample is inside that node. */
    fun attach(page:Page,stroke:Item,firstX:Float,firstY:Float):Boolean {
        val node=page.items.asReversed().firstOrNull {
            it.kind=="sticky" && it.shape=="mindnode" && it.pane==stroke.pane &&
                page.editable(it) && !it.locked && firstX>=it.x && firstX<=it.x+it.w &&
                firstY>=it.y && firstY<=it.y+it.h
        }?:return false
        stroke.parentNode=node.id;stroke.layerId=node.layerId
        val padding=12f
        val left=min(node.x,stroke.x-padding);val top=min(node.y,stroke.y-padding)
        val right=max(node.x+node.w,stroke.x+stroke.w+padding)
        val bottom=max(node.y+node.h,stroke.y+stroke.h+padding)
        node.x=left;node.y=top;node.w=right-left;node.h=bottom-top
        return true
    }

    fun group(page:Page,roots:List<Item>):List<Item> {
        val ids=roots.mapTo(mutableSetOf()){it.id}
        var expanded=true
        while(expanded){expanded=false
            page.items.forEach{child->if(child.parentNode in ids && ids.add(child.id))expanded=true}
        }
        return page.items.filter{it.id in ids && page.editable(it) && !it.locked}
    }
}
