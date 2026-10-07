package com.vuravision.classroom

import com.google.gson.Gson
import java.security.SecureRandom

data class DeviceProfile(val id:String=newId(),val name:String="",val color:Int=0xff4262e8.toInt(),val emoji:String="✏️",val photo:String="") {
    fun validate(){
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        require(name.isNotBlank()&&name.length<=60&&name.none{it.isISOControl()})
        require(emoji.length<=16&&emoji.none{it.isISOControl()}&&color ushr 24==255)
        require(photo.length<=90000&&(photo.isEmpty()||photo.matches(Regex("[A-Za-z0-9+/=]+"))))
    }
}
object RoomRoles {
    const val VIEW="view";const val CONTROL="control";const val COLLAB="collaborate";const val HOST="host"
    val assignable=listOf(VIEW,CONTROL,COLLAB)
}
data class RoomMember(val profile:DeviceProfile,val role:String,val approved:Boolean,val online:Boolean)
data class RoomRect(val left:Float=0f,val top:Float=0f,val right:Float=0f,val bottom:Float=0f)
data class RoomPresence(val peer:String="",val page:String="",val pane:Int=0,val x:Float=0f,val y:Float=0f,val viewport:RoomRect=RoomRect(),
    val selected:List<String> = emptyList(),val live:List<Item> = emptyList(),val active:Boolean=false,val at:Long=0)
data class RoomPaneTools(val color:Int=NAVY,val width:Float=4f,val style:String="round",val dashLength:Float=12f,val dashGap:Float=8f,val zoom:Float=1f,val tx:Float=0f,val ty:Float=0f)
data class RoomTools(val tool:String="pen",val shape:String="rectangle",val color:Int=NAVY,val width:Float=4f,val style:String="round",val opacity:Int=255,
    val highlightColor:Int=0xffffcf40.toInt(),val highlightWidth:Float=6f,val dashLength:Float=12f,val dashGap:Float=8f,val eraserMode:String="stroke",val eraserRadius:Float=18f,
    val panes:List<RoomPaneTools> = emptyList(),val activePane:Int=0,val eraseObjects:Boolean=true,val selectionMode:String="free",val smartMode:String="text",
    val fillColor:Int?=TEAL,val fillAlpha:Int=255,val activeLayer:String="base",val guideSnap:Boolean=true,val holdRecognition:Boolean=true,val holdDelay:Long=1000,val holdTolerance:Float=4f) {
    fun validate(){
        require(tool in listOf("pen","select","pan","erase","shape","highlight","smart","fill"))
        require(width in .1f..80f&&highlightWidth in .1f..80f&&style in PenStyles.keys&&opacity in 0..255)
        require(dashLength in 0f..1000f&&dashGap in 0f..1000f&&eraserRadius in 1f..200f&&eraserMode in listOf("stroke","area"))
        require(activePane in 0..3&&panes.size<=4&&panes.all{it.width in .1f..80f&&it.style in PenStyles.keys&&it.dashLength in 1f..80f&&it.dashGap in 1f..80f&&it.zoom in .0001f..100000f&&it.tx.isFinite()&&it.ty.isFinite()})
        require(selectionMode in listOf("free","box")&&smartMode in listOf("text","formula","graph","shape","search","convert"))
        require(fillAlpha in 0..255&&activeLayer.length in 1..80&&holdDelay in 100..10000&&holdTolerance in .1f..100f)
    }
}
data class RoomPatch(val page:String,val id:String,val before:Item?,val after:Item?)
data class RoomOrder(val page:String,val before:List<String>,val after:List<String>)
data class RoomOperation(val id:String=newId(),val kind:String="edit",val baseRevision:Long=0,val patches:List<RoomPatch> = emptyList(),val structure:Lesson?=null,
    val page:String="",val tools:RoomTools?=null,val orders:List<RoomOrder> = emptyList())
