package com.vuravision.classroom

import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Serialize file operations across Activity recreation as well as within one screen. */
object ProjectIO { val executor=Executors.newSingleThreadExecutor() }

object AssetNames {
    private val allowed=Regex("[a-zA-Z0-9._-]{1,180}")
    fun valid(name:String)=name!="." && name!=".." && allowed.matches(name)
}

/** The same limits apply to saves and imports, so a saved project can be reopened. */
object ArchiveLimits {
    const val DOCUMENT=128L*1024*1024
    const val ASSET=64L*1024*1024
    const val TOTAL=512L*1024*1024
}

class LimitedOutputStream(private val output:OutputStream,private val limit:Long):OutputStream(){
    var count=0L;private set
    override fun write(value:Int){require(count<limit){"Document is too large"};output.write(value);count++}
    override fun write(bytes:ByteArray,offset:Int,length:Int){require(length.toLong()<=limit-count){"Document is too large"};output.write(bytes,offset,length);count+=length}
    override fun flush()=output.flush()
}

/** Keep the latest waiting snapshot of each project, without dropping saves of other projects. */
class ProjectSaveQueue(private val executor:Executor,private val write:(Lesson,File)->Unit){
    private data class Request(val ticket:Long,val doc:Lesson,val file:File,val completed:(Long,Exception?)->Unit)
    private val waiting=linkedMapOf<String,Request>()
    private var running=false
    private var ticket=0L
    @Synchronized fun submit(doc:Lesson,file:File,completed:(Long,Exception?)->Unit):Long{
        val next=++ticket
        waiting[file.absolutePath]=Request(next,doc,file,completed)
        if(!running){running=true;try{executor.execute(::drain)}catch(e:RuntimeException){running=false;waiting.clear();throw e}}
        return next
    }
    private fun drain(){
        while(true){
            val request=synchronized(this){
                val key=waiting.keys.firstOrNull()
                if(key==null){running=false;null}else waiting.remove(key)
            }?:return
            var failure:Exception?=null
            try{write(request.doc,request.file)}catch(e:Exception){failure=e}
            request.completed(request.ticket,failure)
        }
    }
}
