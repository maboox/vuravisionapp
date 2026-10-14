package com.vuravision.classroom.board

import com.vuravision.classroom.ui.INK_NAVY
import com.vuravision.classroom.ui.INK_TEAL
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.settings

/**
 * Everything the teacher has chosen for drawing (item 13): tool, pens and highlighter, eraser, shape, fill, smart mode
 * and hold settings. One object instead of fields spread over the board; boards copy it when the teacher moves
 * between the whiteboard and a PDF, and shared rooms read it to show other people's tools.
 */
class EditorState {
    var fillColor:Int?=INK_TEAL
    var fillAlpha=255
    var tool="pen"
    var selectionMode="free"
    var touchMode=false
    var shape="rectangle"
    var eraserMode="stroke"
    var eraserRadius=18f
    var eraseObjects=true
    var holdRecognitionEnabled=true
    var holdDelayMillis=1000L
    var holdTolerance=4f
    var guideSnapEnabled=true
    var smartMode="text"
    var penColor=INK_NAVY
    var highlightColor=0xffffcf40.toInt()
    var penWidth=4f
    var highlightWidth=6f
    var penStyle="round"
    var penOpacity=255
    var dashLength=12f
    var dashGap=8f
    fun copyFrom(o:EditorState){fillColor=o.fillColor;fillAlpha=o.fillAlpha;tool=o.tool;selectionMode=o.selectionMode;touchMode=o.touchMode;shape=o.shape;eraserMode=o.eraserMode;eraserRadius=o.eraserRadius;eraseObjects=o.eraseObjects;holdRecognitionEnabled=o.holdRecognitionEnabled;holdDelayMillis=o.holdDelayMillis;holdTolerance=o.holdTolerance;guideSnapEnabled=o.guideSnapEnabled;smartMode=o.smartMode;penColor=o.penColor;highlightColor=o.highlightColor;penWidth=o.penWidth;highlightWidth=o.highlightWidth;penStyle=o.penStyle;penOpacity=o.penOpacity;dashLength=o.dashLength;dashGap=o.dashGap}
}
