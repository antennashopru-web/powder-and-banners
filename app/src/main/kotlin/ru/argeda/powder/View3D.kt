package ru.argeda.powder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.GLSurfaceView
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Настоящее 3D: поле боя в OpenGL ES 3 (Renderer3D) и прозрачный слой поверх него
 * для полосок здоровья, ядер, дыма и рамки выделения. Логику и мини-карту по-прежнему ведёт GameView.
 * Каждый кадр поток интерфейса собирает снимок мира (Frame3D) и отдаёт его потоку OpenGL.
 */
@SuppressLint("ViewConstructor")
class View3D(ctx: Context, val gv: GameView, models: Array<Model3D>) : FrameLayout(ctx) {
    val renderer = Renderer3D(models)
    private val glv = GLSurfaceView(ctx)
    private val ov = Overlay(ctx)
    val cam = Cam3D()
    private val dens = resources.displayMetrics.density

    /** высота модели (мировые пиксели) для полосок здоровья */
    private val modelTop = FloatArray(Models3D.COUNT) { i ->
        val m = models[i]
        var top = 0
        for (v in 0 until m.n) top = max(top, m.pos.get(v * 3 + 1).toInt())
        top / 32f * (if (i < Models3D.M_BLD) 1f else KY)
    }

    init {
        glv.setEGLContextClientVersion(3)
        glv.setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        glv.preserveEGLContextOnPause = true
        glv.setRenderer(renderer)
        glv.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        addView(glv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(ov, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onPause() = glv.onPause()
    fun onResume() = glv.onResume()

    // ------------------------------------------------------------ куски местности
    private var game: Game? = null
    private var gen = 0
    private var ctxSeen = -1
    private val cellVer = HashMap<Int, Int>()
    private val requested = HashMap<Int, Int>()
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "terrain3d").apply { priority = Thread.NORM_PRIORITY - 1 } }

    fun chunksDirty(tx: Int, ty: Int, w: Int, h: Int) {
        for (cy in Math.floorDiv(ty - 1, CH)..Math.floorDiv(ty + h, CH)) for (cx in Math.floorDiv(tx - 1, CH)..Math.floorDiv(tx + w, CH)) {
            val k = ck(cx, cy); cellVer[k] = (cellVer[k] ?: 0) + 1
            trees.remove(k)
        }
    }

    private fun ck(cx: Int, cy: Int) = cx * 1000 + cy

    private fun requestChunk(G: Game, cx: Int, cy: Int, key: Int, ver: Int) {
        requested[key] = ver
        val g = gen
        worker.execute {
            val bmp = try { buildChunk(G, cx, cy) } catch (e: Throwable) { null } ?: return@execute
            renderer.chunkUps.add(ChunkUp(g, key, ver, bmp))
            glv.requestRender()
        }
    }

    private fun buildChunk(G: Game, cx: Int, cy: Int): Bitmap {
        val S = CH * TILE
        val px = IntArray(S * S)
        val x0 = cx * CH
        val y0 = cy * CH
        Ground.paint(G, x0, y0, min(CH + 1, G.W - x0), min(CH + 1, G.H - y0), 1, px, S, 0, 0, S, S)
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, S, 0, 0, S, S)
        paintGrassDecor(G, Canvas(bmp), x0, y0, Paint(Paint.ANTI_ALIAS_FLAG))
        val out = bmp.copy(Bitmap.Config.RGB_565, false)
        bmp.recycle()
        return out
    }

    private fun sendMap(G: Game) {
        val low = Bitmap.createBitmap(G.W, G.H, Bitmap.Config.ARGB_8888)
        val water = ByteBuffer.allocateDirect(G.W * G.H)
        for (i in 0 until G.W * G.H) {
            val t = G.terrain[i].toInt()
            low.setPixel(i % G.W, i / G.W, when (t) {
                T_WATER -> Color.rgb(46, 92, 138); T_FOREST -> Color.rgb(46, 78, 34); T_SAND -> Color.rgb(190, 170, 118); else -> Color.rgb(96, 132, 62)
            })
            var open = t == T_WATER
            if (open) for (d in 0 until 4) {
                val x = i % G.W + intArrayOf(1, -1, 0, 0)[d]
                val y = i / G.W + intArrayOf(0, 0, 1, -1)[d]
                if (G.inB(x, y) && G.terrain[G.idx(x, y)].toInt() != T_WATER) open = false
            }
            water.put(if (open) 255.toByte() else 0)
        }
        water.flip()
        renderer.mapUp.set(MapUp(gen, low, water, G.W, G.H))
    }

    // ------------------------------------------------------------ деревья
    private class TreeSet(val data: FloatArray, val kinds: IntArray)
    private val trees = HashMap<Int, TreeSet>()

