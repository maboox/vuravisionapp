package com.vuravision.classroom

import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private data class RoomRecovery(val address:String,val peer:String,val credential:String,val operations:List<RoomOperation>)

/** A single IO queue orders transport; document/UI changes always return to the main thread. */
class RoomController(private val context:Context,private val store:Store,private val board:Board,private val media:Media,
    private val beforeJoin:()->Unit,private val changed:()->Unit):AutoCloseable {
    val discovery=RoomDiscovery(context)
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadScheduledExecutor()
    private val gson=Gson()
    private val pending=linkedMapOf<String,RoomOperation>()
    private val recovery=File(context.filesDir,"room-recovery.json")
    private var server:RoomServer?=null
    private var http:RoomHttp?=null
    private var address:RoomAddress?=null
    private var credential=""
    var peer="";private set
    @Volatile var active=false;private set
    @Volatile var hosting=false;private set
    @Volatile var connected=false;private set
    @Volatile var connecting=false;private set
    @Volatile var ended=false;private set
    @Volatile private var closed=false
    @Volatile private var generation=0
    @Volatile private var revision=-1L
    @Volatile var frame=RoomFrame();private set
    var message="";private set
    private var messageUntil=0L
    private var canonical:Lesson?=null // IO thread only while active
    private var needsDocument=false
    private val recoveryScheduled=java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastSavedRecovery:RoomRecovery?=null
    private var baseline=store.lesson.copyDeep()
    private var applying=false
    private var joinedDocument=false
    private var deferred:Lesson?=null
    private var point=PointF()
    private var touching=false
    @Volatile private var profile=DeviceProfiles.load(context)
    @Volatile private var presence:RoomPresence?=null
    private var sentProfile:DeviceProfile?=null
    private var lastTools:RoomTools?=null
    private var lastPage=""
    private val uploaded=mutableSetOf<String>()
    @Volatile private var historyTarget:Int?=null
    @Volatile private var acknowledgedUndo=0
    private val overlay=RoomPresenceOverlay(board)
    val canEdit get()=!active||(!ended&&frame.approved&&frame.role!=RoomRoles.VIEW&&synchronized(pending){pending.size<126})
    val sharedView get()=frame.role in listOf(RoomRoles.HOST,RoomRoles.CONTROL,RoomRoles.VIEW)
    val invitations get()=server?.addresses(context).orEmpty()
    val canResume get()=recovery.isFile
    init {store.editAllowed={!active||applying||canEdit};board.onInteraction={event->
        if(active){point=board.world(event.x,event.y);touching=event.actionMasked !in listOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL);refreshPresence()
            if(!board.hasActiveInteraction)deferred?.let{deferred=null;applyDocument(it)}}}}
    private fun resetSession(){lastTools=null;lastPage="";sentProfile=null;uploaded.clear();historyTarget=null;acknowledgedUndo=0;needsDocument=false;ended=false;message="";messageUntil=0;touching=false;deferred=null;joinedDocument=hosting}
    fun host(name:String){
        check(!active&&!connecting&&!board.hasActiveInteraction);require(name.isNotBlank()&&name.length<=60)
        profile=DeviceProfiles.load(context);val engine=RoomEngine(store.lesson,profile,name.trim());val service=RoomServer(engine,media)
        service.start(8000,false);server=service;peer=engine.hostId;credential=engine.hostCredential;hosting=true;active=true;connected=true
        generation++;canonical=engine.snapshot();revision=engine.revision;baseline=store.lesson.copyDeep();frame=engine.frame(peer,-1);resetSession();attach();refreshPresence();discovery.advertise(service);schedule(0,generation);changed()
    }
    fun join(value:RoomAddress,result:(String?)->Unit){
        if(active||connecting){result("room_leave_first");return};connecting=true;val expected=++generation;changed()
        io.execute{try{val welcome=gson.fromJson(RoomHttp(value).json("/join",RoomJoin(DeviceProfiles.load(context))),RoomWelcome::class.java)
            check(welcome.frame.protocol==1);require(welcome.peer.matches(Regex("[a-zA-Z0-9-]{1,80}"))&&welcome.credential.matches(Regex("[a-f0-9]{48}")))
            main.post{if(generation==expected&&!closed){address=value;peer=welcome.peer;credential=welcome.credential;http=RoomHttp(value,peer,credential);hosting=false;active=true;connecting=false;connected=true;frame=welcome.frame
                canonical=null;revision=-1;baseline=store.lesson.copyDeep();resetSession();attach();persistRecovery();refreshPresence();schedule(0,expected);changed();result(null)}}
        }catch(e:Exception){main.post{if(generation==expected&&!closed){connecting=false;changed();result(e.message?:"connection_error")}}}}
    }
    fun resume(result:(String?)->Unit){
        if(active||connecting){result("room_leave_first");return}
        try{require(recovery.length()<=64L*1024*1024);val saved=gson.fromJson(recovery.readText(),RoomRecovery::class.java);require(saved.operations.size<=128)
            val target=RoomAddress.parse(saved.address);http=RoomHttp(target,saved.peer,saved.credential);address=target;peer=saved.peer;credential=saved.credential
            synchronized(pending){pending.clear();saved.operations.forEach{pending[it.id]=it}};hosting=false;active=true;connected=false;frame=RoomFrame();canonical=null;revision=-1;baseline=store.lesson.copyDeep();generation++;resetSession();attach();refreshPresence();schedule(0,generation);changed();result(null)
        }catch(_:Exception){result("room_resume_failed")}
    }
    private fun attach(){board.roomOverlay=overlay;board.selectionColor=profile.color
        board.canEditItem={v->!active||(canEdit&&frame.locks[RoomDocuments.key(store.page.id,v.id)] in listOf(null,peer))}
        board.sessionCanEdit=canEdit
        store.sessionUndo={history("undo")};store.sessionRedo={history("redo")};store.sessionHistory={frame.undo to frame.redo};store.sessionSeek={if(canEdit&&!board.hasActiveInteraction)historyTarget=it.coerceIn(0,frame.undo+frame.redo)}
    }
    fun localChange(){
        if(!active||applying||store.isCanceling)return
        if(canEdit)RoomDocuments.edit(baseline,store.lesson,revision)?.let{enqueue(it)}
        val page=store.page.id
        if(canEdit&&frame.role in listOf(RoomRoles.HOST,RoomRoles.CONTROL)&&lastPage!=page){lastPage=page;enqueue(RoomOperation(kind="navigate",baseRevision=revision,page=page))}
        baseline=store.lesson.copyDeep();refreshPresence()
    }
    private fun enqueue(op:RoomOperation){synchronized(pending){check(pending.size<128){"room_queue_full"};pending[op.id]=op};persistRecovery()}
    private fun history(kind:String){if(!active||!canEdit||board.hasActiveInteraction)return;historyTarget=null;enqueue(RoomOperation(kind=kind,baseRevision=revision))}
    private fun queueHistory(){val target=historyTarget?:return;synchronized(pending){
        if(pending.values.any{it.kind in listOf("undo","redo")})return
        val count=(target-acknowledgedUndo).coerceIn(-30,30);repeat(minOf(kotlin.math.abs(count),(126-pending.size).coerceAtLeast(0))){val op=RoomOperation(kind=if(count<0)"undo"else"redo",baseRevision=revision);pending[op.id]=op}
    }}
    fun refreshPresence(){
        if(!active)return;profile=DeviceProfiles.load(context).copy(id=peer);board.selectionColor=profile.color
        presence=RoomPresence(peer,store.page.id,board.activePane,point.x,point.y,board.viewportWorld(),board.selected.take(200),board.roomPreviews(),touching)
        if(canEdit&&frame.role in listOf(RoomRoles.HOST,RoomRoles.CONTROL)){val tools=captureTools();if(tools!=lastTools){lastTools=tools;enqueue(RoomOperation(kind="tools",baseRevision=revision,tools=tools))}}
    }
    private fun captureTools()=RoomTools(board.tool,board.shape,board.penColor,board.penWidth,board.penStyle,board.penOpacity,board.highlightColor,board.highlightWidth,board.dashLength,board.dashGap,board.eraserMode,board.eraserRadius,
        store.page.panes.map{RoomPaneTools(it.color,it.penWidth,it.penStyle,it.dashLength,it.dashGap,it.zoom,it.tx,it.ty)},board.activePane,board.eraseObjects,board.selectionMode,board.smartMode,board.fillColor,board.fillAlpha,store.page.activeLayerId,board.guideSnapEnabled,board.holdRecognitionEnabled,board.holdDelayMillis,board.holdTolerance)
    private fun applyTools(t:RoomTools){
        board.tool=t.tool;board.shape=t.shape;board.penColor=t.color;board.penWidth=t.width;board.penStyle=t.style;board.penOpacity=t.opacity;board.highlightColor=t.highlightColor;board.highlightWidth=t.highlightWidth
        board.dashLength=t.dashLength;board.dashGap=t.dashGap;board.eraserMode=t.eraserMode;board.eraserRadius=t.eraserRadius;board.eraseObjects=t.eraseObjects;board.selectionMode=t.selectionMode;board.smartMode=t.smartMode;board.fillColor=t.fillColor;board.fillAlpha=t.fillAlpha
        board.guideSnapEnabled=t.guideSnap;board.holdRecognitionEnabled=t.holdRecognition;board.holdDelayMillis=t.holdDelay;board.holdTolerance=t.holdTolerance
        t.panes.forEachIndexed{i,v->store.page.panes.getOrNull(i)?.let{p->p.color=v.color;p.penWidth=v.width;p.penStyle=v.style;p.dashLength=v.dashLength;p.dashGap=v.dashGap;p.zoom=v.zoom;p.tx=v.tx;p.ty=v.ty}}
        if(store.page.layers.any{it.id==t.activeLayer})store.page.activeLayerId=t.activeLayer
        if(board.activePane!=t.activePane)board.applySharedPane(t.activePane);lastTools=t;board.sceneChanged();board.onViewportChanged()
    }
    private fun schedule(delay:Long,expected:Int){if(!closed&&active&&generation==expected)io.schedule({poll(expected)},delay,TimeUnit.MILLISECONDS)}
    private fun poll(expected:Int){
        if(closed||!active||generation!=expected)return
        try{
            queueHistory();var ops=synchronized(pending){pending.values.take(32)}
            while(ops.size>1&&gson.toJson(ops).toByteArray().size>8*1024*1024)ops=ops.dropLast(1)
            val transport=http;val currentProfile=profile
            if(!hosting&&transport!=null)try{ops.flatMap{it.patches}.mapNotNull{it.after}.filter{it.asset.isNotEmpty()}.map{it.asset}.distinct().filter{it !in uploaded}.forEach{transport.upload(media.file(it));uploaded.add(it)}}catch(e:Exception){
                val permission=gson.fromJson(transport.json("/poll",RoomPoll(-1)),RoomFrame::class.java)
                if(permission.approved&&permission.role!=RoomRoles.VIEW)throw e
            }
            val request=RoomPoll(if(canonical==null)-1 else revision,ops,presence,currentProfile.takeUnless{it==sentProfile})
            val incoming=if(hosting)server!!.engine.poll(peer,request)else gson.fromJson(transport!!.json("/poll",request),RoomFrame::class.java)
            check(incoming.protocol==1);if(generation!=expected||!active)return;sentProfile=currentProfile
            val altered=incoming.lesson!=null||incoming.deltas.isNotEmpty();if(altered)needsDocument=true
            incoming.lesson?.let{it.validate();canonical=it.copyDeep()}
            canonical?.let{doc->incoming.deltas.forEach{d->merge(doc,d.patches,d.structure,d.orders,d.page)}}
            revision=incoming.revision;acknowledgedUndo=incoming.undo
            if(incoming.receipts.any{!it.accepted}||historyTarget==incoming.undo)historyTarget=null
            synchronized(pending){incoming.receipts.forEach{pending.remove(it.id)}};persistRecovery()
            canonical?.allAssetItems()?.filter{it.asset.isNotEmpty()&&!media.file(it.asset).isFile}?.map{it.asset}?.distinct()?.forEach{transport?.download(it,media.file(it))}
            val doc=canonical?.takeIf{needsDocument||incoming.receipts.any{!it.accepted}||frame.role!=incoming.role||!joinedDocument}?.copyDeep();needsDocument=false
            main.post{if(generation==expected&&active&&!closed){connected=true;receive(incoming,doc);changed()}}
        }catch(e:Exception){main.post{if(generation==expected&&active&&!closed){connected=false
            if(e.message in listOf("removed","room_closed")){ended=true;frame=frame.copy(role=RoomRoles.VIEW,approved=false);board.abortPendingGesture();board.clearSelection();board.sessionCanEdit=false;message="room_closed"}
            else message="room_reconnecting";changed()}}}
        finally{if(!ended)schedule(if(connected)140 else 1000,expected)}
    }
    private fun receive(incoming:RoomFrame,doc:Lesson?){
        val oldRole=frame.role;frame=incoming;board.sessionCanEdit=canEdit
        if(!canEdit||(board.selected.any{incoming.locks[RoomDocuments.key(store.page.id,it)] !in listOf(null,peer)})){board.abortPendingGesture();board.clearSelection()}
        if(doc!=null){if(board.hasActiveInteraction)deferred=doc else applyDocument(doc)}
        if(incoming.approved&&incoming.role in listOf(RoomRoles.HOST,RoomRoles.CONTROL,RoomRoles.VIEW)&&!board.hasActiveInteraction&&synchronized(pending){pending.values.none{it.kind=="tools"}})applyTools(incoming.tools)
        if(incoming.receipts.any{!it.accepted}){message="room_conflict";messageUntil=System.currentTimeMillis()+6000}
        else if(System.currentTimeMillis()>messageUntil)message=if(!incoming.approved)"room_waiting"else""
        if(oldRole!=incoming.role)lastTools=null
        overlay.update(incoming,peer);board.invalidate();refreshPresence()
    }
    private fun merge(doc:Lesson,patches:List<RoomPatch>,meta:Lesson?,orders:List<RoomOrder>,page:String="",optimistic:Boolean=false){
        meta?.let{val old=doc.pages.associateBy{it.id};val id=doc.pages[doc.current].id;doc.title=it.title;doc.customColors=it.customColors?.toMutableList()
            doc.pages=it.pages.map{p->p.copy(items=old[p.id]?.items?:mutableListOf(),layers=p.layers.map{l->l.copy()}.toMutableList(),panes=p.panes.map{v->v.copy()}.toMutableList())}.toMutableList();doc.current=doc.pages.indexOfFirst{p->p.id==id}.coerceAtLeast(0)}
        patches.forEach{p->doc.pages.firstOrNull{it.id==p.page}?.let{v->val i=v.items.indexOfFirst{it.id==p.id};if(p.after==null){if(i>=0)v.items.removeAt(i)}else if(i>=0)v.items[i]=p.after.deepCopy()else v.items.add(p.after.deepCopy())}}
        orders.filter{!optimistic||!RoomDocuments.appendOnly(it)}.forEach{o->doc.pages.firstOrNull{it.id==o.page}?.let{p->val values=p.items.associateBy{it.id};p.items=(o.after.mapNotNull{values[it]}+p.items.filter{it.id !in o.after}).toMutableList()}}
        if(page.isNotEmpty())doc.pages.indexOfFirst{it.id==page}.takeIf{it>=0}?.let{doc.current=it}
    }
    private fun applyDocument(source:Lesson){
        applying=true
        try{val first=!joinedDocument;if(first){beforeJoin();joinedDocument=true}
            val local=store.lesson;val current=store.page.id;var doc=source.copyDeep();if(hosting)doc.pdf=local.pdf
            synchronized(pending){pending.values.filter{it.kind=="edit"}.forEach{op->
                val candidate=doc.copyDeep();try{merge(candidate,op.patches,op.structure,op.orders,optimistic=true);candidate.validate();doc=candidate}catch(_:Exception){message="room_conflict";messageUntil=System.currentTimeMillis()+6000}
            }}
            val old=local.pages.associateBy{it.id};doc.pages.forEach{p->old[p.id]?.let{v->p.panes.forEachIndexed{i,n->v.panes.getOrNull(i)?.let{o->n.zoom=o.zoom;n.tx=o.tx;n.ty=o.ty;n.color=o.color;n.penWidth=o.penWidth;n.penStyle=o.penStyle;n.dashLength=o.dashLength;n.dashGap=o.dashGap}}
                if(p.layers.any{it.id==v.activeLayerId})p.activeLayerId=v.activeLayerId}}
            if(frame.role==RoomRoles.COLLAB||synchronized(pending){pending.values.any{it.kind=="navigate"}})doc.pages.indexOfFirst{it.id==current}.takeIf{it>=0}?.let{doc.current=it}
            doc.validate();store.lesson=doc;baseline=doc.copyDeep();if(store.page.id!=current)board.clearSelection();board.reconcilePage();if(first)board.fit();store.changed()
        }finally{applying=false}
    }
    fun setRole(id:String,role:String){server?.engine?.setRole(id,role);changed()}
    fun remove(id:String){server?.engine?.remove(id);changed()}
    fun pauseDrawing(){board.abortPendingGesture();touching=false;board.clearSelection();refreshPresence()}
    fun flushHostDocument(){if(active&&hosting&&!board.hasActiveInteraction)server?.engine?.snapshot()?.let{applyDocument(it)}}
    fun endHostAndFlush(){if(active&&hosting){pauseDrawing();server?.end();flushHostDocument()}}
    private fun recoveryValue():RoomRecovery?=if(active&&!hosting)address?.let{RoomRecovery(it.url,peer,credential,synchronized(pending){pending.values.toList()})}else null
    private fun writeRecovery(value:RoomRecovery?,expected:Int){if(value==null)return;val temp=File(recovery.path+".part");try{val bytes=gson.toJson(value);synchronized(recovery){if(generation!=expected||closed)return;temp.writeText(bytes);check(temp.renameTo(recovery))}}finally{temp.delete()}}
    private fun persistRecovery(){
        if(!active||hosting||closed||!recoveryScheduled.compareAndSet(false,true))return
        val expected=generation;io.execute{recoveryScheduled.set(false);val value=recoveryValue()
            if(generation==expected&&value!=null&&value!=lastSavedRecovery){writeRecovery(value,expected);lastSavedRecovery=value}
        }
    }
    fun leave(){
        if(!active){connecting=false;generation++;changed();return}
        val oldHttp=http;endHostAndFlush();active=false;hosting=false;connected=false;connecting=false;generation++;discovery.close();server=null;http=null;touching=false
        synchronized(pending){pending.clear()};synchronized(recovery){recovery.delete()};detach();store.replace(store.lesson);baseline=store.lesson.copyDeep();changed()
        if(oldHttp!=null)io.execute{runCatching{oldHttp.json("/leave",mapOf("ok" to true))}}
    }
    private fun detach(){board.abortPendingGesture();board.roomOverlay=null;board.canEditItem={true};board.sessionCanEdit=true;board.selectionColor=0xffe46d38.toInt();board.clearSelection();board.sceneChanged()
        store.sessionUndo=null;store.sessionRedo=null;store.sessionHistory=null;store.sessionSeek=null;store.editAllowed={true}}
    override fun close(){
        if(closed)return;runCatching{recoveryValue()?.let{writeRecovery(it,generation)}};generation++;active=false;closed=true;server?.end();discovery.close();main.removeCallbacksAndMessages(null);io.shutdownNow();detach()
    }
}
