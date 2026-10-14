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
    internal var rooms:RoomController?=null
    internal var roomUi:RoomUi?=null
    private lateinit var roomBadge:WorkspaceIcon
    internal val projectStore=Store()
    val store:Store get()=if(::board.isInitialized)board.store else projectStore
    internal lateinit var whiteboard:Board
    internal lateinit var workspaceHost:FrameLayout
    internal var pdfPane:PdfPane?=null
    internal var shownPdf:PdfWorkspaceState?=null
    internal var pendingPdfMode=0
    internal var afterPdfSave:(()->Unit)?=null
    internal var pendingPdfRevision:Long?=null
    internal var pendingPdfAsset:String?=null
    lateinit var board: Board
    lateinit var media: Media
    internal lateinit var files: LessonFiles
    internal lateinit var root: LinearLayout
    private lateinit var titleView: TextView
    internal lateinit var status: TextView
    internal lateinit var toolDock: ToolDock
    private lateinit var pageLabel: TextView
    private lateinit var pageStrip:LinearLayout
    internal lateinit var canvasHost:FrameLayout
    private lateinit var floatingTools:ClassroomWidgets
    private lateinit var selectionBar:LinearLayout
    private lateinit var selectionHost:HorizontalScrollView
    private var pageSignature=""
    internal val handler = Handler(Looper.getMainLooper())
    internal val worker = ProjectIO.executor
    internal val saveQueue by lazy{ProjectSaveQueue(worker){doc,target->files.atomic(doc,target)}}
    internal var lastSaveTicket=0L
    private var busyDialog:AlertDialog?=null
    internal val recognition = Recognition()
    internal val prefs by lazy { getSharedPreferences("vura", MODE_PRIVATE) }
    internal var documentId = ""
    internal var pendingExport: File? = null
    internal var sharing: Sharing? = null
    internal var shareDialog: Dialog? = null
    internal var taps = 0
    private var artPlayer: ArtPlayer? = null
    internal val autosave = Runnable { persist() }
    internal var dirty = false
    private var loading = false
    internal var lastError = ""
    @Volatile internal var destroyed = false
    // lateinit checks for the feature files (isInitialized is only allowed inside the class).
    internal val workspaceHostReady get()=::workspaceHost.isInitialized
    internal val voiceButtonReady get()=::voiceButton.isInitialized
    internal val voiceMuteButtonReady get()=::voiceMuteButton.isInitialized
    internal var voiceAssistant:VoiceConversation?=null
    private var googleSearch:GoogleSearchWindow?=null
    internal var voiceScreen:VoiceScreen?=null
    internal lateinit var voiceButton:com.google.android.material.button.MaterialButton
    internal var voiceMuted=false
    internal lateinit var voiceMuteButton:WorkspaceIcon
    internal var voiceStarting=false
    internal var voiceStartGeneration=0
    internal var lastUserTouch=0L
    internal val voiceWorker=Executors.newSingleThreadExecutor()
    internal val voiceKeys by lazy{VoiceKeys(this)}
    internal val voicePermissionRequest=7410
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
        Motion.load(this);UiTheme.load(this);setTheme(UiTheme.style())
        super.onCreate(state)
        registerBack()
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
        board.penColor=prefs.getInt("penColor",INK_NAVY)
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
        installLabAssistant()
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
    internal fun sharedRoom(){
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
    internal fun icon(key:String, title:String, active:Boolean=false, action:()->Unit)=WorkspaceIcon(this,key,title,active,action)
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

    internal fun refreshDock() {
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
    internal var selectionBarMode="main"
    private var selectionBarKey=""
    /**
     * Bar above the selection: at most six labeled actions, chosen by what is selected.
     * Smart, Color and More swap the bar's content in place (with Back) instead of opening a window elsewhere:
     * the eye and hand stay on the object, and the board is not covered.
     * Hidden while the selection is being dragged; shown again at the new position on release.
     */
    internal fun refreshSelectionBar(){
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
            "busy"->{
                // An AI request is running: say what, and let the teacher cancel; the board itself stays usable.
                actions.add(label(aiBusyLabel,16f,NAVY,true).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),0,dp(12),0);layoutParams=LinearLayout.LayoutParams(-2,dp(64))})
                action("close",tr("Cancel","لغو")){cancelAi()}
                fillSelectionBar(actions);positionSelectionBar();return
            }
            "smart"->{
                back()
                // AI first when a service is set up: it understands any handwriting, drawing or language.
                val notes=groupableNotes(items)
                if(smartAi.configured()){
                    if(notes.size>=3)action("ai_group",tr("Group ideas","دسته‌بندی ایده‌ها"),tr("Sort the notes into groups by meaning (AI)","چیدن یادداشت‌ها در دسته‌های هم‌معنا (هوش)")){aiGroups()}
                    action("ai_explain",tr("Explain","توضیح")){aiExplain("explain")}
                    action("ai_check",tr("Check","غلط‌یابی")){aiCheck()}
                    action("ai_steps",tr("Steps","گام‌به‌گام")){aiSteps()}
                    action("ai_quiz",tr("Quiz","آزمونک")){aiQuiz()}
                    action("ai_mindmap",tr("Mind map","نقشهٔ ذهنی")){aiMindMap()}
                    action("more",tr("More","بیشتر")){open("smart_more")}
                }else{
                    if(notes.size>=3)action("group_color",tr("By color","بر اساس رنگ"),tr("Arrange the notes in columns by color","چیدن یادداشت‌ها در ستون‌ها بر اساس رنگ")){groupByColor(notes)}
                    offlineSmart(smart,ink){symbol,text,title,run->action(symbol,text,title){run()}}
                    action("ai_setup",tr("Set up AI","راه‌اندازی هوش")){aiSettings()}
                }
                fillSelectionBar(actions);positionSelectionBar();return
            }
            "smart_more"->{
                action(if(resources.configuration.layoutDirection==View.LAYOUT_DIRECTION_RTL)"next" else "menu_back",tr("Back","بازگشت")){open("smart")}
                action("ai_simplify",tr("Simpler","ساده‌تر")){aiExplain("simplify")}
                action("ai_translate",tr("Translate","ترجمه")){aiExplain("translate")}
                if(ink)action("ai_cleanup",tr("Clean up","تمیز کن"),tr("Handwriting to typed text (AI)","دست‌خط به متن تایپی (هوش)")){aiCleanup()}
                if(ink)action("ai_picture",tr("To picture","به تصویر"),tr("Turn the sketch into a clean picture (AI)","تبدیل طرح به تصویر تمیز (هوش)")){aiSketchToPicture()}
                if(groupableNotes(items).size>=3)action("group_color",tr("By color","بر اساس رنگ"),tr("Arrange the notes in columns by color","چیدن یادداشت‌ها در ستون‌ها بر اساس رنگ")){groupByColor(groupableNotes(items))}
                offlineSmart(smart,ink){symbol,text,title,run->action(symbol,text,title){run()}}
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
        items.singleOrNull()?.takeIf{it.shape=="table"}?.let{t->action("table",tr("Edit cells","ویرایش خانه‌ها")){editTable(t)}}
        items.singleOrNull()?.takeIf{it.shape=="frame"}?.let{f->action("rename",tr("Rename","نام قاب")){input(tr("Frame name","نام قاب"),f.text){v->require(v.length<=60);board.edit{if(it.id==f.id)it.text=v.trim()}}}}
        items.singleOrNull()?.takeIf{it.shape=="map_iran"}?.let{m->
            // Names cycle Persian → English → none (a blank map for "where is …?").
            val next=when(m.text){"fa"->"en";"en"->"";else->"fa"}
            action("text",when(m.text){"fa"->tr("Names: Persian","نام‌ها: فارسی");"en"->tr("Names: English","نام‌ها: انگلیسی");else->tr("No names","بدون نام")}){board.edit{if(it.id==m.id)it.text=next};refreshSelectionBar()}}
        items.singleOrNull()?.takeIf{it.shape=="table"}?.let{t->action("chart",tr("Chart","نمودار"),tr("Chart from this table","نمودار از این جدول")){chartFromTable(t)}}
        if(items.size==2){
            // Two objects selected: connect them with an arrow from the first chosen to the second, or remove the link.
            val (a,b)=board.selected.mapNotNull{id->items.firstOrNull{it.id==id}}.takeIf{it.size==2}?.let{it[0] to it[1]}?:(items[0] to items[1])
            val linked=b.id in a.linkIds()||a.id in b.linkIds()
            if(linked)action("disconnect",tr("Unlink","قطع اتصال"),tr("Remove the arrow between them","حذف فلش میان این دو")){store.editMetadata{
                a.links=a.linkIds().filter{it!=b.id}.joinToString(" ").ifEmpty{null};b.links=b.linkIds().filter{it!=a.id}.joinToString(" ").ifEmpty{null}};board.sceneChanged();refreshSelectionBar()}
            else action("connect",tr("Connect","وصل کن"),tr("Draw an arrow from the first to the second; it follows them when moved","فلشی از اولی به دومی؛ با جابه‌جایی همراهشان می‌آید")){store.editMetadata{
                a.links=(a.linkIds()+b.id).joinToString(" ")};board.sceneChanged();refreshSelectionBar()}
        }
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
            // A thin divider: colors on one side, thicknesses on the other, so the dots are not read as more colors.
            actions.add(View(this).apply{setBackgroundColor(OUTLINE);layoutParams=LinearLayout.LayoutParams(dp(1),dp(40)).apply{setMargins(dp(6),dp(12),dp(6),dp(12))}})
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
            else if(Color.luminance(color)>.65 && Color.luminance(item.color)>.65)item.color=INK_NAVY
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
            // Views that bring their own size (swatches, the color/width divider) keep it; actions wrap their content.
            val lp=(v.layoutParams as? LinearLayout.LayoutParams)?:LinearLayout.LayoutParams(-2,dp(64))
            v.measure(if(lp.width>0)View.MeasureSpec.makeMeasureSpec(lp.width,View.MeasureSpec.EXACTLY)else View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            val w=v.measuredWidth+lp.leftMargin+lp.rightMargin
            if(used>0&&used+w>limit){selectionBar.addView(line);line=row();used=0}
            line.addView(v,lp);used+=w
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
        fun chip(title:String,active:Boolean,run:()->Unit)=label(title,14f,if(active)ON_NAVY else NAVY,true).apply{
            gravity=Gravity.CENTER;setPadding(dp(12),0,dp(12),0);minHeight=dp(40);isClickable=true
            background=rounded(if(active)NAVY else SURFACE_VARIANT,dp(20).toFloat());setOnClickListener{run()}
        }
        fun chips(items:List<Pair<String,String>>,current:String,pick:(String)->Unit)=FlowRow(this,dp(6)).apply{items.forEach{(k,t)->addView(chip(t,k==current){pick(k);paneBarKey="";paneBar(index,pen)},ViewGroup.LayoutParams(-2,-2))}}
        if(pen){
            // The dock's ink grid works on the active panel, so the same 6 colors and 3 widths apply here.
            bar.addView(InkStrip(this,{board},showTools=false,grid=true){refreshDock()},LinearLayout.LayoutParams(-2,-2).apply{gravity=Gravity.CENTER_HORIZONTAL;topMargin=dp(6)})
            bar.addView(chips(PenStyles.selectable.map{it to PenStyles.name(this,it)},pane.penStyle){k->store.editMetadata{pane.penStyle=k}},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        }else{
            val patterns=patternChoices()
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
    internal fun refreshPages() {
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
    internal fun savePens() {prefs.edit().putFloat("dashLength",board.dashLength).putFloat("dashGap",board.dashGap).putString("thickStyle",board.profile.thickStyle).putFloat("thickWidth",board.profile.thickWidth).putInt("thickColor",board.profile.thickColor).putInt("penColor",board.penColor).putInt("highlightColor",board.highlightColor).putFloat("penWidth",board.penWidth).putFloat("highlightWidth",board.highlightWidth).putString("penStyle",board.penStyle).putInt("penOpacity",board.penOpacity).apply()
        // Every pen change also belongs to the active dock preset at once, and its dot shows the new color.
        if(::toolDock.isInitialized)toolDock.refresh()}
    private val backgroundSwatches get()=listOf(Color.WHITE,Color.BLACK,0xff172a36.toInt(),PAPER,0xfffff5dc.toInt(),0xffeaf7f3.toInt(),0xffe9f0ff.toInt(),0xfff7ecfc.toInt(),0xfff4e8df.toInt(),0xffdce9ef.toInt())
    internal fun palette(c:LinearLayout,initial:Int,background:Boolean=false,changed:(Int)->Unit){
        val holder=column();c.addView(holder);var current=initial
        fun refresh(){
            holder.removeAllViews()
            val base=if(background)backgroundSwatches else listOf(INK_NAVY,Color.BLACK,Color.WHITE,0xffe45756.toInt(),INK_ORANGE,0xffffcf40.toInt(),INK_TEAL,0xff268bd2.toInt(),0xff865ac7.toInt(),0xffec76ab.toInt())
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
        val initial=if(page.pattern(paneIndex)=="dark"&&pane.background==Color.WHITE)0xff172a36.toInt() else pane.background
        var color=initial
        val grid=PatternGrid(this,board.renderer,patternChoices(),page.pattern(paneIndex).let{if(it in listOf("white","dark"))"plain" else it},3,{color}){key->
            store.editMetadata{
                if(page.pattern(paneIndex)=="dark" && pane.background==Color.WHITE)pane.background=0xff172a36.toInt()
                pane.pattern=key
            }
        }
        c.addView(grid)
        c.addView(label(tr("Background color","رنگ پس‌زمینه"),17f,NAVY,true))
        palette(c,initial,background=true){picked->color=picked;store.editMetadata{
            if(page.pattern(paneIndex)=="dark")pane.pattern="plain"
            pane.background=picked
        };grid.refresh()}
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
        target.keepToolAfterInsert=prefs.getBoolean("keepTool",false)
        target.onViewportChanged={positionSelectionBar()}
        target.gestures.onHistory=::historyHud
        target.gestures.undoEnabled=prefs.getBoolean("twoFingerUndo",true);target.gestures.pieEnabled=prefs.getBoolean("pieEnabled",false);target.gestures.pieHold=prefs.getBoolean("pieHold",false)
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
    internal fun newBoardPage(previous:Page?):Page {
        val inherit=previous!=null && prefs.getBoolean("inheritBackground",false)
        val pattern=if(inherit)previous!!.pattern(whiteboard.activePane) else prefs.getString("defaultPattern","dots")?:"dots"
        val old=previous?.panes?.getOrNull(whiteboard.activePane)?:previous?.panes?.firstOrNull()
        var color=if(inherit)old?.background?:Color.WHITE else prefs.getInt("defaultBackground",Color.WHITE)
        if(pattern=="dark"&&color==Color.WHITE)color=0xff172a36.toInt()
        return Page(background=if(pattern in listOf("white","dark"))"plain"else pattern,panes=mutableListOf(Pane(background=color)))
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
    internal var refreshPieSettings:(()->Unit)?=null
    private var historyOverlay:HistoryScrubView?=null
    private fun historyHud(position:Int,total:Int){
        if(!::canvasHost.isInitialized)return
        if(position<0){historyOverlay?.let{canvasHost.removeView(it)};historyOverlay=null;return}
        val hud=historyOverlay?:HistoryScrubView(this).also{view->
            historyOverlay=view;canvasHost.addView(view,FrameLayout.LayoutParams(min(dp(420),(canvasHost.width-dp(32)).coerceAtLeast(dp(180))),dp(64),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=dp(78)})
        }
        hud.update(position,total)
    }
    internal fun pieSlots():List<String> = try{val a=org.json.JSONArray(prefs.getString("pieSlots","[]"));(0..5).map{a.optString(it).takeIf{key->key in ShortcutCatalog.keys}.orEmpty()}}catch(_:Exception){List(6){""}}
    internal fun assignPie(index:Int){
        choices(tr("Choose shortcut","انتخاب میان‌بر"),listOf("clear_shortcut")+ShortcutCatalog.keys){n->val slots=pieSlots().toMutableList();slots[index]=if(n==0)""else ShortcutCatalog.keys[n-1];prefs.edit().putString("pieSlots",org.json.JSONArray(slots).toString()).apply();pieOverlay?.let{canvasHost.removeView(it)};pieOverlay=null;refreshPieSettings?.invoke()}
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
            "timer","stopwatch","dice","scoreboard","picker"->floatingTools.open(key)
            "laser"->{board.tool="laser";board.clearSelection();refreshDock();toast(tr("Laser pen: lines fade by themselves","قلم لیزری: خط‌ها خودشان محو می‌شوند"))}
            "spotlight"->showSpotlight()
            "ai_quiz"->aiQuiz();"class_quiz"->classQuizzes();"table"->insertTable();"map_iran"->insertIranMap();"frame"->insertFrame();"present_frames"->presentFrames();"ai_chart"->aiChart();"replay"->showReplay();"ai_mindmap"->aiMindMap();"ai_summary"->aiSummary();"ai_service"->aiSettings();"ai_image"->aiPicture()
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
                    // No question first: it is deleted at once and the bar below offers Undo (rule 48: undo beats a confirmation step).
                    if(store.page.layers.size>1 && !isPdfBaseLayer){
                        val target=store
                        store.editMetadata{store.page.items.removeAll{it.layerId==layer.id};store.page.layers.remove(layer);if(store.page.activeLayerId==layer.id)store.page.activeLayerId=store.page.layers.last().id};board.clearSelection();refresh()
                        com.google.android.material.snackbar.Snackbar.make(canvasHost,tr("Layer deleted","لایه حذف شد"),com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setAction(tr("Undo","بازگردانی")){if(board.store===target){board.undo();refresh()}}.show()
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
    internal fun calibrateTips(){
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
    internal fun graph(initial:String="y=2x-5",existing:Item?=null){
        val c=column().apply{pad(20)};c.addView(infoTitle(tr("Information","اطلاعات"),tr("Enter y=f(x). Use x^2, sin(x), sqrt(x), abs(x); angles are radians. Separate up to 3 curves with ;. Both axes use the same scale.","تابع را به صورت y=f(x) وارد کنید. x^2، sin(x)، sqrt(x) و abs(x) مجازند؛ زاویه‌ها رادیانی‌اند. حداکثر ۳ تابع را با ; جدا کنید. مقیاس دو محور برابر است.")))
        val field=field(existing?.text?:initial).apply{layoutDirection=View.LAYOUT_DIRECTION_LTR;textDirection=View.TEXT_DIRECTION_LTR};c.addView(field)
        val examples=row();listOf("y=2x-5","y=x^2","sin(x);cos(x)","sqrt(x)").forEach{v->examples.addView(button(v){field.setText(v)})};c.addView(scrollRow(examples))
        c.addView(label(tr("Half-range on the shorter axis","نیم‌بازهٔ محور کوتاه‌تر"),14f));val domain=field((existing?.domain?:10f).toString());c.addView(domain)
        val d=dialog(s("graph"),ScrollView(this).apply{addView(c)})
        c.addView(button(tr("Plot","رسم نمودار"),true){try{val text=field.text.toString();require(text.split(';').size in 1..3);text.split(';').forEach{MathTools().compile(it)};val span=domain.text.toString().toFloat();require(span in .1f..1000f);if(existing==null)board.insert(Item(kind="graph",text=text,w=560f,h=400f,domain=span))else store.editMetadata{existing.text=text;existing.domain=span;existing.plotIntersections=emptyList()};d.dismiss()}catch(e:Exception){field.error=e.message?:s("error")}})
    }

    internal fun shapes() {
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

    internal var menuReturn:(()->Unit)?=null
    private val menuDialogs=mutableListOf<java.lang.ref.WeakReference<AlertDialog>>()
    internal fun navigationBuilder(title:String):MaterialAlertDialogBuilder {
        val back=menuReturn;menuReturn=null
        menuDialogs.removeAll{it.get()?.isShowing!=true}
        val nested=back!=null || menuDialogs.isNotEmpty()
        val heading=navigationHeading(title,if(nested)({})else null)
        heading.findViewById<View>(R.id.menu_back_button)?.tag=back
        return MaterialAlertDialogBuilder(this).setCustomTitle(heading)
            .setNegativeButton(s("close"),null)
            .setOnCancelListener{d->d.dismiss();back?.invoke()}
    }
    internal fun shown(d:AlertDialog):AlertDialog {
        menuDialogs.add(java.lang.ref.WeakReference(d))
        Fonts.onShown(d)
        d.window?.setGravity(Gravity.CENTER)
        d.findViewById<View>(R.id.menu_back_button)?.let{button->
            @Suppress("UNCHECKED_CAST") val back=button.tag as? (() -> Unit)
            button.setOnClickListener{d.dismiss();back?.invoke()}
        }
        return d
    }
    internal fun dialog(title:String,content:View):AlertDialog =
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

    internal fun choices(title:String,keys:List<String>,action:(Int)->Unit) {
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

    internal fun confirm(title: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setMessage(title)
            .setNegativeButton(s("cancel"), null)
            .setPositiveButton(s("apply")) { _, _ -> action() }
            .show()
    }

    internal fun input(
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

    internal fun error(e: Exception) {
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

    internal fun <T> work(task: () -> T, done: (T) -> Unit) {
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

    internal fun menu() {
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

    internal fun colors() {
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

    internal fun insert() {
        choices(s("insert"), listOf("sticky", "graph", "image", "pdf", "math")) { when(it){
            0->addText(true);1->graph();2->pick("image/*",102);3->choosePdfImport();4->math()
        }}
    }

    internal fun math() {
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
            val label=if(key=="lock")(if(items.all{it.locked})tr("Unlock","باز کردن قفل")else tr("Lock","قفل کردن"))else s(key)
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

    internal fun crop(o:Item) {
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

    private var pagesUndo:String?=null
    internal fun pages() {
        val returnMenu=menuReturn
        val c=column().apply{pad(14);layoutDirection=resources.configuration.layoutDirection}
        c.addView(label(s("pages"),20f,NAVY,true).apply{setPadding(dp(8),dp(6),dp(8),dp(10))})
        val d=dialog("",c)
        d.window?.setTitle(s("pages"))
        d.window?.decorView?.layoutDirection=resources.configuration.layoutDirection
        pagesUndo?.let{message->pagesUndo=null
            c.addView(row().apply{gravity=Gravity.CENTER_VERTICAL;background=rounded(PRIMARY_CONTAINER,dp(14).toFloat());setPadding(dp(14),dp(4),dp(6),dp(4))
                addView(label(message,15f,NAVY,true),LinearLayout.LayoutParams(0,-2,1f))
                addView(button(tr("Undo","بازگردانی"),true){projectStore.undo();whiteboard.clearSelection();whiteboard.reset();refreshPages();d.dismiss();menuReturn=returnMenu;pages()},LinearLayout.LayoutParams(-2,dp(48)))
            },LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})}
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
            }};whiteboard.clearSelection();whiteboard.reset();d.dismiss();menuReturn=returnMenu
                pagesUndo=when(action){3->tr("Page cleared","صفحه پاک شد");4->tr("Page deleted","صفحه حذف شد");else->null};pages()}
            // Item 48: no "are you sure?" — the action happens and Undo sits right here in the list.
            applyAction()
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

    internal fun activateBoard(target:Board){
        bindBoardExtras(target)
        if(board!==target){target.copyToolsFrom(board);board=target;closeSidePanel()}
        target.onObjectActions={editSelection()}
        target.onSmart={processSmart(it)}
        target.onMindAdd={node,sibling->mindNode(node,sibling)}
        refreshDock()
    }
    internal fun projectChanged(){
        dirty=true;status.text=s("saving")
        handler.removeCallbacks(autosave);handler.postDelayed(autosave,1800)
    }
    @Deprecated("Platform compatibility")
    override fun onBackPressed(){withPdfExit{persist();super.onBackPressed()}}
    /**
     * Android 13+ (and every device once the app targets Android 16) sends Back here instead of onBackPressed.
     * Same behavior: unsaved PDF annotations are asked about first; the lesson is saved; the app goes to the background.
     */
    private fun registerBack(){
        if(android.os.Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){
            withPdfExit{persist();if(isTaskRoot)moveTaskToBack(true) else finish()}}
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

    internal var smartSource="auto"
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
    internal fun smart(){smartSettings()}
    internal fun processSmart(items:List<Item>){
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
            googleSearch?.close();googleSearch=GoogleSearchWindow(this,canvasHost){src->addWebImage(src)}.also{it.open(query)}
        }
        val browser:(String,Boolean)->Unit={query,adjacent->googleSearch?.close();googleSearch=null;GoogleSearchBrowser.open(this,query,adjacent)}
        val query=candidates.firstOrNull().orEmpty().trim()
        if(query.isEmpty()){toast(tr("No text was recognized","متنی تشخیص داده نشد"));return}
        if(!target.needsReview(candidates,prefs.getBoolean("smartReview",false))){
            if(target.destination=="floating")floating(query)else browser(query,target.adjacent)
        }else GoogleSearchReview(this).show(candidates,floating,browser,target)
    }

    // ---- Board AI (phase 6) ----
    internal val smartAi by lazy{SmartAi(this,{p->voiceKeys.forProvider(p).read()})}
    internal val aiWorker=Executors.newSingleThreadExecutor()
    internal var aiTicket=0
    internal var aiBusyLabel=""
    internal var stepReveal:StepReveal?=null
    internal var framePresenter:FramePresenter?=null
    /**
     * Voice commands (item 70), on the main thread, only from [VoiceCommands]' list; off with the "Voice commands" switch.
     * Each one is reversible and a toast shows what was done, so a misheard command is visible and easy to undo.
     */
    internal val voiceCommand:((VoiceCommands.Call)->String)? get()=if(!prefs.getBoolean("voiceCommands",true))null else {call->
        val a=call.args
        val done=when(call.name){
            "go_to_page"->when(a.optString("where")){"next"->{switchPage(projectStore.lesson.current+1);tr("Next page","صفحهٔ بعد")}
                "previous"->{switchPage(projectStore.lesson.current-1);tr("Previous page","صفحهٔ قبل")}else->{addPage();tr("New page","صفحهٔ جدید")}}
            "set_tool"->{val tool=when(a.optString("tool")){"highlighter"->"highlight";"eraser"->"erase";"select"->"select";else->"pen"}
                board.tool=tool;refreshDock();tr("Tool: ","ابزار: ")+s(tool)}
            "set_pen_color"->{val color=when(a.optString("color")){"blue"->InkStrip.colors[1];"red"->InkStrip.colors[2];"green"->InkStrip.colors[3];"orange"->InkStrip.colors[4];"purple"->InkStrip.colors[5];else->InkStrip.colors[0]}
                if(board.tool!in listOf("pen","highlight"))board.tool="pen";board.inkColor=color;savePens();refreshDock();tr("Pen color changed","رنگ قلم عوض شد")}
            "start_timer"->{val m=a.optInt("minutes",5).coerceIn(1,60);floatingTools.open("timer",m);tr("Timer: $m min","تایمر: $m دقیقه")}
            "open"->{when(a.optString("what")){"name_picker"->runShortcut("picker");"labs"->runShortcut("lab");else->runShortcut(a.optString("what"))};s(a.optString("what").let{if(it=="name_picker")"picker" else if(it=="labs")"lab" else it})}
            "undo"->{board.undo();tr("Undone","برگشت داده شد")}
            else->null
        }
        if(done==null)"not allowed" else {toast("🎙 $done");"done"}
    }
    internal var spotlight:Spotlight?=null
    internal var replay:Replay?=null
    internal var settingsScreen:SettingsScreen?=null
    private fun insertConstruction(guide:Item,perpendicular:Boolean=false){
        if(guide.locked||!store.page.editable(guide)||!store.page.canDraw()||!board.canEditItem(guide))return
        val result=GeometryTools.construction(guide,perpendicular).apply{color=board.guideInkColor(guide.pane);width=board.guideInkWidth(guide.pane);layerId=store.page.activeLayerId}
        val label=GuideLabels.construction(board,guide,result)
        store.editMetadata{store.page.items.add(result);store.page.items.add(label)}
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

    internal fun secretStudio(){
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
    internal fun maboox(){
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

    internal fun insertBitmap(bitmap: Bitmap) {
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

    internal fun toast(message: String) {
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
        aiWorker.shutdownNow();if(Labs.assistant!=null)Labs.assistant=null
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

/** InputStream.readNBytes needs API 33; this reads at most [limit] bytes on every supported version. */
internal fun java.io.InputStream.readNBytesCompat(limit:Int):ByteArray{
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);var total=0
    while(total<limit){val n=read(buffer,0,minOf(buffer.size,limit-total));if(n<0)break;out.write(buffer,0,n);total+=n}
    return out.toByteArray()
}
