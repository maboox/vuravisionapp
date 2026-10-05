package com.vuravision.classroom

/** Double tap, pause 1–3 seconds, double tap again. No persisted partial sequence. */
class AboutGesture {
    private var tap:Long?=null;private var pair:Long?=null
    fun click(now:Long):Boolean {
        val previous=tap
        if(previous!=null && now-previous in 0..350){
            tap=null
            val first=pair
            if(first!=null && previous-first in 1000..3000){pair=null;return true}
            pair=now
        }else{tap=now;if(pair?.let{now-it>3000}==true)pair=null}
        return false
    }
}
