package com.vuravision.classroom

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** One saved pen: ink or highlighter with its own color and width. */
data class PenPreset(val highlight:Boolean,val color:Int,val width:Float)

/**
 * Side dock for large panels: three pen presets, eraser, select, the most recent inserted tool,
 * Add, Undo/Redo and a switch that moves the dock to the other edge.
 * Everything else lives one tap away in [AddPanel].
 */
class ToolDock(
    context:Context,
    private val host:FrameLayout,
    private val prefs:SharedPreferences,
    private val board:()->Board,
    private val perform:(String)->Unit,
    private val toolSettings:(String)->Unit,
    private val changed:()->Unit,
):FrameLayout(context) {
    companion object {
        val defaults=listOf(PenPreset(false,NAVY,4f),PenPreset(false,0xffdc2626.toInt(),8f),PenPreset(true,0xffffcf40.toInt(),6f))
        /** Tools that can occupy the "recent" slot after being used from the Add panel. */
        val recentable=setOf("shape","text","sticky","mind_map","graph","coordinates","ruler","set_square","protractor","compass","timer","stopwatch","dice","scoreboard","periodic","image","pdf","smart","fill")
        fun presets(prefs:SharedPreferences)=defaults.mapIndexed{i,d->PenPreset(d.highlight,prefs.getInt("dockPen${i}Color",d.color),prefs.getFloat("dockPen${i}Width",d.width).coerceIn(.5f,80f))}
    }
    // Centered children: icons and dividers share one vertical axis whatever the button size.
    private val column=context.column().apply{gravity=Gravity.CENTER_HORIZONTAL;setPadding(context.dp(6),context.dp(6),context.dp(6),context.dp(6))}
    private val penButtons=defaults.indices.map{i->icon(if(defaults[i].highlight)"highlight" else "pen",penTitle(i)){pen(i)}}
    private val eraser=icon("erase",context.s("erase")){tool("erase")}
    private val select=icon("select",context.s("select")){tool("select")}
    private var recentButton=icon(recent(),context.s(recent())){useRecent()}
    private val add=icon("insert",context.s("insert")){toggleAdd()}
    private val undo=icon("undo",context.s("undo")){board().undo()}
    private val redo=icon("redo",context.s("redo")){board().redo()}
    private val swapButton=icon("swap",context.tr("Move toolbar to the other side","انتقال نوار به سمت دیگر")){swap()}
    private val strip=InkStrip(context,board,showTools=false,grid=true,more={stripShown=false;refresh();toolSettings("pen")}){savePreset();changed();refresh()}
    private val stripHost=FrameLayout(context).apply{addView(strip,FrameLayout.LayoutParams(-2,-2));background=rounded(SURFACE,context.dp(20).toFloat(),OUTLINE);elevation=context.dp(6).toFloat();setPadding(context.dp(10),context.dp(10),context.dp(10),context.dp(10));visibility=View.GONE;isClickable=true}
    private val handle=View(context).apply{
        background=rounded(OUTLINE_STRONG,context.dp(6).toFloat());contentDescription=context.tr("Bring the toolbar to this side","آوردن نوار ابزار به این سمت")
        isClickable=true;setOnClickListener{swap()}
    }
    private var addPanel:AddPanel?=null
    private var columns=1
    private var buttonDp=52
    private var stripShown=false
    var end=prefs.getBoolean("dockEnd",false)
        private set
    /** The color/width popover, exposed for layout checks. */
    val inkPopup:View get()=stripHost
    init {
        background=rounded(SURFACE,context.dp(20).toFloat(),OUTLINE);elevation=context.dp(6).toFloat()
        id=R.id.tool_dock
        addView(column,FrameLayout.LayoutParams(-2,-2))
        arrange()
        host.addView(this,FrameLayout.LayoutParams(-2,-2))
        host.addView(stripHost,FrameLayout.LayoutParams(-2,-2))
        host.addView(handle,FrameLayout.LayoutParams(context.dp(10),context.dp(72)))
        host.addOnLayoutChangeListener{_,_,t,_,b,_,ot,_,ob->if(b-t!=ob-ot)fit(b-t)}
        place();refresh()
    }
    private fun buttons()=penButtons+listOf(eraser,select,recentButton,add,undo,redo,swapButton)
    /** One column on tall panels; two columns when one would exceed the space beside the canvas. */
    private fun arrange(){
        column.removeAllViews()
        buttons().forEach{(it.parent as? ViewGroup)?.removeView(it);it.sizeDp=buttonDp}
        // Buttons keep their own square size; LinearLayout's default would stretch them across the panel.
        fun wrap()=LinearLayout.LayoutParams(-2,-2)
        if(columns==1){
            (penButtons+listOf(eraser,select,recentButton,add)).forEach{column.addView(it,wrap())}
            column.addView(divider());column.addView(undo,wrap());column.addView(redo,wrap());column.addView(divider());column.addView(swapButton,wrap())
        }else buttons().chunked(2).forEach{pair->column.addView(context.row().apply{pair.forEach{addView(it,wrap())}},wrap())}
    }
    /** Keep the dock inside the middle of the side, whatever the density or UI size. */
    private fun fit(height:Int){
        if(height<=0)return
        val px=context.dp(100)/100f
        val heightDp=height/px
        // Centered and capped at ~62% of the height, the dock stays clear of both bottom corners.
        val available=heightDp*.62f-16
        val single=(available-20)/buttons().size
        val (cols,size)=if(single>=44)1 to single.coerceAtMost(60f) else 2 to (available/((buttons().size+1)/2)).coerceIn(36f,56f)
        // Rebuild after the current layout pass; adding views inside it leaves them unmeasured.
        post{if(cols!=columns || size.toInt()!=buttonDp){columns=cols;buttonDp=size.toInt();arrange()};place()}
    }
    private fun icon(key:String,title:String,action:()->Unit)=WorkspaceIcon(context,key,title,false,action).apply{sizeDp=52}
    // Dividers are as wide as the icon area (button minus its rounded padding), centered under the buttons.
    private fun divider()=View(context).apply{setBackgroundColor(OUTLINE);layoutParams=LinearLayout.LayoutParams(context.dp(buttonDp-16),context.dp(1)).apply{gravity=Gravity.CENTER_HORIZONTAL;topMargin=context.dp(6);bottomMargin=context.dp(6)}}
    private fun penTitle(i:Int)=if(defaults[i].highlight)context.tr("Highlighter","هایلایتر")else context.tr("Pen ${i+1}","قلم ${i+1}")
    private fun activePreset()=prefs.getInt("dockPen",0).coerceIn(0,defaults.lastIndex)
    private fun recent()=prefs.getString("dockRecent","shape")?.takeIf{it in recentable}?:"shape"

    private fun pen(i:Int){
        val b=board();val preset=presets(prefs)[i]
        val already=activePreset()==i && b.tool==(if(preset.highlight)"highlight" else "pen")
        if(already){stripShown=!stripShown;refresh();post{place()};return}
        prefs.edit().putInt("dockPen",i).apply()
        b.tool=if(preset.highlight)"highlight" else "pen";b.clearSelection()
        if(b.store.page.visiblePaneCount==1){if(preset.highlight){b.highlightColor=preset.color;b.highlightWidth=preset.width}else{b.penColor=preset.color;b.penWidth=preset.width}}
        stripShown=true;changed();refresh();post{place()}
    }
    private fun tool(key:String){
        val b=board()
        if(b.tool==key){toolSettings(key);return}
        b.tool=key;b.clearSelection();stripShown=false;changed();refresh()
    }
    private fun useRecent(){stripShown=false;perform(recent());refresh()}
    /** The Add panel reports what was used so the dock can offer it again with one tap. */
    fun used(key:String){
        if(key !in recentable || key==recent())return
        prefs.edit().putString("dockRecent",key).apply()
        recentButton=icon(key,context.s(key)){useRecent()};arrange();refresh()
    }
    private fun savePreset(){
        val b=board();val i=activePreset();if(b.store.page.visiblePaneCount>1)return
        val highlight=b.tool=="highlight";if(highlight!=defaults[i].highlight)return
        prefs.edit().putInt("dockPen${i}Color",if(highlight)b.highlightColor else b.penColor).putFloat("dockPen${i}Width",if(highlight)b.highlightWidth else b.penWidth).apply()
    }
    private fun toggleAdd(){
        if(addPanel!=null){closeAdd();return}
        stripShown=false
        addPanel=AddPanel(context,host,end,beside(),{closeAdd()}){key->closeAdd();used(key);perform(key)}
        refresh()
    }
    fun closeAdd(){addPanel?.close();addPanel=null;refresh()}
    fun swap(){
        end=!end;prefs.edit().putBoolean("dockEnd",end).apply()
        closeAdd();place()
        if(!isLaidOut)return
        // A short slide from the old edge makes the move easy to follow on a large panel.
        translationX=(if(end==(layoutDirection==LAYOUT_DIRECTION_RTL))-1 else 1)*context.dp(48).toFloat();alpha=.4f
        animate().translationX(0f).alpha(1f).setDuration(260).start()
    }
    /** Distance from the panel edge to just past the dock, for the strip and Add panel. */
    private fun beside()=context.dp(12)+(if(width>0)width else context.dp(buttonDp*columns+14))+context.dp(12)
    private fun place(){
        val side=if(end)Gravity.END else Gravity.START
        (layoutParams as FrameLayout.LayoutParams).apply{gravity=Gravity.CENTER_VERTICAL or side;setMargins(context.dp(12),context.dp(12),context.dp(12),context.dp(12))}
        (handle.layoutParams as FrameLayout.LayoutParams).apply{gravity=Gravity.CENTER_VERTICAL or(if(end)Gravity.START else Gravity.END);setMargins(context.dp(3),0,context.dp(3),0)}
        (stripHost.layoutParams as FrameLayout.LayoutParams).apply{
            gravity=Gravity.TOP or side;topMargin=stripTop();bottomMargin=0
            if(end){marginStart=0;marginEnd=beside()}else{marginStart=beside();marginEnd=0}
        }
        requestLayout();handle.requestLayout();stripHost.requestLayout()
    }
    /** Level with the active pen, kept fully on screen. */
    private fun stripTop():Int{
        val button=penButtons[activePreset()]
        if(!button.isLaidOut||host.height==0)return context.dp(120)
        val at=IntArray(2);val origin=IntArray(2);button.getLocationInWindow(at);host.getLocationInWindow(origin)
        stripHost.measure(MeasureSpec.makeMeasureSpec(host.width,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(host.height,MeasureSpec.AT_MOST))
        return (at[1]-origin[1]).coerceIn(context.dp(12),(host.height-stripHost.measuredHeight-context.dp(12)).coerceAtLeast(context.dp(12)))
    }
    /** A touch anywhere outside the dock and its strip closes the strip; the touch still draws. */
    fun touchedAt(rawX:Float,rawY:Float){
        if(!stripShown)return
        fun inside(v:View):Boolean{if(v.visibility!=View.VISIBLE)return false;val p=IntArray(2);v.getLocationOnScreen(p);return rawX>=p[0]&&rawX<p[0]+v.width&&rawY>=p[1]&&rawY<p[1]+v.height}
        if(!inside(this)&&!inside(stripHost)){stripShown=false;refresh()}
    }
    /** Hide or show with the rest of the canvas chrome (focus mode). */
    fun setChromeVisible(visible:Boolean){
        visibility=if(visible)View.VISIBLE else View.GONE;handle.visibility=visibility
        if(!visible){closeAdd();stripHost.visibility=View.GONE}else refresh()
    }
    fun refresh(){
        val b=board();val editable=b.sessionCanEdit;val presets=presets(prefs);val active=activePreset()
        penButtons.forEachIndexed{i,button->
            val on=i==active && b.tool==(if(presets[i].highlight)"highlight" else "pen")
            if(button.active!=on)button.showActive(on);button.dotColor=presets[i].color
        }
        listOf(eraser to "erase",select to "select").forEach{(button,key)->if(button.active!=(b.tool==key))button.showActive(b.tool==key)}
        if(add.active!=(addPanel!=null))add.showActive(addPanel!=null)
        (penButtons+listOf(eraser,select,recentButton,add)).forEach{it.isEnabled=editable;it.alpha=if(editable)1f else .35f}
        val inking=b.tool=="pen"||b.tool=="highlight"
        if(!inking)stripShown=false
        stripHost.visibility=if(stripShown && visibility==View.VISIBLE)View.VISIBLE else View.GONE
        if(stripShown)strip.refresh()
    }
}

/** Everything that is not on the dock, grouped the way a teacher looks for it. */
class AddPanel(private val context:Context,private val host:FrameLayout,end:Boolean,beside:Int,private val dismiss:()->Unit,private val pick:(String)->Unit) {
    companion object {
        val groups=listOf(
            "board" to listOf("shape","text","sticky","mind_map","image","pdf","graph","coordinates"),
            "geometry" to listOf("ruler","set_square","protractor","compass"),
            "smart" to listOf("smart","layers","split","fill","background","touch"),
            "class" to listOf("timer","stopwatch","dice","scoreboard","periodic","lab","games"),
        )
    }
    private val scrim=FrameLayout(context).apply{isClickable=true;setOnClickListener{dismiss()}}
    private val panel=context.column().apply{
        setPadding(context.dp(14),context.dp(12),context.dp(14),context.dp(14))
        background=rounded(SURFACE,context.dp(20).toFloat(),OUTLINE);elevation=context.dp(8).toFloat();isClickable=true
    }
    init {
        id(panel)
        groups.forEach{(group,keys)->
            panel.addView(context.label(title(group),15f,MUTED,true).apply{setPadding(context.dp(4),context.dp(8),context.dp(4),context.dp(4))})
            val grid=GridLayout(context).apply{columnCount=4}
            keys.forEach{key->grid.addView(tile(key))}
            panel.addView(grid)
        }
        val scroll=ScrollView(context).apply{addView(panel);isVerticalScrollBarEnabled=false}
        scrim.addView(scroll,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_VERTICAL or(if(end)Gravity.END else Gravity.START)).apply{
            topMargin=context.dp(12);bottomMargin=context.dp(12)
            if(end){marginStart=0;marginEnd=beside}else{marginStart=beside;marginEnd=0}
        })
        host.addView(scrim,FrameLayout.LayoutParams(-1,-1))
        panel.alpha=0f;panel.translationY=context.dp(12).toFloat();panel.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }
    private fun id(v:View){v.id=R.id.add_panel}
    private fun title(group:String)=when(group){
        "board"->context.tr("On the board","روی تخته");"geometry"->context.tr("Geometry","هندسه")
        "smart"->context.tr("Smart and page","هوشمند و صفحه");else->context.tr("Class","کلاس")
    }
    private fun label(key:String)=when(key){
        "split"->context.tr("Split board","تقسیم تخته");"layers"->context.tr("Layers","لایه‌ها");"touch"->context.tr("Touch mode","حالت لمس")
        else->context.s(key)
    }
    private fun tile(key:String)=context.column().apply{
        gravity=Gravity.CENTER_HORIZONTAL;setPadding(context.dp(4),context.dp(4),context.dp(4),context.dp(6))
        val icon=WorkspaceIcon(context,key,label(key)){pick(key)}.apply{sizeDp=56}
        addView(icon,LinearLayout.LayoutParams(-2,-2))
        addView(context.label(label(key),14f,NAVY).apply{gravity=Gravity.CENTER;maxLines=2;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(context.dp(92),-2))
        layoutParams=GridLayout.LayoutParams().apply{width=context.dp(96)}
    }
    fun close(){host.removeView(scrim)}
}
