package com.vuravision.classroom

import kotlin.math.*

/** A time-based follower, independent of pressure and input sampling frequency. */
class StrokeSmoother(start:Point) {
    private var x=start.x;private var y=start.y;private var time=start.t
    private var target=start.copy()
    val pending get()=hypot(target.x-x,target.y-y)>.3f
    fun target(point:Point){target=point.copy()}
    fun advance(now:Long):Point? {
        val dt=(now-time).coerceIn(1,80);time=now
        val a=(1-exp(-dt/65.0)).toFloat()
        val nx=x+(target.x-x)*a;val ny=y+(target.y-y)*a
        if(hypot(nx-x,ny-y)<.08f)return null
        x=nx;y=ny;return Point(x,y,now)
    }
    fun finish(end:Point):List<Point> {
        target(end);val result=mutableListOf<Point>()
        repeat(8){advance(time+16)?.let{result.add(it)}}
        if(hypot(target.x-x,target.y-y)>.08f)result.add(target.copy())
        return result
    }
}