    private fun treesFor(G: Game, cx: Int, cy: Int): TreeSet {
        val key = ck(cx, cy)
        trees[key]?.let { return it }
        val d = ArrayList<Float>()
        val kinds = ArrayList<Int>()
        for (ty in cy * CH until min(G.H, cy * CH + CH)) for (tx in cx * CH until min(G.W, cx * CH + CH)) {
            if (G.terrain[G.idx(tx, ty)].toInt() != T_FOREST) continue
            val n = if (hash2(tx, ty, 11) < 0.45) 2 else 1
            val conifer = fbm(tx / 9.0, ty / 9.0, G.seed + 31) < 0.5
            for (k in 0 until n) {
                val ox = if (n == 1) 10 + hash2(tx, ty, 1) * 12 else 4 + k * 16 + hash2(tx, ty, k * 3 + 1) * 8
                val oy = 8 + k * 9 + hash2(tx, ty, k * 3 + 2) * 8
                kinds.add((if (conifer) 0 else 4) + (hash2(tx, ty, k * 3 + 3) * 4).toInt().coerceIn(0, 3))
                d.add((tx * TILE + ox).toFloat()); d.add((ty * TILE + oy).toFloat())
                d.add((hash2(tx, ty, k + 40) * PI * 2).toFloat()); d.add((0.9 + hash2(tx, ty, k + 50) * 0.25).toFloat())
            }
        }
        return TreeSet(d.toFloatArray(), kinds.toIntArray()).also { trees[key] = it }
    }

    // ------------------------------------------------------------ снимок кадра
    private class Bucket { var a = FloatArray(160); var n = 0
        fun add(x: Float, z: Float, ang: Float, sxz: Float, fa: Float, fb: Float, mix: Float, sy: Float, tint: Float, alpha: Float) {
            if (n + 10 > a.size) a = a.copyOf(a.size * 2)
            a[n] = x; a[n + 1] = z; a[n + 2] = ang; a[n + 3] = sxz; a[n + 4] = fa; a[n + 5] = fb; a[n + 6] = mix; a[n + 7] = sy; a[n + 8] = tint; a[n + 9] = alpha
            n += 10
        }
    }
    private val buckets = Array(Models3D.COUNT * 2) { Bucket() }
    private val ghost = Bucket()
    private var ghostModel = 0
    private var dec = FloatArray(1024)
    private var decN = 0
    private val faceAng = HashMap<Int, Float>()
    private var fogSent = -1
    private var lastT = System.nanoTime()
    private val box = DoubleArray(4)

    private fun decal(x: Double, z: Double, rx: Double, rz: Double, col: Int, a: Double, shape: Int, rot: Double = 0.0) {
        if (decN + 10 > dec.size) dec = dec.copyOf(dec.size * 2)
        dec[decN] = x.toFloat(); dec[decN + 1] = z.toFloat(); dec[decN + 2] = rx.toFloat(); dec[decN + 3] = rz.toFloat()
        dec[decN + 4] = Color.red(col) / 255f; dec[decN + 5] = Color.green(col) / 255f; dec[decN + 6] = Color.blue(col) / 255f; dec[decN + 7] = a.toFloat().coerceIn(0f, 1f)
        dec[decN + 8] = shape.toFloat(); dec[decN + 9] = rot.toFloat()
        decN += 10
    }

