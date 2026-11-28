package ru.argeda.powder

import android.graphics.Bitmap
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Наклонная камера над полем: смотрит в центр прямоугольника камеры игры (camX, camY, camZ). */
class Cam3D {
    var yaw = 0.0
    var pitch = 1.0
    var dist = 500.0
    var tx = 0.0
    var tz = 0.0
    var wPx = 1
    var hPx = 1
    var dens = 1f
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val vp = FloatArray(16)
    val inv = FloatArray(16)
    val lvp = FloatArray(16)
    private val tmp = FloatArray(16)
    private val v4 = FloatArray(4)
    private val r4 = FloatArray(4)

    fun update(G: Game, wPx: Int, hPx: Int, dens: Float) {
        this.wPx = maxOf(1, wPx); this.hPx = maxOf(1, hPx); this.dens = dens
        tx = G.camX + G.vw / G.camZ / 2
        tz = G.camY + G.vh / G.camZ / 2
        val zn = ((G.camZ - 0.45) / 1.55).coerceIn(0.0, 1.0)
        pitch = Math.toRadians(60.0 - 16.0 * zn)
        dist = G.vh / G.camZ * 1.32
        val cp = cos(pitch)
        val ex = tx + dist * cp * sin(yaw)
        val ey = dist * sin(pitch)
        val ez = tz + dist * cp * cos(yaw)
        Matrix.setLookAtM(view, 0, ex.toFloat(), ey.toFloat(), ez.toFloat(), tx.toFloat(), 0f, tz.toFloat(), 0f, 1f, 0f)
        Matrix.perspectiveM(proj, 0, FOV, this.wPx.toFloat() / this.hPx, (dist * 0.2).toFloat(), (dist * 4.5).toFloat())
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        Matrix.invertM(inv, 0, vp, 0)
        // свет сверху-слева-сзади: тени на юго-восток, как на старых картинках
        val R = (dist * 1.6).toFloat()
        val snap = 2.0 * R / SHADOW
        val cx = (Math.round(tx / snap) * snap).toFloat()
        val cz = (Math.round(tz / snap) * snap).toFloat()
        Matrix.setLookAtM(tmp, 0, cx + LX * 3000f, LY * 3000f, cz + LZ * 3000f, cx, 0f, cz, 0f, 1f, 0f)
        val lp = FloatArray(16)
        Matrix.orthoM(lp, 0, -R, R, -R, R, 1000f, 5000f)
        Matrix.multiplyMM(lvp, 0, lp, 0, tmp, 0)
    }

    /** мировая точка → экран в dp; out[2] = масштаб (dp на мировой пиксель). false — за камерой */
    fun project(x: Double, y: Double, z: Double, out: FloatArray): Boolean {
        v4[0] = x.toFloat(); v4[1] = y.toFloat() + Relief.at(x, z); v4[2] = z.toFloat(); v4[3] = 1f
        Matrix.multiplyMV(r4, 0, vp, 0, v4, 0)
        val w = r4[3]
        if (w <= 1e-3f) return false
        out[0] = (r4[0] / w + 1f) * 0.5f * wPx / dens
        out[1] = (1f - r4[1] / w) * 0.5f * hPx / dens
        out[2] = proj[5] * hPx * 0.5f / w / dens
        return true
    }

    /** экран (dp) → точка на земле; false — луч не попал в землю */
    fun ground(sx: Float, sy: Float, out: DoubleArray): Boolean {
        val nx = sx * dens / wPx * 2f - 1f
        val ny = 1f - sy * dens / hPx * 2f
        fun un(z: Float, o: DoubleArray) {
            v4[0] = nx; v4[1] = ny; v4[2] = z; v4[3] = 1f
            Matrix.multiplyMV(r4, 0, inv, 0, v4, 0)
            o[0] = (r4[0] / r4[3]).toDouble(); o[1] = (r4[1] / r4[3]).toDouble(); o[2] = (r4[2] / r4[3]).toDouble()
        }
        val a = DoubleArray(3)
        val b = DoubleArray(3)
        un(-1f, a); un(1f, b)
        val dy = b[1] - a[1]
        if (dy > -1e-6) return false
        // луч до холмов: несколько шагов к высоте под точкой пересечения
        var hy = 0.0
        for (k in 0 until 4) {
            val t = (hy - a[1]) / dy
            out[0] = a[0] + (b[0] - a[0]) * t
            out[1] = a[2] + (b[2] - a[2]) * t
            hy = Relief.at(out[0], out[1]).toDouble()
        }
        val t = (hy - a[1]) / dy
        out[0] = a[0] + (b[0] - a[0]) * t
        out[1] = a[2] + (b[2] - a[2]) * t
        return true
    }

