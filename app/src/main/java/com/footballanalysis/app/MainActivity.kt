package com.footballanalysis.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.view.*
import android.widget.*
import java.io.OutputStream
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var editor: EditorView
    private lateinit var status: TextView
    private var videoUri: Uri? = null
    private var videoDurationMs = 0L
    private var currentMs = 0L
    private lateinit var seek: SeekBar
    private var mode = Mode.ARROW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 18, 29)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(12, 23, 35)) }
        val title = TextView(this).apply { text = "FOOTBALL ANALYSIS  •  Protótipo 0.1"; textSize = 19f; setTextColor(Color.WHITE); setPadding(18, 22, 12, 18) }
        root.addView(title)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("Foto") { pick("image/*", 100) })
        actions.addView(button("Vídeo") { pick("video/*", 101) })
        actions.addView(button("Salvar PNG") { saveImage() })
        root.addView(actions)
        status = TextView(this).apply { text = "Abra uma foto ou vídeo para começar"; setTextColor(Color.LTGRAY); setPadding(14, 8, 8, 8) }
        root.addView(status)
        editor = EditorView(this)
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        seek = SeekBar(this).apply { max = 1000; isEnabled = false; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser && videoUri != null) { currentMs = videoDurationMs * progress / 1000; loadFrame() } }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }) }
        root.addView(seek)
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools.addView(button("Seta") { mode = Mode.ARROW; editor.mode = mode })
        tools.addView(button("Círculo") { mode = Mode.CIRCLE; editor.mode = mode })
        tools.addView(button("Linha") { mode = Mode.LINE; editor.mode = mode })
        tools.addView(button("Desfazer") { editor.undo() })
        root.addView(tools)
        val note = TextView(this).apply { text = "Edição no quadro congelado • IA e impedimento em desenvolvimento"; textSize = 11f; setTextColor(Color.LTGRAY); setPadding(14, 10, 8, 20) }
        root.addView(note)
        setContentView(root)
    }
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; setAllCaps(false); setOnClickListener { action() }; setOnLongClickListener {
            val explanations = mapOf(
                "Foto" to "Importa uma fotografia para análise.",
                "Vídeo" to "Importa um vídeo e permite selecionar um quadro.",
                "Salvar PNG" to "Salva o quadro atual com as marcações em uma imagem PNG.",
                "Seta" to "Desenha uma seta para indicar a direção da jogada.",
                "Círculo" to "Destaca uma área ou jogador com um círculo ou elipse.",
                "Linha" to "Desenha uma linha sobre o quadro.",
                "Desfazer" to "Remove a última marcação desenhada."
            )
            Toast.makeText(this@MainActivity, explanations[label] ?: label, Toast.LENGTH_LONG).show()
            true
        }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }
    private fun pick(type: String, code: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type = type }
        startActivityForResult(i, code)
    }
    @Deprecated("Legacy activity result used for minimal dependency-free starter")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == 100) {
            videoUri = null; seek.isEnabled = false; seek.progress = 0
            try { contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: throw IllegalArgumentException("Imagem inválida")
                editor.setImage(bmp); status.text = "Foto carregada"
            } } catch (e: Exception) { toast("Erro ao abrir imagem: ${e.message}") }
        } else if (requestCode == 101) {
            videoUri = uri
            try {
                val r = MediaMetadataRetriever(); r.setDataSource(this, uri)
                videoDurationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                r.release(); currentMs = 0; seek.progress = 0; seek.isEnabled = videoDurationMs > 0; loadFrame()
            } catch (e: Exception) { toast("Erro ao abrir vídeo: ${e.message}") }
        }
    }
    private fun loadFrame() {
        val uri = videoUri ?: return
        try {
            val r = MediaMetadataRetriever(); r.setDataSource(this, uri)
            val bmp = r.getFrameAtTime(currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            r.release()
            if (bmp != null) { editor.setImage(bmp); status.text = "Quadro congelado em %.2f s".format(currentMs / 1000.0) }
            else toast("Não foi possível extrair o quadro")
        } catch (e: Exception) { toast("Erro no quadro: ${e.message}") }
    }
    private fun saveImage() {
        val bmp = editor.exportBitmap() ?: return toast("Abra uma imagem primeiro")
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "analise_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FootballAnalysis")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw Exception("Não foi possível criar arquivo")
            contentResolver.openOutputStream(uri)?.use { if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) throw Exception("Falha ao salvar") }
                ?: throw Exception("Não foi possível gravar arquivo")
            toast("PNG salvo em Imagens/FootballAnalysis")
        } catch (e: Exception) { toast("Erro ao exportar: ${e.message}") }
        finally { bmp.recycle() }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

enum class Mode { ARROW, CIRCLE, LINE }
data class Stroke(val mode: Mode, val start: PointF, val end: PointF)

class EditorView(context: android.content.Context) : View(context) {
    var mode: Mode = Mode.ARROW
    private var source: Bitmap? = null
    private val strokes = mutableListOf<Stroke>()
    private var active: Stroke? = null
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 213, 67); strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    fun setImage(bmp: Bitmap) { source = bmp; strokes.clear(); active = null; invalidate() }
    fun undo() { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); invalidate() } }
    private fun bounds(): RectF {
        val b = source ?: return RectF()
        val scale = min(width.toFloat() / b.width, height.toFloat() / b.height)
        val w = b.width * scale; val h = b.height * scale
        return RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(18, 30, 42))
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
        if (s.mode == Mode.CIRCLE) { c.drawOval(min(x1,x2),min(y1,y2),max(x1,x2),max(y1,y2),pen); return }
        c.drawLine(x1,y1,x2,y2,pen)
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
            MotionEvent.ACTION_UP -> { active?.let { strokes.add(it.copy(end=p)) }; active=null; invalidate(); return true }
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