    /** вызывается каждый кадр из главного цикла */
    fun frame(G: Game) {
        val now = System.nanoTime()
        val dt = min(0.1, (now - lastT) / 1e9)
        lastT = now
        if (G !== game) {
            game = G; gen++; cellVer.clear(); requested.clear(); trees.clear(); faceAng.clear(); fogSent = -1
            sendMap(G)
        }
        val cg = renderer.ctxGen.get()
        if (cg != ctxSeen) { ctxSeen = cg; requested.clear(); fogSent = -1; sendMap(G) }
        if (width == 0) return
        cam.update(G, width, height, dens)
        // туман войны
        gv.syncFog(G)
        if (fogSent != G.fogVer) {
            fogSent = G.fogVer
            val src = gv.fogPixels()
            val bb = ByteBuffer.allocateDirect(src.size * 4).order(ByteOrder.nativeOrder())
            for (c in src) { bb.put((c shr 16).toByte()); bb.put((c shr 8).toByte()); bb.put(c.toByte()); bb.put((c ushr 24).toByte()) }
            bb.flip()
            renderer.fogUp.set(FogUp(gen, bb, G.W, G.H))
        }
        ov.invalidate()
        val f = renderer.free.poll() ?: run { glv.requestRender(); return }
        f.gen = gen; f.time = G.time.toFloat(); f.mapW = (G.W * TILE).toFloat(); f.mapH = (G.H * TILE).toFloat()
        cam.vp.copyInto(f.vp); cam.lvp.copyInto(f.lvp)
        // видимая область земли
        visibleBox(G)
        val MW = G.W * TILE.toDouble()
        val MH = G.H * TILE.toDouble()
        val S = CH * TILE
        f.nChunks = 0
        val cx0 = max(0, floor(box[0] / S).toInt())
        val cx1 = min(ceil(MW / S).toInt() - 1, floor(box[2] / S).toInt())
        val cy0 = max(0, floor(box[1] / S).toInt())
        val cy1 = min(ceil(MH / S).toInt() - 1, floor(box[3] / S).toInt())
        for (b in buckets) b.n = 0
        ghost.n = 0
        decN = 0
        for (cy in cy0..cy1) for (cx in cx0..cx1) {
            val key = ck(cx, cy)
            val ver = cellVer[key] ?: 0
            if (requested[key] != ver) requestChunk(G, cx, cy, key, ver)
            f.addChunk(key, cx * S, cy * S, min(S, (MW - cx * S).toInt()), min(S, (MH - cy * S).toInt()))
            val ts = treesFor(G, cx, cy)
            val d = ts.data
            for (i in ts.kinds.indices) {
                val o = i * 4
                buckets[(Models3D.M_TREE + ts.kinds[i]) * 2].add(d[o], d[o + 1], d[o + 2], d[o + 3], 0f, 0f, 0f, KY * d[o + 3], 0f, 1f)
            }
        }
        fun vis(x: Double, y: Double, m: Double) = x > box[0] - m && x < box[2] + m && y > box[1] - m && y < box[3] + m
        val t = G.time
        // пятна на земле: следы, руины, воронки, пни
        for (d in G.decals) {
            if (!vis(d.x, d.y, 80.0)) continue
            val a = min(1.0, (d.life - d.t) / 5)
            when (d.k) {
                D_CORPSE -> {
                    val fr = if (d.t < 0.3) Models3D.F_IDLE else Models3D.F_DEAD
                    val ang = (d.dir * PI / 4).toFloat()
                    buckets[(Models3D.M_UNIT + d.type) * 2 + d.team.coerceIn(0, 1)].add(d.x.toFloat(), d.y.toFloat(), ang, 1f, fr.toFloat(), fr.toFloat(), 0f, a.toFloat().coerceIn(0.05f, 1f), 0f, 1f)
                    decal(d.x, d.y, 9.0, 6.0, 0xff3a1a10.toInt(), 0.35 * a, 0)
                }
                D_RUIN -> {
                    val cx = d.x + d.w / 2
                    val cy = d.y + d.h * 0.55
                    decal(cx, cy, d.w * 0.6, d.h * 0.5, 0xff1c160f.toInt(), 0.7 * a, 0)
                    for (k in 0 until 12) {
                        val ang = hash2(k, d.x.toInt(), 1) * PI * 2
                        val rr = sqrt(hash2(k, d.y.toInt(), 2))
                        val g = (95 + hash2(k, 3, d.y.toInt()) * 60).toInt()
                        decal(cx + Math.cos(ang) * rr * d.w * 0.4, cy + Math.sin(ang) * rr * d.h * 0.35, 3.0 + hash2(k, 7, d.x.toInt()) * 4, 2.5 + hash2(k, 8, d.x.toInt()) * 3,
                            Color.rgb(g, g - 6, g - 14), a, 3, ang)
                    }
                }
                D_CRATER -> decal(d.x, d.y, 10.0, 8.0, 0xff281e14.toInt(), 0.55 * a, 0)
                D_STUMP -> { decal(d.x, d.y, 3.5, 3.5, 0xff6a4a2a.toInt(), 1.0, 3); decal(d.x, d.y, 2.4, 2.4, 0xffc8a070.toInt(), 1.0, 3) }
                D_PIT -> decal(d.x, d.y, 26.0, 18.0, 0xff463c32.toInt(), 0.5, 0)
            }
        }
        val sel = G.sel
        // залежи
        for (m in G.mines) {
            if (m.dead || !vis(m.x, m.y, 60.0) || G.explored[G.idx(m.tx, m.ty)].toInt() == 0) continue
            val s = (0.6 + 0.4 * sqrt(clamp(m.amount.toDouble() / m.max, 0.0, 1.0))).toFloat()
            buckets[(if (m.res == GOLD) Models3D.M_GOLD else Models3D.M_STONE) * 2].add((m.tx * TILE + 32).toFloat(), (m.ty * TILE + 32).toFloat(), 0f, s, 0f, 0f, 0f, KY * s, 0f, 1f)
            if (sel.contains(m)) decal(m.x, m.y, 36.0, 36.0, 0xffffe080.toInt(), 0.9, 1)
        }
        // здания
        for (b in G.blds) {
            if (!vis(b.x, b.y, 120.0) || !G.seen(b)) continue
            val d = BLD[b.type]
            val team = b.team.coerceIn(0, 1)
            if (isWall(b.type)) {
                // стена: столб и полупролёты к соседям, ворота вдоль или поперёк
                if (sel.contains(b)) decal(b.x, b.y, TILE * 0.62, TILE * 0.62, if (b.team == 0) 0xff7cff6a.toInt() else 0xffff6a5a.toInt(), 0.95, 1)
                val prog = if (b.built) 1f else (b.progress / d.time).toFloat().coerceIn(0f, 1f)
                val sy = KY * (0.15f + 0.85f * prog)
                if (!b.built) buckets[Models3D.M_SCAFFOLD * 2].add(b.x.toFloat(), b.y.toFloat(), 0f, 0.48f, 0f, 0f, 0f, KY * 0.6f, 0f, 1f)
                if (b.type == B_GATE) {
                    val ang = if (G.gateVertical(b)) (PI / 2).toFloat() else 0f
                    buckets[(Models3D.M_BLD + b.type) * 2 + team].add(b.x.toFloat(), b.y.toFloat(), ang, 1f, 0f, 0f, 0f, sy, 0f, 1f)
                } else {
                    buckets[(Models3D.M_BLD + b.type) * 2 + team].add(b.x.toFloat(), b.y.toFloat(), 0f, 1f, 0f, 0f, 0f, sy, 0f, 1f)
                    val m = G.wallLinks(b)
                    for (k in 0 until 4) if (m and (1 shl k) != 0)
                        buckets[Models3D.M_WALLSEG * 2 + team].add(b.x.toFloat(), b.y.toFloat(), (k * PI / 2).toFloat(), 1f, 0f, 0f, 0f, sy, 0f, 1f)
                }
                continue
            }
            decal(b.x, b.y + 4, d.w * TILE * 0.62, d.h * TILE * 0.58, 0xff78603a.toInt(), 0.35, 0)
            if (sel.contains(b)) decal(b.x, b.y, d.w * TILE * 0.62, d.h * TILE * 0.62, if (b.team == 0) 0xff7cff6a.toInt() else 0xffff6a5a.toInt(), 0.95, 1)
            val prog = if (b.built) 1f else (b.progress / d.time).toFloat().coerceIn(0f, 1f)
            if (!b.built) {
                buckets[Models3D.M_SCAFFOLD * 2].add(b.x.toFloat(), b.y.toFloat(), 0f, d.w * TILE / 64f * 0.98f, 0f, 0f, 0f, KY * (0.35f + 0.65f * min(1f, prog * 1.6f)), 0f, 1f)
                if (prog > 0.05f) buckets[(Models3D.M_BLD + b.type) * 2 + team].add(b.x.toFloat(), b.y.toFloat(), 0f, 0.96f, 0f, 0f, 0f, KY * prog, 0f, 1f)
            } else buckets[(Models3D.M_BLD + b.type) * 2 + team].add(b.x.toFloat(), b.y.toFloat(), 0f, 1f, 0f, 0f, 0f, KY, 0f, 1f)
        }
        // отряды
        for (u in G.units) {
            if (!vis(u.x, u.y, 40.0) || !G.seen(u)) continue
            if (sel.contains(u)) {
                val col = if (u.team == 0) 0xff7cff6a.toInt() else 0xffff6a5a.toInt()
                decal(u.x, u.y, u.r + 4, u.r + 4, col, 0.95, 1)
            }
            val target = (u.dir * PI / 4).toFloat()
            var ang = faceAng[u.id] ?: target
            var diff = target - ang
            while (diff > PI) diff -= (2 * PI).toFloat()
            while (diff < -PI) diff += (2 * PI).toFloat()
            ang += diff * min(1f, (dt * 10).toFloat())
            faceAng[u.id] = ang
            var fa: Int
            var fb: Int
            var mix = 0f
            if (UNIT[u.type].fly && u.hitT <= 0) {
                // крылья машут всегда, и в полёте, и на месте
                val ph = u.anim * (if (u.moving) 10 else 7)
                val i = floor(ph).toInt()
                fa = Models3D.F_WALK + Math.floorMod(i, 8); fb = Models3D.F_WALK + Math.floorMod(i + 1, 8); mix = (ph - i).toFloat()
            } else if (u.working) {
                val k = floor(u.anim * 5).toInt() % 4
                fa = Models3D.F_ACT + (if (k == 3) 1 else k); fb = fa
            } else if (u.aimT > 0) {
                fa = if (u.hitT > 0) Models3D.F_ACT + (if (u.hitT > 0.17) 0 else if (u.hitT > 0.08) 1 else 2)
                else if (isGun(u.type) && UNIT[u.type].cd - u.cd < 0.25) Models3D.F_ACT + 1
                else Models3D.F_ACT
                fb = fa
            } else if (u.moving) {
                val ph = u.anim * (if (u.type == U_CAV || u.type == U_DRAGOON) 18 else 14)
                val i = floor(ph).toInt()
                fa = Models3D.F_WALK + Math.floorMod(i, 8); fb = Models3D.F_WALK + Math.floorMod(i + 1, 8); mix = (ph - i).toFloat()
            } else { fa = Models3D.F_IDLE; fb = fa }
            buckets[(Models3D.M_UNIT + u.type) * 2 + u.team.coerceIn(0, 1)].add(u.x.toFloat(), u.y.toFloat(), ang, 1f, fa.toFloat(), fb.toFloat(), mix, 1f, 0f, 1f)
        }
        // приказы и точки сбора
        for (m in G.markers) {
            val k = m.t / 0.8
            decal(m.x, m.y, 5 + k * 14, 5 + k * 14, m.col, 1 - k, 1)
        }
        G.ping?.let { pg -> val k = 1 - (pg.t % 1); decal(pg.x, pg.y, 24 + k * 50, 24 + k * 50, 0xffff3c28.toInt(), 1 - k, 1) }
        val s0 = if (sel.size == 1) sel[0] else null
        if (s0 is Bld) s0.rally?.let { r -> decal(r.x, r.y, 9.0, 9.0, 0xffffe68c.toInt(), 0.8, 1) }
        // призрак здания
        G.placing?.let { p ->
            if (p.type == B_WALL) {
                ghostModel = Models3D.M_BLD + B_WALL
                for (k in p.line.indices) {
                    val i = p.line[k]
                    val cx = (i % G.W + 0.5) * TILE
                    val cy = (i / G.W + 0.5) * TILE
                    decal(cx, cy, TILE / 2.0, TILE / 2.0, if (p.oks[k]) 0xff50ff50.toInt() else 0xffff3c28.toInt(), 0.6, 2)
                    if (p.oks[k]) ghost.add(cx.toFloat(), cy.toFloat(), 0f, 1f, 0f, 0f, 0f, KY, 1f, 0.6f)
                }
                return@let
            }
            val d = BLD[p.type]
            val x0 = p.tx * TILE.toDouble()
            val y0 = p.ty * TILE.toDouble()
            decal(x0 + d.w * TILE / 2.0, y0 + d.h * TILE / 2.0, d.w * TILE / 2.0, d.h * TILE / 2.0, if (p.ok) 0xff50ff50.toInt() else 0xffff3c28.toInt(), 0.6, 2)
            ghostModel = Models3D.M_BLD + p.type
            val gAng = if (p.type == B_GATE && G.gateVerticalAt(p.tx, p.ty, 0)) (PI / 2).toFloat() else 0f
            ghost.add((x0 + d.w * TILE / 2.0).toFloat(), (y0 + d.h * TILE / 2.0).toFloat(), gAng, 1f, 0f, 0f, 0f, KY, if (p.ok) 1f else 2f, 0.6f)
        }
        // упаковка
        var total = 0
        for (b in buckets) total += b.n
        f.ensureInst(total + ghost.n)
        f.instN = 0; f.nb = 0
        for (i in buckets.indices) {
            val b = buckets[i]
            if (b.n == 0) continue
            b.a.copyInto(f.inst, f.instN, 0, b.n)
            f.addBatch(i / 2, i % 2, f.instN, b.n / 10)
            f.instN += b.n
        }
        f.nbOpaque = f.nb
        if (ghost.n > 0) {
            ghost.a.copyInto(f.inst, f.instN, 0, ghost.n)
            f.addBatch(ghostModel, 0, f.instN, ghost.n / 10)
            f.instN += ghost.n
        }
        f.ensureDec(decN)
        dec.copyInto(f.dec, 0, 0, decN); f.decN = decN
        renderer.pending.getAndSet(f)?.let { renderer.free.offer(it) }
        glv.requestRender()
    }