    companion object {
        const val FOV = 40f
        const val SHADOW = 2048
        val LX: Float
        val LY: Float
        val LZ: Float

        init {
            val x = -0.52; val y = 0.80; val z = -0.30
            val l = Math.sqrt(x * x + y * y + z * z)
            LX = (x / l).toFloat(); LY = (y / l).toFloat(); LZ = (z / l).toFloat()
        }
    }
}

/** Один кадр для потока OpenGL: матрицы, экземпляры моделей, пятна на земле, видимые куски местности. */
class Frame3D {
    val vp = FloatArray(16)
    val lvp = FloatArray(16)
    var time = 0f
    var gen = 0
    var mapW = 1f
    var mapH = 1f
    var inst = FloatArray(4096)
    var instN = 0
    var bModel = IntArray(64)
    var bTeam = IntArray(64)
    var bStart = IntArray(64)
    var bCount = IntArray(64)
    var nb = 0
    var nbOpaque = 0
    var dec = FloatArray(1024)
    var decN = 0
    var chunks = IntArray(64 * 5)
    var nChunks = 0

    fun addBatch(model: Int, team: Int, start: Int, count: Int) {
        if (nb == bModel.size) {
            bModel = bModel.copyOf(nb * 2); bTeam = bTeam.copyOf(nb * 2); bStart = bStart.copyOf(nb * 2); bCount = bCount.copyOf(nb * 2)
        }
        bModel[nb] = model; bTeam[nb] = team; bStart[nb] = start; bCount[nb] = count; nb++
    }

    fun ensureInst(n: Int) { if (inst.size < n) inst = inst.copyOf(maxOf(n, inst.size * 2)) }
    fun ensureDec(n: Int) { if (dec.size < n) dec = dec.copyOf(maxOf(n, dec.size * 2)) }
    fun addChunk(key: Int, x0: Int, z0: Int, w: Int, h: Int) {
        if (nChunks * 5 + 5 > chunks.size) chunks = chunks.copyOf(chunks.size * 2)
        val o = nChunks * 5
        chunks[o] = key; chunks[o + 1] = x0; chunks[o + 2] = z0; chunks[o + 3] = w; chunks[o + 4] = h; nChunks++
    }
}

class ChunkUp(val gen: Int, val key: Int, val ver: Int, val bmp: Bitmap)
class MapUp(val gen: Int, val low: Bitmap, val water: ByteBuffer, val w: Int, val h: Int, val hgt: ByteBuffer)
class FogUp(val gen: Int, val px: ByteBuffer, val w: Int, val h: Int)

/** Отрисовка OpenGL ES 3: земля кусками, пятна на земле, модели экземплярами, тени, туман войны. */
class Renderer3D(val models: Array<Model3D>) : GLSurfaceView.Renderer {
    val free = ArrayBlockingQueue<Frame3D>(3).apply { repeat(3) { add(Frame3D()) } }
    val pending = AtomicReference<Frame3D?>(null)
    val chunkUps = ConcurrentLinkedQueue<ChunkUp>()
    val mapUp = AtomicReference<MapUp?>(null)
    val fogUp = AtomicReference<FogUp?>(null)
    /** растёт при каждом новом контексте OpenGL: всё загруженное надо прислать заново */
    val ctxGen = AtomicInteger(0)

    private var cur: Frame3D? = null
    private var vw = 1
    private var vh = 1

    private var pModel = 0
    private var pShadow = 0
    private var pGround = 0
    private var pDecal = 0
    private val uM = HashMap<String, Int>()
    private val uS = HashMap<String, Int>()
    private val uG = HashMap<String, Int>()
    private val uD = HashMap<String, Int>()

    private var quad = 0
    private var instVbo = 0
    private var decVbo = 0
    private var vaoGround = 0
    private var vaoDecal = 0
    private var shadowFbo = 0
    private var shadowTex = 0
    private var fogTex = 0
    private var waterTex = 0
    private var lowTex = 0
    private var hgtTex = 0
    private var detTex = 0
    /** серая бесшовная фактура травы; null — без неё */
    var detail: Bitmap? = null
    private var dummyFog = true
    private var aniso = 0f

