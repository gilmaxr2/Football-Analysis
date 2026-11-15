package com.footballanalysis.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var editor: EditorView
    private lateinit var video: VideoView
    private lateinit var frameArea: FrameLayout
    private lateinit var status: TextView
    private lateinit var timeText: TextView
    private lateinit var seek: SeekBar
    private lateinit var play: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private var videoUri: Uri? = null
    private var durationMs = 0L
    private var positionMs = 0L
    private var frozen = false
    private var seeking = false
    private var frameRate = 30.0
    private var imageLoaded = false
    private val handler = Handler(Looper.getMainLooper())
    private val bg = Color.rgb(17, 27, 36)
    private val panel = Color.rgb(32, 49, 62)
    private val green = Color.rgb(106, 172, 201)
    private val ticker = object : Runnable {
        override fun run() {
            if (::video.isInitialized && videoUri != null && !frozen && !seeking) {
                positionMs = video.currentPosition.toLong().coerceAtLeast(0)
                refreshTime()
            }
            handler.postDelayed(this, 50L)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(panel) }
        header.addView(text("⚽  FOOTBALL ANALYSIS", 17f, green), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(action("Projeto") { toast("Projeto local: editor atual") })
        header.addView(action("Importar foto") { pick("image/*", 100) })
        header.addView(action("Importar vídeo") { pick("video/*", 101) })
        header.addView(action("Salvar PNG") { saveImage() })
        root.addView(header)
        val middle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(panel) }
        val tacticalButtons = mutableListOf<TacticalButton>()

        fun tool(name: String, mode: Mode) {
            val symbol = name.substringBefore(" ")

            lateinit var button: TacticalButton

            button = TacticalButton(
                this,
                symbol,
                name
            ) {
                editor.mode = mode
                tacticalButtons.forEach {
                    it.selectedTool = false
                }
                button.selectedTool = true
                status.text = "Ferramenta: $name"
            }

            tacticalButtons.add(button)

            tools.addView(
                button,
                LinearLayout.LayoutParams(
                    dp(36),
                    dp(36)
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(4)
                }
            )

            if (mode == Mode.ARROW) {
                button.selectedTool = true
            }
        }
            editor.mode = mode
            status.text = "Ferramenta: $name"
        }) }
        tool("↗ Seta", Mode.ARROW)
        tool("━ Linha", Mode.LINE)
        tool("◯ Círculo", Mode.CIRCLE)
        tool("┄ Pontilhada", Mode.DASHED)
        tool("▭ Retângulo", Mode.RECTANGLE)
        tool("▧ Zona", Mode.ZONE)
        tool("┃ Impedimento", Mode.OFFSIDE)
        tools.addView(action("ⓘ Ajuda") { toast("Selecione uma ferramenta e desenhe sobre o quadro congelado.") })
        middle.addView(tools, LinearLayout.LayoutParams(dp(48), -1))
        frameArea = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = VideoView(this).apply { visibility = View.GONE; setOnCompletionListener { play.text = "▶"; positionMs = durationMs; refreshTime() } }
        editor = EditorView(this)
        frameArea.addView(video, FrameLayout.LayoutParams(-1,-1))
        frameArea.addView(editor, FrameLayout.LayoutParams(-1,-1))
        middle.addView(frameArea, LinearLayout.LayoutParams(0,-1,1f))
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(panel) }
        right.addView(text("ANÁLISE TÁTICA", 13f, green))
        right.addView(action("Congelar quadro") { freeze() })
        right.addView(action("Retomar vídeo") { resumeVideo() })
        right.addView(action("Impedimento IA") { toast("IA ainda não implementada; não é VAR oficial.") })
        right.addView(action("Exportar MP4") { toast("Exportação MP4 ainda não implementada; use Salvar PNG para o quadro.") })
        right.addView(text("Marque o lance no quadro congelado.", 11f, Color.LTGRAY))
        middle.addView(right, LinearLayout.LayoutParams(dp(165), -1))
        root.addView(middle, LinearLayout.LayoutParams(-1,0,1f))
        val timeline = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(panel) }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        controls.addView(action("◀ Quadro") { stepFrame(-1) })
        play = action("▶") { togglePlayback() }
        controls.addView(play)
        controls.addView(action("Quadro ▶") { stepFrame(1) })
        controls.addView(action("❄ Congelar") { freeze() })
        timeText = text("00:00:00.000 / 00:00:00.000", 13f, Color.WHITE)
        controls.addView(timeText, LinearLayout.LayoutParams(0,-2,1f))
        undoButton = action("↶") { editor.undo() }
        redoButton = action("↷") { editor.redo() }
        controls.addView(undoButton); controls.addView(redoButton)
        editor.onHistoryChanged = { u,r -> undoButton.isEnabled = u; redoButton.isEnabled = r }
        undoButton.isEnabled = false; redoButton.isEnabled = false
        timeline.addView(controls)
        seek = SeekBar(this).apply {
            max = 1000
            isEnabled = false
            progressTintList = android.content.res.ColorStateList.valueOf(green)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && videoUri != null) {
                        positionMs = durationMs * progress / 1000L
                        refreshTime(updateSeek = false)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(s: SeekBar?) {
                    seeking = false
                    if (videoUri != null) {
                        video.seekTo(positionMs.toInt())
                        if (frozen) showFrame()
                    }
                }
            })
        }
        timeline.addView(seek)
        status = text("Importe um vídeo e pressione ▶ para reproduzir. Pause para marcar o lance.", 11f, Color.LTGRAY)
        timeline.addView(status)
        root.addView(timeline)
        setContentView(root)
        handler.post(ticker)
    }
    private fun dp(n:Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(s:String, size:Float, color:Int) = TextView(this).apply {
        text=s; textSize=size; setTextColor(color); setPadding(dp(9),dp(7),dp(9),dp(7)); gravity=Gravity.CENTER_VERTICAL
    }
    private fun action(s:String, f:()->Unit) = Button(this).apply {
        text=s; textSize=11f; isAllCaps=false
        minHeight=0; minimumHeight=0
        minWidth=0; minimumWidth=0
        setPadding(dp(3),dp(2),dp(3),dp(2))
        setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
        setOnClickListener { f() }
    }
    private fun pick(type:String, request:Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type=type }, request)
    }
    @Deprecated("Uses Android document picker")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode != RESULT_OK) return
        val uri=data?.data ?: return
        if(requestCode==100) {
            video.pause(); video.visibility=View.GONE; videoUri=null; frozen=true; seek.isEnabled=false
            try {
                val bmp=contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw Exception("Imagem inválida")
                editor.setImage(bmp); editor.visibility=View.VISIBLE; imageLoaded=true
                positionMs=0; durationMs=0; refreshTime(); play.text="▶"; status.text="Foto carregada"
            } catch(e:Exception) { toast("Erro: ${e.message}") }
        } else if(requestCode==101) {
            try {
                video.pause(); videoUri=uri; frozen=false; imageLoaded=false
                val r=MediaMetadataRetriever(); r.setDataSource(this,uri)
                durationMs=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                frameRate=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()?.takeIf { it>0 } ?: 30.0
                r.release()
                positionMs=0; seek.isEnabled=durationMs>0; editor.visibility=View.GONE
                video.visibility=View.VISIBLE; video.setVideoURI(uri)
                video.setOnPreparedListener { mp ->
                    mp.isLooping=false
                    durationMs=mp.duration.toLong().takeIf { it>0 } ?: durationMs
                    seek.isEnabled=durationMs>0
                    video.start(); play.text="Ⅱ"; refreshTime()
                }
                status.text="Vídeo carregado. Reproduzindo; pressione ❄ para congelar."
            } catch(e:Exception) { toast("Erro ao abrir vídeo: ${e.message}") }
        }
    }
    private fun togglePlayback() {
        if(videoUri==null) { toast("Importe um vídeo primeiro"); return }
        if(frozen) { resumeVideo(); return }
        if(video.isPlaying) { video.pause(); play.text="▶"; positionMs=video.currentPosition.toLong(); status.text="Pausado" }
        else { video.start(); play.text="Ⅱ"; status.text="Reproduzindo vídeo" }
        refreshTime()
    }
    private fun freeze() {
        if(videoUri==null) { toast("Importe um vídeo primeiro"); return }
        positionMs=video.currentPosition.toLong().coerceAtLeast(0)
        video.pause(); frozen=true; play.text="▶"; showFrame()
    }
    private fun showFrame() {
        val uri=videoUri ?: return
        try {
            val r=MediaMetadataRetriever(); r.setDataSource(this,uri)
            val bmp=r.getFrameAtTime(positionMs*1000,MediaMetadataRetriever.OPTION_CLOSEST)
            r.release()
            if(bmp!=null) {
                editor.setImage(bmp); imageLoaded=true; editor.visibility=View.VISIBLE
                video.visibility=View.GONE; status.text="Quadro congelado em ${fmt(positionMs)} — pronto para marcações"
            } else toast("Não foi possível extrair este quadro")
            refreshTime()
        } catch(e:Exception) { toast("Erro ao congelar: ${e.message}") }
    }
    private fun resumeVideo() {
        if(videoUri==null) return
        frozen=false; editor.visibility=View.GONE; video.visibility=View.VISIBLE
        video.seekTo(positionMs.toInt()); video.start(); play.text="Ⅱ"
        status.text="Reprodução retomada. Marcações do quadro não são aplicadas ao MP4."
    }
    private fun stepFrame(direction:Int) {
        if(videoUri==null) return
        if(!frozen) { video.pause(); frozen=true; play.text="▶" }
        val step=max(1L,(1000.0/frameRate).roundToLong())
        positionMs=(positionMs+direction*step).coerceIn(0L,durationMs)
        video.seekTo(positionMs.toInt()); showFrame()
    }
    private fun fmt(ms:Long):String {
        val t=ms.coerceAtLeast(0)
        return "%02d:%02d:%02d.%03d".format(t/3600000,(t/60000)%60,(t/1000)%60,t%1000)
    }
    private fun refreshTime(updateSeek:Boolean=true) {
        timeText.text="${fmt(positionMs)} / ${fmt(durationMs)}"
        if(updateSeek && !seeking) seek.progress=if(durationMs>0) ((positionMs*1000)/durationMs).toInt().coerceIn(0,1000) else 0
    }
    private fun saveImage() {
        val bmp=if(imageLoaded && editor.visibility==View.VISIBLE) editor.exportBitmap() else null
        if(bmp==null) { toast("Congele um quadro antes de salvar PNG"); return }
        try {
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"analise_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                if(Build.VERSION.SDK_INT>=29) put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FootballAnalysis")
            }
            val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: throw Exception("Não foi possível criar arquivo")
            contentResolver.openOutputStream(uri)?.use { if(!bmp.compress(Bitmap.CompressFormat.PNG,100,it)) throw Exception("Falha ao salvar") }
            toast("PNG salvo em Imagens/FootballAnalysis")
        } catch(e:Exception) { toast("Erro ao salvar: ${e.message}") } finally { bmp.recycle() }
    }
    private fun toast(s:String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onPause() { super.onPause(); if(::video.isInitialized && video.isPlaying) { video.pause(); if(::play.isInitialized) play.text="▶" } }
    override fun onDestroy() { handler.removeCallbacks(ticker); if(::video.isInitialized) video.stopPlayback(); super.onDestroy() }
}