    /** прямоугольник земли, который видит камера (с запасом) */
    private fun visibleBox(G: Game) {
        val w = width / dens
        val h = height / dens
        val p = DoubleArray(2)
        var x0 = 1e9; var y0 = 1e9; var x1 = -1e9; var y1 = -1e9
        for ((sx, sy) in arrayOf(0f to 0f, w to 0f, 0f to h, w to h, w / 2 to 0f)) {
            if (cam.ground(sx, sy, p)) { x0 = min(x0, p[0]); y0 = min(y0, p[1]); x1 = max(x1, p[0]); y1 = max(y1, p[1]) }
        }
        if (x0 > x1) { x0 = cam.tx - 800; x1 = cam.tx + 800; y0 = cam.tz - 800; y1 = cam.tz + 800 }
        box[0] = x0 - 64; box[1] = y0 - 64; box[2] = x1 + 64; box[3] = y1 + 96
    }

    // ------------------------------------------------------------ выбор пальцем
    private val pr = FloatArray(3)

    private fun unitHeight(u: Unt) = modelTop[Models3D.M_UNIT + u.type]

    /** что под пальцем: отряд или здание по экрану, затем залежь или лес по точке на земле */
    fun pick(G: Game, sx: Float, sy: Float, wx: Double, wy: Double): Ent? {
        var best: Ent? = null
        var bd = 22f
        for (u in G.units) {
            if (u.dead || !G.seen(u)) continue
            if (!cam.project(u.x, unitHeight(u) * 0.5, u.y, pr)) continue
            val d = hyp((pr[0] - sx).toDouble(), (pr[1] - sy).toDouble()).toFloat() / max(0.6f, min(1.6f, pr[2]))
            if (d < bd) { bd = d; best = u }
        }
        // здание под пальцем важнее отряда рядом с ним: отряд берётся, только если палец прямо на нём
        if (best != null && bd < 9f) return best
        for (b in G.blds) {
            if (b.dead || !G.seen(b)) continue
            val top = modelTop[Models3D.M_BLD + b.type].toDouble()
            var l = 1e9f; var r = -1e9f; var t = 1e9f; var bt = -1e9f
            for (cx in 0..1) for (cz in 0..1) for (cy in 0..1) {
                if (!cam.project((b.tx + cx * b.w) * TILE.toDouble(), cy * top * 0.8, (b.ty + cz * b.h) * TILE.toDouble(), pr)) continue
                l = min(l, pr[0]); r = max(r, pr[0]); t = min(t, pr[1]); bt = max(bt, pr[1])
            }
            val ins = (r - l) * 0.12f
            if (sx >= l + ins && sx <= r - ins && sy >= t && sy <= bt) return b
        }
        if (best != null) return best
        for (m in G.mines) {
            if (m.dead) continue
            if (wx >= m.tx * TILE - 6 && wx <= (m.tx + 2) * TILE + 6 && wy >= m.ty * TILE - 6 && wy <= (m.ty + 2) * TILE + 6)
                return if (G.explored[G.idx(m.tx, m.ty)].toInt() != 0) m else null
        }
        val tx = floor(wx / TILE).toInt()
        val ty = floor(wy / TILE).toInt()
        if (G.inB(tx, ty) && G.terrain[G.idx(tx, ty)].toInt() == T_FOREST && G.explored[G.idx(tx, ty)].toInt() != 0) {
            val t = TreeRef(G.idx(tx, ty))
            t.tx = tx; t.ty = ty; t.x = (tx + 0.5) * TILE; t.y = (ty + 0.5) * TILE
            return t
        }
        return null
    }