    private class Tex(val id: Int, val ver: Int)
    private val chunkTex = HashMap<Int, Tex>()
    private var gen = -1
    private var instBuf: FloatBuffer = ByteBuffer.allocateDirect(4096 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var decBuf: FloatBuffer = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    /** если OpenGL на устройстве не справился: игра вернётся к 2D */
    @Volatile var failed = false
    var onFail: ((String) -> Unit)? = null

    private fun fail(e: Throwable) {
        if (failed) return
        failed = true
        android.util.Log.e("Powder3D", "3D failed", e)
        onFail?.invoke(e.message ?: e.toString())
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (failed) return
        try { create() } catch (e: Throwable) { fail(e) }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (failed) { glClearColor(0f, 0f, 0f, 1f); glClear(GL_COLOR_BUFFER_BIT); return }
        try { draw() } catch (e: Throwable) { fail(e) }
    }

    private fun create() {
        chunkTex.clear(); gen = -1
        pModel = prog(VS_MODEL, FS_MODEL, uM, "uVP", "uLVP", "uPos", "uRows", "uShadow", "uFog", "uLight", "uMap")
        pShadow = prog(VS_MODEL_SHADOW, FS_EMPTY, uS, "uLVP", "uPos", "uRows")
        pGround = prog(VS_GROUND, FS_GROUND, uG, "uVP", "uLVP", "uRect", "uUV", "uTex", "uShadow", "uFog", "uWater", "uMap", "uTime", "uHgt", "uAmp", "uLight", "uDet")
        pDecal = prog(VS_DECAL, FS_DECAL, uD, "uVP", "uFog", "uMap", "uHgt", "uAmp")
        val ext = glGetString(GL_EXTENSIONS) ?: ""
        if (ext.contains("GL_EXT_texture_filter_anisotropic")) {
            val f = FloatArray(1); glGetFloatv(0x84FF, f, 0); aniso = minOf(4f, f[0])
        }
        val ids = IntArray(4)
        glGenBuffers(3, ids, 0)
        quad = ids[0]; instVbo = ids[1]; decVbo = ids[2]
        val q = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        glBindBuffer(GL_ARRAY_BUFFER, quad)
        glBufferData(GL_ARRAY_BUFFER, q.size * 4, fbuf(q), GL_STATIC_DRAW)
        // сетка земли: кусок делится на GN×GN клеток, высоту берёт вершинный шейдер
        val gv = FloatArray((GN + 1) * (GN + 1) * 2)
        for (j in 0..GN) for (i in 0..GN) { val o = (j * (GN + 1) + i) * 2; gv[o] = i * 2f / GN - 1f; gv[o + 1] = j * 2f / GN - 1f }
        val gi = ShortArray(GN * GN * 6)
        var gk = 0
        for (j in 0 until GN) for (i in 0 until GN) {
            val a = j * (GN + 1) + i; val b = a + 1; val c = a + GN + 1; val d = c + 1
            gi[gk++] = a.toShort(); gi[gk++] = c.toShort(); gi[gk++] = b.toShort(); gi[gk++] = b.toShort(); gi[gk++] = c.toShort(); gi[gk++] = d.toShort()
        }
        glGenBuffers(2, ids, 0)
        val gridVbo = ids[0]; val gridIbo = ids[1]
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo)
        glBufferData(GL_ARRAY_BUFFER, gv.size * 4, fbuf(gv), GL_STATIC_DRAW)
        // VAO земли
        glGenVertexArrays(2, ids, 0)
        vaoGround = ids[0]; vaoDecal = ids[1]
        glBindVertexArray(vaoGround)
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gridIbo)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, gi.size * 2, ByteBuffer.allocateDirect(gi.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(gi); flip() }, GL_STATIC_DRAW)
        glBindVertexArray(vaoDecal)
        glBindBuffer(GL_ARRAY_BUFFER, quad)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0)
        for (l in 1..3) { glEnableVertexAttribArray(l); glVertexAttribDivisor(l, 1) }
        glBindVertexArray(0)
        // модели
        for (m in models) {
            glGenBuffers(1, ids, 0); m.ibo = ids[0]
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m.ibo)
            m.idx.position(0)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, m.tris * 3 * 2, m.idx, GL_STATIC_DRAW)
            for (t in m.colors.indices) {
                glGenBuffers(1, ids, 0); m.cvbo[t] = ids[0]
                glBindBuffer(GL_ARRAY_BUFFER, m.cvbo[t])
                m.colors[t].position(0)
                glBufferData(GL_ARRAY_BUFFER, m.n * 3, m.colors[t], GL_STATIC_DRAW)
            }
            for (t in 0 until 2) {
                if (t >= m.colors.size) { m.vao[t] = m.vao[0]; continue }
                glGenVertexArrays(1, ids, 0); m.vao[t] = ids[0]
                glBindVertexArray(m.vao[t])
                glBindBuffer(GL_ARRAY_BUFFER, m.cvbo[t])
                glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_UNSIGNED_BYTE, true, 3, 0)
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m.ibo)
                for (l in 1..3) { glEnableVertexAttribArray(l); glVertexAttribDivisor(l, 1) }
                glBindVertexArray(0)
            }
            glGenTextures(1, ids, 0); m.tex = ids[0]
            glBindTexture(GL_TEXTURE_2D, m.tex)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 2)
            m.pos.position(0)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16I, 1024, m.rows * m.frames, 0, GL_RGB_INTEGER, GL_SHORT, m.pos)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        }
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4)
        // карта теней
        glGenTextures(1, ids, 0); shadowTex = ids[0]
        glBindTexture(GL_TEXTURE_2D, shadowTex)
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_DEPTH_COMPONENT24, Cam3D.SHADOW, Cam3D.SHADOW)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL)
        glGenFramebuffers(1, ids, 0); shadowFbo = ids[0]
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowTex, 0)
        glDrawBuffers(1, intArrayOf(GL_NONE), 0)
        glReadBuffer(GL_NONE)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        // туман, вода, грубая подложка
        glGenTextures(4, ids, 0); fogTex = ids[0]; waterTex = ids[1]; lowTex = ids[2]; hgtTex = ids[3]
        for (t in intArrayOf(fogTex, waterTex, lowTex, hgtTex)) {
            glBindTexture(GL_TEXTURE_2D, t)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            if (t == hgtTex) px.put(byteArrayOf(0, 0, 0, 0)) else px.put(byteArrayOf(30, 24, 16, 0)); px.flip()
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, px)
        }
        // фактура травы: повтор, мипмапы
        glGenTextures(1, ids, 0); detTex = ids[0]
        glBindTexture(GL_TEXTURE_2D, detTex)
        val db = detail
        if (db != null && !db.isRecycled) GLUtils.texImage2D(GL_TEXTURE_2D, 0, db, 0)
        else {
            val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()); px.put(byteArrayOf(-128, -128, -128, -1)); px.flip()
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, px)
        }
        glGenerateMipmap(GL_TEXTURE_2D)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        dummyFog = true
        ctxGen.incrementAndGet()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { vw = w; vh = h }

    private fun draw() {
        val nf = pending.getAndSet(null)
        if (nf != null) { cur?.let { free.offer(it) }; cur = nf }
        val f = cur
        glClearColor(0.29f, 0.40f, 0.22f, 1f)
        if (f == null) { glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT); return }
        if (f.gen != gen) {
            for (t in chunkTex.values) glDeleteTextures(1, intArrayOf(t.id), 0)
            chunkTex.clear(); gen = f.gen
        }
        uploads(f.gen)
        // ---- тени
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo)
        glViewport(0, 0, Cam3D.SHADOW, Cam3D.SHADOW)
        glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL); glDepthMask(true)
        glDisable(GL_BLEND); glDisable(GL_CULL_FACE)
        glClear(GL_DEPTH_BUFFER_BIT)
        glEnable(GL_POLYGON_OFFSET_FILL); glPolygonOffset(2.5f, 6f)
        glUseProgram(pShadow)
        glUniformMatrix4fv(uS["uLVP"]!!, 1, false, f.lvp, 0)
        glUniform1i(uS["uPos"]!!, 0)
        uploadInst(f)
        for (b in 0 until f.nbOpaque) drawBatch(f, b, uS["uRows"]!!)
        glDisable(GL_POLYGON_OFFSET_FILL)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        // ---- кадр
        glViewport(0, 0, vw, vh)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, shadowTex)
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, fogTex)
        glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, waterTex)
        glActiveTexture(GL_TEXTURE4); glBindTexture(GL_TEXTURE_2D, hgtTex)
        glActiveTexture(GL_TEXTURE5); glBindTexture(GL_TEXTURE_2D, detTex)
        // земля
        glUseProgram(pGround)
        glUniformMatrix4fv(uG["uVP"]!!, 1, false, f.vp, 0)
        glUniformMatrix4fv(uG["uLVP"]!!, 1, false, f.lvp, 0)
        glUniform1i(uG["uTex"]!!, 0); glUniform1i(uG["uShadow"]!!, 1); glUniform1i(uG["uFog"]!!, 2); glUniform1i(uG["uWater"]!!, 3)
        glUniform2f(uG["uMap"]!!, f.mapW, f.mapH)
        glUniform1f(uG["uTime"]!!, f.time)
        glUniform1i(uG["uHgt"]!!, 4); glUniform1f(uG["uAmp"]!!, Relief.AMP); glUniform1i(uG["uDet"]!!, 5)
        glUniform3f(uG["uLight"]!!, Cam3D.LX, Cam3D.LY, Cam3D.LZ)
        glBindVertexArray(vaoGround)
        glActiveTexture(GL_TEXTURE0)
        val span = (CH * TILE).toFloat()
        for (i in 0 until f.nChunks) {
            val o = i * 5
            val x0 = f.chunks[o + 1].toFloat()
            val z0 = f.chunks[o + 2].toFloat()
            val w = f.chunks[o + 3].toFloat()
            val h = f.chunks[o + 4].toFloat()
            glUniform4f(uG["uRect"]!!, x0, z0, w, h)
            val t = chunkTex[f.chunks[o]]
            if (t != null) {
                glBindTexture(GL_TEXTURE_2D, t.id)
                glUniform4f(uG["uUV"]!!, 0f, 0f, w / span, h / span)
            } else {
                glBindTexture(GL_TEXTURE_2D, lowTex)
                glUniform4f(uG["uUV"]!!, x0 / f.mapW, z0 / f.mapH, w / f.mapW, h / f.mapH)
            }
            glDrawElements(GL_TRIANGLES, GN * GN * 6, GL_UNSIGNED_SHORT, 0)
        }
        // пятна на земле
        if (f.decN > 0) {
            glUseProgram(pDecal)
            glUniformMatrix4fv(uD["uVP"]!!, 1, false, f.vp, 0)
            glUniform1i(uD["uFog"]!!, 2)
            glUniform2f(uD["uMap"]!!, f.mapW, f.mapH)
            glUniform1i(uD["uHgt"]!!, 4); glUniform1f(uD["uAmp"]!!, Relief.AMP)
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glDepthMask(false); glDisable(GL_DEPTH_TEST)
            if (decBuf.capacity() < f.decN) decBuf = ByteBuffer.allocateDirect(f.dec.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            decBuf.clear(); decBuf.put(f.dec, 0, f.decN); decBuf.flip()
            glBindBuffer(GL_ARRAY_BUFFER, decVbo)
            glBufferData(GL_ARRAY_BUFFER, f.decN * 4, decBuf, GL_STREAM_DRAW)
            glBindVertexArray(vaoDecal)
            glVertexAttribPointer(1, 4, GL_FLOAT, false, 40, 0)
            glVertexAttribPointer(2, 4, GL_FLOAT, false, 40, 16)
            glVertexAttribPointer(3, 2, GL_FLOAT, false, 40, 32)
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, f.decN / 10)
            glDepthMask(true); glEnable(GL_DEPTH_TEST); glDisable(GL_BLEND)
        }
        // модели
        glUseProgram(pModel)
        glUniformMatrix4fv(uM["uVP"]!!, 1, false, f.vp, 0)
        glUniformMatrix4fv(uM["uLVP"]!!, 1, false, f.lvp, 0)
        glUniform1i(uM["uPos"]!!, 0); glUniform1i(uM["uShadow"]!!, 1); glUniform1i(uM["uFog"]!!, 2)
        glUniform3f(uM["uLight"]!!, Cam3D.LX, Cam3D.LY, Cam3D.LZ)
        glUniform2f(uM["uMap"]!!, f.mapW, f.mapH)
        glActiveTexture(GL_TEXTURE0)
        for (b in 0 until f.nbOpaque) drawBatch(f, b, uM["uRows"]!!)
        if (f.nb > f.nbOpaque) {
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            for (b in f.nbOpaque until f.nb) drawBatch(f, b, uM["uRows"]!!)
            glDisable(GL_BLEND)
        }
        glBindVertexArray(0)
        val err = glGetError()
        if (err != GL_NO_ERROR && !errLogged) { errLogged = true; android.util.Log.w("Powder3D", "GL error 0x" + Integer.toHexString(err)) }
    }
    private var errLogged = false

    private fun uploadInst(f: Frame3D) {
        if (instBuf.capacity() < f.instN) instBuf = ByteBuffer.allocateDirect(f.inst.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        instBuf.clear(); instBuf.put(f.inst, 0, f.instN); instBuf.flip()
        glBindBuffer(GL_ARRAY_BUFFER, instVbo)
        glBufferData(GL_ARRAY_BUFFER, maxOf(4, f.instN * 4), instBuf, GL_STREAM_DRAW)
    }

    private fun drawBatch(f: Frame3D, b: Int, uRows: Int) {
        val m = models[f.bModel[b]]
        glBindVertexArray(m.vao[f.bTeam[b].coerceIn(0, 1)])
        glBindBuffer(GL_ARRAY_BUFFER, instVbo)
        val off = f.bStart[b] * 4
        glVertexAttribPointer(1, 4, GL_FLOAT, false, INST * 4, off)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, INST * 4, off + 16)
        glVertexAttribPointer(3, 3, GL_FLOAT, false, INST * 4, off + 32)
        glBindTexture(GL_TEXTURE_2D, m.tex)
        glUniform1i(uRows, m.rows)
        glDrawElementsInstanced(GL_TRIANGLES, m.tris * 3, GL_UNSIGNED_SHORT, 0, f.bCount[b])
    }

    private fun uploads(g: Int) {
        mapUp.get()?.let { mu ->
            if (mu.gen == g) {
                mapUp.compareAndSet(mu, null)
                glBindTexture(GL_TEXTURE_2D, lowTex)
                GLUtils.texImage2D(GL_TEXTURE_2D, 0, mu.low, 0)
                glBindTexture(GL_TEXTURE_2D, waterTex)
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
                mu.water.position(0)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, mu.w, mu.h, 0, GL_RED, GL_UNSIGNED_BYTE, mu.water)
                glBindTexture(GL_TEXTURE_2D, hgtTex)
                mu.hgt.position(0)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, mu.w, mu.h, 0, GL_RED, GL_UNSIGNED_BYTE, mu.hgt)
                glPixelStorei(GL_UNPACK_ALIGNMENT, 4)
            }
        }
        fogUp.getAndSet(null)?.let { fu ->
            if (fu.gen == g) {
                glBindTexture(GL_TEXTURE_2D, fogTex)
                fu.px.position(0)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, fu.w, fu.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, fu.px)
                dummyFog = false
            }
        }
        var n = 0
        while (n < 3) {
            val c = chunkUps.poll() ?: break
            if (c.gen != g) { c.bmp.recycle(); continue }
            val old = chunkTex[c.key]
            if (old != null && old.ver >= c.ver) { c.bmp.recycle(); continue }
            val id = old?.id ?: IntArray(1).also { glGenTextures(1, it, 0) }[0]
            glBindTexture(GL_TEXTURE_2D, id)
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, c.bmp, 0)
            glGenerateMipmap(GL_TEXTURE_2D)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            if (aniso > 1f) glTexParameterf(GL_TEXTURE_2D, 0x84FE, aniso)
            chunkTex[c.key] = Tex(id, c.ver)
            c.bmp.recycle()
            n++
        }
    }

    // ------------------------------------------------------------ шейдеры
    private fun fbuf(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); flip() }

    private fun shader(type: Int, src: String): Int {
        val s = glCreateShader(type)
        glShaderSource(s, src); glCompileShader(s)
        val ok = IntArray(1); glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("shader: " + glGetShaderInfoLog(s))
        return s
    }

    private fun prog(vs: String, fs: String, u: HashMap<String, Int>, vararg names: String): Int {
        val p = glCreateProgram()
        glAttachShader(p, shader(GL_VERTEX_SHADER, vs)); glAttachShader(p, shader(GL_FRAGMENT_SHADER, fs))
        glLinkProgram(p)
        val ok = IntArray(1); glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("link: " + glGetProgramInfoLog(p))
        u.clear()
        for (n in names) u[n] = glGetUniformLocation(p, n)
        return p
    }

    companion object {
        /** чисел на один экземпляр модели */
        const val INST = 11
        /** клеток сетки на сторону куска земли */
        const val GN = 32
        private const val MODEL_COMMON = """#version 300 es
precision highp float;
precision highp int;
precision highp isampler2D;
layout(location=0) in vec3 aCol;
layout(location=1) in vec4 iA;
layout(location=2) in vec4 iB;
layout(location=3) in vec3 iC;
uniform isampler2D uPos;
uniform int uRows;
uniform mat4 uLVP;
vec3 P(int f) {
  int v = gl_VertexID;
  return vec3(texelFetch(uPos, ivec2(v & 1023, f * uRows + (v >> 10)), 0).xyz) * (1.0 / 32.0);
}
vec3 world() {
  vec3 p = mix(P(int(iB.x)), P(int(iB.y)), iB.z);
  p.xz *= iA.w; p.y *= iB.w;
  float c = cos(iA.z), s = sin(iA.z);
  return vec3(iA.x + p.x * c - p.z * s, p.y + iC.z, iA.y + p.x * s + p.z * c);
}
"""
        const val VS_MODEL = MODEL_COMMON + """
uniform mat4 uVP;
out vec3 vCol; out vec3 vW; out vec4 vL; flat out vec2 vC;
void main() {
  vec3 w = world();
  vW = w; vCol = aCol; vC = iC.xy; vL = uLVP * vec4(w, 1.0);
  gl_Position = uVP * vec4(w, 1.0);
}
"""
        const val VS_MODEL_SHADOW = MODEL_COMMON + """
void main() { gl_Position = uLVP * vec4(world(), 1.0); }
"""
        const val FS_EMPTY = """#version 300 es
precision mediump float;
void main() {}
"""
        private const val SHADOW_FN = """
float shade(vec4 l) {
  vec3 c = l.xyz / l.w * 0.5 + 0.5;
  if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
  float d = 1.0 / 2048.0;
  float s = texture(uShadow, vec3(c.xy + vec2(-d, -d), c.z)) + texture(uShadow, vec3(c.xy + vec2(d, -d), c.z))
          + texture(uShadow, vec3(c.xy + vec2(-d, d), c.z)) + texture(uShadow, vec3(c.xy + vec2(d, d), c.z));
  return s * 0.25;
}
"""
        const val FS_MODEL = """#version 300 es
precision highp float;
precision highp sampler2DShadow;
in vec3 vCol; in vec3 vW; in vec4 vL; flat in vec2 vC;
uniform sampler2DShadow uShadow;
uniform sampler2D uFog;
uniform vec3 uLight;
uniform vec2 uMap;
out vec4 o;
""" + SHADOW_FN + """
void main() {
  vec3 n = normalize(cross(dFdx(vW), dFdy(vW)));
  float d = max(dot(n, uLight), 0.0) * shade(vL);
  float hemi = 0.5 + 0.5 * n.y;
  vec3 col = vCol * (vec3(0.30, 0.31, 0.36) + vec3(0.26, 0.25, 0.22) * hemi + vec3(0.78, 0.72, 0.60) * d);
  if (vC.x > 0.5) {
    vec3 tc = vC.x < 1.5 ? vec3(0.45, 1.0, 0.45) : vC.x < 2.5 ? vec3(1.0, 0.35, 0.3) : vec3(1.0, 0.95, 0.8);
    col = mix(col, tc, 0.45);
  }
  col = mix(vec3(dot(col, vec3(0.299, 0.587, 0.114))), col, 1.12);   // чуть сочнее цвета
  vec4 f = texture(uFog, clamp(vW.xz / uMap, 0.0, 1.0));
  col = mix(col, f.rgb, f.a);
  // дымка над далёкими горами за краем карты
  float away = max(max(-vW.x, vW.x - uMap.x), max(-vW.z, vW.z - uMap.y));
  col = mix(col, vec3(0.50, 0.60, 0.52), smoothstep(120.0, 750.0, away) * 0.65);
  o = vec4(col, vC.y);
}
"""
        const val VS_GROUND = """#version 300 es
precision highp float;
layout(location=0) in vec2 aQ;
uniform mat4 uVP; uniform mat4 uLVP;
uniform vec4 uRect; uniform vec4 uUV;
uniform sampler2D uHgt; uniform float uAmp; uniform vec2 uMap; uniform vec3 uLight;
out vec3 vW; out vec4 vL; out vec2 vUV; out float vSun;
float H(vec2 p) { return textureLod(uHgt, p / uMap, 0.0).r * uAmp; }
void main() {
  vec2 q = aQ * 0.5 + 0.5;
  vec2 w = uRect.xy + q * uRect.zw;
  vW = vec3(w.x, H(w), w.y);
  // свет по склону: светлая сторона к солнцу, тёмная от него (поверх нарисованной светотени — мягко)
  float e = 24.0;
  vec3 n = normalize(vec3(H(w - vec2(e, 0.0)) - H(w + vec2(e, 0.0)), 2.0 * e, H(w - vec2(0.0, e)) - H(w + vec2(0.0, e))));
  float flat0 = uLight.y;
  vSun = clamp(1.0 + (dot(n, uLight) - flat0) * 1.25, 0.72, 1.22);
  vUV = uUV.xy + q * uUV.zw;
  vL = uLVP * vec4(vW, 1.0);
  gl_Position = uVP * vec4(vW, 1.0);
}
"""
        const val FS_GROUND = """#version 300 es
precision highp float;
precision highp sampler2DShadow;
in vec3 vW; in vec4 vL; in vec2 vUV; in float vSun;
uniform sampler2D uTex; uniform sampler2DShadow uShadow; uniform sampler2D uFog; uniform sampler2D uWater; uniform sampler2D uDet;
uniform vec2 uMap; uniform float uTime;
out vec4 o;
""" + SHADOW_FN + """
void main() {
  vec3 c = texture(uTex, vUV).rgb;
  vec2 m = vW.xz / uMap;
  float wt = texture(uWater, m).r;
  // травинки вблизи: только там, где земля зелёная
  float grass = smoothstep(0.015, 0.07, c.g - max(c.r, c.b * 1.1)) * (1.0 - smoothstep(0.3, 0.6, wt));
  float dt = texture(uDet, vW.xz / 80.0).r * 0.6 + texture(uDet, vW.xz / 23.0 + 0.37).r * 0.4;
  c *= 1.0 + (dt - 0.5) * 0.55 * grass;
  if (wt > 0.6) {
    float k = smoothstep(0.6, 1.0, wt);
    float a = sin(vW.x * 0.11 + uTime * 1.4 + sin(vW.z * 0.05) * 3.0) * sin(vW.z * 0.17 - uTime * 1.1);
    float b = sin(vW.x * 0.07 - vW.z * 0.09 + uTime * 0.8);
    c += vec3(0.75, 0.88, 1.0) * (smoothstep(0.82, 1.0, a) * 0.22 + b * 0.025) * k;
  }
  float s = shade(vL);
  c *= mix(0.58, 1.04, s) * vec3(1.02, 1.0, 0.96) * vSun;
  vec4 f = texture(uFog, m);
  o = vec4(mix(c, f.rgb, f.a), 1.0);
}
"""
        const val VS_DECAL = """#version 300 es
precision highp float;
layout(location=0) in vec2 aQ;
layout(location=1) in vec4 iA;
layout(location=2) in vec4 iB;
layout(location=3) in vec2 iC;
uniform mat4 uVP;
uniform sampler2D uHgt; uniform float uAmp; uniform vec2 uMap;
out vec2 vQ; out vec4 vCol; flat out float vK; out vec2 vW;
void main() {
  vQ = aQ; vCol = iB; vK = iC.x;
  float c = cos(iC.y), s = sin(iC.y);
  vec2 q = vec2(aQ.x * iA.z, aQ.y * iA.w);
  vec2 w = iA.xy + vec2(q.x * c - q.y * s, q.x * s + q.y * c);
  vW = w;
  float h = textureLod(uHgt, w / uMap, 0.0).r * uAmp;
  gl_Position = uVP * vec4(w.x, h + 0.6, w.y, 1.0);
}
"""
        const val FS_DECAL = """#version 300 es
precision highp float;
in vec2 vQ; in vec4 vCol; flat in float vK; in vec2 vW;
uniform sampler2D uFog; uniform vec2 uMap;
out vec4 o;
void main() {
  float r = length(vQ);
  float a;
  if (vK < 0.5) a = smoothstep(1.0, 0.55, r);
  else if (vK < 1.5) a = smoothstep(0.72, 0.84, r) * smoothstep(1.0, 0.9, r) + 0.18 * step(r, 0.9);
  else if (vK < 2.5) { float e = max(abs(vQ.x), abs(vQ.y)); a = 0.35 + 0.65 * step(0.9, e); }
  else if (vK > 3.5) a = smoothstep(0.93, 0.965, r) * smoothstep(1.0, 0.975, r) + 0.07 * step(r, 0.97);
  else a = step(r, 1.0);
  if (a <= 0.003) discard;
  vec4 f = texture(uFog, vW / uMap);
  o = vec4(mix(vCol.rgb, f.rgb, f.a), vCol.a * a * (1.0 - f.a * 0.85));
}
"""
    }
}
