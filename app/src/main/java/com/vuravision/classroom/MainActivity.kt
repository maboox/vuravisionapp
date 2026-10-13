package com.vuravision.classroom

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : Activity() {
    private var rooms:RoomController?=null
    private var roomUi:RoomUi?=null
    private lateinit var roomBadge:WorkspaceIcon
    private val projectStore=Store()
    val store:Store get()=if(::board.isInitialized)board.store else projectStore
    private lateinit var whiteboard:Board
    private lateinit var workspaceHost:FrameLayout
    private var pdfPane:PdfPane?=null
    private var shownPdf:PdfWorkspaceState?=null
    private var pendingPdfMode=0
    private var afterPdfSave:(()->Unit)?=null
    private var pendingPdfRevision:Long?=null
    private var pendingPdfAsset:String?=null
    lateinit var board: Board
    lateinit var media: Media
    private lateinit var files: LessonFiles
    private lateinit var root: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var status: TextView
    private lateinit var toolDock: ToolDock
    private lateinit var pageLabel: TextView
    private lateinit var pageStrip:LinearLayout
    private lateinit var canvasHost:FrameLayout
    private lateinit var floatingTools:ClassroomWidgets
    private lateinit var selectionBar:LinearLayout
    private lateinit var selectionHost:HorizontalScrollView
    private var pageSignature=""
    private val handler = Handler(Looper.getMainLooper())
    private val worker = ProjectIO.executor
    private val saveQueue by lazy{ProjectSaveQueue(worker){doc,target->files.atomic(doc,target)}}
    private var lastSaveTicket=0L
    private var busyDialog:AlertDialog?=null
    private val recognition = Recognition()
    private val prefs by lazy { getSharedPreferences("vura", MODE_PRIVATE) }
    private var documentId = ""
    private var pendingExport: File? = null
    private var sharing: Sharing? = null
    private var shareDialog: Dialog? = null
    private var taps = 0
    private var artPlayer: ArtPlayer? = null
    private val autosave = Runnable { persist() }
    private var dirty = false
    private var loading = false
    private var lastError = ""
    @Volatile private var destroyed = false
    private var voiceAssistant:VoiceConversation?=null
    private var googleSearch:GoogleSearchWindow?=null
    private var voiceScreen:VoiceScreen?=null
    private lateinit var voiceButton:com.google.android.material.button.MaterialButton
    private var voiceMuted=false
    private lateinit var voiceMuteButton:WorkspaceIcon
    private var voiceStarting=false
    private var voiceStartGeneration=0
    private var lastUserTouch=0L
    private val voiceWorker=Executors.newSingleThreadExecutor()
    private val voiceKeys by lazy{VoiceKeys(this)}
    private val voicePermissionRequest=7410
    private val voicePreferenceListener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->
        if(key=="voiceEnabled"){if(!VoiceSettings.enabled(this))stopVoice()else setVoiceState("voice_assistant")}
    }

    override fun attachBaseContext(base: Context) {
        val lang =
            base.getSharedPreferences("vura", MODE_PRIVATE).getString("language", "en") ?: "en"
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(lang))
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Fonts.initialize(this)
        pendingExport = prefs.getString("pendingExport", null)?.let { name ->
            File(cacheDir, "exports").resolve(File(name).name).takeIf { it.isFile && it.length()>0 }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        media = Media(this)
        files = LessonFiles(media)
        board = Board(this, projectStore, Renderer(media))
        whiteboard=board
        board.profile =
            TouchProfile(
                true,
                prefs.getFloat("thin", 5f),
                prefs.getFloat("palm", 50f),
                prefs.getBoolean("palmErase", false),
                prefs.getBoolean("multiTouch",true),
                prefs.getFloat("thickWidth",10f),
            )
        board.profile.thickColor=prefs.getInt("thickColor",0xffe45756.toInt())
        board.eraserMode=prefs.getString("eraserMode","stroke")?:"stroke"
        board.eraserRadius=prefs.getFloat("eraserRadius",18f)
        board.eraseObjects=prefs.getBoolean("eraseObjects",true)
        board.erasePdfContent=prefs.getBoolean("erasePdfContent",false)
        board.pdfPalmErase=prefs.getBoolean("pdfPalmErase",false)
        board.penColor=prefs.getInt("penColor",NAVY)
        board.highlightColor=prefs.getInt("highlightColor",0xffffcf40.toInt())
        board.penWidth=prefs.getFloat("penWidth",4f)
        board.highlightWidth=prefs.getFloat("highlightWidth",6f)
        board.profile.thickStyle=prefs.getString("thickStyle","round")?:"round"
        board.dashLength=prefs.getFloat("dashLength",12f)
        board.dashGap=prefs.getFloat("dashGap",8f)
        board.penStyle=prefs.getString("penStyle","round")?:"round"
        board.gestures.undoEnabled=prefs.getBoolean("twoFingerUndo",true)
        board.gestures.pieEnabled=prefs.getBoolean("pieEnabled",false)
        board.penOpacity=prefs.getInt("penOpacity",255)
        board.selectionMode=prefs.getString("selectionMode","free")?.takeIf{it in listOf("free","box")}?:"free"
        board.touchMode=prefs.getBoolean("touchMode",false)
        smartSource=prefs.getString("smartSource","auto")?:"auto"
        board.smartMode=prefs.getString("smartMode","text")?:"text"
        board.holdRecognitionEnabled=prefs.getBoolean("holdRecognition",true)
        board.holdDelayMillis=prefs.getLong("holdDelay",1000L).coerceIn(500L,2500L)
        board.holdTolerance=prefs.getFloat("holdTolerance",4f).coerceIn(2f,12f)
        board.guideSnapEnabled=prefs.getBoolean("guideSnap",true)
        buildUI()
        Fonts.applyTree(window.decorView)
        media.assetReady = { asset -> handler.post { if(!destroyed){
            if(projectStore.page.items.any{it.asset==asset})whiteboard.sceneChanged()
            pdfPane?.mediaReady(asset)
        } } }
        media.error = { message -> handler.post { if(!destroyed)status.text = s("error") + ": " + message } }
        whiteboard.onActivate={activateBoard(whiteboard)}
        board.onSelection = { refreshDock() }
        board.onObjectActions={editSelection()}
        board.onMindAdd={node,sibling->mindNode(node,sibling)}
        board.onSmart={processSmart(it)}
        bindBoardExtras(board)
        store.changed = {
            if(whiteboard.activePane !in projectStore.page.visiblePaneIndices)whiteboard.applySharedPane(0)
            rooms?.localChange()
            dirty = true
            if(!whiteboard.isCommitting)whiteboard.sceneChanged() else whiteboard.invalidate()
            refreshDock()
            refreshTitle()
            status.text = s("saving")
            handler.removeCallbacks(autosave)
            handler.postDelayed(autosave, 1800)
        }
        documentId = prefs.getString("current", null) ?: newId()
        prefs.edit().putString("current", documentId).apply()
        val f = lessonFile()
        if (f.exists() || File(f.path + ".bak").exists()) loadFile(f, false)
        else {
            store.lesson.title = s("lesson")
            store.lesson.pages[0]=newBoardPage(null)
            refreshTitle()
        }
        setupRooms()
    }

    private fun setupRooms(){
        rooms=RoomController(this,projectStore,whiteboard,media,{
            persist();documentId=newId();prefs.edit().putString("current",documentId).apply()
        },::roomStateChanged)
        roomUi=RoomUi(this,rooms!!)
    }
    private fun roomStateChanged(){
        if(destroyed||!::roomBadge.isInitialized)return
        val session=rooms;roomBadge.visibility=if(session?.active==true)View.VISIBLE else View.GONE
        roomBadge.alpha=if(session?.connected==true)1f else .5f;roomBadge.contentDescription=s("shared_room")+" · "+session?.frame?.room.orEmpty()
        refreshDock();refreshTitle();roomUi?.refresh();whiteboard.invalidate()
    }
    private fun sharedRoom(){
        if(loading||whiteboard.hasActiveInteraction){toast(s("room_finish_first"));return}
        activateBoard(whiteboard);projectStore.lesson.pdf?.let{it.fullscreen=false};syncPdfWorkspace()
        roomUi?.home(menuReturn.also{menuReturn=null})
    }
    private val chrome = mutableListOf<View>()
    private var focusMode = false
    private var sidePanel: View? = null
    private var sidePanelPage: Page? = null
    private var splitControls: FrameLayout? = null
    private var splitControlsPage: Page? = null
    private fun icon(key:String, title:String, active:Boolean=false, action:()->Unit)=WorkspaceIcon(this,key,title,active,action)
    private fun surface(view:View):View = view.apply {
        background=rounded(SURFACE,dp(12).toFloat(),OUTLINE)
        elevation=dp(4).toFloat()
    }
    private fun buildUI() {
        root=column().apply{fitsSystemWindows=true;setBackgroundColor(PAPER)}
        setContentView(root)
        canvasHost=FrameLayout(this)
        workspaceHost=FrameLayout(this)
        workspaceHost.addView(whiteboard,FrameLayout.LayoutParams(-1,-1))
        canvasHost.addView(workspaceHost,FrameLayout.LayoutParams(-1,-1))
        root.addView(canvasHost,LinearLayout.LayoutParams(-1,-1))
        floatingTools=ClassroomWidgets(this,canvasHost)
        fun floating(v:View, gravity:Int, top:Int=12, bottom:Int=12, width:Int=-2, height:Int=-2){
            surface(v);canvasHost.addView(v,FrameLayout.LayoutParams(width,height,gravity).apply{setMargins(dp(12),dp(top),dp(12),dp(bottom))});chrome.add(v)
        }
        val header=row().apply{pad(4)}
        header.addView(icon("menu",s("main_menu")){menu()}.apply{id=R.id.main_menu_button})
        header.addView(icon("files",s("files")){fileMenu()})
        header.addView(icon("share",s("share")){shareOptions()})
        val headerHost=HorizontalScrollView(this).apply{id=R.id.main_bar;isHorizontalScrollBarEnabled=false;addView(header)}
        floating(headerHost,Gravity.BOTTOM or Gravity.RIGHT)
        (headerHost.layoutParams as FrameLayout.LayoutParams).rightMargin=dp(72)
        // Titles/status remain available in the document menu; canvas chrome uses icons.
        titleView=label("");status=label("");pageLabel=label("")
        toolDock=ToolDock(this,canvasHost,prefs,{board},{dockAction(it)},{key->when(key){"pen"->penSettings();"erase"->eraserSettings();"select"->selectionSettings();else->{}}}){savePens();refreshDock()}
        chrome.add(toolDock)
        pageStrip=row().apply{pad(4)}
        pageStrip.layoutDirection=View.LAYOUT_DIRECTION_LTR
        pageLabel=label("",14f,NAVY,true).apply{gravity=Gravity.CENTER;minWidth=dp(72);layoutDirection=View.LAYOUT_DIRECTION_LTR}
        val pageHost=HorizontalScrollView(this).apply{id=R.id.page_bar;isHorizontalScrollBarEnabled=false;addView(pageStrip)}
        floating(pageHost,Gravity.BOTTOM or Gravity.LEFT)
        // A column of rows: actions wrap to a second row when the free width (beside the dock) is narrow.
        selectionBar=column().apply{setPadding(dp(4),dp(4),dp(4),dp(4))}
        selectionHost=HorizontalScrollView(this).apply{id=R.id.selection_toolbar;isHorizontalScrollBarEnabled=false;addView(selectionBar);visibility=View.GONE
            background=rounded(SURFACE,dp(18).toFloat(),OUTLINE);elevation=dp(6).toFloat()}
        canvasHost.addView(selectionHost,FrameLayout.LayoutParams(-2,dp(72),Gravity.TOP or Gravity.LEFT))
        // Presentation mode: everything but a small pen bar (3 pens, eraser, Undo) fades away. Before, this hid every tool,
        // so writing one word meant leaving the mode and entering it again.
        lateinit var focus:WorkspaceIcon
        focus=icon("fullscreen",tr("Presentation mode","حالت ارائه")){
            focusMode=!focusMode;focus.showActive(focusMode)
            window.decorView.systemUiVisibility=if(focusMode)View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else View.SYSTEM_UI_FLAG_VISIBLE
            chrome.forEach{if(it!==toolDock)it.visibility=if(focusMode)View.GONE else View.VISIBLE}
            toolDock.setPresentation(focusMode)
            if(focusMode){closeSidePanel();selectionHost.visibility=View.GONE}else refreshSelectionBar()
            refreshSplitControls()
        }
        surface(focus)
        canvasHost.addView(focus,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.RIGHT).apply{setMargins(dp(12),dp(12),dp(12),dp(12))})
        voiceButton=button(s("voice_assistant")){toggleVoice()}.apply{
            id=R.id.voice_assistant_button
            setIconResource(R.drawable.ph_mic);iconSize=dp(20)
            maxWidth=dp(300);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
            contentDescription=s("voice_assistant")
            visibility=if(VoiceSettings.enabled(this@MainActivity))View.VISIBLE else View.GONE
        }
        // Independent control remains reachable when the drawing toolbars are hidden.
        canvasHost.addView(voiceButton,FrameLayout.LayoutParams(-2,dp(48),Gravity.BOTTOM or Gravity.RIGHT).apply{setMargins(dp(78),dp(12),dp(if(resources.configuration.layoutDirection==View.LAYOUT_DIRECTION_RTL)76 else 12),dp(72))})
        voiceMuteButton=icon("mute",tr("Mute microphone","بی‌صدا کردن میکروفن")){voiceMuted=!voiceMuted;voiceAssistant?.setMuted(voiceMuted);refreshMute()}
        voiceMuteButton.visibility=View.GONE
        canvasHost.addView(voiceMuteButton,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.RIGHT).apply{setMargins(dp(12),0,dp(12),dp(132))})
        roomBadge=icon("shared_room",s("shared_room")){sharedRoom()}.apply{visibility=View.GONE}
        surface(roomBadge);canvasHost.addView(roomBadge,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.RIGHT).apply{setMargins(dp(12),dp(12),dp(12),dp(12))})
        whiteboard.fitViewport={
            val area=RectF(0f,0f,whiteboard.width.toFloat(),whiteboard.height.toFloat())
            val origin=IntArray(2);whiteboard.getLocationInWindow(origin)
            (chrome+listOf(focus,voiceButton,voiceMuteButton)).filter{it.visibility==View.VISIBLE&&it.width>0&&it.height>0}.forEach{v->
                val at=IntArray(2);v.getLocationInWindow(at)
                val r=RectF((at[0]-origin[0]).toFloat(),(at[1]-origin[1]).toFloat(),(at[0]-origin[0]+v.width).toFloat(),(at[1]-origin[1]+v.height).toFloat())
                if(r.height()>whiteboard.height*.5f){
                    if(r.centerX()<whiteboard.width/2f)area.left=maxOf(area.left,r.right+dp(12))else area.right=minOf(area.right,r.left-dp(12))
                }else if(r.top>whiteboard.height*.55f)area.bottom=minOf(area.bottom,r.top-dp(12))
            }
            area
        }
        prefs.registerOnSharedPreferenceChangeListener(voicePreferenceListener)
        canvasHost.addOnLayoutChangeListener{_,l,t,r,b,ol,ot,or,ob->if(r-l!=or-ol||b-t!=ob-ot){
            val narrow=r-l<dp(800)
            (pageHost.layoutParams as FrameLayout.LayoutParams).apply{bottomMargin=dp(if(narrow)76 else 12);width=min(dp(344),(r-l-dp(24)).coerceAtLeast(dp(144)))};pageHost.requestLayout()
            (headerHost.layoutParams as FrameLayout.LayoutParams).apply{width=min(dp(248),(r-l-dp(96)).coerceAtLeast(dp(144)))};headerHost.requestLayout()
            positionSelectionBar()
            refreshSplitControls()
        }}
        // The dock can change width (1 or 2 columns) or side after the split controls were placed: keep them clear of it.
        canvasHost.viewTreeObserver.addOnGlobalLayoutListener{if(!destroyed)positionSplitControls()}
        refreshDock();refreshPages()
    }
    private fun closeSidePanel(){sidePanel?.let{canvasHost.removeView(it)};sidePanel=null;sidePanelPage=null}
    private fun showSidePanel(title:String, content:View){
        closeSidePanel()
        val panel=column().apply{pad(12)}
        val heading=row();heading.addView(label(title,18f,NAVY,true),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(icon("close",s("close")){closeSidePanel()});panel.addView(heading)
        panel.addView(ScrollView(this).apply{addView(content)},LinearLayout.LayoutParams(-1,0,1f))
        surface(panel);sidePanel=panel;sidePanelPage=store.page
        canvasHost.addView(panel,FrameLayout.LayoutParams(dp(340).coerceAtMost(resources.displayMetrics.widthPixels-dp(88)),-1,Gravity.END).apply{setMargins(dp(78),dp(76),dp(12),dp(76))})
    }

    private fun refreshTitle() {
        titleView.text = projectStore.lesson.title.ifBlank { s("lesson") }
        pageLabel.text = "${projectStore.lesson.current+1} / ${projectStore.lesson.pages.size}"
        refreshPages()
    }

    private fun refreshDock() {
        rooms?.refreshPresence()
        if(!::toolDock.isInitialized)return
        syncPdfWorkspace()
        pdfPane?.refreshTools()
        if(sidePanelPage!=null && sidePanelPage!==store.page)closeSidePanel()
        if(splitControlsPage!==store.page)refreshSplitControls()
        refreshSelectionBar()
        toolDock.refresh()
    }
    /** Actions from the dock and its Add panel; most reuse the Pie/shortcut commands. */
    private fun dockAction(key:String){
        if(key in listOf("shape","smart","text","sticky"))pdfPane?.writingMode()
        when(key){
            "split"->splitSettings();"lab"->openExplorer(true);"games"->openExplorer(false)
            "smart"->smart();"text"->addText(false)
            "touch"->if(!board.hasActiveInteraction){board.touchMode=!board.touchMode;prefs.edit().putBoolean("touchMode",board.touchMode).apply();toast(if(board.touchMode)tr("Pen + touch","قلم + لمس")else tr("Two tips","دو سر قلم"))}
            else->runShortcut(key)
        }
        refreshDock()
    }
    /** What the selection bar shows: its main actions, or one of them opened in place ("smart", "color", "more"). */
    private var selectionBarMode="main"
    private var selectionBarKey=""
    /**
     * Bar above the selection: at most six labeled actions, chosen by what is selected.
     * Smart, Color and More swap the bar's content in place (with Back) instead of opening a window elsewhere:
     * the eye and hand stay on the object, and the board is not covered.
     * Hidden while the selection is being dragged; shown again at the new position on release.
     */
    private fun refreshSelectionBar(){
        if(!::selectionBar.isInitialized)return
        val items=board.chosen()
        selectionHost.visibility=if(items.isEmpty()||focusMode||board.isTransforming)View.GONE else View.VISIBLE
        val key=items.joinToString{it.id};if(key!=selectionBarKey){selectionBarKey=key;selectionBarMode="main"}
        selectionBar.removeAllViews();if(items.isEmpty()||board.isTransforming)return
        val actions=mutableListOf<View>()
        fun action(symbol:String,label:String,title:String=label,active:Boolean=false,run:()->Unit){actions.add(ActionIcon(this,symbol,title,label,active,run))}
        fun open(mode:String){selectionBarMode=mode;refreshSelectionBar()}
        // "Back" points toward the reading start: right in Persian, left in English.
        fun back()=action(if(resources.configuration.layoutDirection==View.LAYOUT_DIRECTION_RTL)"next" else "menu_back",tr("Back","بازگشت")){open("main")}
        val ink=items.any{it.kind=="ink"};val smart=items.any{ObjectActions.canSmart(it)}
        when(selectionBarMode){
            "smart"->{
                back()
                if(smart){
                    action("text",tr("To text","به متن"),tr("Convert to text · detect language","تبدیل به متن · تشخیص زبان")){board.smartMode="text";processSmart(board.chosen())}
                    action("formula",tr("Solve","حل کن"),tr("Calculate / solve","محاسبه / حل")){board.smartMode="formula";processSmart(board.chosen())}
                    action("graph",tr("Plot","نمودار"),tr("Plot function","رسم تابع")){board.smartMode="graph";processSmart(board.chosen())}
                    action("convert",tr("Units","تبدیل واحد"),tr("Convert units","تبدیل واحد")){board.smartMode="convert";processSmart(board.chosen())}
                    action("search",tr("Search","جستجو"),tr("Search Google","جستجو در گوگل")){board.smartMode="search";processSmart(board.chosen())}
                }
                if(ink)action("shape",tr("To shape","به شکل"),tr("Recognize shape","تشخیص شکل")){board.smartMode="shape";processSmart(board.chosen())}
                fillSelectionBar(actions);positionSelectionBar();return
            }
            "color"->{
                back();colorActions(items,actions)
                fillSelectionBar(actions);positionSelectionBar();return
            }
            "more"->{
                back();moreActions(items){symbol,label,run->action(symbol,label){run()}}
                fillSelectionBar(actions);positionSelectionBar();return
            }
            "text_size"->{
                // Sizes readable from the back of a classroom, plus an exact value when needed.
                back();val current=items.singleOrNull()?.width?.toInt()
                listOf(20,28,40,56,72).forEach{n->action("text","$n",tr("Text size $n","اندازهٔ متن $n"),active=current==n){board.edit{it.width=n.toFloat();TextLayout.fit(it)};refreshSelectionBar()}}
                action("more",tr("Exact","دقیق"),s("text_size")){input(s("text_size"),(current?:28).toString()){v->val n=v.toFloat();require(n in 8f..120f);board.edit{it.width=n;TextLayout.fit(it)};refreshSelectionBar()}}
                fillSelectionBar(actions);positionSelectionBar();return
            }
        }
        val pdf=items.singleOrNull()?.takeIf{it.kind=="pdf"}
        if(pdf!=null){
            action("previous",tr("Previous","قبلی")){if(pdf.pdfPage>0)board.edit{it.pdfPage--}}
            action("page_picker","${pdf.pdfPage+1} / ${pdf.pageCount}",s("page_picker")){input(s("page_picker"),"${pdf.pdfPage+1}"){v->val n=v.toInt();require(n in 1..pdf.pageCount);board.edit{it.pdfPage=n-1}}}
            action("next",tr("Next","بعدی")){if(pdf.pdfPage<pdf.pageCount-1)board.edit{it.pdfPage++}}
        }
        if(smart||ink)action("smart",tr("Smart","هوشمند")){open("smart")}
        items.singleOrNull()?.takeIf{it.shape=="mindnode"}?.let{node->action("text",tr("Add text","افزودن متن")){textEditor(node)}}
        items.singleOrNull()?.let{o->if(Measurements.of(o)!=null)action("measure",s("measure")){measureShape(o)}}
        if(items.any{ObjectActions.canColor(it)})action("color",s("color")){open("color")}
        if(items.singleOrNull()?.kind in listOf("image","pdf"))action("crop",s("crop")){crop(items.single())}
        action("duplicate",s("duplicate")){
            val copies=duplicateItems(MindMap.group(store.page,board.chosen())).onEach{it.x+=24;it.y+=24;it.locked=false}
            store.editMetadata{store.page.items.addAll(copies)};board.selected.clear();board.selected.addAll(copies.map{it.id});refreshDock()
        }
        action("delete",s("delete")){board.delete()}
        action("more",tr("More","بیشتر"),s("edit")){open("more")}
        fillSelectionBar(actions);positionSelectionBar()
    }
    /**
     * Color in the bar itself: the same six ink colors as the pen strip plus white, and for strokes three widths.
     * Sticky notes get paper colors instead (dark ink on a dark note would be unreadable). "Custom" opens the full palette.
     */
    private fun colorActions(items:List<Item>,actions:MutableList<View>){
        val targets=items.filter{ObjectActions.canColor(it)}
        val notes=targets.isNotEmpty()&&targets.all{it.kind=="sticky"}
        val current=targets.firstOrNull()?.let{if(it.kind=="sticky")it.noteColor else it.color}
        val colors=if(notes)listOf(0xfffff59d.toInt(),0xfff8bbd0.toInt(),0xffbbdefb.toInt(),0xffc8e6c9.toInt(),0xffffe0b2.toInt(),0xffe1bee7.toInt(),Color.WHITE)
            else InkStrip.colors+Color.WHITE
        colors.forEach{color->actions.add(InkStrip.Swatch(this,color,null){applyColor(color);refreshSelectionBar()}.apply{
            chosen=color==current;contentDescription=tr("Color","رنگ");layoutParams=LinearLayout.LayoutParams(dp(48),dp(64))})}
        if(!notes&&targets.any{it.kind=="ink"||it.kind=="shape"}){
            val width=targets.first().width
            InkStrip.penWidths.forEachIndexed{i,w->actions.add(InkStrip.Swatch(this,null,i){board.edit{o->if(o.kind=="ink"||o.kind=="shape")o.width=w};refreshSelectionBar()}.apply{
                chosen=kotlin.math.abs(width-w)<.01f;ink=current?:NAVY;contentDescription=tr("Thickness","ضخامت")})}
        }
        actions.add(ActionIcon(this,"palette",tr("Custom color","رنگ دلخواه"),tr("Custom","دلخواه")){colors()})
    }
    private fun applyColor(color:Int)=board.edit{item->
        if(!ObjectActions.canColor(item))return@edit
        if(item.kind=="sticky"){
            item.noteColor=color
            if(Color.luminance(color)<.25 && Color.luminance(item.color)<.35)item.color=Color.WHITE
            else if(Color.luminance(color)>.65 && Color.luminance(item.color)>.65)item.color=NAVY
        }else item.color=color
    }
    /** Free horizontal span for canvas popups: the canvas minus the dock and a 12dp gap. */
    private fun freeSpan():Pair<Int,Int>{
        var minLeft=dp(12);var maxRight=canvasHost.width-dp(12)
        if(::toolDock.isInitialized&&toolDock.visibility==View.VISIBLE&&toolDock.width>0){
            if(toolDock.left<canvasHost.width/2)minLeft=maxOf(minLeft,toolDock.right+dp(12))else maxRight=minOf(maxRight,toolDock.left-dp(12))
        }
        return minLeft to maxRight
    }
    /** Greedy rows within the free width, so every action stays visible without scrolling. */
    private fun fillSelectionBar(actions:List<View>){
        selectionBar.removeAllViews()
        val (minLeft,maxRight)=freeSpan()
        // Before the first layout the width is unknown: keep one row; the next refresh wraps if needed.
        val limit=if(canvasHost.width==0)Int.MAX_VALUE else (maxRight-minLeft-dp(8)).coerceAtLeast(dp(64))
        var line=row();var used=0
        actions.forEach{v->
            v.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            if(used>0&&used+v.measuredWidth>limit){selectionBar.addView(line);line=row();used=0}
            line.addView(v,LinearLayout.LayoutParams(-2,dp(64)));used+=v.measuredWidth
        }
        selectionBar.addView(line)
    }
    /** Above the selection (below it when there is no room), never under the dock or off screen. */
    private fun positionSelectionBar(){
        if(!::selectionHost.isInitialized||selectionHost.visibility!=View.VISIBLE||canvasHost.width<=0)return
        val bounds=board.selectionScreenBounds()?:return
        val a=IntArray(2);val b=IntArray(2);board.getLocationInWindow(a);canvasHost.getLocationInWindow(b);bounds.offset((a[0]-b[0]).toFloat(),(a[1]-b[1]).toFloat())
        val (minLeft,maxRight)=freeSpan()
        val maxWidth=(maxRight-minLeft).coerceAtLeast(dp(64))
        selectionBar.measure(View.MeasureSpec.makeMeasureSpec(maxWidth,View.MeasureSpec.AT_MOST),View.MeasureSpec.UNSPECIFIED)
        val barWidth=selectionBar.measuredWidth.coerceIn(dp(64),maxWidth);val barHeight=selectionBar.measuredHeight.coerceAtLeast(dp(72))
        // Clear of the selection handles, which the board draws in physical dp (not scaled by UI size):
        // rotation handle 28dp above the top edge with a 23dp touch radius, resize handle 23dp around the bottom corner.
        val d=resources.displayMetrics.density
        val above=bounds.top-barHeight-(28+23+8)*d;val y=if(above>=dp(12))above else bounds.bottom+(23+8)*d
        (selectionHost.layoutParams as FrameLayout.LayoutParams).apply{
            width=barWidth;height=barHeight;gravity=Gravity.TOP or Gravity.LEFT
            leftMargin=(bounds.centerX()-barWidth/2).toInt().coerceIn(minLeft,(maxRight-barWidth).coerceAtLeast(minLeft))
            val floor=if(::toolDock.isInitialized)toolDock.bottomObstacle() else canvasHost.height-dp(84)
            topMargin=y.toInt().coerceIn(dp(12),(floor-barHeight-dp(12)).coerceAtLeast(dp(12)))
        };selectionHost.requestLayout()
    }
    private fun splitSettings(){
        if(board!==whiteboard){pdfMenu();return}
        // Beside the dock like other tool sheets; four side-by-side choices instead of a radio list in the middle of the board.
        val c=column().apply{pad(16)}
        lateinit var d:AlertDialog
        c.addView(segmented(listOf("1" to "1","2" to "2","3" to "3","4" to "4"),store.page.visiblePaneCount.toString()){n->board.split(n.toInt());refreshSplitControls();d.dismiss()})
        c.addView(label(tr("Each panel has its own pen, background and Undo","هر بخش قلم، پس‌زمینه و بازگردانی خودش را دارد"),13f,MUTED))
        d=toolSheet(tr("Split board","تقسیم تخته"),c)
    }
    private fun refreshSplitControls(){
        closePaneBar()
        splitControls?.let{canvasHost.removeView(it)};splitControls=null;splitControlsPage=store.page
        val n=store.page.visiblePaneCount;if(n==1||focusMode||canvasHost.width==0)return
        val overlay=FrameLayout(this);splitControls=overlay
        canvasHost.addView(overlay,0.coerceAtLeast(canvasHost.childCount-1),FrameLayout.LayoutParams(-1,-1))
        val columns=n
        store.page.panes.take(n).forEachIndexed{i,pane->
            val actions=row().apply{pad(2)};surface(actions)
            actions.addView(icon("pen",tr("Panel ${i+1}: pen","بخش ${i+1}: قلم")){paneBar(i,true)})
            actions.addView(icon("background",tr("Panel ${i+1}: background","بخش ${i+1}: پس‌زمینه")){paneBar(i,false)})
            // As wide as both buttons really are: a narrower scrolling box let the second button stick out under the dock.
            actions.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            val controlWidth=min(actions.measuredWidth,(canvasHost.width/columns-dp(12)).coerceAtLeast(dp(40)))
            // Explicit LEFT: with the default START gravity, Persian (RTL) put every panel's buttons at the right edge, ignoring leftMargin.
            overlay.addView(scrollRow(actions),FrameLayout.LayoutParams(controlWidth,maxOf(dp(52),actions.measuredHeight),Gravity.TOP or Gravity.LEFT))
        }
        positionSplitControls()
    }
    /** Bottom corner of each panel, never under the dock; re-run on every layout because the dock moves and resizes. */
    private fun positionSplitControls(){
        val overlay=splitControls?:return;val n=overlay.childCount;if(n==0||canvasHost.width==0)return
        val (freeLeft,freeRight)=freeSpan()
        for(i in 0 until n){
            val v=overlay.getChildAt(i);val lp=v.layoutParams as FrameLayout.LayoutParams;val w=lp.width
            val left=((i+1)*canvasHost.width/n-w-dp(6)).coerceAtMost(freeRight-w).coerceAtLeast(maxOf(freeLeft,i*canvasHost.width/n))
            val top=(canvasHost.height-dp(if(canvasHost.width<dp(800))216 else 144)).coerceAtLeast(dp(12))
            // Only on change, so this layout listener cannot trigger itself forever.
            if(lp.leftMargin!=left||lp.topMargin!=top){lp.leftMargin=left;lp.topMargin=top;v.layoutParams=lp}
        }
    }
    private var paneBarView:View?=null
    private var paneBarKey=""
    private fun closePaneBar(){paneBarView?.let{(it.parent as? ViewGroup)?.removeView(it)};paneBarView=null;paneBarKey=""}
    /**
     * A small bar inside the split panel itself, just above its controls: the teacher at that panel sees the
     * change in place, and other panels stay untouched (a centered dialog covered every panel).
     */
    private fun paneBar(index:Int,pen:Boolean){
        val key="$index:$pen";val reopen=paneBarKey!=key;closePaneBar();if(!reopen)return
        val page=store.page;val pane=page.panes[index];val n=page.visiblePaneCount
        board.applySharedPane(index)
        val bar=column().apply{setPadding(dp(10),dp(8),dp(10),dp(10));background=rounded(SURFACE,dp(18).toFloat(),OUTLINE);elevation=dp(8).toFloat();isClickable=true}
        bar.addView(label(if(pen)tr("Panel ${index+1} · pen","بخش ${index+1} · قلم")else tr("Panel ${index+1} · background","بخش ${index+1} · پس‌زمینه"),14f,MUTED,true).apply{gravity=Gravity.CENTER})
        fun chip(title:String,active:Boolean,run:()->Unit)=label(title,14f,if(active)Color.WHITE else NAVY,true).apply{
            gravity=Gravity.CENTER;setPadding(dp(12),0,dp(12),0);minHeight=dp(40);isClickable=true
            background=rounded(if(active)NAVY else SURFACE_VARIANT,dp(20).toFloat());setOnClickListener{run()}
        }
        fun chips(items:List<Pair<String,String>>,current:String,pick:(String)->Unit)=FlowRow(this,dp(6)).apply{items.forEach{(k,t)->addView(chip(t,k==current){pick(k);paneBarKey="";paneBar(index,pen)},ViewGroup.LayoutParams(-2,-2))}}
        if(pen){
            // The dock's ink grid works on the active panel, so the same 6 colors and 3 widths apply here.
            bar.addView(InkStrip(this,{board},showTools=false,grid=true){refreshDock()},LinearLayout.LayoutParams(-2,-2).apply{gravity=Gravity.CENTER_HORIZONTAL;topMargin=dp(6)})
            bar.addView(chips(PenStyles.selectable.map{it to PenStyles.name(this,it)},pane.penStyle){k->store.editMetadata{pane.penStyle=k}},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        }else{
            val patterns=listOf("plain" to tr("Plain","ساده"),"dots" to s("dots"),"grid" to s("grid"),"ruled" to s("ruled"),"hatch" to tr("Hatched","هاشور"))
            bar.addView(chips(patterns,page.pattern(index)){k->store.editMetadata{if(page.pattern(index)=="dark"&&pane.background==Color.WHITE)pane.background=0xff172a36.toInt();pane.pattern=k}},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
            // White, paper, soft blue, soft green, chalkboard green, dark slate.
            val colors=listOf(Color.WHITE,0xfffaf6ea.toInt(),0xffeef4ff.toInt(),0xffeaf6ee.toInt(),0xff20392f.toInt(),0xff172a36.toInt())
            bar.addView(FlowRow(this,dp(8)).apply{colors.forEach{c->addView(View(this@MainActivity).apply{
                background=rounded(c,dp(18).toFloat(),if(c==pane.background)NAVY else OUTLINE_STRONG);isClickable=true;contentDescription=tr("Background color","رنگ پس‌زمینه")
                setOnClickListener{store.editMetadata{if(page.pattern(index)=="dark")pane.pattern="plain";pane.background=c};paneBarKey="";paneBar(index,false)}
            },ViewGroup.LayoutParams(dp(40),dp(40)))}},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        }
        // Inside this panel and, where the panel meets the dock, beside the dock rather than under it.
        val paneWidth=canvasHost.width/n;val (freeLeft,freeRight)=freeSpan()
        val left0=maxOf(index*paneWidth+dp(8),freeLeft);val right0=minOf((index+1)*paneWidth-dp(8),freeRight)
        val width=(right0-left0).coerceAtMost(dp(320)).coerceAtLeast(dp(160))
        bar.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.UNSPECIFIED)
        val controlsTop=canvasHost.height-dp(if(canvasHost.width<dp(800))216 else 144)
        canvasHost.addView(bar,FrameLayout.LayoutParams(width,-2,Gravity.TOP or Gravity.LEFT).apply{
            leftMargin=(left0+(right0-left0-width)/2).coerceAtLeast(left0)
            topMargin=(controlsTop-bar.measuredHeight-dp(8)).coerceAtLeast(dp(12))
        })
        paneBarView=bar;paneBarKey=key
    }
    private fun refreshPages() {
        if(!::pageStrip.isInitialized)return
        val signature="${projectStore.lesson.current}:${projectStore.lesson.pages.joinToString{it.id}}:${projectStore.page.visiblePaneCount}"
        pageLabel.text="${projectStore.lesson.current+1} / ${projectStore.lesson.pages.size}"
        if(signature==pageSignature)return
        pageSignature=signature;pageStrip.removeAllViews()
        pageStrip.addView(icon("previous",s("previous")){switchPage(projectStore.lesson.current-1)})
        pageStrip.addView(pageLabel)
        pageStrip.addView(icon("pages",s("pages")+" · ${projectStore.lesson.current+1}/${projectStore.lesson.pages.size}"){pages()})
        pageStrip.addView(icon("next",s("next")){switchPage(projectStore.lesson.current+1)})
        pageStrip.addView(icon("insert",s("add_page")){addPage()})
        pageStrip.addView(icon("fit",s("fit")){if(board===whiteboard)board.fit()else pdfPane?.fitWidth()})
        refreshSplitControls()
    }
    private fun switchPage(index:Int) {
        if(rooms?.active==true&&rooms?.frame?.role==RoomRoles.VIEW)return
        if(index !in projectStore.lesson.pages.indices || board.hasActiveInteraction)return
        activateBoard(whiteboard);closeSidePanel();projectStore.lesson.current=index;whiteboard.clearSelection();whiteboard.reset();projectStore.changed()
    }
    private fun addPage() {
        if(projectStore.lesson.pages.size>=200 || board.hasActiveInteraction)return
        activateBoard(whiteboard);projectStore.editMetadata{projectStore.lesson.pages.add(projectStore.lesson.current+1,newBoardPage(projectStore.page));projectStore.lesson.current++}
        whiteboard.clearSelection();whiteboard.reset()
    }
    private fun savePens() {prefs.edit().putFloat("dashLength",board.dashLength).putFloat("dashGap",board.dashGap).putString("thickStyle",board.profile.thickStyle).putFloat("thickWidth",board.profile.thickWidth).putInt("thickColor",board.profile.thickColor).putInt("penColor",board.penColor).putInt("highlightColor",board.highlightColor).putFloat("penWidth",board.penWidth).putFloat("highlightWidth",board.highlightWidth).putString("penStyle",board.penStyle).putInt("penOpacity",board.penOpacity).apply()
        // Every pen change also belongs to the active dock preset at once, and its dot shows the new color.
        if(::toolDock.isInitialized)toolDock.refresh()}
    private val backgroundSwatches get()=listOf(Color.WHITE,Color.BLACK,0xff172a36.toInt(),PAPER,0xfffff5dc.toInt(),0xffeaf7f3.toInt(),0xffe9f0ff.toInt(),0xfff7ecfc.toInt(),0xfff4e8df.toInt(),0xffdce9ef.toInt())
    private fun palette(c:LinearLayout,initial:Int,background:Boolean=false,changed:(Int)->Unit){
        val holder=column();c.addView(holder);var current=initial
        fun refresh(){
            holder.removeAllViews()
            val base=if(background)backgroundSwatches else listOf(NAVY,Color.BLACK,Color.WHITE,0xffe45756.toInt(),ORANGE,0xffffcf40.toInt(),TEAL,0xff268bd2.toInt(),0xff865ac7.toInt(),0xffec76ab.toInt())
            holder.addView(colorPalette(current,base+projectStore.lesson.customColors.orEmpty()+current,{
                ColorPickerDialog.show(this,current){color->
                    current=color
                    if(color !in projectStore.lesson.customColors.orEmpty())projectStore.editMetadata{val colors=projectStore.lesson.customColors?:mutableListOf<Int>().also{projectStore.lesson.customColors=it};if(colors.size<40)colors.add(color)}
                    changed(color);refresh()
                }
            }){current=it;changed(it)})
        }
        refresh()
    }

    private fun backgroundSettings(paneIndex:Int=board.activePane){
        val page=store.page;val pane=page.panes[paneIndex.coerceIn(page.panes.indices)]
        val c=column().apply{pad(16)}
        c.addView(label(tr("Page pattern","الگوی صفحه"),17f,NAVY,true))
        c.addView(infoTitle(tr("Background scope","محدودهٔ پس‌زمینه"),tr("Pattern and color apply only to this panel.","طرح و رنگ فقط برای همین بخش اعمال می‌شوند.")))
        val patterns=listOf("plain" to tr("Plain","ساده"),"dots" to s("dots"),"grid" to s("grid"),"ruled" to s("ruled"),"hatch" to tr("Hatched","هاشور"))
        val patternRows=column();c.addView(patternRows)
        fun refreshPattern(){patternRows.removeAllViews();patterns.chunked(3).forEach{chunk->
            patternRows.addView(row().apply{chunk.forEach{(key,title)->
                addView(button(title,if(page.pattern(paneIndex) in listOf("white","dark"))key=="plain" else page.pattern(paneIndex)==key){
                    store.editMetadata{
                        if(page.pattern(paneIndex)=="dark" && pane.background==Color.WHITE)pane.background=0xff172a36.toInt()
                        pane.pattern=key
                    }
                    refreshPattern()
                },LinearLayout.LayoutParams(0,dp(52),1f).apply{setMargins(dp(2),dp(4),dp(2),dp(4))})
            }})
        }}
        refreshPattern()
        c.addView(label(tr("Background color","رنگ پس‌زمینه"),17f,NAVY,true))
        val initial=if(page.pattern(paneIndex)=="dark"&&pane.background==Color.WHITE)0xff172a36.toInt() else pane.background
        palette(c,initial,background=true){color->store.editMetadata{
            if(page.pattern(paneIndex)=="dark")pane.pattern="plain"
            pane.background=color
        }}
        toolSheet(tr("Background · page ${store.lesson.current+1}","پس‌زمینه · صفحهٔ ${store.lesson.current+1}"),ScrollView(this).apply{addView(c)})
    }
    private fun slider(c:LinearLayout,name:String,value:Float,maxValue:Int=40,undoable:Boolean=false,changed:(Float)->Unit){
        val title=label("$name · ${value.toInt()}",15f,NAVY,true);title.setPadding(dp(4),dp(18),dp(4),dp(8));c.addView(title)
        c.addView(SeekBar(this).apply{max=maxValue-1;progress=value.toInt()-1;contentDescription=name;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){title.text="$name · ${n+1}";if(user){changed(n+1f);if(undoable)board.sceneChanged()}}
            override fun onStartTrackingTouch(v:SeekBar?){if(undoable)store.checkpoint()};override fun onStopTrackingTouch(v:SeekBar?){if(undoable)store.changed()}
        })})
    }
    private fun bindBoardExtras(target:Board){
        target.store.historyLimit=HistorySettings.load(this)
        target.onViewportChanged={positionSelectionBar()}
        target.gestures.onHistory=::historyHud
        target.gestures.undoEnabled=prefs.getBoolean("twoFingerUndo",true);target.gestures.pieEnabled=prefs.getBoolean("pieEnabled",false)
        target.gestures.onPie={x,y->val a=IntArray(2);val b=IntArray(2);target.getLocationInWindow(a);canvasHost.getLocationInWindow(b);pieMenu(x+a[0]-b[0],y+a[1]-b[1])}
        target.onEducationalTap={o,point->when(o.kind){
            "periodic"->PeriodicTable.hit(this,o,point.x,point.y)?.let{e->if(!o.locked&&store.page.editable(o))store.editMetadata{o.selectedElement=e.number};PeriodicTable.elementDialog(this,e){board.insert(it)}}
            "graph"->coordinateTap(o,point)
        }}
    }
    private fun fillSettings(){
        val c=column().apply{pad(16)}
        c.addView(infoTitle(s("fill"),tr("Tap inside a closed shape. Empty board space and open strokes stay unchanged. The outline is preserved.","داخل شکل بسته بزنید؛ فضای تخته و خط باز رنگ نمی‌شوند. خط دور شکل حفظ می‌شود.")))
        palette(c,board.fillColor?:board.penColor){board.fillColor=it;board.tool="fill";board.clearSelection();refreshDock()}
        slider(c,tr("Fill opacity","کدری رنگ داخل"),board.fillAlpha/255f*100,100){board.fillAlpha=(it/100*255).toInt()}
        c.addView(button(tr("No fill","بدون رنگ داخل")){board.fillColor=null;board.tool="fill";board.clearSelection();refreshDock()})
        val d=toolSheet(s("fill"),c)
        c.addView(button(tr("Use bucket","استفاده از سطل رنگ"),true){board.tool="fill";board.clearSelection();refreshDock();d.dismiss()})
    }
    private fun newBoardPage(previous:Page?):Page {
        val inherit=previous!=null && prefs.getBoolean("inheritBackground",false)
        val pattern=if(inherit)previous!!.pattern(whiteboard.activePane) else prefs.getString("defaultPattern","dots")?:"dots"
        val old=previous?.panes?.getOrNull(whiteboard.activePane)?:previous?.panes?.firstOrNull()
        var color=if(inherit)old?.background?:Color.WHITE else prefs.getInt("defaultBackground",Color.WHITE)
        if(pattern=="dark"&&color==Color.WHITE)color=0xff172a36.toInt()
        return Page(background=if(pattern in listOf("white","dark"))"plain"else pattern,panes=mutableListOf(Pane(background=color)))
    }
    private fun defaultBackgroundSettings(){
        val c=column().apply{pad(16)}
        val patterns=listOf("plain","dots","grid","ruled","hatch")
        val pick=Spinner(this).apply{adapter=OptionAdapter(this@MainActivity,patterns.map{s(it)});setSelection(patterns.indexOf(prefs.getString("defaultPattern","dots")).coerceAtLeast(0))};c.addView(pick)
        var color=prefs.getInt("defaultBackground",Color.WHITE);palette(c,color,true){color=it}
        val inherit=Switch(this).apply{text=tr("New page uses current page background","صفحهٔ جدید از پس‌زمینهٔ صفحهٔ فعلی استفاده کند");isChecked=prefs.getBoolean("inheritBackground",false)};c.addView(inherit)
        c.addView(infoTitle(tr("New pages","صفحات جدید"),tr("Only the color and pattern are copied. Existing pages are unchanged. The first page of a new lesson uses these defaults.","فقط رنگ و طرح کپی می‌شوند. صفحات موجود تغییر نمی‌کنند. صفحهٔ اول فایل جدید از پیش‌فرض استفاده می‌کند.")))
        val d=dialog(s("page_background"),c);c.addView(button(s("apply"),true){prefs.edit().putString("defaultPattern",patterns[pick.selectedItemPosition]).putInt("defaultBackground",color).putBoolean("inheritBackground",inherit.isChecked).apply();d.dismiss()})
    }
    private fun calibrationSettings(){
        val c=column().apply{pad(16)}
        c.addView(button(tr("Pen tips and palm","سر قلم و کف دست")){calibrateTips()})
        c.addView(button(s("touch_test")){dialog(s("touch_test"),TouchDiagnostics(this,board.profile).apply{minimumHeight=dp(320)})})
        c.addView(infoTitle(tr("Real centimetres","سانتی‌متر واقعی"),tr("Measure the line below with a physical ruler, then enter its length in cm. Calibration belongs to this display. Without calibration, geometry uses board units (u).", "خط زیر را با خطکش واقعی اندازه بگیرید و طولش را به سانتی‌متر وارد کنید. کالیبراسیون مخصوص همین نمایشگر است. بدون آن، واحد هندسی u است.")))
        val line=object:View(this){override fun onDraw(c:Canvas){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=NAVY;strokeWidth=3f};c.drawLine(dp(16).toFloat(),height/2f,width-dp(16).toFloat(),height/2f,p);listOf(dp(16).toFloat(),width-dp(16).toFloat()).forEach{x->c.drawLine(x,8f,x,height-8f,p)}}};c.addView(line,LinearLayout.LayoutParams(-1,dp(40)))
        val length=field(hintValue=tr("Measured line length (cm)","طول واقعی خط (سانتی‌متر)"));length.inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;c.addView(length)
        val d=dialog(s("calibration"),c)
        c.addView(button(s("apply"),true){val n=length.text.toString().toFloatOrNull();val px=line.width-dp(32);if(n==null||!n.isFinite()||n<=0||px<=0||px/n !in 5f..1000f){length.error=tr("Enter a valid measured length","طول واقعی معتبر وارد کنید");return@button};prefs.edit().putFloat("pixelsPerCm",px/n).apply();board.sceneChanged();d.dismiss()})
        c.addView(button(tr("Use board units","استفاده از واحد تخته")){prefs.edit().remove("pixelsPerCm").apply();board.sceneChanged();d.dismiss()})
    }
    private fun measureShape(o:Item){
        if(Measurements.of(o)!=null)board.showMeasurements(o)
    }
    private fun coordinateTap(o:Item,point:PointF){
        val (x,y)=o.local(point.x,point.y);val step=min(o.w,o.h)/(2*o.domain)
        val coordinate=PlotPoint(((x-o.w/2)/step).toDouble(),((o.h/2-y)/step).toDouble())
        choices("(${DisplayNumbers.one(coordinate.x)}, ${DisplayNumbers.one(coordinate.y)})",listOf("add_point","coordinates")){i->if(i==0 && !o.locked && store.page.editable(o)){store.editMetadata{o.plotPoints=(o.plotPoints.orEmpty()+coordinate).takeLast(200);o.plotIntersections=emptyList()}}else coordinateSettings(o)}
    }
    private fun coordinateSettings(o:Item){
        val c=column().apply{pad(16)}
        c.addView(button(tr("Functions and axis range","توابع و بازهٔ محور")){graph(existing=o)})
        val r=row();val x=field(hintValue="x");val y=field(hintValue="y");r.addView(x,LinearLayout.LayoutParams(0,-2,1f));r.addView(y,LinearLayout.LayoutParams(0,-2,1f));c.addView(r)
        val list=column();c.addView(list)
        fun refresh(){list.removeAllViews();o.plotPoints.orEmpty().takeLast(40).forEach{point->val r=row();r.addView(label("(${DisplayNumbers.one(point.x)}, ${DisplayNumbers.one(point.y)})"),LinearLayout.LayoutParams(0,-2,1f));r.addView(icon("delete",s("delete")){if(!o.locked&&store.page.editable(o)){store.editMetadata{o.plotPoints=o.plotPoints.orEmpty()-point;o.plotIntersections=emptyList()};refresh()}});list.addView(r)};o.plotIntersections.orEmpty().forEach{p->list.addView(label("× (${DisplayNumbers.one(p.x)}, ${DisplayNumbers.one(p.y)})",14f,ORANGE))}}
        c.addView(button(s("add_point")){try{val a=x.text.toString().toDouble();val b=y.text.toString().toDouble();require(a.isFinite()&&b.isFinite()&&abs(a)<=100000&&abs(b)<=100000);if(!o.locked&&store.page.editable(o))store.editMetadata{o.plotPoints=(o.plotPoints.orEmpty()+PlotPoint(a,b)).takeLast(200);o.plotIntersections=emptyList()};refresh()}catch(_:Exception){x.error=tr("Enter finite coordinates","مختصات معتبر وارد کنید")}})
        c.addView(Switch(this).apply{text=s("connect_points");isChecked=o.connectPlotPoints;setOnCheckedChangeListener{_,v->if(!o.locked&&store.page.editable(o))store.editMetadata{o.connectPlotPoints=v;o.plotIntersections=emptyList()}}})
        c.addView(button(tr("Find intersections","یافتن تقاطع‌ها")){
            val functions=o.text.split(';');val domain=o.domain.toDouble();val vertices=o.plotPoints.orEmpty().toList();val connected=o.connectPlotPoints
            work({CoordinateTools.intersections(functions,domain,vertices,connected)}){points->
                if(store.page.items.any{it===o}&&!o.locked&&store.page.editable(o)&&o.plotPoints.orEmpty()==vertices&&o.text.split(';')==functions&&o.domain.toDouble()==domain&&o.connectPlotPoints==connected){store.editMetadata{o.plotIntersections=points};refresh()}
            }
        })
        c.addView(infoTitle(tr("Intersections","تقاطع‌ها"),tr("Numerical approximations within the axis range. Tangencies or very narrow features may be missed. Up to 3 functions are supported; points can also be placed by tapping the graph.","تقاطع‌ها به‌صورت عددی در بازهٔ محور پیدا می‌شوند. تماس مماسی یا جزئیات بسیار باریک ممکن است پیدا نشوند. حداکثر ۳ تابع؛ نقطه‌گذاری با لمس نمودار هم ممکن است.")))
        refresh();dialog(s("coordinates"),ScrollView(this).apply{addView(c)})
    }
    private var pieOverlay:View?=null
    private var refreshPieSettings:(()->Unit)?=null
    private var historyOverlay:HistoryScrubView?=null
    private fun historyHud(position:Int,total:Int){
        if(!::canvasHost.isInitialized)return
        if(position<0){historyOverlay?.let{canvasHost.removeView(it)};historyOverlay=null;return}
        val hud=historyOverlay?:HistoryScrubView(this).also{view->
            historyOverlay=view;canvasHost.addView(view,FrameLayout.LayoutParams(min(dp(420),(canvasHost.width-dp(32)).coerceAtLeast(dp(180))),dp(64),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=dp(78)})
        }
        hud.update(position,total)
    }
    private fun pieSlots():List<String> = try{val a=org.json.JSONArray(prefs.getString("pieSlots","[]"));(0..5).map{a.optString(it).takeIf{key->key in ShortcutCatalog.keys}.orEmpty()}}catch(_:Exception){List(6){""}}
    private fun assignPie(index:Int){
        choices(tr("Choose shortcut","انتخاب میان‌بر"),listOf("clear_shortcut")+ShortcutCatalog.keys){n->val slots=pieSlots().toMutableList();slots[index]=if(n==0)""else ShortcutCatalog.keys[n-1];prefs.edit().putString("pieSlots",org.json.JSONArray(slots).toString()).apply();pieOverlay?.let{canvasHost.removeView(it)};pieOverlay=null;refreshPieSettings?.invoke()}
    }
    private fun pieSettings(){
        val c=column().apply{pad(16)}
        c.addView(Switch(this).apply{text=tr("Enable five-finger pie menu","فعال‌سازی منوی پنج‌انگشتی");isChecked=prefs.getBoolean("pieEnabled",false);setOnCheckedChangeListener{_,v->prefs.edit().putBoolean("pieEnabled",v).apply();board.gestures.reset();whiteboard.gestures.pieEnabled=v;board.gestures.pieEnabled=v}})
        c.addView(infoTitle(tr("Gesture","ژست"),tr("Double tap with five fingers, lift your hand, then tap a shortcut. Long-press to change a shortcut. Movement and palm contact do not trigger the menu.","دو بار سریع با پنج انگشت ضربه بزنید، دست را بردارید و میان‌بر را بزنید. برای تغییر میان‌بر، آن را نگه دارید. حرکت و تماس کف دست منو را باز نمی‌کنند.")))
        val slots=column();c.addView(slots)
        fun refresh(){slots.removeAllViews();pieSlots().forEachIndexed{i,key->val r=row();r.addView(icon(if(key.isBlank())"insert"else key,if(key.isBlank())tr("Assign shortcut","تعیین میان‌بر")else s(key)){assignPie(i)});r.addView(button("${i+1} · "+if(key.isBlank())"+"else s(key)){assignPie(i)},LinearLayout.LayoutParams(0,-2,1f));slots.addView(r)}}
        refreshPieSettings=::refresh;refresh()
        dialog(s("pie_settings"),ScrollView(this).apply{addView(c)}).setOnDismissListener{refreshPieSettings=null}
    }
    private fun pieMenu(x:Float,y:Float){
        if(!prefs.getBoolean("pieEnabled",false)||destroyed)return
        pieOverlay?.let{canvasHost.removeView(it)}
        val overlay=FrameLayout(this).apply{layoutDirection=View.LAYOUT_DIRECTION_LTR;setBackgroundColor(0x12000000);isClickable=true};pieOverlay=overlay
        val radius=dp(100).toFloat();val margin=dp(150).toFloat();val cx=x.coerceIn(min(margin,canvasHost.width/2f),max(canvasHost.width-margin,canvasHost.width/2f));val cy=y.coerceIn(min(margin,canvasHost.height/2f),max(canvasHost.height-margin,canvasHost.height/2f))
        fun dismiss(){canvasHost.removeView(overlay);pieOverlay=null}
        overlay.setOnClickListener{dismiss()}
        val all=pieSlots().mapIndexed{i,key->i to key};val assigned=all.filter{it.second.isNotBlank()};val shown=assigned.ifEmpty{all}
        shown.forEachIndexed{position,(i,key)->val angle=(-90+position*360.0/shown.size)*PI/180;val cell=column().apply{gravity=Gravity.CENTER;background=rounded(SURFACE,dp(16).toFloat(),OUTLINE);pad(4)}
            val b=icon(if(key.isBlank())"insert"else key,if(key.isBlank())tr("Assign shortcut","تعیین میان‌بر")else s(key)){dismiss();if(key.isBlank())assignPie(i)else runShortcut(key)}
            b.setOnLongClickListener{dismiss();assignPie(i);true};cell.addView(b)
            if(key.isNotBlank())cell.addView(label(s(key),11f).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER})
            overlay.addView(cell,FrameLayout.LayoutParams(dp(84),dp(80),Gravity.TOP or Gravity.LEFT).apply{leftMargin=(cx+radius*cos(angle)-dp(42)).toInt();topMargin=(cy+radius*sin(angle)-dp(40)).toInt()})
        }
        canvasHost.addView(overlay,FrameLayout.LayoutParams(-1,-1))
    }
    private fun runShortcut(key:String){
        if(rooms?.active==true&&key in listOf("new","open","recent","language","ui_size")){toast(s("room_leave_first"));return}
        when(key){
            "undo"->board.undo();"redo"->board.redo();"clear_page"->clearPageNow()
            "pen","erase","select","pan"->{board.tool=key;board.clearSelection();refreshDock()}
            "shape"->shapes();"fill"->fillSettings()
            "color"->{val c=column().apply{pad(12)};val d=toolSheet(s("color"),c);val pane=store.page.panes[board.activePane];palette(c,if(store.page.visiblePaneCount>1)pane.color else board.inkColor){color->if(store.page.visiblePaneCount>1)store.editMetadata{pane.color=color}else board.inkColor=color;savePens();d.dismiss()}}
            "thicker","thinner"->{val delta=if(key=="thicker")2 else -2;if(store.page.visiblePaneCount>1){val pane=store.page.panes[board.activePane];store.editMetadata{pane.penWidth=(pane.penWidth+delta).coerceIn(1f,80f)}}else board.inkWidth=(board.inkWidth+delta).coerceIn(1f,80f);savePens()}
            "new_page"->addPage();"new_layer"->{if(store.page.layers.size<100)store.editMetadata{val layer=Layer(name=tr("Layer ","لایهٔ ")+(store.page.layers.size+1));store.page.layers.add(layer);store.page.activeLayerId=layer.id}}
            "layers"->layers();"pages"->pages();"background"->backgroundSettings();"fit"->board.fit()
            "coordinates"->board.insert(Item(kind="graph",text="",w=560f,h=400f));"graph"->graph();"measure"->board.chosen().singleOrNull()?.let{measureShape(it)}
            "periodic"->PeriodicTable.show(this){board.insert(it)}
            "text"->addText(false);"sticky"->addText(true);"mind_map"->mindNode(null)
            "ruler","set_square","protractor","compass"->board.insert(Item(kind="shape",shape=key,w=if(key=="compass")300f else 360f,h=when(key){"compass"->300f;"ruler"->64f;else->180f},geometryVersion=1,geometryAngle=if(key=="compass")-60f else 45f))
            "smart"->smartSettings();"math"->math();"insert"->insert();"image"->pick("image/*",102);"pdf"->choosePdfImport()
            "timer","stopwatch","dice","scoreboard"->floatingTools.open(key)
            "files"->projectFileMenu()
            "new"->withPdfExit{confirm(s("new_confirm")){persist();documentId=newId();prefs.edit().putString("current",documentId).apply();projectStore.replace(Lesson(title=s("lesson"),pages=mutableListOf(newBoardPage(null))));whiteboard.clearSelection();whiteboard.reset()}}
            "open"->withPdfExit{pick("application/octet-stream",101)}
            "save"->export("vura",true){createDocument(it,"application/octet-stream")}
            "recent"->withPdfExit{recent()}
            "export_pdf","export_png","export_jpg"->exportChoice(key.substringAfter('_'))
            "rename"->rename();"lab"->openExplorer(true);"games"->openExplorer(false);"share"->shareOptions();"help"->quickGuide()
            "google_search_settings"->GoogleSearchSettingsUi(this).show(menuReturn.also{menuReturn=null});"fonts"->fontSettings();"models"->models();"input_controls"->settingsAt("input");"page_background"->defaultBackgroundSettings();"calibration"->calibrationSettings()
            "settings"->settings()
            "language"->choices(s("language"),listOf("english","persian")){i->persist();prefs.edit().putString("language",if(i==0)"en"else"fa").apply();worker.execute{handler.post{if(!destroyed)recreate()}}}
            "ui_size"->choices(s("ui_size"),listOf("small","medium","large")){i->persist();prefs.edit().putFloat("uiScale",listOf(.8f,1f,1.4f)[i]).apply();worker.execute{handler.post{if(!destroyed)recreate()}}}
            "delete"->board.delete()
            "duplicate"->{val copies=duplicateItems(board.chosen());store.editMetadata{copies.forEach{it.x+=24;it.y+=24;store.page.items.add(it)}}}
            "lock"->board.edit{it.locked=true}
            "unlock_all"->board.unlockAll()
            "front","back"->{val chosen=board.chosen();store.editMetadata{store.page.items.removeAll(chosen.toSet());if(key=="front")store.page.items.addAll(chosen)else store.page.items.addAll(0,chosen)}}
        }
    }
    private fun refreshMute(){
        if(!::voiceMuteButton.isInitialized)return
        voiceMuteButton.visibility=if(VoiceSettings.enabled(this)&&(voiceStarting||voiceAssistant?.active==true))View.VISIBLE else View.GONE
        voiceMuteButton.isSelected=voiceMuted;voiceMuteButton.alpha=if(voiceMuted)1f else .6f
        voiceMuteButton.setImageResource(IconCatalog.resource(if(voiceMuted)"mute"else"voice_assistant"))
        voiceMuteButton.contentDescription=if(voiceMuted)tr("Microphone muted · tap to unmute","میکروفن بی‌صدا · برای فعال کردن بزنید")else tr("Microphone active · tap to mute","میکروفن فعال · برای بی‌صدا کردن بزنید")
        voiceMuteButton.tooltipText=voiceMuteButton.contentDescription
    }

    private fun penSettings(){
        // The highlighter has its own color and width; "More settings" from its strip must edit it, not the pen.
        if(board.tool=="highlight"&&store.page.visiblePaneCount==1){
            val c=column().apply{pad(16)}
            palette(c,board.highlightColor){board.highlightColor=it;savePens()}
            slider(c,tr("Thickness","ضخامت"),board.highlightWidth,80){board.highlightWidth=it;savePens()}
            toolSheet(tr("Highlighter","هایلایتر"),ScrollView(this).apply{addView(c)});return
        }
        val c=column().apply{pad(16)};val body=column();var broad=false
        fun panel()=store.page.panes.getOrNull(board.activePane)?.takeIf{store.page.visiblePaneCount>1}
        fun penColor()=panel()?.color?:if(broad)board.profile.thickColor else board.penColor
        fun penWidth()=panel()?.penWidth?:if(broad)board.profile.thickWidth else board.penWidth
        fun dashLength()=panel()?.dashLength?:board.dashLength
        fun dashGap()=panel()?.dashGap?:board.dashGap
        fun tipStyle()=panel()?.penStyle?:if(broad)board.profile.thickStyle else board.penStyle
        val preview=object:View(this){override fun onDraw(canvas:Canvas){
            val style=tipStyle();val color=penColor();val width=penWidth()
            val o=Item(shape=style,color=color,width=width*resources.displayMetrics.density,alpha=board.penOpacity,dashLength=dashLength(),dashGap=dashGap())
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;strokeWidth=width*resources.displayMetrics.density*(if(style=="highlight")4f else if(style=="marker")1.8f else 1f);alpha=if(style=="highlight")75*board.penOpacity/255 else board.penOpacity;this.style=Paint.Style.STROKE;strokeCap=if(style in listOf("marker","highlight"))Paint.Cap.SQUARE else Paint.Cap.ROUND;if(style=="dashed")pathEffect=DashPathEffect(floatArrayOf(dashLength(),dashGap()),0f)}
            PenStyles.apply(paint,o)
            val path=Path();path.moveTo(20f,height*.7f);path.cubicTo(this.width*.3f,0f,this.width*.55f,height.toFloat(),this.width-20f,height*.3f);canvas.drawPath(path,paint)
        }}.apply{background=rounded(PAPER,dp(10).toFloat())}
        c.addView(preview,LinearLayout.LayoutParams(-1,dp(44)))
        fun refresh(){
            body.removeAllViews();palette(body,penColor()){color->val pane=panel();if(pane!=null)store.editMetadata{pane.color=color}else if(broad)board.profile.thickColor=color else board.penColor=color;savePens();preview.invalidate()}
            slider(body,tr("Thickness","ضخامت"),penWidth(),80){value->val pane=panel();if(pane!=null)store.editMetadata{pane.penWidth=value}else if(broad)board.profile.thickWidth=value else board.penWidth=value;savePens();preview.invalidate()}
            // Every line style visible at once (wraps instead of scrolling); the current one is filled.
            val styles=FlowRow(this,dp(6));body.addView(styles,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
            PenStyles.selectable.forEach{key->styles.addView(button(PenStyles.name(this,key),tipStyle()==key){val pane=panel();if(pane!=null)store.editMetadata{pane.penStyle=key}else if(broad)board.profile.thickStyle=key else board.penStyle=key;savePens();refresh();preview.invalidate()},ViewGroup.LayoutParams(-2,dp(44)))}
            slider(body,tr("Opacity","کدری"),board.penOpacity/255f*100,100){board.penOpacity=(it/100*255).toInt();savePens();preview.invalidate()}
            if(tipStyle()=="dashed"){
                slider(body,tr("Dash length","طول خط‌چین"),dashLength(),80){value->val pane=panel();if(pane!=null)store.editMetadata{pane.dashLength=value}else board.dashLength=value;savePens();preview.invalidate()}
                slider(body,tr("Dash gap","فاصلهٔ خط‌چین"),dashGap(),80){value->val pane=panel();if(pane!=null)store.editMetadata{pane.dashGap=value}else board.dashGap=value;savePens();preview.invalidate()}
            }
        }
        // Fine/broad tip is a two-way switch, so the active side must show (both looked the same before).
        val tips=row()
        fun tipsRefresh(){tips.removeAllViews()
            tips.addView(button(tr("Fine tip","سر باریک"),!broad){broad=false;tipsRefresh();refresh();preview.invalidate()},LinearLayout.LayoutParams(0,dp(44),1f).apply{marginEnd=dp(6)})
            tips.addView(button(tr("Broad tip","سر پهن"),broad){broad=true;tipsRefresh();refresh();preview.invalidate()},LinearLayout.LayoutParams(0,dp(44),1f))
        }
        if(!board.touchMode && panel()==null){tipsRefresh();c.addView(tips,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})}
        c.addView(body);refresh();toolSheet(s("pen"),ScrollView(this).apply{addView(c)})
    }

    private fun layers(){
        if(sidePanel!=null){closeSidePanel();return}
        val content=column();val pageId=store.page.id
        fun refresh(){
            if(store.page.id!=pageId){closeSidePanel();return}
            content.removeAllViews()
            content.addView(label(tr("Top layer is in front. Drag the handle to reorder.","لایهٔ بالایی در جلو است. برای جابه‌جایی، دستگیره را بکشید."),13f,MUTED))
            content.addView(button(tr("Add layer","افزودن لایه")){if(store.page.layers.size<100){store.editMetadata{val l=Layer(name=tr("Layer","لایه")+" ${store.page.layers.size+1}");store.page.layers.add(l);store.page.activeLayerId=l.id};board.clearSelection();refresh()}})
            content.addView(button(tr("Unlock all on this page","بازکردن همهٔ قفل‌ها در این صفحه")){
                val count=board.unlockAll()
                toast(if(count>0)tr("Objects and layers unlocked on this page","قفل آبجکت‌ها و لایه‌های این صفحه باز شد")else tr("No locked objects or layers","آبجکت یا لایهٔ قفل‌شده‌ای نیست"));refresh()
            })
            store.page.layers.asReversed().forEach { layer ->
                val isPdfBaseLayer=board.pdfBaseId!=null && store.page.items.any{it.id==board.pdfBaseId && it.layerId==layer.id}
                val card=column().apply{pad(6);background=rounded(if(layer.id==store.page.activeLayerId)PRIMARY_CONTAINER else SURFACE,dp(10).toFloat(),OUTLINE)}
                content.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
                val head=row();card.addView(head)
                head.addView(label(layer.name,15f,NAVY,true).apply{
                    maxLines=2;setOnClickListener{store.editMetadata{store.page.activeLayerId=layer.id};board.clearSelection();refresh()}
                    setOnLongClickListener{input(tr("Layer name","نام لایه"),layer.name){v->require(v.isNotBlank()&&v.length<=200);store.editMetadata{layer.name=v};refresh()};true}
                },LinearLayout.LayoutParams(0,dp(52),1f))
                val handle=icon("drag",tr("Hold and drag to reorder","نگه دارید و برای جابه‌جایی بکشید")){}
                handle.setOnLongClickListener{it.startDragAndDrop(ClipData.newPlainText("layer",layer.id),View.DragShadowBuilder(it),layer.id,0)}
                head.addView(handle)
                card.setOnDragListener{v,event->
                    when(event.action){
                        DragEvent.ACTION_DRAG_STARTED->event.localState is String && store.page.layers.any{it.id==event.localState}
                        DragEvent.ACTION_DRAG_ENTERED->{v.alpha=.65f;true}
                        DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED->{v.alpha=1f;true}
                        DragEvent.ACTION_DROP->{v.alpha=1f;val from=store.page.layers.indexOfFirst{it.id==event.localState};val to=store.page.layers.indexOf(layer)
                            if(from>=0&&to>=0&&from!=to){store.editMetadata{store.page.layers.add(to,store.page.layers.removeAt(from))};refresh()};true}
                        else->true
                    }
                }
                val actions=row();card.addView(scrollRow(actions))
                actions.addView(icon(if(layer.visible)"visible"else"hidden",tr("Show / hide","نمایان / پنهان"),!layer.visible){store.editMetadata{layer.visible=!layer.visible};board.clearSelection();refresh()})
                actions.addView(icon(if(layer.locked)"lock"else"unlock",tr("Lock / unlock","قفل / بازکردن"),layer.locked){store.editMetadata{layer.locked=!layer.locked};board.clearSelection();refresh()})
                actions.addView(icon("up",tr("Move up","بالاتر")){val i=store.page.layers.indexOf(layer);if(i<store.page.layers.lastIndex){store.editMetadata{java.util.Collections.swap(store.page.layers,i,i+1)};refresh()}})
                actions.addView(icon("down",tr("Move down","پایین‌تر")){val i=store.page.layers.indexOf(layer);if(i>0){store.editMetadata{java.util.Collections.swap(store.page.layers,i,i-1)};refresh()}})
                actions.addView(icon("delete",tr("Delete layer","حذف لایه")){
                    if(store.page.layers.size>1 && !isPdfBaseLayer)confirm(tr("Delete layer and its contents?","لایه و محتوایش حذف شود؟")){
                        store.editMetadata{store.page.items.removeAll{it.layerId==layer.id};store.page.layers.remove(layer);if(store.page.activeLayerId==layer.id)store.page.activeLayerId=store.page.layers.last().id};board.clearSelection();refresh()
                    }
                }.apply{isEnabled=store.page.layers.size>1 && !isPdfBaseLayer;alpha=if(isEnabled)1f else .3f})
                val opacity=label(tr("Opacity","میزان کدری")+" ${(layer.opacity*100).toInt()}%",13f,MUTED);card.addView(opacity)
                card.addView(SeekBar(this).apply{max=100;progress=(layer.opacity*100).toInt();contentDescription=tr("Layer opacity","میزان کدری لایه");setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onStartTrackingTouch(v:SeekBar?){store.checkpoint()}
                    override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){if(user){layer.opacity=n/100f;opacity.text=tr("Opacity","میزان کدری")+" $n%";board.sceneChanged()}}
                    override fun onStopTrackingTouch(v:SeekBar?){store.changed();board.clearSelection()}
                })})
                if(board.chosen().isNotEmpty()&&!layer.locked&&layer.visible)card.addView(button(tr("Move selection here","انتقال انتخاب به این لایه")){val chosen=board.chosen();store.editMetadata{chosen.forEach{it.layerId=layer.id}};board.clearSelection();refresh()})
            }
        }
        refresh();showSidePanel(tr("Layers","لایه‌ها"),content)
    }
    private fun calibrateTips(){
        val c=column().apply{pad(20)}
        c.addView(infoTitle(tr("Information","اطلاعات"),tr("Contact width comes from the touch controller. Calibrate in raw pixels, not screen centimetres. Unknown/zero contact sizes use the fine tip.","اندازهٔ تماس را کنترلر لمس گزارش می‌کند. آستانه‌ها با پیکسل خام تنظیم می‌شوند، نه سانتی‌متر صفحه. اندازهٔ صفر یا نامشخص، سر باریک محسوب می‌شود.")))
        c.addView(label(tr("Fine tip maximum contact width (px)","بیشترین عرض تماس سر باریک (پیکسل)"),14f));val thin=field(board.profile.thin.toString());c.addView(thin)
        c.addView(label(tr("Palm rejection starts at (px)","شروع تشخیص کف دست (پیکسل)"),14f));val palm=field(board.profile.palm.toString());c.addView(palm)
        val palmErase=CheckBox(this).apply{text=s("palm_erase");isChecked=board.profile.palmErase};c.addView(palmErase)
        c.addView(label(tr("Between these thresholds: broad-tip color and thickness. Set each tip's appearance in Pen settings.","بین این دو آستانه، رنگ و ضخامت سر پهن اعمال می‌شود. ظاهر هر دو سر را از تنظیمات قلم انتخاب کنید."),14f))
        c.addView(button(s("touch_test")){dialog(s("touch_test"),TouchDiagnostics(this,board.profile).apply{minimumHeight=dp(320)})})
        val d=dialog(tr("Dual-tip calibration","کالیبراسیون دو سر قلم"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){val a=thin.text.toString().toFloatOrNull();val b=palm.text.toString().toFloatOrNull();if(a==null||b==null||!a.isFinite()||!b.isFinite()||a<=0||b<=a){thin.error=tr("Use 0 < fine < palm","باید ۰ < سر باریک < کف دست باشد");return@button};board.profile.calibrated=true;board.profile.thin=a;board.profile.palm=b;board.profile.palmErase=palmErase.isChecked;prefs.edit().putBoolean("calibrated",true).putFloat("thin",a).putFloat("palm",b).putBoolean("palmErase",palmErase.isChecked).apply();savePens();d.dismiss()})
    }
    /**
     * Eraser sheet, most used first: what it erases, how big, then what else it may touch.
     * PDF options appear only while a PDF is open (they mean nothing otherwise). Clearing the page stays last and
     * needs a slide, because it is the one destructive action here.
     */
    private fun eraserSettings(){
        val c=column().apply{pad(16)}
        c.addView(sectionTitle(tr("Erases","پاک می‌کند")))
        c.addView(segmented(listOf("stroke" to tr("Whole stroke","کل خط"),"area" to tr("Only where touched","فقط محل لمس")),board.eraserMode){
            board.eraserMode=it;prefs.edit().putString("eraserMode",it).apply()})
        c.addView(sectionTitle(tr("Size","اندازه")))
        val sizes=listOf(10f to tr("Small","کوچک"),18f to tr("Medium","متوسط"),36f to tr("Large","بزرگ"))
        c.addView(segmented(sizes.map{(r,t)->r.toInt().toString() to t},sizes.firstOrNull{kotlin.math.abs(it.first-board.eraserRadius)<.5f}?.first?.toInt()?.toString()){
            board.eraserRadius=it.toFloat();prefs.edit().putFloat("eraserRadius",board.eraserRadius).apply()})
        c.addView(sectionTitle(tr("Also","همچنین")))
        c.addView(switchRow(tr("Erase objects too","اشیاء را هم پاک کند"),board.eraseObjects,tr("Text, shapes and images, not only handwriting","متن، شکل و تصویر؛ نه فقط دست‌نوشته")){
            board.eraseObjects=it;prefs.edit().putBoolean("eraseObjects",it).apply()})
        c.addView(switchRow(tr("Use palm as a temporary eraser","کف دست، پاک‌کن موقت باشد"),board.profile.palmErase,tr("Needs a panel that reports touch size","پنل باید اندازهٔ تماس را گزارش کند")){
            board.profile.palmErase=it;prefs.edit().putBoolean("palmErase",it).apply()})
        if(projectStore.lesson.pdf!=null){
            c.addView(sectionTitle("PDF"))
            c.addView(switchRow(tr("Also cover the PDF's own content","محتوای خود PDF را هم بپوشاند"),board.erasePdfContent,tr("Covered with white; Undo restores it. Not a secure deletion.","با سفید پوشانده می‌شود و با بازگردانی برمی‌گردد؛ حذف امن نیست.")){
                board.erasePdfContent=it;prefs.edit().putBoolean("erasePdfContent",it).apply()})
            c.addView(switchRow(tr("Palm erasing inside the PDF","پاک‌کردن با کف دست داخل PDF"),board.pdfPalmErase){
                board.pdfPalmErase=it;prefs.edit().putBoolean("pdfPalmErase",it).apply()})
        }
        val d=toolSheet(s("erase"),ScrollView(this).apply{addView(c)})
        addClearSlider(c){d.dismiss()}
    }
    private fun addText(sticky:Boolean){textEditor(null,sticky)}
    private fun textEditor(existing:Item?,sticky:Boolean=existing?.kind=="sticky"){
        if(existing?.locked==true)return
        val item=existing?.deepCopy()?:Item(kind=if(sticky)"sticky"else"text",width=if(sticky)24f else 32f,color=board.penColor)
        Fonts.stamp(item)
        val c=column().apply{pad(20)};val value=field(item.text,tr("Write your text…","متن را بنویسید…"));value.minLines=2;c.addView(value)
        Fonts.bind(value,item.bold,item.italic,Fonts.fa(item),Fonts.en(item))
        val format=row();c.addView(format)
        fun refreshFormat(){format.removeAllViews()
            format.addView(icon("bold",tr("Bold","پررنگ"),item.bold){item.bold=!item.bold;Fonts.bind(value,item.bold,item.italic,Fonts.fa(item),Fonts.en(item));refreshFormat()})
            format.addView(icon("italic",tr("Italic","مورب"),item.italic){item.italic=!item.italic;Fonts.bind(value,item.bold,item.italic,Fonts.fa(item),Fonts.en(item));refreshFormat()})
            listOf("start","center","end").forEach{key->format.addView(icon("align_$key",tr("Alignment: $key","تراز: ")+when(key){"start"->tr("Start","ابتدا");"center"->tr("Center","وسط");else->tr("End","انتها")},item.textAlign==key){item.textAlign=key;refreshFormat()})}
        };refreshFormat()
        c.addView(label(tr("Text color","رنگ متن"),16f,NAVY,true));palette(c,item.color){item.color=it;value.setTextColor(it)}
        slider(c,tr("Text size","اندازهٔ متن"),item.width,120){item.width=it}
        if(sticky){c.addView(label(tr("Note background","رنگ یادداشت"),16f,NAVY,true));palette(c,item.noteColor){item.noteColor=it;value.background=rounded(it,dp(8).toFloat())}}
        val d=dialog(s(if(sticky)"sticky"else"text"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){val entered=value.text?.toString().orEmpty();if(entered.isBlank()){value.error=s("empty");return@button};item.text=entered;val oldWidth=item.w;val oldHeight=item.h;TextLayout.fit(item)
            if(item.shape=="mindnode"){item.w=maxOf(oldWidth,item.w);item.h=maxOf(oldHeight,item.h)}
            if(existing==null)board.insert(item)else store.editMetadata{val index=store.page.items.indexOfFirst{it.id==existing.id};if(index>=0)store.page.items[index]=item};d.dismiss()})
    }
    private fun graph(initial:String="y=2x-5",existing:Item?=null){
        val c=column().apply{pad(20)};c.addView(infoTitle(tr("Information","اطلاعات"),tr("Enter y=f(x). Use x^2, sin(x), sqrt(x), abs(x); angles are radians. Separate up to 3 curves with ;. Both axes use the same scale.","تابع را به صورت y=f(x) وارد کنید. x^2، sin(x)، sqrt(x) و abs(x) مجازند؛ زاویه‌ها رادیانی‌اند. حداکثر ۳ تابع را با ; جدا کنید. مقیاس دو محور برابر است.")))
        val field=field(existing?.text?:initial).apply{layoutDirection=View.LAYOUT_DIRECTION_LTR;textDirection=View.TEXT_DIRECTION_LTR};c.addView(field)
        val examples=row();listOf("y=2x-5","y=x^2","sin(x);cos(x)","sqrt(x)").forEach{v->examples.addView(button(v){field.setText(v)})};c.addView(scrollRow(examples))
        c.addView(label(tr("Half-range on the shorter axis","نیم‌بازهٔ محور کوتاه‌تر"),14f));val domain=field((existing?.domain?:10f).toString());c.addView(domain)
        val d=dialog(s("graph"),ScrollView(this).apply{addView(c)})
        c.addView(button(tr("Plot","رسم نمودار"),true){try{val text=field.text.toString();require(text.split(';').size in 1..3);text.split(';').forEach{MathTools().compile(it)};val span=domain.text.toString().toFloat();require(span in .1f..1000f);if(existing==null)board.insert(Item(kind="graph",text=text,w=560f,h=400f,domain=span))else store.editMetadata{existing.text=text;existing.domain=span;existing.plotIntersections=emptyList()};d.dismiss()}catch(e:Exception){field.error=e.message?:s("error")}})
    }

    private fun shapes() {
        val c=column().apply{pad(12)};val d=toolSheet(s("shape"),ScrollView(this).apply{addView(c)})
        c.addView(button(tr("Automatic shape recognition","تشخیص خودکار شکل")){board.tool="smart";board.smartMode="shape";board.clearSelection();refreshDock();d.dismiss()})
        Shapes.keys.chunked(4).forEach{chunk->val r=row();chunk.forEach{key->
            val cell=column().apply{gravity=Gravity.CENTER;pad(5);background=rounded(PAPER,dp(10).toFloat())}
            cell.addView(object:View(this){private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=TEAL;style=Paint.Style.STROKE;strokeWidth=dp(2).toFloat()}
                override fun onDraw(canvas:Canvas){
                    val ratio=if(key in listOf("rectangle","rounded_rectangle","ellipse","trapezoid","parallelogram","line","arrow","double_arrow","speech"))1.8f else 1f
                    val previewWidth=minOf((width-dp(24)).toFloat(),(height-dp(16))*ratio).coerceAtLeast(1f)
                    val previewHeight=previewWidth/ratio
                    canvas.save();canvas.translate((width-previewWidth)/2f,(height-previewHeight)/2f)
                    Shapes.draw(canvas,key,previewWidth,previewHeight,paint);canvas.restore()
                }
            },LinearLayout.LayoutParams(-1,dp(56)))
            cell.addView(label(s(key),11f).apply{gravity=Gravity.CENTER})
            cell.setOnClickListener{board.shape=key;board.tool="shape";board.clearSelection();refreshDock();d.dismiss()}
            r.addView(cell,LinearLayout.LayoutParams(0,dp(96),1f).apply{setMargins(dp(3),dp(3),dp(3),dp(3))})
        };c.addView(r)}
    }

    private var menuReturn:(()->Unit)?=null
    private val menuDialogs=mutableListOf<java.lang.ref.WeakReference<AlertDialog>>()
    private fun navigationBuilder(title:String):MaterialAlertDialogBuilder {
        val back=menuReturn;menuReturn=null
        menuDialogs.removeAll{it.get()?.isShowing!=true}
        val nested=back!=null || menuDialogs.isNotEmpty()
        val heading=navigationHeading(title,if(nested)({})else null)
        heading.findViewById<View>(R.id.menu_back_button)?.tag=back
        return MaterialAlertDialogBuilder(this).setCustomTitle(heading)
            .setNegativeButton(s("close"),null)
            .setOnCancelListener{d->d.dismiss();back?.invoke()}
    }
    private fun shown(d:AlertDialog):AlertDialog {
        menuDialogs.add(java.lang.ref.WeakReference(d))
        Fonts.onShown(d)
        d.window?.setGravity(Gravity.CENTER)
        d.findViewById<View>(R.id.menu_back_button)?.let{button->
            @Suppress("UNCHECKED_CAST") val back=button.tag as? (() -> Unit)
            button.setOnClickListener{d.dismiss();back?.invoke()}
        }
        return d
    }
    private fun dialog(title:String,content:View):AlertDialog =
        navigationBuilder(title).setView(content).create().also{it.show();shown(it)}
    /**
     * Settings for the tool just tapped: a sheet beside the dock instead of a centered dialog, with no dimming,
     * so the board stays visible and the eye stays near the tool. Touching outside closes it.
     */
    private fun toolSheet(title:String,content:View):AlertDialog =
        navigationBuilder(title).setView(content).create().also{d->
            d.setCanceledOnTouchOutside(true);d.show();shown(d)
            d.window?.let{w->
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                // A touch outside closes the sheet AND reaches the board or dock: tapping another pen switches at once
                // instead of the first tap being swallowed just to close the sheet.
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
                val screen=resources.displayMetrics.widthPixels
                val at=IntArray(2);var onLeft=true;var edge=dp(12)
                if(::toolDock.isInitialized&&toolDock.isShown&&toolDock.width>0){
                    toolDock.getLocationOnScreen(at);onLeft=at[0]+toolDock.width/2<screen/2
                    edge=if(onLeft)at[0]+toolDock.width+dp(12) else screen-at[0]+dp(12)
                }
                w.setGravity((if(onLeft)Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL)
                w.attributes=w.attributes.apply{x=edge;y=0}
                w.setLayout(minOf(dp(440),screen-edge-dp(12)),WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }

    private fun choices(title:String,keys:List<String>,action:(Int)->Unit) {
        val parent=menuReturn
        navigationBuilder(title).setAdapter(MenuRows(this,keys)){d,i->
            d.dismiss()
            menuReturn={menuReturn=parent;choices(title,keys,action)}
            try{action(i)}finally{menuReturn=null}
        }.show().also{shown(it)}
    }

    /** Only one real choice, so the sheet is one segmented row with a line saying what each does. */
    private fun selectionSettings(){
        val c=column().apply{pad(16)}
        val hint=label("",13f,MUTED)
        fun explain(mode:String){hint.text=if(mode=="free")tr("Draw a loop around what you want","دور چیزی که می‌خواهید خط بکشید")else tr("Drag a rectangle over what you want","روی چیزی که می‌خواهید مستطیل بکشید")}
        c.addView(segmented(listOf("free" to s("selection_free"),"box" to s("selection_box")),board.selectionMode){mode->
            prefs.edit().putString("selectionMode",mode).apply();whiteboard.selectionMode=mode;board.selectionMode=mode;board.clearSelection();explain(mode)})
        explain(board.selectionMode);c.addView(hint)
        toolSheet(s("selection_settings"),c)
    }

    private fun confirm(title: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setMessage(title)
            .setNegativeButton(s("cancel"), null)
            .setPositiveButton(s("apply")) { _, _ -> action() }
            .show()
    }

    private fun input(
        title: String,
        value: String = "",
        multi: Boolean = false,
        action: (String) -> Unit,
    ) {
        val box = column().apply { pad(20) }
        val edit =
            field(value).apply {
                setSingleLine(!multi)
                maxLines = 8
            }
        box.addView(edit)
        val d =
            navigationBuilder(title)
                .setView(box)
                .setPositiveButton(s("apply"), null)
                .create()
        d.show();shown(d)
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                action(edit.text.toString())
                d.dismiss()
            } catch (e: Exception) {
                edit.error = e.message ?: s("error")
            }
        }
    }

    private fun error(e: Exception) {
        lastError = "${e.javaClass.simpleName}: ${e.message}"
        if (!destroyed) {
            status.text = s("error")
            MaterialAlertDialogBuilder(this)
                .setTitle(s("error"))
                .setMessage(lastError)
                .setPositiveButton(s("close"), null)
                .show()
        }
    }

    private fun <T> work(task: () -> T, done: (T) -> Unit) {
        if (loading || destroyed) return
        loading = true
        val progress =
            MaterialAlertDialogBuilder(this)
                .setView(
                    row().apply {
                        pad(24)
                        addView(
                            ProgressBar(this@MainActivity),
                            LinearLayout.LayoutParams(dp(36), dp(36)),
                        )
                        addView(label(s("busy")).apply { pad(16) })
                    }
                )
                .setCancelable(false)
                .show()
        busyDialog=progress
        worker.execute {
            try {
                val result = task()
                handler.post {
                    loading = false
                    if (!destroyed) {
                        progress.dismiss();busyDialog=null
                        try{done(result)}catch(e:Exception){error(e)}
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    loading = false
                    if (!destroyed) {
                        progress.dismiss();busyDialog=null
                        error(e)
                    }
                }
            }
        }
    }

    private fun lessonFile(id: String = documentId) = File(filesDir, "lessons/$id.vura")

    private fun persist() {
        if(!dirty || destroyed)return
        if(whiteboard.hasActiveInteraction || board.hasActiveInteraction){handler.removeCallbacks(autosave);handler.postDelayed(autosave,1000);return}
        val doc=projectStore.lesson.copyForSave()
        val id=documentId
        dirty=false
        lastSaveTicket=saveQueue.submit(doc,lessonFile()){ticket,failure->
            if(!destroyed)handler.post{
                if(!destroyed && id==documentId && ticket==lastSaveTicket){
                    if(failure==null){if(!dirty)status.text=s("saved")}
                    else{dirty=true;lastError=failure.message.orEmpty();status.text=s("save_error");toast(s("save_error"))}
                }
            }
        }
    }

    private fun loadFile(f: File, changeId: Boolean = true) {
        handler.removeCallbacks(autosave)
        persist()
        work({
            try {
                files.read(f.inputStream(),true) to false
            } catch (e: Exception) {
                val backup = File(f.path + ".bak")
                if (!backup.exists()) throw e
                files.read(backup.inputStream(),true) to true
            }
        }) { (doc, backup) ->
            if (changeId || backup) {
                documentId = if (backup) newId() else f.nameWithoutExtension
                prefs.edit().putString("current", documentId).apply()
            }
            projectStore.replace(doc)
            whiteboard.clearSelection()
            whiteboard.reset()
            if (backup) {status.text = s("restored_backup");toast(s("restored_backup"))}
        }
    }

    private fun rename() {
        input(s("lesson"), projectStore.lesson.title) { value ->
            require(value.isNotBlank())
            projectStore.editMetadata { projectStore.lesson.title = value.take(100) }
        }
    }

    private fun menu() {
        choices(projectStore.lesson.title.ifBlank{"VuraVision"}+" · "+status.text, listOf("lab", "games", "settings", "help")) {
            when (it) {
                0 -> openExplorer(true)
                1 -> openExplorer(false)
                2 -> settings()
                3 -> quickGuide()
            }
        }
    }

    private fun quickGuide(){
        UserGuide.show(this,media,{},menuReturn.also{menuReturn=null})
    }

    private fun fileMenu() {
        if(projectStore.lesson.pdf!=null){
            choices(s("files"),listOf(tr("PDF file and workspace","فایل PDF و فضای مطالعه"),tr("Board projects and exports","پروژه‌ها و خروجی تخته"))){i->if(i==0)pdfMenu()else projectFileMenu()}
        } else projectFileMenu()
    }
    private fun projectFileMenu() {
        if(rooms?.active==true){choices(s("files"),listOf("save","export_pdf","export_png","export_jpg")){i->if(i==0)export("vura",true){createDocument(it,"application/octet-stream")}else exportChoice(listOf("pdf","png","jpg")[i-1])};return}
        activateBoard(whiteboard)
        choices(
            s("files"),
            listOf("new", "open", "save", "recent", "export_pdf", "export_png", "export_jpg", "rename"),
        ) {
            when (it) {
                0 ->
                    withPdfExit{confirm(s("new_confirm")) {
                        persist()
                        documentId = newId()
                        prefs.edit().putString("current", documentId).apply()
                        projectStore.replace(Lesson(title = s("lesson"),pages=mutableListOf(newBoardPage(null))))
                        whiteboard.clearSelection()
                        whiteboard.reset()
                    }
                    }
                1 -> withPdfExit{pick("application/octet-stream", 101)}
                2 -> export("vura", true) { createDocument(it, "application/octet-stream") }
                3 -> withPdfExit{recent()}
                4 -> exportChoice("pdf")
                5 -> exportChoice("png")
                6 -> exportChoice("jpg")
                7 -> rename()
            }
        }
    }

    private fun recent() {
        val list =
            File(filesDir, "lessons")
                .listFiles()
                ?.filter { it.extension == "vura" }
                ?.sortedByDescending { it.lastModified() }
                ?.take(30)
                .orEmpty()
        if (list.isEmpty()) {
            toast(s("empty"))
            return
        }
        work({
            list.map { f ->
                val name =
                    runCatching {
                            java.util.zip.ZipFile(f).use { z ->
                                com.google.gson
                                    .Gson()
                                    .fromJson(
                                        z.getInputStream(z.getEntry("document.json")).reader(),
                                        Lesson::class.java,
                                    )
                                    .title
                            }
                        }
                        .getOrDefault(f.name)
                "$name  ·  ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(f.lastModified()))}"
            }
        }) { names ->
            MaterialAlertDialogBuilder(this)
                .setTitle(s("recent"))
                .setItems(names.toTypedArray()) { _, i -> loadFile(list[i]) }
                .setNegativeButton(s("cancel"), null)
                .show()
        }
    }

    private fun colors() {
        val chosen=board.chosen().filter{ObjectActions.canColor(it)}
        if(board.chosen().isNotEmpty() && chosen.isEmpty())return
        val initial=chosen.singleOrNull()?.let{if(it.kind=="sticky")it.noteColor else it.color}?:board.inkColor
        val c=column().apply{pad(16)}
        palette(c,initial){color->
            if(chosen.isEmpty()){board.inkColor=color;savePens()}
            else{applyColor(color);refreshSelectionBar()}
        }
        dialog(tr("Color","رنگ"),ScrollView(this).apply{addView(c)})
    }

    private fun insert() {
        choices(s("insert"), listOf("sticky", "graph", "image", "pdf", "math")) { when(it){
            0->addText(true);1->graph();2->pick("image/*",102);3->choosePdfImport();4->math()
        }}
    }

    private fun math() {
        choices(s("math"), listOf("evaluate", "solve")) { i ->
            input(s(if (i == 0) "evaluate" else "solve"), if (i == 0) "2*(3+4)" else "2x+3=11") {
                value ->
                val answer =
                    if (i == 0) MathTools.format(MathTools().evaluate(value))
                    else SmartMath.solve(value)
                board.insert(
                    Item(kind = "text", text = "$value\n= $answer", width = 28f, w = 420f, h = 150f)
                )
            }
        }
    }

    /** Tapping a selected object, or "More": the remaining actions open inside the selection bar. */
    private fun editSelection() {
        if(board.chosen().isEmpty())return
        selectionBarMode="more";refreshSelectionBar()
    }
    /** Every action that applies to [items], by key; the bar shows the common ones and "More" the rest. */
    private fun selectionKeys(items:List<Item>):List<String> {
        val one = items.singleOrNull()
        val keys = mutableListOf("delete", "duplicate", "lock", "front", "back")
        if(items.any{ObjectActions.canColor(it)})keys.add("color")
        if(items.any{ObjectActions.canSmart(it)})keys.add("smart")
        if(one!=null&&Measurements.of(one)!=null)keys.add("measure")
        if(one!=null&&ShapeFill.path(one)!=null)keys.add("fill")
        if(one?.kind=="periodic")keys.add("periodic")
        if(one?.kind=="graph")keys.add("coordinates")
        if (one?.kind in listOf("text", "sticky", "graph")) keys.add("text")
        if(one?.shape=="mindnode")keys.addAll(listOf("add_child","add_sibling"))
        if(one?.shape in GeometryTools.keys){keys.add("rotate");if(one?.shape!="ruler")keys.add("draw_with_tool")}
        if(one?.shape=="set_square")keys.add("guide_slope")
        if(one!=null && one.cuts.isNotEmpty())keys.add("restore_erased")
        if (one?.kind in listOf("text", "sticky")) keys.add("text_size")
        if (one?.kind == "graph") keys.add("domain")
        if(one?.shape in listOf("ruler","set_square"))keys.addAll(listOf("draw_parallel","draw_perpendicular"))
        if (one?.kind == "pdf") keys.addAll(listOf("previous", "next", "page_picker"))
        if (one?.kind in listOf("image", "pdf")) keys.add("crop")
        return keys
    }
    /** Keys already on the main selection bar, so "More" does not repeat them. */
    private fun mainBarKeys(items:List<Item>):Set<String> {
        val one=items.singleOrNull()
        return buildSet{
            if(items.any{it.kind=="ink"}||items.any{ObjectActions.canSmart(it)})add("smart")
            if(one?.shape=="mindnode")add("text")
            if(one!=null&&Measurements.of(one)!=null)add("measure")
            if(items.any{ObjectActions.canColor(it)})add("color")
            if(one?.kind in listOf("image","pdf"))add("crop")
            if(one?.kind=="pdf")addAll(listOf("previous","next","page_picker"))
            add("duplicate");add("delete")
        }
    }
    private fun moreActions(items:List<Item>,action:(String,String,()->Unit)->Unit){
        val shown=mainBarKeys(items)
        selectionKeys(items).filter{it !in shown}.forEach{key->
            val label=if(key=="lock"&&items.all{it.locked})tr("Unlock","باز کردن قفل")else s(key)
            action(if(key=="lock"&&items.all{it.locked})"unlock" else key,label){runSelectionKey(key,board.chosen());refreshSelectionBar()}
        }
    }
    private fun runSelectionKey(key:String,items:List<Item>) {
        if(items.isEmpty())return
        val one = items.singleOrNull()
        when (key) {
            "measure" -> one?.let{measureShape(it)}
            "fill" -> fillSettings()
            "periodic" -> one?.let{PeriodicTable.show(this){board.insert(it)}}
            "coordinates" -> one?.let{coordinateSettings(it)}
            "smart" -> smartSettings()
            "add_child" -> mindNode(one)
            "add_sibling" -> mindNode(one,true)
            "rotate" -> one?.let{o->input(s("rotate"),DisplayNumbers.one(o.rotation.toDouble())){value->val angle=value.toFloat();require(angle.isFinite());board.edit{it.rotation=angle%360}}}
            "guide_slope" -> one?.let{o->input(s("guide_slope"),DisplayNumbers.one(kotlin.math.atan2(o.h,o.w)*180/kotlin.math.PI)){value->val angle=value.toFloat();require(angle.isFinite()&&angle in 10f..80f);board.edit{GeometryTools.setSlope(it,angle)}}}
            "draw_parallel","draw_perpendicular" -> one?.let{insertConstruction(it,key=="draw_perpendicular")}
            "draw_with_tool" -> one?.let{insertConstruction(it)}
            "delete" -> board.delete()
            "duplicate" -> {
                store.editMetadata {
                    duplicateItems(MindMap.group(store.page,items))
                        .onEach {
                            it.apply {
                                x += 24
                                y += 24
                                locked = false
                            }
                        }
                        .forEach { store.page.items.add(it) }
                }
            }
            "lock" -> {
                store.editMetadata { items.forEach { it.locked = !it.locked } }
            }
            "front" -> {
                store.editMetadata {
                    store.page.items.removeAll(items.toSet())
                    store.page.items.addAll(items)
                }
            }
            "back" -> {
                store.editMetadata {
                    store.page.items.removeAll(items.toSet())
                    store.page.items.addAll(0, items)
                }
            }
            "color" -> colors()
            "restore_erased" -> board.edit{it.cuts=emptyList()}
            "text" -> if(one!!.kind=="graph")graph(existing=one)else textEditor(one)
            "text_size" -> selectionBarMode="text_size"
            "domain" ->
                input(s("domain"), one!!.domain.toString()) { v ->
                    val n = v.toFloat()
                    require(n in .1f..1000f)
                    board.edit { it.domain = n }
                }
            "previous" -> board.edit { it.pdfPage = (it.pdfPage - 1).coerceAtLeast(0) }
            "next" ->
                board.edit { it.pdfPage = (it.pdfPage + 1).coerceAtMost(it.pageCount - 1) }
            "page_picker" ->
                input(s("page_picker"), "${one!!.pdfPage+1}") { v ->
                    val page = v.toInt()
                    require(page in 1..one.pageCount)
                    board.edit { it.pdfPage = page - 1 }
                }
            "crop" -> crop(one!!)
        }
    }

    private fun crop(o:Item) {
        if(o.locked || o.kind !in listOf("image","pdf"))return
        val targetPage=store.page;val targetStore=store;val original=o.deepCopy()
        val returnMenu=menuReturn;menuReturn=null
        work({requireNotNull(media.image(original,true))}){preview->
            menuReturn=returnMenu
            val content=column().apply{pad(8)}
            val selector=ImageCropView(this,preview)
            content.addView(selector,LinearLayout.LayoutParams(-1,min(dp(460),(resources.displayMetrics.heightPixels*.55f).toInt())))
            val actions=row()
            actions.addView(button(s("reset")){selector.reset()})
            var close:()->Unit={}
            actions.addView(button(s("crop"),true){
                val region=RectF(selector.region);close()
                work({
                    val source=requireNotNull(media.image(original,true))
                    val left=(source.width*region.left).toInt().coerceIn(0,source.width-1)
                    val top=(source.height*region.top).toInt().coerceIn(0,source.height-1)
                    val right=ceil(source.width*region.right).toInt().coerceIn(left+1,source.width)
                    val bottom=ceil(source.height*region.bottom).toInt().coerceIn(top+1,source.height)
                    val rendered=if(original.cuts.isEmpty())source else Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888).also{bitmap->
                        Renderer(media).draw(Canvas(bitmap),original.copy(x=0f,y=0f,w=source.width.toFloat(),h=source.height.toFloat(),rotation=0f,alpha=255),true)
                    }
                    val result=Bitmap.createBitmap(rendered,left,top,right-left,bottom-top)
                    try{media.save(result)}finally{if(result!==source && result!==rendered)result.recycle();if(rendered!==source)rendered.recycle()}
                }, cropped@{asset->
                    if(targetStore.page!==targetPage)return@cropped
                    val item=targetPage.items.firstOrNull{it.id==original.id}?:return@cropped
                    if(item.locked || !targetPage.editable(item))return@cropped
                    val center=item.global(item.w*(region.left+region.right)/2,item.h*(region.top+region.bottom)/2)
                    targetStore.editMetadata{
                        item.asset=asset;item.kind="image";item.w*=region.width();item.h*=region.height()
                        item.x=center.first-item.w/2;item.y=center.second-item.h/2;item.pdfPage=0;item.pageCount=1;item.cuts=emptyList()
                    }
                    board.sceneChanged();refreshDock()
                })
            })
            content.addView(scrollRow(actions));close=dialog(s("crop"),content)::dismiss
        }
    }

    private fun pages() {
        val returnMenu=menuReturn
        val c=column().apply{pad(14);layoutDirection=resources.configuration.layoutDirection}
        c.addView(label(s("pages"),20f,NAVY,true).apply{setPadding(dp(8),dp(6),dp(8),dp(10))})
        val d=dialog("",c)
        d.window?.setTitle(s("pages"))
        d.window?.decorView?.layoutDirection=resources.configuration.layoutDirection
        val actions=row();c.addView(actions)
        actions.addView(icon("new_page",s("add_page")){addPage();d.dismiss()})
        actions.addView(icon("background",s("background")){backgroundSettings()})
        actions.addView(label("${projectStore.lesson.pages.size} · ${s("pages")}",14f,MUTED).apply{gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,dp(48),1f))
        val list=ListView(this).apply{divider=null;isVerticalScrollBarEnabled=true}
        c.addView(list,LinearLayout.LayoutParams(-1,dp(360)))
        fun perform(i:Int,action:Int){
            if(!whiteboard.sessionCanEdit)return
            val page=projectStore.lesson.pages[i]
            fun applyAction(){projectStore.editMetadata{when(action){
                0->if(projectStore.lesson.pages.size<200){val copy=page.copied().apply{id=newId();items=duplicateItems(items).toMutableList();alternateCanvas?.let{it.items=duplicateItems(it.items).toMutableList()}};projectStore.lesson.pages.add(i+1,copy);projectStore.lesson.current=i+1}
                1,2->{val target=i+if(action==1)-1 else 1;if(target in projectStore.lesson.pages.indices){val current=projectStore.page.id;java.util.Collections.swap(projectStore.lesson.pages,i,target);projectStore.lesson.current=projectStore.lesson.pages.indexOfFirst{it.id==current}}}
                3->{val ids=page.items.filter{it.pane in page.visiblePaneIndices&&!it.locked&&page.editable(it)&&whiteboard.canEditItem(it)}.map{it.id}.toSet();page.items.removeAll{it.id in ids};page.items.filter{it.parentNode in ids}.forEach{it.parentNode=""}}
                4->if(projectStore.lesson.pages.size>1){val current=projectStore.page.id;projectStore.lesson.pages.removeAt(i);projectStore.lesson.current=projectStore.lesson.pages.indexOfFirst{it.id==current}.takeIf{it>=0}?:i.coerceAtMost(projectStore.lesson.pages.lastIndex)}
            }};whiteboard.clearSelection();whiteboard.reset();d.dismiss();menuReturn=returnMenu;pages()}
            if(action>=3)confirm(s("clear_confirm")){applyAction()}else applyAction()
        }
        data class PageRow(val root:LinearLayout,val preview:PageThumbnail,val title:TextView,val detail:TextView,val actions:LinearLayout)
        list.adapter=object:BaseAdapter(){
            override fun getCount()=projectStore.lesson.pages.size
            override fun getItem(position:Int)=projectStore.lesson.pages[position]
            override fun getItemId(position:Int)=position.toLong()
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                val holder=convertView?.tag as? PageRow ?: run{
                    val r=row().apply{pad(8);minimumHeight=dp(88)}
                    val info=column();val preview=PageThumbnail(this@MainActivity,whiteboard.renderer)
                    info.addView(preview,LinearLayout.LayoutParams(dp(90),dp(58)))
                    val title=label("",14f,NAVY,true);val detail=label("",11f,MUTED);info.addView(title);info.addView(detail);r.addView(info,LinearLayout.LayoutParams(dp(98),-2))
                    val controls=row();r.addView(scrollRow(controls),LinearLayout.LayoutParams(0,dp(48),1f))
                    PageRow(r,preview,title,detail,controls).also{r.tag=it}
                }
                val page=getItem(position);val current=position==projectStore.lesson.current
                holder.root.background=rounded(if(current)0xffeeebff.toInt()else SURFACE,dp(12).toFloat(),if(current)TEAL else OUTLINE)
                holder.preview.page=page;holder.title.text="${s("page_short")} ${position+1}"+(if(current)" ✓"else "")
                holder.detail.text=tr("${page.items.size} objects","${page.items.size} شیء")
                holder.root.setOnClickListener{switchPage(position);d.dismiss()};holder.preview.setOnClickListener{holder.root.performClick()}
                holder.actions.removeAllViews()
                val keys=listOf("duplicate","move_up","move_down","clear_page","delete")
                keys.forEachIndexed{action,key->holder.actions.addView(icon(key,s(key)) {perform(position,action)}.apply{
                    isEnabled=whiteboard.sessionCanEdit&&when(action){0->projectStore.lesson.pages.size<200;1->position>0;2->position<count-1;4->count>1;else->true};alpha=if(isEnabled)1f else .35f
                })}
                return holder.root
            }
        }
        list.setSelection(projectStore.lesson.current)
    }

    private fun activateBoard(target:Board){
        bindBoardExtras(target)
        if(board!==target){target.copyToolsFrom(board);board=target;closeSidePanel()}
        target.onObjectActions={editSelection()}
        target.onSmart={processSmart(it)}
        target.onMindAdd={node,sibling->mindNode(node,sibling)}
        refreshDock()
    }
    private fun projectChanged(){
        dirty=true;status.text=s("saving")
        handler.removeCallbacks(autosave);handler.postDelayed(autosave,1800)
    }
    private fun syncPdfWorkspace(){
        if(!::workspaceHost.isInitialized)return
        val state=projectStore.lesson.pdf
        if(shownPdf===state)return
        shownPdf=state
        if(board!==whiteboard){whiteboard.copyToolsFrom(board);board=whiteboard}
        pdfPane?.dispose();pdfPane=null
        (whiteboard.parent as? ViewGroup)?.removeView(whiteboard)
        workspaceHost.removeAllViews()
        // PDF fullscreen hides the board (GONE) inside the split layout; it must be visible again on its own.
        whiteboard.visibility=View.VISIBLE
        if(state==null){workspaceHost.addView(whiteboard,FrameLayout.LayoutParams(-1,-1));whiteboard.sceneChanged();return}
        val reader=PdfPane(this,state,media,{board},{activateBoard(it)},{projectChanged()},{projectChanged()},{pdfMenu()},{savePens();refreshDock()})
        pdfPane=reader
        val split=PdfSplitLayout(this,state,whiteboard,reader){projectChanged()}
        workspaceHost.addView(split,FrameLayout.LayoutParams(-1,-1).apply{setMargins(dp(78),dp(76),dp(12),dp(76))})
    }
    private fun refreshPdfPresentation(){
        (pdfPane?.parent as? PdfSplitLayout)?.applyPresentation()
        workspaceHost.post{if(!destroyed){pdfPane?.redraw();whiteboard.sceneChanged()}}
    }
    private fun choosePdfImport(){
        navigationBuilder(tr("Open PDF","بازکردن PDF"))
            .setItems(arrayOf(tr("PDF object on board","PDF به‌صورت شیء روی تخته"),tr("Scrollable PDF beside board","PDF قابل اسکرول کنار تخته"))){_,i->
                pendingPdfMode=i
                if(i==1)withPdfExit{pick("application/pdf",103)}else pick("application/pdf",103)
            }.show().also{shown(it)}
    }
    private fun openPdf(uri:android.net.Uri,flags:Int){
        try{contentResolver.takePersistableUriPermission(uri,flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))}catch(_:SecurityException){}
        val mode=pendingPdfMode
        work({
            val asset=media.import(requireNotNull(contentResolver.openInputStream(uri)),"pdf")
            try{
                if(mode==0){
                    val item=Item(kind="pdf",asset=asset,pageCount=media.pdfCount(asset),w=560f)
                    val image=requireNotNull(media.image(item,true));item.h=item.w*image.height/image.width
                    item to null
                }else{
                    val sizes=media.pdfSizes(asset)
                    val title=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else "PDF"}?:"PDF"
                    val state=PdfWorkspaceState(asset=asset,sourceUri=uri.toString(),title=title.take(250),originalHash=PdfExport.hash(media.file(asset).inputStream()))
                    state.sheets=sizes.mapIndexed{i,(w,h)->
                        PdfSheet(w,h,Page(background="plain",items=mutableListOf(Item(kind="pdf",asset=asset,pdfPage=i,pageCount=sizes.size,x=0f,y=0f,w=w,h=h,locked=true))))
                    }.toMutableList()
                    null to state
                }
            }catch(e:Exception){media.file(asset).delete();throw e}
        }){(item,state)->
            if(item!=null){activateBoard(whiteboard);whiteboard.insert(item)}
            else if(state!=null){activateBoard(whiteboard);projectStore.editMetadata{projectStore.lesson.pdf=state};syncPdfWorkspace();projectChanged()}
        }
    }
    private fun pdfMenu(){
        val state=projectStore.lesson.pdf?:return
        val entries=listOf(tr("Save annotations to the original PDF","ذخیرهٔ یادداشت‌ها روی همان PDF"),tr("Save as a new PDF","ذخیره به‌صورت PDF جدید"),
            tr("Keep annotations in the project only","یادداشت‌ها فقط در پروژه بمانند"),tr("Hand / writing mode","حالت دست / نوشتن"),
            tr("Swap PDF and board sides","تعویض سمت PDF و تخته"),tr("PDF fullscreen / split","PDF تمام‌صفحه / کنار تخته"),
            tr("Fit PDF to width","نمایش PDF متناسب با عرض"),tr("Copy page or region to board","کپی صفحه یا بخشی از آن به تخته"),
            tr("Export board pages as PDF","خروجی PDF از صفحات تخته"),tr("Close PDF workspace","بستن بخش PDF"))
        choices(state.title+if(state.unsaved)" •"else"",entries){i->when(i){
            0->savePdf(true)
            1->savePdf(false)
            2->{persist();toast(tr("Editable annotations are saved in the project","یادداشت‌های قابل‌ویرایش در پروژه ذخیره می‌شوند"))}
            3->{pdfPane?.toggleHand();toast(if(pdfPane?.hand==true)tr("Hand: drag to scroll","دست: برای اسکرول بکشید")else tr("Writing: use the selected tool","نوشتن: از ابزار انتخاب‌شده استفاده کنید"))}
            4->{state.onRight=!state.onRight;refreshPdfPresentation();projectChanged()}
            5->{state.fullscreen=!state.fullscreen;refreshPdfPresentation();projectChanged()}
            6->pdfPane?.fitWidth()
            7->copyPdfPage()
            8->choices(s("export_pdf"),listOf("current_page","all_pages")){n->export("pdf",n==1){createDocument(it,"application/pdf")}}
            9->withPdfExit{activateBoard(whiteboard);projectStore.editMetadata{projectStore.lesson.pdf=null}}
        }}
    }
    private fun withPdfExit(action:()->Unit){
        val state=projectStore.lesson.pdf
        if(state==null || !state.unsaved){action();return}
        MaterialAlertDialogBuilder(this).setTitle(tr("PDF has unsaved annotations","یادداشت‌های PDF ذخیره نشده‌اند"))
            .setMessage(tr("The project keeps editable notes. Choose whether to also save them into a PDF before leaving.","پروژه یادداشت‌های قابل‌ویرایش را نگه می‌دارد. انتخاب کنید قبل از خروج در PDF هم ذخیره شوند یا نه."))
            .setPositiveButton(tr("Save PDF","ذخیرهٔ PDF")){_,_->
                MaterialAlertDialogBuilder(this).setTitle(tr("Save PDF","ذخیرهٔ PDF")).setItems(arrayOf(tr("Original file","همان فایل"),tr("New file","فایل جدید"))){_,i->savePdf(i==0,action)}.show()
            }.setNegativeButton(tr("Leave without PDF save","خروج بدون ذخیره در PDF")){_,_->persist();action()}
            .setNeutralButton(tr("Continue editing","ادامهٔ کار"),null).show()
    }
    private fun savePdf(overwrite:Boolean,then:(()->Unit)?=null){
        val live=projectStore.lesson.pdf?:return
        if(board.hasActiveInteraction)return
        if(overwrite && live.sourceUri.isBlank()){
            MaterialAlertDialogBuilder(this).setTitle(tr("Original file is unavailable","فایل اصلی در دسترس نیست"))
                .setMessage(tr("This project was imported or its original file permission is unavailable. Save a new PDF.","این پروژه وارد شده یا دسترسی به فایل اصلی موجود نیست. یک PDF جدید ذخیره کنید."))
                .setPositiveButton(tr("Save new PDF","ذخیرهٔ PDF جدید")){_,_->savePdf(false,then)}.setNegativeButton(s("cancel"),null).show();return
        }
        val snapshot=live.deepCopy()
        work({
            val output=File(cacheDir,"exports/PDF-${System.currentTimeMillis()}.pdf")
            PdfExport.write(media,snapshot,output)
            if(overwrite){
                val uri=android.net.Uri.parse(snapshot.sourceUri)
                val backup=File(cacheDir,"exports/Original-${System.currentTimeMillis()}.pdf")
                requireNotNull(contentResolver.openInputStream(uri)).use{input->backup.outputStream().use{boundedCopy(input,it,128L*1024*1024)}}
                check(PdfExport.hash(backup.inputStream())==snapshot.originalHash){tr("The original PDF changed outside the app. Save a new file to keep both versions.","فایل اصلی بیرون از برنامه تغییر کرده؛ برای حفظ هر دو نسخه، فایل جدید ذخیره کنید.")}
                try{
                    requireNotNull(contentResolver.openOutputStream(uri,"wt")).use{out->output.inputStream().use{it.copyTo(out)};out.flush()}
                    check(PdfExport.hash(requireNotNull(contentResolver.openInputStream(uri)))==PdfExport.hash(output.inputStream())){"PDF write verification failed"}
                }catch(e:Exception){
                    try{requireNotNull(contentResolver.openOutputStream(uri,"wt")).use{out->backup.inputStream().use{it.copyTo(out)}}}catch(_:Exception){}
                    throw java.io.IOException(tr("Could not replace the original PDF. Recovery copies remain in the app; save a new PDF.","جایگزینی PDF اصلی انجام نشد. نسخه‌های بازیابی داخل برنامه حفظ شده‌اند؛ PDF جدید ذخیره کنید."),e)
                }
                output to PdfExport.hash(output.inputStream())
            }else output to ""
        }){(output,hash)->
            if(overwrite){live.savedRevision=snapshot.revision;live.originalHash=hash;projectChanged();toast(s("saved"));then?.invoke()}
            else{pendingPdfAsset=live.asset;pendingPdfRevision=snapshot.revision;afterPdfSave=then;createDocument(output,"application/pdf")}
        }
    }
    private fun copyPdfPage(){
        val state=projectStore.lesson.pdf?:return
        val sheet=state.sheets[state.current]
        fun copy(region:android.graphics.RectF){
            val page=sheet.page.copy(items=sheet.page.items.map{it.deepCopy()}.toMutableList())
            work({
                val scale=1920f/maxOf(sheet.width,sheet.height)
                val full=Bitmap.createBitmap((sheet.width*scale).toInt().coerceAtLeast(1),(sheet.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                try{
                    val canvas=Canvas(full);canvas.drawColor(Color.WHITE);canvas.scale(scale,scale);Renderer(media).scene(canvas,page,true)
                    val left=(region.left*full.width).toInt().coerceIn(0,full.width-1);val top=(region.top*full.height).toInt().coerceIn(0,full.height-1)
                    val w=((region.right-region.left)*full.width).toInt().coerceIn(1,full.width-left);val h=((region.bottom-region.top)*full.height).toInt().coerceIn(1,full.height-top)
                    val crop=Bitmap.createBitmap(full,left,top,w,h)
                    try{Item(kind="image",asset=media.save(crop),w=560f,h=560f*h/w)}finally{if(crop!==full)crop.recycle()}
                }finally{full.recycle()}
            }){item->activateBoard(whiteboard);state.fullscreen=false;refreshPdfPresentation();whiteboard.insert(item)}
        }
        MaterialAlertDialogBuilder(this).setTitle(tr("Copy PDF to board","کپی PDF به تخته"))
            .setItems(arrayOf(tr("Whole current page","تمام صفحهٔ فعلی"),tr("Select a region","انتخاب بخشی از صفحه"))){_,i->
                if(i==0)copy(RectF(0f,0f,1f,1f))
                else work({
                    val scale=1536f/maxOf(sheet.width,sheet.height)
                    val bitmap=Bitmap.createBitmap((sheet.width*scale).toInt().coerceAtLeast(1),(sheet.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.scale(scale,scale)
                    Renderer(media).scene(canvas,sheet.page,true);bitmap
                }){bitmap->
                    val selector=PdfRegionSelector(this,bitmap)
                    MaterialAlertDialogBuilder(this).setTitle(tr("Drag a rectangle","یک مستطیل بکشید")).setView(selector)
                        .setPositiveButton(s("apply")){_,_->copy(RectF(selector.region))}.setNegativeButton(s("cancel"),null).show().setOnDismissListener{bitmap.recycle()}
                }
            }.show()
    }
    @Deprecated("Platform compatibility")
    override fun onBackPressed(){withPdfExit{persist();super.onBackPressed()}}

    private fun pick(mime: String, code: Int) {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = mime
                    if (code == 101) type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    if(code==103)addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                },
                code,
            )
        } catch (e: Exception) {
            error(e)
        }
    }

    @Deprecated("Platform compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(roomUi?.result(requestCode,resultCode,data)==true)return
        if(requestCode==101&&rooms?.active==true){toast(s("room_leave_first"));return}
        if (resultCode != RESULT_OK) {afterPdfSave=null;return}
        if(requestCode==105){val f=File(cacheDir,"lab-snapshot.png");if(f.exists()){val bitmap=BitmapFactory.decodeFile(f.path);if(bitmap!=null)insertBitmap(bitmap);f.delete()};return}
        val uri = data?.data ?: return
        when (requestCode) {
            101 ->
                work({ files.read(requireNotNull(contentResolver.openInputStream(uri))) }) { doc ->
                    persist()
                    documentId = newId()
                    prefs.edit().putString("current", documentId).apply()
                    projectStore.replace(doc)
                    activateBoard(whiteboard)
                    whiteboard.clearSelection()
                    whiteboard.fit()
                }
            103 -> openPdf(uri,data?.flags?:0)
            102 ->
                work({
                    val asset =
                        media.import(
                            requireNotNull(contentResolver.openInputStream(uri)),
                            if (requestCode == 103) "pdf" else "img",
                        )
                    try {
                        val o =
                            Item(
                                kind = if (requestCode == 103) "pdf" else "image",
                                asset = asset,
                                w = 560f,
                                h = 400f,
                            )
                        if (requestCode == 103) o.pageCount = media.pdfCount(asset)
                        val b = requireNotNull(media.image(o, true))
                        o.h = o.w * b.height / b.width
                        o
                    } catch (e: Exception) {
                        media.file(asset).delete()
                        throw e
                    }
                }) {
                    board.insert(it)
                }
            104 -> {
                val file = pendingExport ?: return
                work({
                    check(file.isFile && file.length()>0) { "Export is missing. Your internal lesson is preserved; please export again." }
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { out ->
                        val count=file.inputStream().use { it.copyTo(out) }
                        check(count==file.length() && count>0) { "Incomplete export" }
                        out.flush()
                    }
                }) {
                    toast(s("saved"))
                    pendingExport = null
                    prefs.edit().remove("pendingExport").apply()
                    projectStore.lesson.pdf?.let{state->if(state.asset==pendingPdfAsset && pendingPdfRevision!=null){state.savedRevision=pendingPdfRevision!!;projectChanged()}}
                    pendingPdfAsset=null;pendingPdfRevision=null
                    afterPdfSave?.let{afterPdfSave=null;it()}
                }
            }
        }
    }

    private fun exportChoice(ext: String) {
        if(ext=="pdf" && projectStore.lesson.pdf!=null){pdfMenu();return}
        if (ext == "pdf")
            choices(s("export_pdf"), listOf("current_page", "all_pages")) { i ->
                export(ext, i == 1) { createDocument(it, "application/pdf") }
            }
        else export(ext, false) { createDocument(it, "image/${if(ext=="jpg")"jpeg"else"png"}") }
    }

    private fun export(ext: String, all: Boolean, done: (File) -> Unit) {
        val doc = projectStore.lesson.copyForSave()
        work({
            val folder = File(cacheDir, "exports").apply { mkdirs() }
            val target = File(folder, "VuraVision-${System.currentTimeMillis()}.$ext")
            val renderer = Renderer(media)
            if (ext == "vura") files.atomic(doc, target)
            else if (ext == "pdf") {
                val pdf = PdfDocument()
                try {
                    val pages = if (all) doc.pages else listOf(doc.pages[doc.current])
                    pages.forEachIndexed { i, page ->
                        val p =
                            pdf.startPage(PdfDocument.PageInfo.Builder(1190, 842, i + 1).create())
                        renderer.page(p.canvas, page, 1190, 842, true)
                        pdf.finishPage(p)
                    }
                    target.outputStream().use { pdf.writeTo(it) }
                } finally {
                    pdf.close()
                }
            } else {
                val bitmap = Bitmap.createBitmap(1920, 1200, Bitmap.Config.ARGB_8888)
                renderer.page(Canvas(bitmap), doc.pages[doc.current], 1920, 1200, true)
                target.outputStream().use {
                    check(bitmap.compress(
                        if (ext == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                        92,
                        it,
                    )) { "Image export failed" }
                }
                bitmap.recycle()
            }
            check(target.isFile && target.length()>0){"Export is empty"}
            target
        }) {
            done(it)
        }
    }

    private fun createDocument(file: File, mime: String) {
        pendingExport = file
        prefs.edit().putString("pendingExport", file.name).apply()
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = mime
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, file.name)
                },
                104,
            )
        } catch (e: Exception) {
            error(e)
        }
    }

    private fun shareOptions(){
        val choices=arrayOf(tr("Send PDF via another app / cloud","فرستادن PDF با برنامهٔ دیگر / فضای ابری"),
            tr("QR for a public HTTPS link","QR برای لینک عمومی HTTPS"),
            tr("QR on the same local network","QR روی شبکهٔ محلی"),
            // Where a teacher looks for "share with the class"; it was only in the hidden Engineering menu.
            tr("Live class on this network (experimental)","کلاس زنده روی همین شبکه (آزمایشی)"))
        MaterialAlertDialogBuilder(this).setTitle(s("share"))
            .setItems(choices){_,index->when(index){0->sharePdf();1->publicQr();2->share();else->sharedRoom()}}
            .setNegativeButton(s("cancel"),null).show()
    }
    private fun sharePdf(){
        export("pdf",true){file->try{
            val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
                type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newRawUri(file.name,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },s("share")))
        }catch(e:Exception){error(e)}}
    }
    private fun publicQr(){
        input(tr("Paste a public HTTPS link to your uploaded PDF","لینک عمومی HTTPS فایل PDF بارگذاری‌شده را وارد کنید")) {raw->
            val url=raw.trim();val uri=android.net.Uri.parse(url)
            require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.authority?.contains('@')!=true){
                tr("Enter a valid public HTTPS address","نشانی معتبر و عمومی HTTPS وارد کنید")
            }
            val c=column().apply{pad(20);gravity=Gravity.CENTER}
            c.addView(label(tr("Anyone with access to this public link can open it from another network. Check sharing permissions in the cloud app.","هر کسی که به این لینک عمومی دسترسی داشته باشد می‌تواند از شبکهٔ دیگر آن را باز کند. مجوزهای اشتراک را در برنامهٔ ابری بررسی کنید."),14f,MUTED))
            c.addView(ImageView(this).apply{setImageBitmap(Sharing.qr(url));contentDescription=url},LinearLayout.LayoutParams(dp(230),dp(230)))
            c.addView(label(url,12f).apply{setTextIsSelectable(true)})
            c.addView(button(s("copy")){(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision",url));toast(s("done"))})
            dialog(s("share"),c)
        }
    }
    private fun share() {
        export("pdf", true) { file ->
            try {
                shareDialog?.dismiss()
                sharing?.stop()
                val server = Sharing(file, "application/pdf")
                server.start(5000, false)
                val urls=server.urls(this)
                val url=urls.firstOrNull()
                if (url == null) {
                    server.stop()
                    toast(s("no_network"))
                    return@export
                }
                sharing = server
                val c =
                    column().apply {
                        pad(20)
                        gravity = Gravity.CENTER
                    }
                c.addView(label(s("share_help"), 15f))
                val qr=ImageView(this).apply{setImageBitmap(Sharing.qr(url));contentDescription=url}
                c.addView(qr,LinearLayout.LayoutParams(dp(230),dp(230)))
                val address=label(url,12f).apply{setTextIsSelectable(true)};c.addView(address)
                c.addView(button(s("copy")){(getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision",address.text));toast(s("done"))})
                if(urls.size>1)c.addView(button(s("network_address")){MaterialAlertDialogBuilder(this).setItems(urls.toTypedArray()){_,index->address.text=urls[index];qr.setImageBitmap(Sharing.qr(urls[index]));qr.contentDescription=urls[index]}.show()})
                c.addView(label(s("share_troubleshoot"),12f,MUTED))
                val count = label("${s("downloads")}: 0", 12f, MUTED)
                c.addView(count)
                val d = dialog(s("share"), c)
                shareDialog = d
                val update =
                    object : Runnable {
                        override fun run() {
                            if (d.isShowing) {
                                count.text = "${s("downloads")}: ${server.downloads}"
                                handler.postDelayed(this, 1000)
                            }
                        }
                    }
                handler.post(update)
                handler.postDelayed(
                    {
                        if (sharing === server) {
                            server.stop()
                            d.dismiss()
                            toast(s("expired"))
                        }
                    },
                    1800000,
                )
                c.addView(button(s("stop_sharing")){server.stop();if(sharing===server)sharing=null;d.dismiss()})
                d.setOnDismissListener{handler.removeCallbacks(update);server.stop();if(sharing===server)sharing=null;shareDialog=null}
            } catch (e: Exception) {
                error(e)
            }
        }
    }

    private var smartSource="auto"
    private fun smartSettings(){
        val c=column().apply{pad(20)}
        c.addView(infoTitle(tr("Information","اطلاعات"),tr("Select writing, then use the actions above it to recognize text, solve, convert units, search Google, or recognize shapes. Drawing a loop is not needed.","نوشته را انتخاب کنید و از نوار بالای آن متن، حل، تبدیل واحد، جستجو در گوگل یا تشخیص شکل را بزنید. نیازی به دور کشیدن نیست.")))
        val keys=listOf("auto","offline","en-US","fa")
        val source=Spinner(this).apply{adapter=OptionAdapter(this@MainActivity,listOf(tr("Auto · English / Persian","خودکار · انگلیسی / فارسی"),tr("Offline Latin","لاتین آفلاین"),tr("English handwriting","دست‌خط انگلیسی"),tr("Persian handwriting","دست‌خط فارسی")));setSelection(keys.indexOf(smartSource).coerceAtLeast(0))};c.addView(source)
        val review=Switch(this).apply{text=tr("Review before applying","بررسی نتیجه قبل از اعمال");isChecked=prefs.getBoolean("smartReview",false)};c.addView(review)
        val d=toolSheet(s("smart"),c)
        d.setOnDismissListener{smartSource=keys[source.selectedItemPosition];prefs.edit().putString("smartSource",smartSource).putBoolean("smartReview",review.isChecked).apply()}
        c.addView(button(s("models")){d.dismiss();models()})
        c.addView(infoTitle(tr("Information","اطلاعات"),tr("Auto uses installed handwriting models and falls back to bundled Latin OCR. Install Persian and English models below when needed.","حالت خودکار از مدل‌های دست‌نویس نصب‌شده استفاده می‌کند و در غیر این صورت سراغ تشخیص لاتین همراه برنامه می‌رود. مدل‌های فارسی و انگلیسی را در صورت نیاز نصب کنید.")))
    }
    private fun applySmart(original:List<Item>,value:String,mode:String){
        require(value.isNotBlank()){"No recognition result"}
        require(original.all{v->store.page.items.any{it===v} && store.page.editable(v) && !v.locked}){"Selection changed"}
        val b=contentBounds(original);val first=original.first();val size=if(first.kind=="text")first.width else b.height().coerceIn(18f,64f)
        val o=when(mode){
            "convert"->Item(kind="text",text=UnitConversion.result(value),width=size)
            "formula"->{val clean=value.trim();val expression=clean.trimEnd('=',' ');val answer=if(expression.contains('='))SmartMath.steps(expression)else SmartMath.result(expression);Item(kind="text",text=if(expression.contains('='))answer else (if(clean.endsWith('='))"" else "= ")+answer,width=size)}
            "graph"->{value.split(';').forEach{MathTools().compile(it)};Item(kind="graph",text=value,w=560f,h=400f)}
            else->Item(kind="text",text=value,width=size)
        }
        o.color=first.color;o.layerId=first.layerId;o.pane=first.pane;if(o.kind=="text")TextLayout.fit(o)
        o.x=if(mode=="formula")b.right+16 else b.left;o.y=if(mode in listOf("graph","convert"))b.bottom+20 else b.top
        if(mode=="formula" && o.text.contains('\n')){o.x=b.left;o.y=b.bottom+20}
        store.editMetadata{if(mode=="text")store.page.items.removeAll(original.toSet());store.page.items.add(o)}
        board.selected.clear();board.selected.add(o.id);refreshDock()
    }
    private fun smart(){smartSettings()}
    private fun processSmart(items:List<Item>){
        val chosen=items.filter{store.page.editable(it)&&!it.locked&&ObjectActions.canSmart(it)};if(chosen.isEmpty())return
        val pageId=store.page.id;val mode=board.smartMode
        fun reviewResult(values:List<String>){if(destroyed)return;status.text=s("ready");if(store.page.id!=pageId){toast(tr("Return to the original page and try again","به صفحهٔ اصلی برگردید و دوباره تلاش کنید"));return};if(mode=="search"){reviewGoogleSearch(values);return};if(values.isEmpty()||prefs.getBoolean("smartReview",false))reviewSmart(chosen,values,mode)else try{applySmart(chosen,values.first(),mode)}catch(e:Exception){toast(e.message?:s("error"));reviewSmart(chosen,values,mode)}}
        if(mode=="shape"){store.editMetadata{chosen.filter{it.kind=="ink"}.forEach{o->ShapeRecognition.convert(o)?.let{store.page.items.remove(o);it.layerId=o.layerId;it.pane=o.pane;store.page.items.add(it)}}};board.clearSelection();return}
        if(chosen.all{it.kind!="ink"}){reviewResult(listOf(chosen.joinToString("\n"){it.text}));return}
        val strokes=chosen.filter{it.kind=="ink"};status.text=s("busy")
        fun fallback(e:Exception){lastError=android.util.Log.getStackTraceString(e);status.text=s("error");toast(recognition.failure(this,e));reviewResult(emptyList())}
        if(smartSource=="auto"){
            fun latin()=OfflineText.recognize(chosen,board.renderer,::reviewResult,::fallback)
            fun english(persian:List<String>){recognition.installed("en-US",{ready->
                if(ready)recognition.recognize("en-US",strokes,{english->
                    val faText=persian.firstOrNull().orEmpty()
                    reviewResult(if(faText.any{it in '\u0600'..'\u06ff'})persian+english else english+persian)
                },{if(persian.isNotEmpty())reviewResult(persian)else latin()})
                else if(persian.isNotEmpty())reviewResult(persian)else latin()
            },{if(persian.isNotEmpty())reviewResult(persian)else latin()})}
            recognition.installed("fa",{ready->if(ready)recognition.recognize("fa",strokes,::english,{english(emptyList())})else english(emptyList())},{english(emptyList())})
        }else if(smartSource=="offline")OfflineText.recognize(chosen,board.renderer,::reviewResult,::fallback)
        else recognition.installed(smartSource,{installed->if(installed)recognition.recognize(smartSource,strokes,::reviewResult,::fallback)else{status.text=s("model_required");reviewResult(emptyList());toast(s("model_required"))}},::fallback)
    }
    private fun reviewSmart(original:List<Item>,candidates:List<String>,mode:String){
        val pageId=store.page.id;val c=column().apply{pad(20)}
        c.addView(label(if(candidates.isEmpty())tr("No reliable recognition. Enter or correct the content below; your original ink is preserved until you apply.","نتیجهٔ قابل‌اعتماد پیدا نشد. متن را وارد یا اصلاح کنید؛ تا زمان اعمال، دست‌نویس اصلی حفظ می‌شود.")else tr("Review the recognized content","متن تشخیص‌داده‌شده را بررسی کنید"),14f,MUTED))
        val text=field(candidates.firstOrNull().orEmpty()).apply{if(mode!="text"){layoutDirection=View.LAYOUT_DIRECTION_LTR;textDirection=View.TEXT_DIRECTION_LTR}};c.addView(text)
        val options=row();candidates.take(3).forEach{v->options.addView(button(v.take(40)){text.setText(v)})};c.addView(scrollRow(options))
        val result=label("",19f,TEAL,true).apply{setTextIsSelectable(true)};c.addView(result)
        val keep=CheckBox(this).apply{this.text=s("keep_ink");isChecked=mode!="text";isEnabled=mode=="text"};c.addView(keep)
        fun prepared():Item{val value=text.text.toString();require(value.isNotBlank()){s("empty")};return when(mode){
            "convert"->Item(kind="text",text=UnitConversion.result(value),width=30f).also{TextLayout.fit(it)}
            "formula"->Item(kind="text",text="$value\n${SmartMath.result(value)}",width=30f).also{TextLayout.fit(it)}
            "graph"->{require(value.split(';').size in 1..3);value.split(';').forEach{MathTools().compile(it)};Item(kind="graph",text=value,w=560f,h=400f)}
            else->Item(kind="text",text=value,width=30f).also{TextLayout.fit(it)}
        }}
        c.addView(button(tr("Preview result","پیش‌نمایش نتیجه")){try{val item=prepared();result.text=if(item.kind=="graph")tr("Function is valid — Apply to plot","تابع معتبر است — برای رسم، اعمال را بزنید")else item.text;text.error=null}catch(e:Exception){text.error=e.message;result.text=""}})
        val d=dialog(s("review"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){try{
            require(store.page.id==pageId){"Page changed"}
            if(mode!="text" || !keep.isChecked)applySmart(original,text.text.toString(),mode)
            else {val item=prepared();val first=original.first();item.layerId=first.layerId;item.pane=first.pane;item.color=first.color;require(original.all{v->store.page.items.any{it===v}&&store.page.editable(v)&&!v.locked}){"Selection changed"};val bounds=contentBounds(original);item.x=bounds.left;item.y=bounds.bottom+20;store.editMetadata{store.page.items.add(item)};board.selected.clear();board.selected.add(item.id);refreshDock()}
            d.dismiss()
        }catch(e:Exception){text.error=e.message}})
    }

    private fun reviewGoogleSearch(candidates:List<String>){
        val target=GoogleSearchSettings.load(this)
        val floating:(String)->Unit={query->
            googleSearch?.close();googleSearch=GoogleSearchWindow(this,canvasHost).also{it.open(query)}
        }
        val browser:(String,Boolean)->Unit={query,adjacent->googleSearch?.close();googleSearch=null;GoogleSearchBrowser.open(this,query,adjacent)}
        val query=candidates.firstOrNull().orEmpty().trim()
        if(query.isEmpty()){toast(tr("No text was recognized","متنی تشخیص داده نشد"));return}
        if(!target.needsReview(candidates,prefs.getBoolean("smartReview",false))){
            if(target.destination=="floating")floating(query)else browser(query,target.adjacent)
        }else GoogleSearchReview(this).show(candidates,floating,browser,target)
    }

    private fun models(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("Offline Latin OCR is already included","تشخیص لاتین آفلاین همراه برنامه نصب است"),18f,TEAL,true))
        c.addView(infoTitle(tr("Information","اطلاعات"),tr("No model download is needed for clear Latin characters and simple arithmetic. Google digital-ink models below are optional for handwriting; Persian still needs its language model.","برای حروف واضح لاتین و محاسبات ساده نیازی به دانلود نیست. مدل‌های زیر برای دست‌نویس گوگل‌اند؛ تشخیص دست‌نویس فارسی همچنان به مدل زبان نیاز دارد.")))
        c.addView(infoTitle(tr("Information","اطلاعات"),tr("Model downloads need access to dl.google.com over HTTPS. If the connection is refused, try another network and check VPN, proxy or firewall settings, then Retry. Offline Latin OCR remains available.","دانلود مدل به دسترسی HTTPS به dl.google.com نیاز دارد. اگر اتصال رد شد، شبکهٔ دیگری را امتحان و تنظیمات VPN، پراکسی یا فایروال را بررسی کنید؛ سپس دوباره تلاش کنید. تشخیص لاتین آفلاین در دسترس است.")))
        c.addView(button(tr("Network settings","تنظیمات شبکه")){try{startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))}catch(e:Exception){error(e)}})
        c.addView(button(tr("Use offline Latin OCR","استفاده از تشخیص لاتین آفلاین")){smartSource="offline";prefs.edit().putString("smartSource",smartSource).apply();toast(s("done"))})
        listOf("en-US" to "english","fa" to "persian").forEach{(lang,key)->
            val card=column().apply{pad(14);background=rounded(PAPER,dp(14).toFloat())};c.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,dp(12))})
            card.addView(label(s(key),18f,NAVY,true));val state=label(s("check_status"),14f,MUTED);card.addView(state)
            val progress=ProgressBar(this).apply{visibility=View.GONE};card.addView(progress,LinearLayout.LayoutParams(dp(32),dp(32)))
            val r=row();card.addView(scrollRow(r));var raw="";var busy=false
            val install=button(s("install")){};r.addView(install)
            fun failed(e:Exception){busy=false;progress.visibility=View.GONE;raw=android.util.Log.getStackTraceString(e);lastError=raw;state.text=recognition.failure(this,e);install.isEnabled=true;install.text=s("retry")}
            fun update(){recognition.installed(lang,{ready->if(!destroyed){state.text=s(if(ready)"installed"else"not_installed");install.text=s(if(ready)"installed"else"install");install.isEnabled=!ready&&!busy}},::failed)}
            fun download(){if(busy)return;busy=true;install.isEnabled=false;progress.visibility=View.VISIBLE;state.text=s("downloading");recognition.download(lang,{if(!destroyed){busy=false;progress.visibility=View.GONE;update()}},{if(!destroyed)failed(it)})}
            install.setOnClickListener{download()}
            r.addView(button(tr("Retry download","تلاش مجدد")){if(!busy){recognition.installed(lang,{ready->if(ready)update()else download()},::failed)}})
            r.addView(button(s("check_status")){if(!busy)update()})
            r.addView(button(tr("Details","جزئیات")){val details="Model: $lang\nSDK: digital-ink 19.0.0\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAPI: ${Build.VERSION.SDK_INT}\nLocale: ${Locale.getDefault()}\n\n${raw.ifBlank{tr("No error recorded","خطایی ثبت نشده است")}}";val body=column().apply{pad(16)};body.addView(label(details,12f).apply{setTextIsSelectable(true)});body.addView(button(s("copy")){(getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision diagnostics",details));toast(s("done"))});dialog(tr("Download diagnostics","گزارش دانلود"),ScrollView(this).apply{addView(body)})})
            update()
        }
        dialog(s("models"),ScrollView(this).apply{addView(c)})
    }


    private var settingsScreen:SettingsScreen?=null
    private fun settings()=settingsAt(prefs.getString("settingsTab",null))
    /**
     * One settings screen with categories (item 44). On/off and few-option settings apply in place;
     * only settings that need room open a page, which returns here with its back arrow.
     */
    private fun settingsAt(tab:String?){
        menuReturn=null
        fun page(title:String,value:()->String={""},open:()->Unit)=SettingRow.Page(title,value){menuReturn={};try{open()}finally{menuReturn=null}}
        fun roomSafe(action:()->Unit){if(rooms?.active==true)toast(s("room_leave_first"))else action()}
        fun restart(edit:SharedPreferences.Editor.()->Unit){persist();prefs.edit().apply(edit).apply();worker.execute{handler.post{if(!destroyed)recreate()}}}
        val providers=mapOf("gemini" to "Gemini","openai" to "OpenAI","openrouter" to "OpenRouter","avalai" to "AvalAI")
        val categories=listOf(
            SettingsCategory("general",tr("General","عمومی"),"settings",listOf(
                SettingRow.Choice(s("language"),listOf("en" to "English","fa" to "فارسی"),{prefs.getString("language","en")?:"en"}){v->roomSafe{restart{putString("language",v)}}},
                SettingRow.Choice(s("ui_size"),listOf("0.8" to tr("Small","کوچک"),"1.0" to tr("Medium","متوسط"),"1.4" to tr("Large","بزرگ")),
                    {prefs.getFloat("uiScale",1f).let{if(it<.9f)"0.8" else if(it>1.2f)"1.4" else "1.0"}}){v->roomSafe{restart{putFloat("uiScale",v.toFloat())}}},
                SettingRow.Choice(tr("Toolbar height on the side","ارتفاع نوار ابزار کنار صفحه"),listOf("top" to tr("Higher","بالاتر"),"middle" to tr("Middle","وسط"),"bottom" to tr("Lower","پایین‌تر")),
                    {toolDock.position}){toolDock.position=it},
                page(s("fonts"),{Fonts.persian.first{it.id==Fonts.persianId}.let{tr(it.en,it.fa)}+" · "+Fonts.english.first{it.id==Fonts.englishId}.let{tr(it.en,it.fa)}}){fontSettings()},
            )),
            SettingsCategory("input",tr("Pen and touch","قلم و لمس"),"pen",listOf(
                SettingRow.Toggle(tr("Pen + touch (off: two tips)","قلم + لمس (خاموش: دو سر قلم)"),null,{board.touchMode}){v->if(!board.hasActiveInteraction){board.touchMode=v;prefs.edit().putBoolean("touchMode",v).apply();refreshDock()}},
                SettingRow.Toggle(s("multi_touch_short"),null,{board.profile.multiTouch}){v->if(!board.hasActiveInteraction){board.profile.multiTouch=v;prefs.edit().putBoolean("multiTouch",v).apply()}},
                SettingRow.Toggle(tr("Two-finger double tap: Undo","دو بار ضربه با دو انگشت: بازگردانی"),tr("Hold the second tap and slide to browse history","ضربهٔ دوم را نگه دارید و بکشید تا تاریخچه را مرور کنید"),{prefs.getBoolean("twoFingerUndo",true)}){v->
                    prefs.edit().putBoolean("twoFingerUndo",v).apply();whiteboard.gestures.undoEnabled=v;board.gestures.undoEnabled=v;board.gestures.reset()},
                SettingRow.Toggle(tr("Five-finger pie menu","منوی دایره‌ای پنج‌انگشتی"),tr("Double tap with five fingers to open your shortcuts","با پنج انگشت دو بار ضربه بزنید تا میان‌برهایتان باز شود"),{prefs.getBoolean("pieEnabled",false)}){v->
                    prefs.edit().putBoolean("pieEnabled",v).apply();board.gestures.reset();whiteboard.gestures.pieEnabled=v;board.gestures.pieEnabled=v},
                page(s("pie_settings")){pieSettings()},
                page(s("calibration"),{tr("Pen tips, palm and real centimetres","سر قلم، کف دست و سانتی‌متر واقعی")}){calibrationSettings()},
            )),
            SettingsCategory("board",tr("Board","تخته"),"background",listOf(
                page(s("page_background"),{tr("For new pages","برای صفحه‌های جدید")}){defaultBackgroundSettings()},
                page(s("history_settings"),{tr("${HistorySettings.load(this)} steps","${HistorySettings.load(this)} مرحله")}){historySettings()},
            )),
            SettingsCategory("smart",tr("Smart","هوشمند"),"sparkle",listOf(
                page(s("voice_assistant"),{if(VoiceSettings.enabled(this))tr("On","روشن")+" · "+(providers[VoiceSettings.load(this).provider]?:VoiceSettings.load(this).provider)else tr("Off","خاموش")}){voiceSettings()},
                page(s("models"),{tr("Handwriting recognition","تشخیص دست‌خط")}){models()},
                page(s("google_search_settings")){GoogleSearchSettingsUi(this).show(menuReturn)},
            )),
            SettingsCategory("class",tr("Class","کلاس"),"users",listOf(
                page(s("device_profile"),{DeviceProfiles.load(this).name}){roomUi?.profile(menuReturn)},
                page(s("shared_room"),{tr("Experimental · share this board with devices on the same network","آزمایشی · اشتراک همین تخته با دستگاه‌های همان شبکه")}){sharedRoom()},
            )),
            SettingsCategory("about",tr("About","درباره"),"info",listOf(
                page(s("cache"),{tr("Frees memory used by images and PDF pages","حافظهٔ تصاویر و صفحه‌های PDF را آزاد می‌کند")}){media.clear();board.sceneChanged();toast(s("done"))},
                page(s("about"),{"VuraVision ${BuildConfig.VERSION_NAME}"}){about()},
            )),
        )
        settingsScreen=SettingsScreen(this,categories){prefs.edit().putString("settingsTab",it).apply()}.also{it.show(tab)}
    }

    private fun about(){
        val c=column().apply{pad(20)};val gesture=AboutGesture();var close:()->Unit={}
        c.addView(ImageView(this).apply{
            setImageResource(R.drawable.vura_brand);scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="VuraVision logo"
            setOnClickListener{if(gesture.click(android.os.SystemClock.uptimeMillis())){prefs.edit().putBoolean("engineering",true).apply();close();engineering()}}
        },LinearLayout.LayoutParams(-1,dp(150)))
        c.addView(label("VuraVision ${BuildConfig.VERSION_NAME}\nBeyond Vision\n\n${s("about_text")}",16f))
        close=dialog(s("about"),c)::dismiss
    }

    private fun fontSettings(){
        val content=column().apply{pad(16)}
        content.addView(infoTitle(tr("Information","اطلاعات"),tr("Choose independent fonts for Persian and English. New text, sticky notes and smart conversion keep these fonts in the project and PDF exports.","فونت فارسی و انگلیسی را جدا انتخاب کنید. متن تازه، یادداشت و تبدیل هوشمند این فونت‌ها را در پروژه و خروجی PDF حفظ می‌کنند.")))
        var fa=Fonts.persianId;var en=Fonts.englishId
        val faChoice=button(""){};val enChoice=button(""){}
        val preview=label("نمونهٔ فارسی: یادگیری با ویوراویژن\nEnglish: Learning with VuraVision\n۱۲۳۴۵۶۷۸۹۰ · 1234567890",21f)
        fun refreshPreview(){
            faChoice.text=tr("Persian","فارسی")+": "+Fonts.persian.first{it.id==fa}.let{tr(it.en,it.fa)}
            enChoice.text=tr("English","انگلیسی")+": "+Fonts.english.first{it.id==en}.let{tr(it.en,it.fa)}
            Fonts.bind(preview,fontFa=fa,fontEn=en)
        }
        fun choose(families:List<Fonts.Family>,persian:Boolean){
            val list=column().apply{pad(8)}
            val picker=dialog(tr("Choose font","انتخاب فونت"),ScrollView(this).apply{addView(list)})
            families.forEach{family->
                val row=column().apply{pad(10)}
                row.addView(label(tr(family.en,family.fa),15f,NAVY,true))
                val sample=label(if(persian)"یادگیری، خلاقیت و نوشتن ۱۲۳"else"Learning, creativity and writing 123",22f)
                // Preview each row with that family's face, not the current global choice.
                Fonts.bind(sample,fontFa=if(persian)family.id else fa,fontEn=if(persian)en else family.id)
                row.addView(sample);row.setOnClickListener{if(persian)fa=family.id else en=family.id;refreshPreview();picker.dismiss()}
                list.addView(row,LinearLayout.LayoutParams(-1,-2))
            }
        }
        faChoice.setOnClickListener{choose(Fonts.persian,true)};enChoice.setOnClickListener{choose(Fonts.english,false)}
        content.addView(faChoice);content.addView(enChoice);content.addView(preview)
        val existing=CheckBox(this).apply{text=tr("Also apply to editable text on the active page (Undo available)","روی متن‌های قابل‌ویرایش صفحهٔ فعال هم اعمال شود (قابل بازگردانی)")}
        content.addView(existing)
        val dialog=dialog(s("fonts"),ScrollView(this).apply{addView(content)})
        refreshPreview()
        content.addView(button(s("apply"),true){
            if(board.hasActiveInteraction)return@button
            Fonts.choose(this,fa,en)
            if(existing.isChecked){
                val items=store.page.items.filter{it.kind in listOf("text","sticky") && !it.locked && store.page.editable(it)}
                if(items.isNotEmpty())store.editMetadata{items.forEach{item->val w=item.w;val h=item.h;Fonts.stamp(item,true);TextLayout.fit(item);if(item.kind=="sticky"){item.w=maxOf(w,item.w);item.h=maxOf(h,item.h)}}}
            }
            Fonts.applyTree(window.decorView);whiteboard.sceneChanged();pdfPane?.refreshText()
            dialog.dismiss();toast(s("saved"))
        })
    }

    private fun insertConstruction(guide:Item,perpendicular:Boolean=false){
        if(guide.locked||!store.page.editable(guide)||!store.page.canDraw()||!board.canEditItem(guide))return
        val result=GeometryTools.construction(guide,perpendicular).apply{color=board.guideInkColor(guide.pane);width=board.guideInkWidth(guide.pane);layerId=store.page.activeLayerId}
        val label=GuideLabels.construction(board,guide,result)
        store.editMetadata{store.page.items.add(result);store.page.items.add(label)}
    }
    private fun historySettings(){
        val c=column().apply{pad(16)};val values=listOf(10,25,50,100,200)
        c.addView(infoTitle(s("history_settings"),tr("History steps on this device; per author in a room. Larger limits use more memory.","تعداد مراحل بازگردانی این دستگاه؛ در اتاق برای هر کاربر. مقدار بیشتر حافظهٔ بیشتری مصرف می‌کند.")))
        val pick=Spinner(this).apply{adapter=OptionAdapter(this@MainActivity,values.map{it.toString()});setSelection(values.indexOf(HistorySettings.load(this@MainActivity)).coerceAtLeast(0))};c.addView(pick)
        val d=dialog(s("history_settings"),c);c.addView(button(s("apply"),true){val value=values[pick.selectedItemPosition];prefs.edit().putInt("historyLimit",value).apply();projectStore.historyLimit=value;board.store.historyLimit=value;pdfPane?.setHistoryLimit(value);rooms?.setHistoryLimit(value);d.dismiss()})
    }
    /** Clear unlocked, editable items in the active panel; Undo restores them. */
    private fun clearActivePane():Boolean{
        val page=store.page;val pane=board.activePane
        if(board.hasActiveInteraction||!board.sessionCanEdit)return false
        val ids=page.items.filter{it.pane==pane&&!it.locked&&page.editable(it)&&board.canEditItem(it)}.map{it.id}.toSet()
        if(ids.isNotEmpty())store.editMetadata{page.items.removeAll{it.id in ids};page.items.filter{it.parentNode in ids}.forEach{it.parentNode=""}}
        board.clearSelection();return ids.isNotEmpty()
    }
    private fun addClearSlider(c:LinearLayout,done:()->Unit){
        val targetBoard=board;val targetStore=store;val page=store.page;val pane=board.activePane
        val title=if(page.visiblePaneCount>1)tr("Slide to clear this panel","برای پاک کردن این بخش بکشید")else tr("Slide to clear page","برای پاک کردن صفحه بکشید")
        c.addView(SlideToClearView(this,title){
            if(board!==targetBoard||targetStore.page!==page||board.activePane!=pane)return@SlideToClearView
            clearActivePane();done()
        },LinearLayout.LayoutParams(dp(320),dp(64)).apply{gravity=Gravity.CENTER_HORIZONTAL})
    }
    /** Pie/shortcut clear acts immediately; the undo action replaces a confirmation step. */
    private fun clearPageNow(){
        if(!clearActivePane())return
        com.google.android.material.snackbar.Snackbar.make(canvasHost,tr("Page cleared","صفحه پاک شد"),com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setAction(tr("Undo","بازگردانی")){board.undo()}.show()
    }

    private fun engineering() {
        val keys=listOf("shared_room","history_settings","report", "stress", "advanced_board", "secret_studio", "maboox", "voice_behavior")
        choices(s("engineering"),keys) {
            when (keys[it]) {
                "history_settings"->historySettings()
                "shared_room"->sharedRoom()
                "advanced_board" -> advancedBoard()
                "secret_studio" -> secretStudio()
                "maboox" -> maboox()
                "voice_behavior" -> VoiceSettingsUi(this,menuReturn.also{menuReturn=null}).advanced{stopVoice()}
                "report" -> {
                    val report =
                        "VuraVision ${BuildConfig.VERSION_NAME}\n${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n${resources.displayMetrics}\nPointers: device-reported only\nObjects: ${store.page.items.size}\nLast error: $lastError\n" +
                            InputDevice.getDeviceIds().joinToString("\n") { id ->
                                InputDevice.getDevice(id)
                                    ?.let { "${it.name}: sources=${it.sources}" }
                                    .orEmpty()
                            }
                    val text =
                        label(report, 13f).apply {
                            pad(18)
                            setTextIsSelectable(true)
                        }
                    dialog(s("report"), ScrollView(this).apply { addView(text) })
                }
                "stress" -> {
                    if(board.hasActiveInteraction)return@choices
                    confirm(tr("Add 300 test strokes? Undo removes them.","۳۰۰ خط آزمایشی اضافه شود؟ با بازگردانی حذف می‌شوند.")) {
                    store.editMetadata {
                        repeat(300) { n ->
                            store.page.items.add(
                                Item(
                                    layerId = store.page.activeLayerId,
                                    x = (n % 30) * 24f,
                                    y = (n / 30) * 30f,
                                    w = 20f,
                                    h = 20f,
                                    inkW = 20f,
                                    inkH = 20f,
                                    color = if (n % 2 == 0) ORANGE else TEAL,
                                    points =
                                        (0..30)
                                            .map { v ->
                                                Point(v * 20f / 30, 10 + sin(v * .4).toFloat() * 8)
                                            }
                                            .toMutableList(),
                                )
                            )
                        }
                    }
                    board.fit()
                    }
                }
            }
        }
    }

    private fun advancedBoard(){
        if(board.hasActiveInteraction){toast(tr("Finish the stroke first","ابتدا رسم را تمام کنید"));return}
        val c=column().apply{pad(20)}
        c.addView(label(tr("Behind the board","پشت پردهٔ تخته"),22f,NAVY,true))
        fun toggle(en:String,fa:String,initial:Boolean,change:(Boolean)->Unit){
            c.addView(Switch(this).apply{text=tr(en,fa);isChecked=initial;setOnCheckedChangeListener{_,v->change(v)}})
        }
        toggle("Hold to recognize geometry","تشخیص هندسی با مکث",board.holdRecognitionEnabled){v->board.holdRecognitionEnabled=v;prefs.edit().putBoolean("holdRecognition",v).apply()}
        val delayLabel=label("");c.addView(delayLabel)
        fun delayText(){delayLabel.text=tr("Hold time: ${board.holdDelayMillis} ms","زمان مکث: ${board.holdDelayMillis} میلی‌ثانیه")}
        delayText()
        c.addView(SeekBar(this).apply{max=20;progress=((board.holdDelayMillis-500)/100).toInt();contentDescription=tr("Shape hold time","زمان مکث تشخیص شکل");setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(v:SeekBar?){}
            override fun onStopTrackingTouch(v:SeekBar?){}
            override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){if(user){board.holdDelayMillis=500L+n*100L;prefs.edit().putLong("holdDelay",board.holdDelayMillis).apply();delayText()}}
        })})
        val toleranceLabel=label("");c.addView(toleranceLabel)
        fun toleranceText(){toleranceLabel.text=tr("Stationary pen tolerance: ${board.holdTolerance.toInt()}","حساسیت ثابت‌ماندن قلم: ${board.holdTolerance.toInt()}")}
        toleranceText()
        c.addView(SeekBar(this).apply{max=10;progress=board.holdTolerance.toInt()-2;contentDescription=tr("Stationary pen tolerance","حساسیت ثابت‌ماندن قلم");setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(v:SeekBar?){}
            override fun onStopTrackingTouch(v:SeekBar?){}
            override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){if(user){board.holdTolerance=(n+2).toFloat();prefs.edit().putFloat("holdTolerance",board.holdTolerance).apply();toleranceText()}}
        })})
        toggle("Snap pen to ruler / compass","اتصال قلم به خط‌کش و پرگار",board.guideSnapEnabled){v->board.guideSnapEnabled=v;prefs.edit().putBoolean("guideSnap",v).apply()}
        toggle("Reject palm instead of erasing","نادیده‌گرفتن کف دست به‌جای پاک‌کردن",!board.profile.palmErase){v->board.profile.palmErase=!v;prefs.edit().putBoolean("palmErase",!v).apply()}
        c.addView(infoTitle(tr("Information","اطلاعات"),tr("Lower hold time responds faster. A larger tolerance accepts more small hand movements. These settings are saved on this device.","زمان مکث کمتر، تشخیص سریع‌تر می‌دهد. مقدار حساسیت بزرگ‌تر، لرزش بیشتری را می‌پذیرد. تنظیمات روی همین دستگاه ذخیره می‌شوند.")))
        c.addView(button(tr("Reset advanced settings","بازنشانی تنظیمات پیشرفته")){
            board.holdRecognitionEnabled=true;board.holdDelayMillis=1000L;board.holdTolerance=4f;board.guideSnapEnabled=true
            prefs.edit().remove("holdRecognition").remove("holdDelay").remove("holdTolerance").remove("guideSnap").apply()
            toast(tr("Defaults restored; reopen this panel to refresh controls","مقادیر پیش‌فرض برگشت؛ برای نمایش مقادیر تازه پنل را دوباره باز کنید"))
        })
        dialog(tr("Behind the board","پشت پردهٔ تخته"),ScrollView(this).apply{addView(c)})
    }
    private fun secretStudio(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("The secret studio","آتلیهٔ مخفی"),24f,NAVY,true))
        c.addView(infoTitle(tr("Information","اطلاعات"),tr("Watch portraits drawn stroke by stroke with the board's own pens. Each drawing opens on a new page and can be edited, erased, saved or exported. Undo removes the new page. These are original stylized illustrations.","پرتره‌ها با قلم خود تخته، خط‌به‌خط رسم می‌شوند. هر نقاشی در صفحهٔ تازه باز می‌شود و قابل ویرایش، پاک‌کردن، ذخیره و خروجی است. بازگردانی، صفحهٔ نقاشی را حذف می‌کند. این‌ها تصویرسازی‌های گرافیکی اختصاصی‌اند.")))
        val d=dialog(tr("The secret studio","آتلیهٔ مخفی"),ScrollView(this).apply{addView(c)})
        listOf("mona-vura" to tr("Mona × Vura · Renaissance remix","مونا × ویورا · برداشت رنسانسی"),"aurora" to tr("Aurora · anime portrait","آرورا · پرترهٔ انیمه")).forEach{(key,title)->
            c.addView(button(title){
                if(board.hasActiveInteraction){toast(tr("Finish the stroke first","ابتدا رسم را تمام کنید"));return@button}
                try{val artwork=Artwork.load(this,key);d.dismiss();artPlayer?.stop();artPlayer=ArtPlayer(this,board,store,artwork).also{it.start()}}catch(e:Exception){error(e)}
            })
        }
        c.addView(button(tr("A tiny surprise: constellation","سورپرایز کوچک: صورت فلکی")){
            if(board.hasActiveInteraction)return@button
            d.dismiss();val page=Page(background="plain",panes=mutableListOf(Pane(background=0xff152538.toInt())))
            val points=listOf(90f to 220f,210f to 140f,325f to 235f,425f to 150f,510f to 265f,410f to 360f,250f to 380f)
            store.editMetadata{
                store.lesson.pages.add(page);store.lesson.current=store.lesson.pages.lastIndex
                points.zipWithNext().forEach{(a,b)->page.items.add(Item(kind="ink",color=0xff97bdcc.toInt(),width=2f,w=600f,h=460f,inkW=600f,inkH=460f,points=mutableListOf(Point(a.first,a.second),Point(b.first,b.second))))}
                points.forEach{(x,y)->page.items.add(Item(kind="shape",shape="star",x=x-12,y=y-12,w=24f,h=24f,color=0xffffcc80.toInt(),width=2f))}
                page.items.add(Item(kind="text",text="Beyond Vision",x=180f,y=435f,w=300f,h=60f,color=Color.WHITE,width=24f))
            };board.reset();board.clearSelection();board.fit()
        })
        c.addView(label("Powered by Maboox",14f,TEAL,true))
    }
    private fun maboox(){
        val c=column().apply{pad(24)}
        c.addView(label("Powered by Maboox",25f,NAVY,true))
        c.addView(label(tr("A little craft behind every classroom.","کمی هنر، پشت هر کلاس."),16f,MUTED))
        c.addView(label("maboox@yahoo.com",18f,TEAL).apply{setTextIsSelectable(true);textDirection=View.TEXT_DIRECTION_LTR})
        c.addView(button(tr("Copy email","کپی ایمیل")){
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Maboox","maboox@yahoo.com"));toast(tr("Email copied","ایمیل کپی شد"))
        })
        dialog("Maboox",c)
    }

    private fun mindNode(parent:Item?,sibling:Boolean=false){
        if(parent!=null&&(parent.locked||!store.page.editable(parent)))return
        if(parent==null && !store.page.canDraw()){toast(tr("Select an unlocked visible layer","یک لایهٔ نمایان و باز انتخاب کنید"));return}
        fun node(x:Float,y:Float,parentId:String="",layer:String=store.page.activeLayerId,pane:Int=board.activePane)=
            Item(kind="sticky",shape="mindnode",width=24f,w=180f,h=120f,x=x,y=y,
                noteColor=0xffe2f2f0.toInt(),parentNode=parentId,layerId=layer,pane=pane)
        val added=if(parent==null){
            val center=board.center();val root=node(center.x-200,center.y-60)
            listOf(root,node(root.x+root.w+90,root.y-85,root.id),node(root.x+root.w+90,root.y+85,root.id))
        }else{
            val parentId=if(sibling)parent.parentNode else parent.id
            val peers=store.page.items.filter{it.shape=="mindnode" && it.parentNode==parentId && it.pane==parent.pane}
            val x=if(sibling)parent.x else parent.x+parent.w+90
            val y=if(sibling)maxOf(parent.y+parent.h+24,peers.maxOfOrNull{it.y+it.h+24}?:parent.y)
                  else maxOf(parent.y,peers.maxOfOrNull{it.y+it.h+24}?:parent.y)
            listOf(node(x,y,parentId,parent.layerId,parent.pane))
        }
        store.editMetadata{store.page.items.addAll(added)}
        board.selected.clear();board.selected.add(added.first().id);board.tool="select";board.sceneChanged();refreshDock()
    }

    private fun openExplorer(lab:Boolean){
        val back=menuReturn;menuReturn=null
        if(lab)Labs.show(this,{insertBitmap(it)},{board.insert(it)},back)else Games.show(this,back)
    }

    private fun insertBitmap(bitmap: Bitmap) {
        work({ media.save(bitmap) }) { name ->
            board.insert(
                Item(
                    kind = "image",
                    asset = name,
                    w = 620f,
                    h = 620f * bitmap.height / bitmap.width,
                )
            )
            bitmap.recycle()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        roomUi?.pause();rooms?.pauseDrawing()
        stopVoice()
        historyHud(-1,0)
        if(::board.isInitialized){board.abortPendingGesture();board.measurements.clear()}
        pieOverlay?.let{canvasHost.removeView(it)};pieOverlay=null
        if(::board.isInitialized)board.gestures.reset()
        googleSearch?.close();googleSearch=null
        super.onPause()
    }

    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        lastUserTouch=SystemClock.uptimeMillis()
        if(event.actionMasked==MotionEvent.ACTION_DOWN && ::toolDock.isInitialized)toolDock.touchedAt(event.rawX,event.rawY)
        // A touch outside the panel bar closes it, except on the panel buttons, which toggle it themselves.
        paneBarView?.let{bar->if(event.actionMasked==MotionEvent.ACTION_DOWN){
            fun hit(v:View):Boolean{val p=IntArray(2);v.getLocationOnScreen(p);return event.rawX>=p[0]&&event.rawX<p[0]+v.width&&event.rawY>=p[1]&&event.rawY<p[1]+v.height}
            val onControls=splitControls?.let{o->(0 until o.childCount).any{hit(o.getChildAt(it))}}==true
            if(!hit(bar)&&!onControls)handler.post{if(paneBarView===bar)closePaneBar()}
        }}
        return super.dispatchTouchEvent(event)
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(roomUi?.permission(requestCode,grantResults)==true)return
        if(requestCode==voicePermissionRequest){
            if(grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)toggleVoice()
            else toast(s("voice_permission_needed"))
        }
    }

    /** Opens even while the assistant is off: this page is where it is turned on (it used to silently do nothing). */
    private fun voiceSettings(){
        VoiceSettingsUi(this,menuReturn.also{menuReturn=null}).connection(voiceKeys,voiceWorker){stopVoice()}
    }
    private fun toggleVoice(){
        if(!VoiceSettings.enabled(this)){stopVoice();return}
        if(voiceStarting || voiceAssistant?.active==true){stopVoice();return}
        val settings=VoiceSettings.load(this)
        val keys=voiceKeys.forProvider(settings.provider)
        if(!keys.hasKey()){voiceSettings();return}
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),voicePermissionRequest);return
        }
        val generation=++voiceStartGeneration
        voiceStarting=true;setVoiceState("voice_connecting")
        voiceWorker.execute{
            try{
                val key=keys.read();val knowledge=VoiceKnowledge.load(this)
                handler.post{
                    if(destroyed || !VoiceSettings.enabled(this) || !voiceStarting || generation!=voiceStartGeneration)return@post
                    voiceStarting=false
                    val ended:(String?)->Unit={reason->
                        voiceScreen?.close();voiceScreen=null;voiceAssistant=null;setVoiceState("voice_assistant")
                        if(reason!=null)toast(s(reason))
                    }
                    val audio=VoiceAudio(this,if(settings.provider=="openai")24000 else 16000)
                    val assistant:VoiceConversation=if(settings.provider in listOf("openrouter","avalai"))RouterVoiceAssistant(settings,knowledge,audio,RouterVoiceHttp(key,RouterEndpoint.of(settings)),{setVoiceState(it)},ended,connected={voiceScreen?.refresh()})
                        else VoiceAssistant(settings,key,knowledge,audio,{setVoiceState(it)},ended,connected={voiceScreen?.refresh()})
                    voiceAssistant=assistant
                    assistant.setMuted(voiceMuted)
                    voiceScreen=VoiceScreen(this,workspaceHost,settings.frameSeconds,{assistant.active && assistant.ready},
                        {board.hasActiveInteraction || SystemClock.uptimeMillis()-lastUserTouch<500},assistant::sendImage,{if(assistant.active)assistant.stop("voice_vision_error")})
                    assistant.start();voiceScreen?.start()
                }
            }catch(_:Exception){handler.post{if(!destroyed && voiceStarting && generation==voiceStartGeneration){voiceStarting=false;setVoiceState("voice_assistant");toast(s("voice_key_storage_error"))}}}
        }
    }
    private fun setVoiceState(value:String){
        if(!::voiceButton.isInitialized)return
        voiceButton.visibility=if(VoiceSettings.enabled(this))View.VISIBLE else View.GONE
        voiceButton.text=s(value)
        refreshMute()
        val active=value!="voice_assistant"
        voiceButton.setIconResource(if(active)R.drawable.ph_stop else R.drawable.ph_mic)
        voiceButton.contentDescription=if(active)s(value)+". "+s("voice_stop")else s("voice_assistant")
        voiceButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(active)PRIMARY_CONTAINER else SURFACE)
    }
    private fun stopVoice(){
        voiceMuted=false
        ++voiceStartGeneration
        voiceStarting=false
        voiceScreen?.close();voiceScreen=null
        voiceAssistant?.stop();voiceAssistant=null
        setVoiceState("voice_assistant")
    }

    override fun onStop() {
        board.gestures.reset()
        artPlayer?.pause()
        handler.removeCallbacks(autosave)
        rooms?.flushHostDocument();persist()
        super.onStop()
    }

    override fun onDestroy() {
        rooms?.endHostAndFlush();persist();roomUi?.close();rooms?.close()
        destroyed = true
        busyDialog?.dismiss();busyDialog=null
        menuDialogs.mapNotNull{it.get()}.forEach{it.dismiss()};menuDialogs.clear()
        prefs.unregisterOnSharedPreferenceChangeListener(voicePreferenceListener)
        googleSearch?.close();googleSearch=null
        stopVoice()
        voiceWorker.shutdown()
        artPlayer?.stop()
        floatingTools.closeAll()
        handler.removeCallbacksAndMessages(null)
        sharing?.stop()
        shareDialog?.dismiss()
        pdfPane?.dispose()
        worker.execute{media.close()}
        super.onDestroy()
    }
}
