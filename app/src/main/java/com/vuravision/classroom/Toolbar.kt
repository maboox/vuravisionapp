package com.vuravision.classroom

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * One saved pen: ink or highlighter with its own color and width; ink pens also keep their own line style,
 * opacity and dash pattern, so changing one pen never changes another.
 */
data class PenPreset(val highlight:Boolean,val color:Int,val width:Float,val style:String="round",val opacity:Int=255,val dashLength:Float=12f,val dashGap:Float=8f)

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
        val recentable=setOf("shape","text","sticky","mind_map","graph","coordinates","ruler","set_square","protractor","compass","timer","stopwatch","dice","scoreboard","periodic","image","pdf","smart","fill","laser","spotlight","picker")
        fun presets(prefs:SharedPreferences)=defaults.mapIndexed{i,d->PenPreset(d.highlight,prefs.getInt("dockPen${i}Color",d.color),prefs.getFloat("dockPen${i}Width",d.width).coerceIn(.5f,80f),
            prefs.getString("dockPen${i}Style",d.style)?.takeIf{it in PenStyles.keys}?:d.style,prefs.getInt("dockPen${i}Opacity",d.opacity).coerceIn(10,255),
            prefs.getFloat("dockPen${i}DashLength",d.dashLength).coerceIn(1f,80f),prefs.getFloat("dockPen${i}DashGap",d.dashGap).coerceIn(1f,80f))}
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
    private val strip=InkStrip(context,board,showTools=false,grid=true,more={stripShown=false;refresh();toolSettings("pen")}){saveActive();changed();refresh()}
    private val stripHost=FrameLayout(context).apply{addView(strip,FrameLayout.LayoutParams(-2,-2));background=rounded(SURFACE,context.dp(20).toFloat(),OUTLINE);elevation=context.dp(6).toFloat();setPadding(context.dp(10),context.dp(10),context.dp(10),context.dp(10));visibility=View.GONE;isClickable=true}
    private val handle=View(context).apply{
        background=rounded(OUTLINE_STRONG,context.dp(6).toFloat());contentDescription=context.tr("Bring the toolbar to this side","آوردن نوار ابزار به این سمت")
        isClickable=true;setOnClickListener{swap()}
    }
    private var addPanel:AddPanel?=null
    /** Up to two tools the teacher pinned from the Add panel (long press), e.g. ruler for maths, text for literature. */
    private var pinButtons=pins().map{key->icon(key,context.s(key)){perform(key)}}
    private fun pins()=prefs.getString("dockPins","")!!.split(',').filter{it.isNotBlank()}.take(2)
    /** Long press in the Add panel: pin, or unpin if already pinned. Returns true when the tool is now pinned. */
    fun togglePin(key:String):Boolean{
        val now=pins().toMutableList();val pinned=key !in now
        if(pinned){now.add(key);while(now.size>2)now.removeAt(0)}else now.remove(key)
        prefs.edit().putString("dockPins",now.joinToString(",")).apply()
        pinButtons=now.map{k->icon(k,context.s(k)){perform(k)}};columns=0;arrange();fit(host.height);refresh();place()
        // The dock may have grown (or gone to two columns): reopen the Add panel beside its new edge.
        if(addPanel!=null)post{post{if(addPanel!=null){closeAdd();toggleAdd()}}}
        return pinned
    }
    private var columns=1
    private var buttonDp=52
    private var stripShown=false
    var end=prefs.getBoolean("dockEnd",false)
        private set
    /**
     * Vertical place on the side: "middle" (default), "top" or "bottom". On an 86" panel the middle puts the pens
     * around 1.7 m; "bottom" brings them within reach of children and shorter teachers, "top" suits a raised panel.
     */
    var position=prefs.getString("dockPosition","middle")?.takeIf{it in listOf("top","middle","bottom")}?:"middle"
        set(value){if(value==field||value !in listOf("top","middle","bottom"))return;field=value;prefs.edit().putString("dockPosition",value).apply();columns=0;fit(host.height);place()}
    /** Presentation: only the three pens, eraser and Undo stay, so the class sees the board and the teacher can still write. */
    var presentation=false
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
        // Every host layout: the bottom bars can move (narrow screens) without the host changing size.
        host.addOnLayoutChangeListener{_,_,t,_,b,_,_,_,_->fit(b-t)}
        place();refresh()
    }
    private fun buttons()=if(presentation)penButtons+listOf(eraser,undo) else penButtons+listOf(eraser,select,recentButton)+pinButtons+listOf(add,undo,redo,swapButton)
    fun setPresentation(on:Boolean){
        if(on==presentation)return
        presentation=on;closeAdd();stripShown=false;columns=0
        handle.visibility=if(on)View.GONE else View.VISIBLE
        arrange();fit(host.height);refresh();place()
    }
    /** One column on tall panels; two columns when one would exceed the space beside the canvas. */
    private fun arrange(){
        column.removeAllViews()
        buttons().forEach{(it.parent as? ViewGroup)?.removeView(it);it.sizeDp=buttonDp}
        // Buttons keep their own square size; LinearLayout's default would stretch them across the panel.
        fun wrap()=LinearLayout.LayoutParams(-2,-2)
        if(presentation){
            if(columns==1){(penButtons+eraser).forEach{column.addView(it,wrap())};column.addView(divider());column.addView(undo,wrap())}
            else listOf(listOf(penButtons[0],penButtons[1]),listOf(penButtons[2],eraser),listOf(undo)).forEach{pair->column.addView(pairRow(pair),wrap())}
        }else if(columns==1){
            (penButtons+listOf(eraser,select,recentButton)+pinButtons+add).forEach{column.addView(it,wrap())}
            column.addView(divider());column.addView(undo,wrap());column.addView(redo,wrap());column.addView(divider());column.addView(swapButton,wrap())
        }else{
            // Pairs that belong together: two pens, highlighter+eraser, select+recent, Add+side switch, then Undo+Redo.
            // Pins, Add and the side switch fill pairs in order, so an odd count never leaves a button in the middle.
            val rows=listOf(listOf(penButtons[0],penButtons[1]),listOf(penButtons[2],eraser),listOf(select,recentButton))+(pinButtons+listOf(add,swapButton)).chunked(2)+listOf(listOf(undo,redo))
            rows.forEachIndexed{i,pair->if(i==rows.lastIndex)column.addView(divider().apply{(layoutParams as LinearLayout.LayoutParams).width=context.dp(buttonDp*2-16)});column.addView(pairRow(pair),wrap())}
        }
    }
    /** Two buttons side by side; a lone button keeps the start column (an empty cell holds the other), never the middle. */
    private fun pairRow(pair:List<View>)=context.row().apply{
        pair.forEach{addView(it,LinearLayout.LayoutParams(-2,-2))}
        if(pair.size==1)addView(View(context),LinearLayout.LayoutParams(context.dp(buttonDp),context.dp(buttonDp)))
    }
    /** Keep the dock inside the middle of the side, whatever the density or UI size. */
    private fun fit(height:Int){
        if(height<=0)return
        val px=context.dp(100)/100f
        val heightDp=height/px
        // Centered and capped at 72% of the height: one column on a 720dp (1080p) panel. Being centered,
        // it must also leave the bottom bars' height free at both ends.
        val reserved=(height-bottomObstacle())/px
        // At the top or bottom it only has to stay above the bottom bars, not clear of them at both ends.
        val available=minOf(heightDp*.72f,heightDp-(if(position=="middle")2 else 1)*reserved-24)-16
        // Overheads: 12dp padding plus 13dp per divider (two in one column, one in two columns).
        val single=(available-38)/buttons().size
        val (cols,size)=if(single>=44)1 to single.coerceAtMost(60f) else 2 to ((available-25)/((buttons().size+1)/2)).coerceIn(36f,56f)
        // Rebuild after the current layout pass; adding views inside it leaves them unmeasured.
        // Only a real change rebuilds (posting unconditionally would loop: place() requests another layout).
        if(cols!=columns || size.toInt()!=buttonDp)post{if(cols!=columns || size.toInt()!=buttonDp){columns=cols;buttonDp=size.toInt();arrange();place()}}
        // At the bottom the dock sits on the bottom bars, which can move after a layout; follow them (only on change).
        else if((layoutParams as? FrameLayout.LayoutParams)?.bottomMargin!=bottomMargin())post{place()}
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
        // Keep what was just changed on the pen being left (settings may still be open when the next pen is tapped).
        saveActive()
        prefs.edit().putInt("dockPen",i).apply()
        b.tool=if(preset.highlight)"highlight" else "pen";b.clearSelection()
        if(b.store.page.visiblePaneCount==1){if(preset.highlight){b.highlightColor=preset.color;b.highlightWidth=preset.width}else{b.penColor=preset.color;b.penWidth=preset.width;b.penStyle=preset.style;b.penOpacity=preset.opacity;b.dashLength=preset.dashLength;b.dashGap=preset.dashGap}}
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
    /** Stores the board's current ink into the active preset; called on every pen change, not only when the dock refreshes. */
    fun saveActive(){
        val b=board();val i=activePreset();if(b.store.page.visiblePaneCount>1)return
        val highlight=b.tool=="highlight";if(b.tool!="pen"&&!highlight || highlight!=defaults[i].highlight)return
        val edit=prefs.edit().putInt("dockPen${i}Color",if(highlight)b.highlightColor else b.penColor).putFloat("dockPen${i}Width",if(highlight)b.highlightWidth else b.penWidth)
        if(!highlight)edit.putString("dockPen${i}Style",b.penStyle).putInt("dockPen${i}Opacity",b.penOpacity).putFloat("dockPen${i}DashLength",b.dashLength).putFloat("dockPen${i}DashGap",b.dashGap)
        edit.apply()
    }
    private fun toggleAdd(){
        if(addPanel!=null){closeAdd();return}
        stripShown=false
        val gap=context.dp(12);val onRight=isLaidOut&&left+width/2>host.width/2
        val freeWidth=(if(!isLaidOut)host.width-2*gap else if(onRight)left-2*gap else host.width-right-2*gap).coerceAtLeast(context.dp(200))
        addPanel=AddPanel(context,host,freeWidth,(bottomObstacle()-2*gap).coerceAtLeast(context.dp(200)),{closeAdd()},{key->togglePin(key)}){key->closeAdd();used(key);perform(key)}.also{panel->
            panel.place{w->if(!isLaidOut)gap else if(onRight)(left-gap-w).coerceAtLeast(gap) else (right+gap)}
        }
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
    // The strip and Add panel are placed from the dock's width; re-place them whenever it changes.
    private fun bottomMargin()=if(position=="bottom"&&host.height>0)(host.height-bottomObstacle()+context.dp(12)).coerceAtLeast(context.dp(12)) else context.dp(12)
    /** Top of the highest bottom bar (page bar, main bar), or the canvas bottom. */
    fun bottomObstacle():Int=listOf(R.id.page_bar,R.id.main_bar).mapNotNull{host.findViewById<View>(it)}.filter{it.visibility==View.VISIBLE&&it.height>0}.minOfOrNull{it.top}?:host.height
    private fun place(){
        val side=if(end)Gravity.END else Gravity.START
        val vertical=when(position){"top"->Gravity.TOP;"bottom"->Gravity.BOTTOM;else->Gravity.CENTER_VERTICAL}
        (layoutParams as FrameLayout.LayoutParams).apply{gravity=vertical or side;setMargins(context.dp(12),context.dp(12),context.dp(12),bottomMargin())}
        (handle.layoutParams as FrameLayout.LayoutParams).apply{gravity=Gravity.CENTER_VERTICAL or(if(end)Gravity.START else Gravity.END);setMargins(context.dp(3),0,context.dp(3),0)}
        placeStrip()
        requestLayout();handle.requestLayout();stripHost.requestLayout()
    }
    /**
     * Absolute position from where the dock actually is after layout (not from a width computed earlier):
     * just outside its inner edge, level with the active pen, fully on screen.
     */
    private fun placeStrip(){
        // Fresh params: a start/end margin left on the old ones would override leftMargin in LTR layouts.
        val lp=FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.LEFT)
        stripHost.measure(MeasureSpec.makeMeasureSpec(host.width.coerceAtLeast(1),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(host.height.coerceAtLeast(1),MeasureSpec.AT_MOST))
        val gap=context.dp(12);val w=stripHost.measuredWidth
        val onRight=isLaidOut&&left+width/2>host.width/2
        lp.leftMargin=if(!isLaidOut)gap else if(onRight)(left-gap-w).coerceAtLeast(gap) else (right+gap).coerceAtMost((host.width-w-gap).coerceAtLeast(gap))
        lp.topMargin=stripTop()
        stripHost.layoutParams=lp
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){super.onLayout(changed,l,t,r,b);if(changed)post{placeStrip()}}
    /** Level with the active pen, kept fully on screen. */
    private fun stripTop():Int{
        val button=penButtons[activePreset()]
        if(!button.isLaidOut||host.height==0)return context.dp(120)
        val at=IntArray(2);val origin=IntArray(2);button.getLocationInWindow(at);host.getLocationInWindow(origin)
        return (at[1]-origin[1]).coerceIn(context.dp(12),(bottomObstacle()-stripHost.measuredHeight-context.dp(12)).coerceAtLeast(context.dp(12)))
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
        // Changes made in full pen settings (style, color, width) belong to the active preset only.
        saveActive()
        val b=board();val editable=b.sessionCanEdit;val presets=presets(prefs);val active=activePreset()
        penButtons.forEachIndexed{i,button->
            val on=i==active && b.tool==(if(presets[i].highlight)"highlight" else "pen")
            if(button.active!=on)button.showActive(on);button.dotColor=presets[i].color
        }
        listOf(eraser to "erase",select to "select").forEach{(button,key)->if(button.active!=(b.tool==key))button.showActive(b.tool==key)}
        if(add.active!=(addPanel!=null))add.showActive(addPanel!=null)
        // The recent slot shows when its tool is the active one (e.g. the laser pen), like the pens do.
        if(recentButton.active!=(b.tool==recent()))recentButton.showActive(b.tool==recent())
        (penButtons+listOf(eraser,select,recentButton,add)+pinButtons).forEach{it.isEnabled=editable;it.alpha=if(editable)1f else .35f}
        val inking=b.tool=="pen"||b.tool=="highlight"
        if(!inking)stripShown=false
        stripHost.visibility=if(stripShown && visibility==View.VISIBLE)View.VISIBLE else View.GONE
        if(stripShown)strip.refresh()
    }
}

/**
 * Everything that is not on the dock, grouped the way a teacher looks for it.
 * Columns follow the free width beside the dock (2-4); height stops above the bottom bars and scrolls.
 */
class AddPanel(private val context:Context,private val host:FrameLayout,freeWidth:Int,private val maxHeight:Int,private val dismiss:()->Unit,
               private val pin:(String)->Boolean={false},private val pick:(String)->Unit) {
    companion object {
        val groups=listOf(
            "board" to listOf("shape","text","sticky","table","mind_map","image","pdf","graph","coordinates"),
            "geometry" to listOf("ruler","set_square","protractor","compass"),
            "smart" to listOf("smart","ai_image","ai_quiz","ai_mindmap","ai_summary","layers","split","fill","background","touch"),
            "class" to listOf("laser","spotlight","picker","class_quiz","replay","timer","stopwatch","dice","scoreboard","periodic","lab","games"),
        )
    }
    private val scrim=FrameLayout(context).apply{isClickable=true;setOnClickListener{dismiss()}}
    private val panel=context.column().apply{setPadding(context.dp(14),context.dp(12),context.dp(14),context.dp(14));isClickable=true}
    private val scroll=ScrollView(context).apply{
        addView(panel);isVerticalScrollBarEnabled=true;id=R.id.add_panel
        background=rounded(SURFACE,context.dp(20).toFloat(),OUTLINE);elevation=context.dp(8).toFloat()
    }
    private val columns=((freeWidth-context.dp(28))/context.dp(88)).coerceIn(2,4)
    private val body=context.column()
    /**
     * Search (item 47): "where is the protractor?" is asked here, in the catalog, so the field sits on top of it.
     * It finds every command the app has (not only these tiles), by its name in either language or its key.
     */
    private val search=EditText(context).apply{
        hint=context.tr("Search tools: protractor, timer, layers…","جستجوی ابزار: نقاله، تایمر، لایه‌ها…");contentDescription=context.tr("Search tools","جستجوی ابزار")
        isSingleLine=true;textSize=15f*context.uiScale();setTextColor(NAVY);setHintTextColor(MUTED);Fonts.bind(this,false)
        background=rounded(SURFACE_VARIANT,context.dp(14).toFloat());setPadding(context.dp(14),0,context.dp(14),0)
        imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){}
            override fun afterTextChanged(s:android.text.Editable?){show(s?.toString().orEmpty().trim())}
        })
    }
    init {
        panel.addView(search,LinearLayout.LayoutParams(-1,context.dp(44)).apply{bottomMargin=context.dp(4)})
        panel.addView(body)
        show("")
        panel.addView(context.label(context.tr("Long-press a tool to pin it to the toolbar","برای سنجاق کردن ابزار به نوار، آن را نگه دارید"),12f,MUTED).apply{gravity=Gravity.CENTER;setPadding(context.dp(4),context.dp(10),context.dp(4),0)})
        host.addView(scrim,FrameLayout.LayoutParams(-1,-1))
    }
    /** Every searchable command: the tiles here first, then the rest of the app's commands. */
    private fun searchable()=(groups.flatMap{it.second}+ShortcutCatalog.keys).distinct().filter{it !in listOf("insert","pen","erase","select","undo","redo")}
    fun matches(query:String)=searchable().filter{key->listOf(label(key),key.replace('_',' ')).any{it.contains(query,ignoreCase=true)}}
    private fun show(query:String){
        body.removeAllViews()
        if(query.isEmpty()){
            val subject=Subjects.current(context)
            (listOfNotNull(Subjects.tools(subject).takeIf{it.isNotEmpty()}?.let{"subject" to it})+groups).forEach{(group,keys)->
                body.addView(context.label(title(group),15f,MUTED,true).apply{setPadding(context.dp(4),context.dp(8),context.dp(4),context.dp(4))})
                val grid=GridLayout(context).apply{columnCount=columns}
                keys.forEach{key->grid.addView(tile(key))}
                body.addView(grid)
            }
            return
        }
        val found=matches(query)
        if(found.isEmpty()){body.addView(context.label(context.tr("Nothing found","چیزی پیدا نشد"),14f,MUTED).apply{setPadding(context.dp(4),context.dp(16),context.dp(4),context.dp(16))});return}
        val grid=GridLayout(context).apply{columnCount=columns}
        found.forEach{key->grid.addView(tile(key))}
        body.addView(grid)
    }
    /** Absolute placement next to the dock: [left] gets the measured width and returns the left edge. */
    fun place(left:(Int)->Int){
        scroll.measure(View.MeasureSpec.makeMeasureSpec(host.width,View.MeasureSpec.AT_MOST),View.MeasureSpec.UNSPECIFIED)
        val h=scroll.measuredHeight.coerceAtMost(maxHeight);val w=scroll.measuredWidth
        val top=((maxHeight-h)/2+context.dp(12)).coerceAtLeast(context.dp(12))
        scrim.addView(scroll,FrameLayout.LayoutParams(w,h,Gravity.TOP or Gravity.LEFT).apply{leftMargin=left(w);topMargin=top})
        scroll.alpha=0f;scroll.translationY=context.dp(12).toFloat();scroll.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }
    private fun title(group:String)=when(group){
        "board"->context.tr("On the board","روی تخته");"geometry"->context.tr("Geometry","هندسه")
        "smart"->context.tr("Smart and page","هوشمند و صفحه");"subject"->context.tr("For ","برای ")+Subjects.name(context,Subjects.current(context))
        else->context.tr("Class","کلاس")
    }
    private fun label(key:String)=when(key){
        "split"->context.tr("Split board","تقسیم تخته");"layers"->context.tr("Layers","لایه‌ها");"touch"->context.tr("Touch mode","حالت لمس")
        else->context.s(key)
    }
    private fun tile(key:String)=context.column().apply{
        gravity=Gravity.CENTER_HORIZONTAL;setPadding(context.dp(4),context.dp(4),context.dp(4),context.dp(6))
        val icon=WorkspaceIcon(context,key,label(key)){pick(key)}.apply{sizeDp=56
            setOnLongClickListener{val now=pin(key);Toast.makeText(context,if(now)context.tr("Pinned to the toolbar","به نوار ابزار سنجاق شد")else context.tr("Unpinned","از نوار برداشته شد"),Toast.LENGTH_SHORT).show();true}}
        addView(icon,LinearLayout.LayoutParams(-2,-2))
        // Two lines, shrinking to 10sp for a single long word (e.g. "Background") instead of clipping it.
        addView(context.label(label(key),13f,NAVY).apply{gravity=Gravity.CENTER;maxLines=2;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP)},LinearLayout.LayoutParams(context.dp(84),context.dp(40)))
        layoutParams=GridLayout.LayoutParams().apply{width=context.dp(88)}
    }
    fun close(){host.removeView(scrim)}
}
