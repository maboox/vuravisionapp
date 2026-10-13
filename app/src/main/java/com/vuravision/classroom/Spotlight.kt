package com.vuravision.classroom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Spotlight: darkens the whole board except a circle the teacher drags over what matters now, so the class looks there.
 * Its controls (smaller, larger, close) sit at the bottom center, within reach; a drag anywhere moves the light.
 */
@SuppressLint("ViewConstructor")
class Spotlight(context:Context,private val close:()->Unit):FrameLayout(context){
    private var cx=-1f;private var cy=-1f
    var radius=context.dp(170).toFloat()
        private set
    private val dim=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xeb0b0f1a.toInt()}
    private val ring=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=context.dp(3).toFloat();color=0x80ffffff.toInt()}
    private val path=Path().apply{fillType=Path.FillType.EVEN_ODD}
    init{
        setWillNotDraw(false);isClickable=true;contentDescription=context.s("spotlight")
        val bar=context.row().apply{background=rounded(SURFACE,context.dp(24).toFloat());setPadding(context.dp(6),context.dp(6),context.dp(6),context.dp(6));elevation=context.dp(6).toFloat()}
        bar.addView(context.button("−"){resize(.8f)}.apply{contentDescription=context.tr("Smaller","کوچک‌تر")},LinearLayout.LayoutParams(context.dp(64),context.dp(52)))
        bar.addView(context.button("+"){resize(1.25f)}.apply{contentDescription=context.tr("Larger","بزرگ‌تر")},LinearLayout.LayoutParams(context.dp(64),context.dp(52)).apply{marginStart=context.dp(6)})
        bar.addView(WorkspaceIcon(context,"close",context.s("close")){close()},LinearLayout.LayoutParams(context.dp(52),context.dp(52)).apply{marginStart=context.dp(6)})
        addView(bar,LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=context.dp(24)})
    }
    private fun resize(f:Float){radius=(radius*f).coerceIn(context.dp(60).toFloat(),maxOf(width,height).toFloat());invalidate()}
    override fun onDraw(c:Canvas){
        if(cx<0){cx=width/2f;cy=height/2f}
        path.reset();path.addRect(0f,0f,width.toFloat(),height.toFloat(),Path.Direction.CW);path.addCircle(cx,cy,radius,Path.Direction.CW)
        c.drawPath(path,dim);c.drawCircle(cx,cy,radius,ring)
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(e.actionMasked==MotionEvent.ACTION_DOWN||e.actionMasked==MotionEvent.ACTION_MOVE){cx=e.x;cy=e.y;invalidate()}
        return true
    }
}