    // ------------------------------------------------------------ жесты
    private class Gest(var mode: Int, val sx: Float, val sy: Float) {
        var lx = sx
        var ly = sy
        var moved = false
        var d0 = 0f
        var z0 = 1.0
        var a0 = 0.0
        var yaw0 = 0.0
        var twist = false
        var mx = 0f
        var my = 0f
        var wx = 0.0
        var wy = 0.0
    }

    private var gest: Gest? = null
    private val longPress = Runnable {
        val g = gest
        if (g != null && g.mode == M_PEND) { g.mode = M_BOX; g.moved = true; performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    }
    var onTouchStart: (() -> Unit)? = null
    var onBoxDone: (() -> Unit)? = null
    private val gp = DoubleArray(2)
    private val gq = DoubleArray(2)

    /** сдвинуть камеру так, чтобы точка земли под (ax, ay) оказалась под (bx, by) */
    private fun drag(G: Game, ax: Float, ay: Float, bx: Float, by: Float) {
        if (!cam.ground(ax, ay, gp) || !cam.ground(bx, by, gq)) return
        G.camX += gp[0] - gq[0]; G.camY += gp[1] - gq[1]; G.clampCam()
        cam.update(G, width, height, dens)
    }

    private fun groundAt(G: Game, sx: Float, sy: Float): DoubleArray? {
        cam.update(G, width, height, dens)
        val p = DoubleArray(2)
        return if (cam.ground(sx, sy, p)) p else null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val G = game ?: return true
        if (G.over) return true
        val d = dens
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTouchStart?.invoke()
                val g = Gest(if (G.boxMode && G.placing == null) M_BOX else M_PEND, e.x / d, e.y / d)
                gest = g
                // стена: палец тянет её от точки касания
                if (G.placing?.type == B_WALL) groundAt(G, g.sx, g.sy)?.let { w -> g.mode = M_WALL; g.wx = w[0]; g.wy = w[1]; G.setWallLine(w[0], w[1], w[0], w[1]) }
                if (g.mode == M_PEND && G.placing == null) postDelayed(longPress, 400)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2) {
                    removeCallbacks(longPress)
                    val ax = e.getX(0) / d; val ay = e.getY(0) / d; val bx = e.getX(1) / d; val by = e.getY(1) / d
                    val g = Gest(M_PINCH, ax, ay)
                    g.d0 = max(1f, hyp((ax - bx).toDouble(), (ay - by).toDouble()).toFloat()); g.z0 = G.camZ
                    g.a0 = atan2((by - ay).toDouble(), (bx - ax).toDouble()); g.yaw0 = cam.yaw
                    g.mx = (ax + bx) / 2; g.my = (ay + by) / 2
                    gest = g
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val g = gest ?: return true
                if (g.mode == M_PINCH) {
                    if (e.pointerCount < 2) return true
                    val ax = e.getX(0) / d; val ay = e.getY(0) / d; val bx = e.getX(1) / d; val by = e.getY(1) / d
                    val dd = hyp((ax - bx).toDouble(), (ay - by).toDouble()).toFloat()
                    val mx = (ax + bx) / 2
                    val my = (ay + by) / 2
                    drag(G, g.mx, g.my, mx, my)
                    // приближение вокруг центра экрана
                    val cxw = G.camX + G.vw / G.camZ / 2
                    val cyw = G.camY + G.vh / G.camZ / 2
                    G.camZ = clamp(g.z0 * dd / g.d0, 0.45, 2.0)
                    G.camX = cxw - G.vw / G.camZ / 2; G.camY = cyw - G.vh / G.camZ / 2
                    // поворот двумя пальцами
                    var da = atan2((by - ay).toDouble(), (bx - ax).toDouble()) - g.a0
                    while (da > PI) da -= 2 * PI
                    while (da < -PI) da += 2 * PI
                    if (!g.twist && abs(da) > 0.12) { g.twist = true; g.a0 += da; da = 0.0 }
                    if (g.twist) cam.yaw = g.yaw0 + da
                    G.clampCam()
                    g.mx = mx; g.my = my
                    return true
                }
                if (g.mode == M_DEAD) return true
                val x = e.getX(0) / d
                val y = e.getY(0) / d
                if (g.mode == M_WALL) { groundAt(G, x, y)?.let { w -> G.setWallLine(g.wx, g.wy, w[0], w[1]) }; return true }
                val far = hyp((x - g.sx).toDouble(), (y - g.sy).toDouble()) > 9
                if (g.mode == M_PEND && far) { g.mode = M_PAN; removeCallbacks(longPress) }
                if (g.mode == M_PAN) drag(G, g.lx, g.ly, x, y)
                if (g.mode == M_BOX && far) g.moved = true
                g.lx = x; g.ly = y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val g = gest ?: return true
                removeCallbacks(longPress)
                if (g.mode == M_PINCH) g.mode = M_DEAD
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val g = gest ?: return true
                gest = null
                if (g.mode == M_PEND || (g.mode == M_BOX && !g.moved)) tap(G, g.sx, g.sy)
                else if (g.mode == M_BOX) {
                    cam.update(G, width, height, dens)
                    val l = min(g.sx, g.lx); val r = max(g.sx, g.lx); val t = min(g.sy, g.ly); val b = max(g.sy, g.ly)
                    val list = G.units.filter {
                        it.team == 0 && !it.dead && cam.project(it.x, unitHeight(it) * 0.4, it.y, pr) && pr[0] >= l - 6 && pr[0] <= r + 6 && pr[1] >= t - 6 && pr[1] <= b + 6
                    }
                    G.selectUnits(list)
                    if (G.boxMode) { G.boxMode = false; G.uiDirty = true }
                    onBoxDone?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); gest = null }
        }
        return true
    }

    private fun tap(G: Game, sx: Float, sy: Float) {
        val p = groundAt(G, sx, sy) ?: return
        G.tapAt(p[0], p[1], if (G.placing != null) null else pick(G, sx, sy, p[0], p[1]))
    }

    /** вернуть камеру на север */
    fun resetYaw() { cam.yaw = 0.0 }

    // ------------------------------------------------------------ верхний слой
    private inner class Overlay(ctx: Context) : View(ctx) {
        private val pf = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ps = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rf = RectF()
        private val path = Path()
        private val dash = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        private val q = FloatArray(3)
        private val q2 = FloatArray(3)
        private var vig: Paint? = null

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { vig = null }

        override fun onDraw(c: Canvas) {
            val G = game ?: return
            if (width == 0) return
            c.save()
            c.scale(dens, dens)
            val t = G.time
            val sel = G.sel
            // огонь на повреждённых зданиях
            for (b in G.blds) {
                if (!b.built || isWall(b.type) || b.hp >= b.maxhp * 0.55 || !G.seen(b)) continue
                val n = if (b.hp < b.maxhp * 0.3) 4 else 2
                for (i in 0 until n) {
                    val fx = b.tx * TILE + b.w * TILE * (0.25 + 0.5 * hash2(b.id, i, 3))
                    val fz = b.ty * TILE + b.h * TILE * (0.3 + 0.5 * hash2(b.id, i, 4))
                    if (!cam.project(fx, modelTop[Models3D.M_BLD + b.type] * 0.55, fz, q)) continue
                    val s = q[2]
                    val fl = (sin(t * 13 + i * 2) * 2).toFloat() * s
                    val hg = (14 + sin(t * 9 + i) * 3).toFloat() * s
                    val x = q[0]; val y = q[1]
                    pf.color = rgba(255, 120, 30, .85)
                    path.reset(); path.moveTo(x - 6 * s, y); path.quadTo(x - 4 * s + fl, y - hg * 0.6f, x + fl, y - hg); path.quadTo(x + 4 * s + fl, y - hg * 0.5f, x + 6 * s, y); path.close()
                    c.drawPath(path, pf)
                    pf.color = rgba(255, 220, 90, .9)
                    path.reset(); path.moveTo(x - 3 * s, y); path.quadTo(x - 2 * s + fl, y - hg * 0.4f, x + fl * 0.6f, y - hg * 0.65f); path.quadTo(x + 2 * s, y - hg * 0.3f, x + 3 * s, y); path.close()
                    c.drawPath(path, pf)
                }
            }
            // ядра
            for (p in G.proj) {
                val k = min(1.0, p.t / p.dur)
                val L = hyp(p.tx - p.sx, p.ty - p.sy)
                if (!cam.project(p.x, 8 + sin(k * PI) * L * projArc(p.src) * 1.4, p.y, q)) continue
                pf.color = 0xff1a1a1a.toInt(); c.drawCircle(q[0], q[1], projSize(p.src) * q[2], pf)
            }
            // дым, вспышки, пыль, искры
            for (fx in G.fx) {
                val k = fx.t / fx.life
                val hgt = when (fx.k) { FX_SMOKE -> 10 + k * 26; FX_EMBER -> 14 + k * 30; FX_DUST -> 3.0; FX_BOOM -> 8.0; else -> 14.0 }
                if (!cam.project(fx.x, hgt, fx.y, q)) continue
                val x = q[0]; val y = q[1]; val s = q[2]
                when (fx.k) {
                    FX_SMOKE -> { pf.color = if (fx.dark) rgba(60, 55, 50, 0.55 * (1 - k)) else rgba(235, 235, 230, 0.6 * (1 - k)); c.drawCircle(x, y, (fx.r * (1 + k * 1.6)).toFloat() * s, pf) }
                    FX_FLASH -> { pf.color = rgba(255, 220, 120, 1 - k); c.drawCircle(x, y, ((if (fx.big) 8 else 4) * (1 - k * 0.5)).toFloat() * s, pf) }
                    FX_TRACER -> {
                        if (cam.project(fx.x2, 14.0, fx.y2, q2)) { ps.color = rgba(255, 240, 200, 0.7 * (1 - k)); ps.strokeWidth = 0.9f; c.drawLine(x, y, q2[0], q2[1], ps) }
                    }
                    FX_DUST -> { pf.color = rgba(150, 122, 82, 0.45 * (1 - k)); c.drawCircle(x, y, (fx.r * (1 + k * 1.4)).toFloat() * s, pf) }
                    FX_EMBER -> { pf.color = rgba(255, (180 - k * 120).toInt(), 60, 1 - k); c.drawRect(x, y, x + 1.8f * s, y + 1.8f * s, pf) }
                    FX_BOOM -> { pf.color = rgba(255, (160 - k * 100).toInt(), 40, 0.85 * (1 - k)); c.drawCircle(x, y, (fx.r * (0.4 + k * 0.6)).toFloat() * s, pf) }
                }
            }
            // полоски здоровья
            for (e in G.units) {
                if (!G.seen(e)) continue
                val isSel = sel.contains(e)
                if (!isSel && e.hp >= e.maxhp) continue
                if (!cam.project(e.x, unitHeight(e) + 5.0, e.y, q)) continue
                bar(c, q[0], q[1], 16f * min(1.3f, max(0.8f, q[2])), 2.6f, e, null)
            }
            for (b in G.blds) {
                if (!G.seen(b)) continue
                if (!sel.contains(b) && b.hp >= b.maxhp && b.built) continue
                val top = if (b.built) modelTop[Models3D.M_BLD + b.type] else modelTop[Models3D.M_SCAFFOLD]
                if (!cam.project(b.x, top + 8.0, b.y, q)) continue
                bar(c, q[0], q[1], b.w * TILE * 0.7f * min(1.2f, max(0.7f, q[2])), 3.6f, b, if (b.built) null else (b.progress / BLD[b.type].time).toFloat())
            }
            // путь к точке сбора
            val s0 = if (sel.size == 1) sel[0] else null
            if (s0 is Bld) s0.rally?.let { r ->
                if (cam.project(s0.x, 2.0, s0.y, q) && cam.project(r.x, 2.0, r.y, q2)) {
                    ps.color = rgba(255, 230, 140, .75); ps.strokeWidth = 1.3f; ps.pathEffect = dash
                    c.drawLine(q[0], q[1], q2[0], q2[1], ps); ps.pathEffect = null
                    if (cam.project(r.x, 24.0, r.y, q)) {
                        ps.color = 0xff3a2a1a.toInt(); ps.strokeWidth = 1.6f; c.drawLine(q2[0], q2[1], q[0], q[1], ps)
                        val w = (sin(t * 4 + r.x) * 1.5).toFloat()
                        pf.color = TEAM[0].main
                        path.reset(); path.moveTo(q[0], q[1]); path.lineTo(q[0] + 12, q[1] + 3 + w); path.lineTo(q[0], q[1] + 7); path.close()
                        c.drawPath(path, pf)
                    }
                }
            }
            // рамка выделения
            val gs = gest
            if (gs != null && gs.mode == M_BOX && gs.moved) {
                val x = min(gs.sx, gs.lx); val y = min(gs.sy, gs.ly)
                val w = abs(gs.lx - gs.sx); val h = abs(gs.ly - gs.sy)
                pf.color = rgba(124, 255, 106, .12); c.drawRect(x, y, x + w, y + h, pf)
                ps.color = 0xff7cff6a.toInt(); ps.strokeWidth = 1.5f; c.drawRect(x, y, x + w, y + h, ps)
            }
            drawVignette(c)
            c.restore()
        }

        private fun bar(c: Canvas, cx: Float, y: Float, w: Float, bh: Float, e: Ent, prog: Float?) {
            val x = cx - w / 2
            pf.color = rgba(20, 12, 6, .78); rf.set(x - 1.2f, y - 1.2f, x + w + 1.2f, y + bh + 1.2f); c.drawRoundRect(rf, 1.6f, 1.6f, pf)
            val f = clamp(e.hp / e.maxhp, 0.0, 1.0).toFloat()
            pf.color = if (e.team != 0) 0xffe0503c.toInt() else if (f > 0.5) 0xff6ad23e.toInt() else if (f > 0.25) 0xffeec23a.toInt() else 0xffee4433.toInt()
            c.drawRect(x, y, x + w * f, y + bh, pf)
            pf.color = rgba(255, 255, 255, .28); c.drawRect(x, y, x + w * f, y + bh * 0.4f, pf)
            if (prog != null) { pf.color = 0xffe8b84a.toInt(); c.drawRect(x, y + bh + 2, x + w * prog, y + bh + 4, pf) }
        }

        private fun drawVignette(c: Canvas) {
            val w = width / dens
            val h = height / dens
            val p = vig ?: run {
                val r0 = min(w, h) * 0.35f
                val r1 = max(w, h) * 0.75f
                val p0 = r0 / r1
                Paint().apply {
                    shader = RadialGradient(w / 2, h / 2, r1,
                        intArrayOf(rgba(255, 200, 120, 0.0), rgba(255, 200, 120, 0.0), rgba(60, 30, 0, .08), rgba(20, 10, 0, .38)),
                        floatArrayOf(0f, p0, p0 + 0.6f * (1 - p0), 1f), Shader.TileMode.CLAMP)
                }.also { vig = it }
            }
            c.drawRect(0f, 0f, w, h, p)
        }
    }

    companion object {
        const val KY = 1.25f
        const val M_PEND = 0
        const val M_PAN = 1
        const val M_BOX = 2
        const val M_PINCH = 3
        const val M_DEAD = 4
        const val M_WALL = 5
    }
}