data class RoomPoll(val revision:Long=-1,val operations:List<RoomOperation> = emptyList(),val presence:RoomPresence?=null,val profile:DeviceProfile?=null)
data class RoomReceipt(val id:String,val accepted:Boolean,val reason:String="")
data class RoomDelta(val revision:Long,val patches:List<RoomPatch> = emptyList(),val structure:Lesson?=null,val page:String="",val orders:List<RoomOrder> = emptyList())
data class RoomFrame(val protocol:Int=1,val room:String="",val revision:Long=0,val lesson:Lesson?=null,val members:List<RoomMember> = emptyList(),val serverTime:Long=0,
    val presence:List<RoomPresence> = emptyList(),val locks:Map<String,String> = emptyMap(),val authors:Map<String,String> = emptyMap(),val role:String=RoomRoles.VIEW,
    val approved:Boolean=false,val receipts:List<RoomReceipt> = emptyList(),val undo:Int=0,val redo:Int=0,val tools:RoomTools=RoomTools(),val deltas:List<RoomDelta> = emptyList())
data class RoomJoin(val profile:DeviceProfile,val protocol:Int=1)
data class RoomWelcome(val peer:String,val credential:String,val frame:RoomFrame)
data class RoomListing(val protocol:Int=1,val name:String="",val host:DeviceProfile?=null)

object RoomDocuments {
    fun key(page:String,id:String)="$page/$id"
    fun same(a:Any?,b:Any?)=a==b
    /** Device camera, active layer, pen settings and the separate PDF reader stay local. */
    fun structure(doc:Lesson)=doc.copy(current=0,pdf=null,customColors=doc.customColors?.toMutableList(),pages=doc.pages.map{p->p.copy(items=mutableListOf(),
        activeLayerId=p.layers.first().id,layers=p.layers.map{it.copy()}.toMutableList(),panes=p.panes.map{Pane(background=it.background,pattern=it.pattern)}.toMutableList(),alternateCanvas=p.alternateCanvas?.duplicate(false))}.toMutableList())
    fun appendOnly(order:RoomOrder)=order.after==order.before.filter{it in order.after}+order.after.filter{it !in order.before}
    fun edit(before:Lesson,after:Lesson,revision:Long):RoomOperation? {
        val old=before.pages.flatMap{p->p.items.map{key(p.id,it.id) to (p.id to it)}}.toMap()
        val next=after.pages.flatMap{p->p.items.map{key(p.id,it.id) to (p.id to it)}}.toMap()
        val changes=(old.keys+next.keys).distinct().mapNotNull{k->val a=old[k];val b=next[k]
            if(same(a?.second,b?.second))null else RoomPatch((b?:a)!!.first,(b?:a)!!.second.id,a?.second?.deepCopy(),b?.second?.deepCopy())}
        val meta=structure(after).takeUnless{same(structure(before),it)}
        val priorPages=before.pages.associateBy{it.id}
        val orders=after.pages.mapNotNull{p->val a=priorPages[p.id]?.items?.map{it.id}.orEmpty();val b=p.items.map{it.id};if(a==b)null else RoomOrder(p.id,a,b)}
        return if(changes.isEmpty()&&meta==null&&orders.isEmpty())null else RoomOperation(baseRevision=revision,patches=changes,structure=meta,orders=orders)
    }
}

