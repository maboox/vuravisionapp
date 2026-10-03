package com.vuravision.classroom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

interface VoiceAudioPort {
    fun start(input:(ByteArray,Int)->Unit,failure:()->Unit)
    fun enqueue(pcm:ByteArray)
    fun interrupt()
    fun close()
}

/** Two dedicated threads; neither recording nor blocking playback runs on the UI/socket thread. */
class VoiceAudio(private val context:Context):VoiceAudioPort {
    private data class Chunk(val generation:Int,val bytes:ByteArray)
    private val queue=LinkedBlockingQueue<Chunk>(96)
    private val queuedBytes=AtomicInteger()
    private val generation=AtomicInteger()
    @Volatile private var running=false
    private var recorder:AudioRecord?=null
    private var player:AudioTrack?=null
    private var echo:AcousticEchoCanceler?=null
    private var noise:NoiseSuppressor?=null
    private var recordThread:Thread?=null
    private var playThread:Thread?=null
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode=AudioManager.MODE_NORMAL
    private var previousSpeaker=false
    private var focus:AudioFocusRequest?=null
    private var audioConfigured=false
    private var failed:(()->Unit)?=null
    @Synchronized override fun start(input:(ByteArray,Int)->Unit,failure:()->Unit){
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw SecurityException("Microphone permission required")
        check(!running);failed=failure
        try {
            previousMode=manager.mode;previousSpeaker=manager.isSpeakerphoneOn;audioConfigured=true
            val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener{change->if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)failed?.invoke()}.build()
            check(manager.requestAudioFocus(focus!!)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            manager.mode=AudioManager.MODE_IN_COMMUNICATION
            manager.isSpeakerphoneOn=true
            val minIn=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            check(minIn>0)
            recorder=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minIn*2,5120)).build()
            check(recorder!!.state==AudioRecord.STATE_INITIALIZED)
            val minOut=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);check(minOut>0)
            player=AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minOut,4096)).build()
            check(player!!.state==AudioTrack.STATE_INITIALIZED)
            if(AcousticEchoCanceler.isAvailable())echo=AcousticEchoCanceler.create(recorder!!.audioSessionId)?.apply{enabled=true}
            if(NoiseSuppressor.isAvailable())noise=NoiseSuppressor.create(recorder!!.audioSessionId)?.apply{enabled=true}
            val mic=recorder!!;val speaker=player!!
            running=true;mic.startRecording();speaker.play()
            recordThread=Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                val buffer=ByteArray(1280) // 40 ms, PCM16 mono / 16 kHz.
                try{while(running){val n=mic.read(buffer,0,buffer.size,AudioRecord.READ_BLOCKING);if(n>0)input(buffer,n)else if(running)throw IllegalStateException("Audio input unavailable")}}
                catch(_:Exception){if(running)failure()}
            },"vura-voice-mic").apply{start()}
            playThread=Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                try{while(running){
                    val chunk=queue.take();queuedBytes.addAndGet(-chunk.bytes.size)
                    var offset=0
                    while(running && chunk.generation==generation.get() && offset<chunk.bytes.size){
                        val n=speaker.write(chunk.bytes,offset,minOf(4096,chunk.bytes.size-offset),AudioTrack.WRITE_BLOCKING)
                        if(n<=0){if(running && chunk.generation==generation.get())throw IllegalStateException("Audio output unavailable");break};offset+=n
                    }
                }}catch(_:InterruptedException){}catch(_:Exception){if(running)failure()}
            },"vura-voice-speaker").apply{start()}
        }catch(e:Exception){close();throw e}
    }
    override fun enqueue(pcm:ByteArray){
        if(!running || pcm.isEmpty())return
        if(queuedBytes.addAndGet(pcm.size)>2_000_000 || !queue.offer(Chunk(generation.get(),pcm))){queuedBytes.addAndGet(-pcm.size);failed?.invoke()}
    }
    @Synchronized override fun interrupt(){
        generation.incrementAndGet()
        // Drain rather than resetting the counter: a playback thread may already own a chunk.
        while(true){val chunk=queue.poll()?:break;queuedBytes.addAndGet(-chunk.bytes.size)}
        player?.let{try{it.pause();it.flush();if(running)it.play()}catch(_:Exception){}}
    }
    @Synchronized override fun close(){
        running=false;failed=null;generation.incrementAndGet()
        try{recorder?.stop()}catch(_:Exception){}
        try{player?.pause();player?.flush()}catch(_:Exception){}
        recordThread?.interrupt();playThread?.interrupt()
        echo?.release();noise?.release();echo=null;noise=null
        recorder?.release();player?.release();recorder=null;player=null
        while(true){val chunk=queue.poll()?:break;queuedBytes.addAndGet(-chunk.bytes.size)}
        focus?.let{manager.abandonAudioFocusRequest(it)};focus=null
        if(audioConfigured){manager.mode=previousMode;manager.isSpeakerphoneOn=previousSpeaker;audioConfigured=false}
    }
}