enum class Mode { ARROW, CIRCLE, LINE, DASHED, RECTANGLE, ZONE, OFFSIDE }
data class Stroke(val mode: Mode, val start: PointF, val end: PointF)

class EditorView(context: android.content.Context) : View(context) {
    var mode: Mode = Mode.ARROW
    private var source: Bitmap? = null
    private val strokes = mutableListOf<Stroke>()
    private val redoStrokes = mutableListOf<Stroke>()
    var onHistoryChanged: ((Boolean, Boolean) -> Unit)? = null
    private fun notifyHistory() { onHistoryChanged?.invoke(strokes.isNotEmpty(), redoStrokes.isNotEmpty()) }
    private var active: Stroke? = null
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 213, 67); strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    fun setImage(bmp: Bitmap) { source = bmp; strokes.clear(); redoStrokes.clear(); active = null; invalidate(); notifyHistory() }
    fun undo() {
        if (strokes.isNotEmpty()) {
            redoStrokes.add(strokes.removeAt(strokes.lastIndex))
            invalidate()
            notifyHistory()
        }
    }
    fun redo() {
        if (redoStrokes.isNotEmpty()) {
            strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
            invalidate()
            notifyHistory()
        }
    }
    private fun bounds(): RectF {
        val b = source ?: return RectF()
        val scale = min(width.toFloat() / b.width, height.toFloat() / b.height)
        val w = b.width * scale; val h = b.height * scale
        return RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(11, 19, 17))
        val b = source ?: return
        val dst = bounds(); canvas.drawBitmap(b, null, dst, imagePaint)
        canvas.save(); canvas.clipRect(dst)
        val scale = dst.width()
        pen.strokeWidth = max(3f, scale * 0.006f)
        for (s in strokes) drawStroke(canvas, s, dst)
        active?.let { drawStroke(canvas, it, dst) }
        canvas.restore()
    }
    private fun drawStroke(c: Canvas, s: Stroke, dst: RectF) {
        val x1 = dst.left + s.start.x * dst.width(); val y1 = dst.top + s.start.y * dst.height()
        val x2 = dst.left + s.end.x * dst.width(); val y2 = dst.top + s.end.y * dst.height()
        if (s.mode == Mode.RECTANGLE || s.mode == Mode.ZONE) {
            val oldStyle = pen.style
            if (s.mode == Mode.ZONE) {
                pen.style = Paint.Style.FILL
                pen.alpha = 65
            }
            c.drawRect(min(x1,x2), min(y1,y2),
                max(x1,x2), max(y1,y2), pen)
            pen.style = oldStyle
            pen.alpha = 255
            return
        }
        if (s.mode == Mode.CIRCLE) { c.drawOval(min(x1,x2),min(y1,y2),max(x1,x2),max(y1,y2),pen); return }
        if (s.mode == Mode.DASHED)
            pen.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
        if (s.mode == Mode.OFFSIDE)
            c.drawLine(x1, dst.top, x1, dst.bottom, pen)
        else
            c.drawLine(x1,y1,x2,y2,pen)
        pen.pathEffect = null
        if (s.mode == Mode.ARROW) {
            val a = atan2(y2-y1,x2-x1); val len = max(14f, dst.width() * .025f)
            c.drawLine(x2,y2,x2-len*cos(a-.55f),y2-len*sin(a-.55f),pen)
            c.drawLine(x2,y2,x2-len*cos(a+.55f),y2-len*sin(a+.55f),pen)
        }
    }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (source == null) return false
        val b = bounds()
        val p = PointF(((event.x - b.left) / b.width()).coerceIn(0f,1f), ((event.y - b.top) / b.height()).coerceIn(0f,1f))
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { if (!b.contains(event.x,event.y)) return false; active = Stroke(mode,p,p); invalidate(); return true }
            MotionEvent.ACTION_MOVE -> { active = active?.copy(end=p); invalidate(); return true }
            MotionEvent.ACTION_UP -> { active?.let {
                strokes.add(it.copy(end=p))
                redoStrokes.clear()
            }
                active=null; invalidate(); notifyHistory(); return true }
            MotionEvent.ACTION_CANCEL -> { active=null; invalidate(); return true }
        }
        return true
    }
    fun exportBitmap(): Bitmap? {
        val b = source ?: return null
        val output = Bitmap.createBitmap(b.width,b.height,Bitmap.Config.ARGB_8888)
        val c = Canvas(output); c.drawBitmap(b,0f,0f,imagePaint)
        val dst = RectF(0f,0f,b.width.toFloat(),b.height.toFloat())
        pen.strokeWidth = max(3f,b.width*.006f)
        for (s in strokes) drawStroke(c,s,dst)
        return output
    }
}
