package com.vuravision.classroom

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GeometryMindMapTest {
    @Test fun rotatedRulerProjectsNearbyInkOntoItsEdge(){
        val ruler=Item(kind="shape",shape="ruler",x=100f,y=100f,w=300f,h=50f,rotation=45f)
        val (x,y)=ruler.global(140f,0f)
        val contact=ruler.global(140f,-8f)
        val projected=GeometryTools.snap(ruler,contact.first,contact.second)
        assertNotNull(projected)
        assertEquals(x,projected!!.x,.01f);assertEquals(y,projected.y,.01f)
        val far=ruler.global(140f,-40f)
        assertNull(GeometryTools.snap(ruler,far.first,far.second))
    }

    @Test fun compassRadiusFollowsLegsAndSnapsToCircle(){
        val compass=Item(kind="shape",shape="compass",x=50f,y=30f,w=120f,h=160f)
        val radius=hypot(60f,160f)
        val sample=GeometryTools.snap(compass,compass.x+compass.w/2+radius+5f,compass.y)
        assertNotNull(sample)
        assertEquals(compass.x+compass.w/2+radius,sample!!.x,.01f)
        assertEquals(2*radius,GeometryTools.construction(compass).w,.01f)
    }

    @Test fun freehandTriangleDoesNotTurnIntoCircle(){
        val pts=mutableListOf<Point>()
        val corners=listOf(0f to 100f,100f to 0f,200f to 100f,0f to 100f)
        corners.zipWithNext().forEach{(a,b)->for(i in 0..12){val t=i/12f;pts.add(Point(a.first+(b.first-a.first)*t,a.second+(b.second-a.second)*t))}}
        val triangle=Item(w=200f,h=100f,inkW=200f,inkH=100f,points=pts)
        assertEquals("triangle",ShapeRecognition.convert(triangle)?.shape)
    }

    @Test fun inkStartingInsideNodeExpandsAndMovesWithItsTree(){
        val page=Page();val parent=Item(kind="sticky",shape="mindnode",x=100f,y=100f,w=180f,h=120f)
        val child=Item(kind="sticky",shape="mindnode",x=350f,y=100f,parentNode=parent.id)
        val ink=Item(x=210f,y=150f,w=220f,h=80f,layerId=parent.layerId)
        page.items.addAll(listOf(parent,child,ink))
        assertTrue(MindMap.attach(page,ink,210f,150f))
        assertEquals(parent.id,ink.parentNode)
        assertTrue(parent.x+parent.w>=ink.x+ink.w+12f)
        assertEquals(setOf(parent.id,child.id,ink.id),MindMap.group(page,listOf(parent)).map{it.id}.toSet())
        assertFalse(MindMap.attach(page,Item(x=0f,y=0f,w=5f,h=5f),0f,0f))
    }
}
