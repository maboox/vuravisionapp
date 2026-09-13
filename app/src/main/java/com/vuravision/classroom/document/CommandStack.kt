package com.vuravision.classroom.document

interface BoardCommand { fun apply(doc: BoardDocument); fun revert(doc: BoardDocument) }

class AddObjectCommand(private val obj: CanvasObject): BoardCommand {
    override fun apply(doc: BoardDocument) { doc.objects.add(obj) }
    override fun revert(doc: BoardDocument) { doc.objects.removeAll { it.id == obj.id } }
}
class RemoveObjectCommand(private val obj: CanvasObject): BoardCommand {
    private var index = -1
    override fun apply(doc: BoardDocument) { index = doc.objects.indexOfFirst { it.id==obj.id }; doc.objects.removeAll { it.id==obj.id } }
    override fun revert(doc: BoardDocument) { if(index<0 || index>doc.objects.size) doc.objects.add(obj) else doc.objects.add(index,obj) }
}
class CommandStack(private val document: BoardDocument) {
    private val undo = ArrayDeque<BoardCommand>(); private val redo = ArrayDeque<BoardCommand>()
    fun execute(c: BoardCommand) { c.apply(document); undo.addLast(c); redo.clear() }
    fun undo(): Boolean { val c=undo.removeLastOrNull()?:return false; c.revert(document); redo.addLast(c); return true }
    fun redo(): Boolean { val c=redo.removeLastOrNull()?:return false; c.apply(document); undo.addLast(c); return true }
    fun clear() { undo.clear(); redo.clear() }
}
