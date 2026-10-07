package com.vuravision.classroom

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RoomEngineTest {
    private val host=DeviceProfile(name="Host")
    private fun engine(doc:Lesson=Lesson())=RoomEngine(doc,host,"Class")
    private fun guest(e:RoomEngine,role:String=RoomRoles.COLLAB):RoomWelcome=e.join(RoomJoin(DeviceProfile(name="Guest"))).also{e.setRole(it.peer,role)}
    private fun edit(e:RoomEngine,peer:String,change:(Lesson)->Unit):RoomFrame {val before=e.snapshot();val after=before.copyDeep();change(after);return e.poll(peer,RoomPoll(e.revision,listOf(requireNotNull(RoomDocuments.edit(before,after,e.revision)))))}
    private fun add(e:RoomEngine,peer:String,item:Item=Item(kind="shape"))=edit(e,peer){it.pages[0].items.add(item)}
    private fun op(e:RoomEngine,peer:String,kind:String)=e.poll(peer,RoomPoll(e.revision,listOf(RoomOperation(kind=kind))))
    @Test fun pendingGuestsReceiveNoDocumentUntilApproved(){val e=engine();val p=e.join(RoomJoin(DeviceProfile(name="Visitor")));assertFalse(p.frame.approved);assertNull(p.frame.lesson)
        assertNull(e.poll(p.peer,RoomPoll()).lesson);e.setRole(p.peer,RoomRoles.VIEW);val f=e.poll(p.peer,RoomPoll());assertTrue(f.approved);assertNotNull(f.lesson);assertEquals(RoomRoles.VIEW,f.role)}
    @Test fun profileIdentityCannotImpersonateHostOrAnotherSession(){val e=engine();val a=e.join(RoomJoin(host));val b=e.join(RoomJoin(host));assertNotEquals(host.id,a.peer);assertNotEquals(a.peer,b.peer);assertFalse(e.authenticate(a.peer,b.credential));assertTrue(e.authenticate(a.peer,a.credential))}
    @Test fun collaborateUndoAffectsOnlyThatAuthorsHistory(){val e=engine();val a=guest(e);val b=guest(e);val x=Item(kind="shape");val y=Item(kind="shape");add(e,a.peer,x);add(e,b.peer,y)
        assertTrue(op(e,a.peer,"undo").receipts.single().accepted);assertEquals(listOf(y.id),e.snapshot().pages[0].items.map{it.id});assertEquals(1,e.frame(b.peer,e.revision).undo);op(e,a.peer,"redo");assertEquals(2,e.snapshot().pages[0].items.size)}
    @Test fun controlUsesHostUndoAndSharedNavigationAndTools(){val doc=Lesson(pages=mutableListOf(Page(),Page()));val e=engine(doc);val p=guest(e,RoomRoles.CONTROL);add(e,host.id);assertEquals(1,e.frame(p.peer,e.revision).undo)
        op(e,p.peer,"undo");assertTrue(e.snapshot().pages[0].items.isEmpty());val tools=RoomTools(tool="shape",shape="circle",color=TEAL)
        val result=e.poll(p.peer,RoomPoll(e.revision,listOf(RoomOperation(kind="tools",tools=tools),RoomOperation(kind="navigate",page=doc.pages[1].id))));assertTrue(result.receipts.all{it.accepted});assertEquals(1,e.snapshot().current);assertEquals(tools,e.tools)}
    @Test fun retriesAreIdempotentEvenAfterAnotherOperation(){val e=engine();val p=guest(e);val before=e.snapshot();val after=before.copyDeep().apply{pages[0].items.add(Item(kind="shape"))};val op=RoomDocuments.edit(before,after,0)!!
        e.poll(p.peer,RoomPoll(0,listOf(op)));add(e,host.id);val revision=e.revision;val result=e.poll(p.peer,RoomPoll(0,listOf(op)));assertTrue(result.receipts.single().accepted);assertEquals(revision,e.revision);assertEquals(2,e.snapshot().pages[0].items.size)}
    @Test fun conditionalObjectConflictRejectsTheWholeOperation(){val item=Item(kind="shape");val doc=Lesson().apply{pages[0].items.add(item)};val e=engine(doc);val a=guest(e);val b=guest(e);val before=e.snapshot();val next=before.copyDeep().apply{pages[0].items[0].x=20f;pages[0].items.add(Item(kind="shape"))};val stale=RoomDocuments.edit(before,next,0)!!
        edit(e,a.peer){it.pages[0].items[0].x=10f};val result=e.poll(b.peer,RoomPoll(0,listOf(stale)));assertFalse(result.receipts.single().accepted);assertEquals(1,e.snapshot().pages[0].items.size);assertEquals(10f,e.snapshot().pages[0].items[0].x,0f);assertNotNull(result.lesson)}
    @Test fun undoCannotOverwriteAnotherPersonsSubsequentEdit(){val e=engine();val a=guest(e);val b=guest(e);add(e,a.peer);edit(e,b.peer){it.pages[0].items[0].x=99f};val result=op(e,a.peer,"undo");assertFalse(result.receipts.single().accepted);assertEquals(99f,e.snapshot().pages[0].items[0].x,0f);assertEquals(1,result.undo)}
    @Test fun selectionLeaseIsFirstComeAndExpires(){var clock=10000L;val item=Item(kind="shape");val e=RoomEngine(Lesson().apply{pages[0].items.add(item)},host,"Room"){clock};val a=guest(e);val b=guest(e);val page=e.snapshot().pages[0].id
        fun presence(id:String)=e.poll(id,RoomPoll(presence=RoomPresence(page=page,selected=listOf(item.id))))
        presence(a.peer);val f=presence(b.peer);assertEquals(a.peer,f.locks[RoomDocuments.key(page,item.id)]);assertTrue(f.presence.first{it.peer==b.peer}.selected.isEmpty())
        assertFalse(edit(e,b.peer){it.pages[0].items[0].x=1f}.receipts.single().accepted);clock+=5001;assertEquals(b.peer,presence(b.peer).locks[RoomDocuments.key(page,item.id)])}
    @Test fun idleHeartbeatsDoNotReviveAFadedGhost(){var now=1000L;val e=RoomEngine(Lesson(),host,"Room"){now};val p=guest(e);val position=RoomPresence(page=e.snapshot().pages[0].id,x=30f,y=50f)
        e.poll(p.peer,RoomPoll(presence=position));now+=61000;assertTrue(e.poll(p.peer,RoomPoll(presence=position)).presence.isEmpty());assertEquals(1,e.poll(p.peer,RoomPoll(presence=position.copy(x=31f))).presence.size)}
    @Test fun livePreviewDoesNotCommitInkToTheDocument(){val e=engine();val p=guest(e);val live=Item(w=1f,h=1f,points=mutableListOf(Point(0f,0f),Point(10f,10f)));val f=e.poll(p.peer,RoomPoll(presence=RoomPresence(page=e.snapshot().pages[0].id,live=listOf(live),active=true)))
        assertEquals(1,f.presence.single().live.size);assertTrue(e.snapshot().pages[0].items.isEmpty());assertEquals(0L,e.revision)}
    @Test fun viewersCannotDrawTakeLocksOrPublishLiveInk(){val item=Item(kind="shape");val e=engine(Lesson().apply{pages[0].items.add(item)});val p=guest(e,RoomRoles.VIEW)
        val f=e.poll(p.peer,RoomPoll(presence=RoomPresence(page=e.snapshot().pages[0].id,selected=listOf(item.id),live=listOf(Item(w=1f,h=1f)),active=true)));assertTrue(f.locks.isEmpty());assertTrue(f.presence.single().live.isEmpty());assertFalse(add(e,p.peer).receipts.single().accepted)}
    @Test fun downgradeReleasesLocksAndRevocationRejectsCredential(){val item=Item(kind="shape");val e=engine(Lesson().apply{pages[0].items.add(item)});val p=guest(e);e.poll(p.peer,RoomPoll(presence=RoomPresence(page=e.snapshot().pages[0].id,selected=listOf(item.id))));e.setRole(p.peer,RoomRoles.VIEW);assertTrue(e.frame(host.id,-1).locks.isEmpty());assertFalse(add(e,p.peer).receipts.single().accepted);e.remove(p.peer);assertFalse(e.authenticate(p.peer,p.credential))}
    @Test fun concurrentAppendOrderRemainsHostArrivalOrder(){val e=engine();val a=guest(e);val b=guest(e);val before=e.snapshot();val x=Item(kind="shape");val y=Item(kind="shape");val aa=before.copyDeep().apply{pages[0].items.add(x)};val bb=before.copyDeep().apply{pages[0].items.add(y)}
        e.poll(a.peer,RoomPoll(0,listOf(RoomDocuments.edit(before,aa,0)!!)));val frame=e.poll(b.peer,RoomPoll(0,listOf(RoomDocuments.edit(before,bb,0)!!)));assertTrue(frame.receipts.single().accepted);assertEquals(listOf(x.id,y.id),e.snapshot().pages[0].items.map{it.id});op(e,a.peer,"undo");assertEquals(listOf(y.id),e.snapshot().pages[0].items.map{it.id})}
    @Test fun undoRestoresDeletedObjectAtItsOriginalStackPosition(){val items=MutableList(3){Item(kind="shape")};val e=engine(Lesson().apply{pages[0].items.addAll(items)});val p=guest(e);edit(e,p.peer){it.pages[0].items.removeAt(1)};op(e,p.peer,"undo");assertEquals(items.map{it.id},e.snapshot().pages[0].items.map{it.id})}
    @Test fun explicitReorderIsUndoable(){val items=MutableList(3){Item(kind="shape")};val e=engine(Lesson().apply{pages[0].items.addAll(items)});val p=guest(e);edit(e,p.peer){it.pages[0].items.reverse()};assertEquals(items.reversed().map{it.id},e.snapshot().pages[0].items.map{it.id});op(e,p.peer,"undo");assertEquals(items.map{it.id},e.snapshot().pages[0].items.map{it.id})}
    @Test fun staleStructuralChangeIsRejected(){val e=engine();val p=guest(e);val before=e.snapshot();val after=before.copyDeep().apply{pages.add(Page())};add(e,host.id);val result=e.poll(p.peer,RoomPoll(0,listOf(RoomDocuments.edit(before,after,0)!!)));assertFalse(result.receipts.single().accepted);assertEquals(1,e.snapshot().pages.size)}
    @Test fun independentCameraAndPenChangesDoNotCreateDocumentOperations(){val before=Lesson();val after=before.copyDeep().apply{pages[0].panes[0].zoom=2f;pages[0].panes[0].tx=120f;pages[0].panes[0].color=TEAL;pages[0].panes[0].penWidth=12f};assertNull(RoomDocuments.edit(before,after,0))}
    @Test fun expiredJournalFallsBackToASnapshot(){val e=engine();repeat(260){e.poll(host.id,RoomPoll(e.revision,listOf(RoomOperation(kind="tools",tools=RoomTools(width=1f+it%20)))))};val result=e.frame(host.id,0);assertNotNull(result.lesson);assertTrue(result.deltas.isEmpty())}
    @Test fun invalidGeometryRejectsAtomically(){val e=engine();val p=guest(e);val result=add(e,p.peer,Item(kind="shape",x=Float.NaN));assertFalse(result.receipts.single().accepted);assertTrue(e.snapshot().pages[0].items.isEmpty());assertEquals(0L,e.revision)}
    @Test fun profilesUpdateInRoomAndLeavingMarksOffline(){val e=engine();val p=guest(e);val f=e.poll(p.peer,RoomPoll(profile=DeviceProfile(name="New name",color=TEAL,emoji="🌱")));assertEquals("New name",f.members.first{it.profile.id==p.peer}.profile.name);e.leave(p.peer);assertFalse(e.frame(host.id,-1).members.first{it.profile.id==p.peer}.online)}
    @Test fun endingRoomFreezesDocumentAndInvalidatesCredentials(){val e=engine();add(e,host.id);val p=guest(e);val final=e.snapshot();e.end();assertFalse(e.authenticate(p.peer,p.credential));try{add(e,host.id);fail("Closed room must reject edits")}catch(_:IllegalStateException){};assertEquals(final,e.snapshot())}
    @Test fun sharedHistoryDefaultsToFiftyAndCanBeTrimmed(){val e=engine();repeat(60){assertTrue(add(e,host.id).receipts.single().accepted)};assertEquals(50,e.frame(host.id,e.revision).undo);e.setHistoryLimit(10);assertEquals(10,e.frame(host.id,e.revision).undo);repeat(10){assertTrue(op(e,host.id,"undo").receipts.single().accepted)};assertEquals(50,e.snapshot().pages[0].items.size)}
    @Test fun sharedSplitSwitchPreservesBothCanvasesAndUndo(){val original=Item(kind="text",text="Solo");val e=engine(Lesson(pages=mutableListOf(Page(items=mutableListOf(original)))))
        assertTrue(edit(e,host.id){it.pages[0].switchPanels(3)}.receipts.single().accepted)
        assertTrue(edit(e,host.id){it.pages[0].items.add(Item(kind="text",text="Partner",pane=2));it.pages[0].panes[2].pattern="grid"}.receipts.single().accepted)
        assertTrue(edit(e,host.id){it.pages[0].switchPanels(1)}.receipts.single().accepted);assertEquals(listOf("Solo"),e.snapshot().pages[0].items.map{it.text})
        assertTrue(op(e,host.id,"undo").receipts.single().accepted);val split=e.snapshot().pages[0];assertEquals(3,split.visiblePaneCount);assertEquals("grid",split.pattern(2));assertEquals(2,split.items.size)
    }

}