/** The host serializes conditional object edits, selection leases and per-author history. */
class RoomEngine(initial:Lesson,host:DeviceProfile,val name:String,private val clock:()->Long={System.currentTimeMillis()}) {
    private data class Peer(var profile:DeviceProfile,val credential:String,var role:String,var approved:Boolean,var seen:Long,var revoked:Boolean=false)
    private data class Lease(val peer:String,val until:Long)
    private data class History(val patches:List<RoomPatch>,val before:Lesson?,val after:Lesson?,val orders:List<RoomOrder>)
    private val peers=linkedMapOf<String,Peer>()
    private val leases=mutableMapOf<String,Lease>()
    private val positions=mutableMapOf<String,RoomPresence>()
    private val authors=mutableMapOf<String,String>()
    private val past=mutableMapOf<String,ArrayDeque<History>>()
    private val future=mutableMapOf<String,ArrayDeque<History>>()
    private var limit=50
    val historyLimit get()=limit
    @Synchronized fun setHistoryLimit(value:Int){limit=value.coerceIn(10,200);(past.values+future.values).forEach{while(it.size>historyLimit)it.removeFirst()}}
    private val receipts=mutableMapOf<String,LinkedHashMap<String,RoomReceipt>>()
    private val journal=ArrayDeque<RoomDelta>()
    val hostId=host.id
    val hostCredential=secret()
    private var document=initial.copyDeep().apply{pdf=null}
    private var ended=false
    var revision=0L;private set
    var tools=RoomTools();private set
    init {host.validate();validDocument(document);peers[host.id]=Peer(host,hostCredential,RoomRoles.HOST,true,clock())
        document.pages.forEach{p->p.items.forEach{authors[RoomDocuments.key(p.id,it.id)]=host.id}}}
    companion object {fun secret()=ByteArray(24).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}}
    private fun validDocument(doc:Lesson){doc.validate();require(doc.pages.map{it.id}.distinct().size==doc.pages.size);require(doc.allAssetItems().all{it.asset.isEmpty()||it.asset.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}"))&&!it.asset.contains("..")});require(Gson().toJson(doc).toByteArray().size<=12*1024*1024){"room_document_size"}}
    @Synchronized fun join(request:RoomJoin):RoomWelcome {
        check(!ended){"room_closed"};require(request.protocol==1){"protocol"};request.profile.validate();prune()
        require(peers.values.count{!it.revoked}<16){"room_full"}
        val id=newId();val p=Peer(request.profile.copy(id=id),secret(),RoomRoles.VIEW,false,clock());peers[id]=p
        return RoomWelcome(id,p.credential,frame(id,-1))
    }
    @Synchronized fun authenticate(id:String,credential:String)=!ended&&peers[id]?.let{!it.revoked&&it.credential==credential}==true
    @Synchronized fun setRole(id:String,role:String){require(id!=hostId&&role in RoomRoles.assignable);val p=requireNotNull(peers[id]);check(!p.revoked);p.role=role;p.approved=true;release(id);positions.remove(id)}
    @Synchronized fun remove(id:String){require(id!=hostId);peers[id]?.revoked=true;release(id);positions.remove(id);past.remove(id);future.remove(id);receipts.remove(id)}
    @Synchronized fun leave(id:String){if(id!=hostId){peers[id]?.seen=clock()-10001;release(id);positions.remove(id)}}
    @Synchronized fun snapshot()=document.copyDeep()
    @Synchronized fun end(){ended=true}
    private fun owner(id:String)=if(peers.getValue(id).role==RoomRoles.CONTROL)hostId else id
    private fun release(id:String){leases.entries.removeAll{it.value.peer==id}}
    private fun prune(){val now=clock();leases.entries.removeAll{it.value.until<now||peers[it.value.peer]?.let{it.revoked||now-it.seen>10000}!=false};positions.entries.removeAll{peers[it.key]?.revoked==true}}
    @Synchronized fun poll(id:String,request:RoomPoll):RoomFrame {
        check(!ended){"room_closed"};val peer=requireNotNull(peers[id]);check(!peer.revoked){"removed"};peer.seen=clock();prune()
        request.profile?.let{it.validate();peer.profile=it.copy(id=id)}
        request.presence?.let{receivePresence(id,it)};require(request.operations.size<=32)
        val result=request.operations.map{op->val cached=receipts.getOrPut(id){linkedMapOf()};cached[op.id]?:run{
            val answer=try{applyOperation(id,op);RoomReceipt(op.id,true)}catch(e:Exception){RoomReceipt(op.id,false,e.message?.take(80)?:"invalid_operation")}
            cached[op.id]=answer;while(cached.size>256)cached.remove(cached.keys.first());answer}}
        return frame(id,if(result.any{!it.accepted})-1 else request.revision,result)
    }
    private fun receivePresence(id:String,p:RoomPresence){
        require(p.pane in 0..3&&listOf(p.x,p.y,p.viewport.left,p.viewport.top,p.viewport.right,p.viewport.bottom).all{it.isFinite()})
        require(p.selected.size<=200&&p.live.size<=8);val peer=peers.getValue(id);val page=document.pages.firstOrNull{it.id==p.page}?:return
        val editing=peer.approved&&peer.role!=RoomRoles.VIEW
        val desired=if(editing)p.selected.filter{itemId->page.items.any{it.id==itemId}}else emptyList()
        val keys=desired.map{RoomDocuments.key(page.id,it)}.toSet();leases.entries.removeAll{it.value.peer==id&&it.key !in keys}
        keys.forEach{k->if(leases[k]?.peer in listOf(null,id))leases[k]=Lease(id,clock()+5000)}
        val live=if(editing)p.live.filter{v->v.kind in listOf("ink","shape")&&v.points.size<=2000&&v.pane in page.visiblePaneIndices&&
            (page.items.none{it.id==v.id}||leases[RoomDocuments.key(page.id,v.id)]?.peer==id)&&
            listOf(v.x,v.y,v.w,v.h,v.width,v.rotation,v.inkW,v.inkH).all{it.isFinite()}&&v.width in .1f..200f&&v.w>0&&v.h>0&&v.inkW>0&&v.inkH>0&&v.points.all{it.x.isFinite()&&it.y.isFinite()}}.map{it.deepCopy()}else emptyList()
        val old=positions[id];val touched=p.active||old==null||old.page!=p.page||old.viewport!=p.viewport||old.x!=p.x||old.y!=p.y||old.selected!=desired
        // Retain the previous timestamp after expiry so idle heartbeats cannot revive the ghost.
        positions[id]=p.copy(peer=id,selected=desired.filter{leases[RoomDocuments.key(page.id,it)]?.peer==id},live=live,at=if(touched)clock()else old!!.at)
    }
    private fun applyOperation(id:String,op:RoomOperation){
        require(op.id.matches(Regex("[a-zA-Z0-9-]{1,80}")));val p=peers.getValue(id);check(p.approved&&p.role!=RoomRoles.VIEW){"read_only"}
        when(op.kind){
            "edit"->{
                require(op.patches.size<=20000);if(op.structure!=null)check(op.baseRevision==revision){"document_changed"}
                val before=op.structure?.let{RoomDocuments.structure(document)};commit(id,op.patches,op.structure,op.orders)
                val entry=History(op.patches.map{it.copy(before=it.before?.deepCopy(),after=it.after?.deepCopy())},before,op.structure?.let{RoomDocuments.structure(document)},op.orders)
                val stack=past.getOrPut(owner(id)){ArrayDeque()};stack.addLast(entry);while(stack.size>historyLimit)stack.removeFirst();future.getOrPut(owner(id)){ArrayDeque()}.clear()
            }
            "undo","redo"->{
                val undo=op.kind=="undo";val who=owner(id);val source=(if(undo)past else future).getOrPut(who){ArrayDeque()};val entry=source.lastOrNull()?:return
                val expected=if(undo)entry.after else entry.before;if(expected!=null)check(RoomDocuments.same(RoomDocuments.structure(document),expected)){"document_changed"}
                commit(id,if(undo)entry.patches.map{it.copy(before=it.after,after=it.before)}else entry.patches,if(undo)entry.before else entry.after,
                    if(undo)entry.orders.map{it.copy(before=it.after,after=it.before)}else entry.orders)
                source.removeLast();(if(undo)future else past).getOrPut(who){ArrayDeque()}.addLast(entry)
            }
            "navigate"->{check(p.role in listOf(RoomRoles.HOST,RoomRoles.CONTROL)){"independent_view"};val i=document.pages.indexOfFirst{it.id==op.page};require(i>=0)
                if(document.current!=i){document.current=i;revision++;record(RoomDelta(revision,page=op.page))}}
            "tools"->{check(p.role in listOf(RoomRoles.HOST,RoomRoles.CONTROL)){"independent_tools"};val t=requireNotNull(op.tools);t.validate();tools=t;revision++;record(RoomDelta(revision))}
            else->error("unknown_operation")
        }
    }
    private fun commit(id:String,patches:List<RoomPatch>,structure:Lesson?,orders:List<RoomOrder>){
        check(patches.map{RoomDocuments.key(it.page,it.id)}.distinct().size==patches.size){"duplicate_patch"}
        patches.forEach{p->val key=RoomDocuments.key(p.page,p.id);check(leases[key]?.peer in listOf(null,id)){"object_in_use"}
            val current=document.pages.firstOrNull{it.id==p.page}?.items?.firstOrNull{it.id==p.id};check(RoomDocuments.same(current,p.before)){"object_changed"};check(p.after==null||p.after.id==p.id){"invalid_id"}}
        require(orders.size<=200&&orders.map{it.page}.distinct().size==orders.size)
        orders.forEach{o->require(o.after.size<=20000&&o.after.distinct().size==o.after.size)
            val current=document.pages.firstOrNull{it.id==o.page}?.items?.map{it.id}.orEmpty();val common=current.toSet().intersect(o.before.toSet())
            check(current.filter{it in common}==o.before.filter{it in common}){"order_changed"}}
        val candidate=document.copyDeep()
        if(structure!=null){
            require(structure.pdf==null&&structure.pages.size in 1..200);val old=candidate.pages.associateBy{it.id};val current=candidate.pages[candidate.current].id
            candidate.title=structure.title;candidate.customColors=structure.customColors?.toMutableList()
            candidate.pages=structure.pages.map{m->m.copied().copy(items=old[m.id]?.items?:mutableListOf(),layers=m.layers.map{it.copy()}.toMutableList(),panes=m.panes.map{it.copy()}.toMutableList())}.toMutableList()
            candidate.current=candidate.pages.indexOfFirst{it.id==current}.coerceAtLeast(0)
        }
        patches.forEach{p->val page=candidate.pages.firstOrNull{it.id==p.page};if(page==null){check(p.after==null){"page_missing"};return@forEach}
            val i=page.items.indexOfFirst{it.id==p.id};if(p.after==null){if(i>=0)page.items.removeAt(i)}else if(i>=0)page.items[i]=p.after.deepCopy()else page.items.add(p.after.deepCopy())}
        orders.forEach{o->candidate.pages.firstOrNull{it.id==o.page}?.let{page->
            if(!RoomDocuments.appendOnly(o)){val items=page.items.associateBy{it.id};page.items=(o.after.mapNotNull{items[it]}+page.items.filter{it.id !in o.after}).toMutableList()}}}
        validDocument(candidate);document=candidate;revision++
        record(RoomDelta(revision,patches.map{it.copy(before=null,after=it.after?.deepCopy())},structure?.let{RoomDocuments.structure(candidate)},
            orders=orders.mapNotNull{o->candidate.pages.firstOrNull{it.id==o.page}?.let{RoomOrder(o.page,emptyList(),it.items.map{v->v.id})}}))
        patches.forEach{p->val key=RoomDocuments.key(p.page,p.id);if(p.after==null){authors.remove(key);leases.remove(key)}else authors.putIfAbsent(key,owner(id))}
    }
    private fun record(delta:RoomDelta){journal.addLast(delta);while(journal.size>256)journal.removeFirst()}
    @Synchronized fun frame(id:String,known:Long,receipts:List<RoomReceipt> = emptyList()):RoomFrame {
        prune();val p=peers.getValue(id);val who=owner(id);val now=clock();val changes=journal.filter{it.revision>known}
        val weight=changes.sumOf{d->d.patches.sumOf{p->p.after?.let{v->64L+v.points.size+v.fillRuns.orEmpty().size*3L+v.cuts.size*8L+v.plotPoints.orEmpty().size*4L+v.text.length/8}?:0L}+
            d.orders.sumOf{it.after.size.toLong()}+d.structure?.pages.orEmpty().sumOf{64L+it.layers.size*10L}}
        val canDelta=known>=0&&known<=revision&&weight<100000&&(known==revision||journal.firstOrNull()?.revision?.let{known>=it-1}==true)
        return RoomFrame(room=name,revision=revision,serverTime=now,lesson=if(p.approved&&known!=revision&&!canDelta)document.copyDeep()else null,
            members=peers.filterValues{!it.revoked}.map{(key,value)->RoomMember(value.profile,value.role,value.approved,key==hostId||now-value.seen<=10000)},
            presence=if(p.approved)positions.values.filter{now-it.at<=60000}.map{it.copy(active=it.active&&now-it.at<1000,live=if(now-it.at<1500)it.live else emptyList())}else emptyList(),
            locks=if(p.approved)leases.mapValues{it.value.peer}else emptyMap(),authors=if(p.approved)authors.toMap()else emptyMap(),role=p.role,approved=p.approved,
            receipts=receipts,undo=past[who]?.size?:0,redo=future[who]?.size?:0,tools=tools,deltas=if(p.approved&&canDelta)changes else emptyList())
    }
}
