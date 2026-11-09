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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun rgba(r: Int, g: Int, b: Int, a: Double): Int = Color.argb((clamp(a, 0.0, 1.0) * 255).toInt(), r, g, b)

/** Поле боя: шаг логики, отрисовка мира на Canvas и жесты пальцами. */
@SuppressLint("ViewConstructor")
class GameView(ctx: Context, val spr: Sprites) : View(ctx) {
    var game: Game? = null
        set(v) {
            field = v; chunks.clear(); clouds = null; fogBmp = null; fogVer = -1; gest = null; acc = 0.0
            lastT = System.nanoTime()
            if (v != null) { v.vw = vwDp; v.vh = vhDp; v.clampCam() }
            invalidate()
        }
    val dens = resources.displayMetrics.density
    var vwDp = 800.0
        private set
    var vhDp = 450.0
        private set

    private val pf = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ps = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val pb = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val pNoFilter = Paint()
    private val rf = RectF()
    private val path = Path()
    private val dash = DashPathEffect(floatArrayOf(4f, 4f), 0f)

    // ------------------------------------------------------------ цикл
    private var lastT = System.nanoTime()
    private var acc = 0.0

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        vwDp = w / dens.toDouble(); vhDp = h / dens.toDouble()
        vig = null
        game?.let { it.vw = vwDp; it.vh = vhDp; it.clampCam() }
    }

    /** шаг логики; вызывается из Choreographer, возвращает реальное время кадра */
    fun step(): Double {
        val now = System.nanoTime()
        val dtReal = min(0.1, (now - lastT) / 1e9)
        lastT = now
        val G = game ?: return dtReal
        if (!G.paused && !G.over) {
            acc += dtReal * G.speed
            var n = 0
            while (acc >= STEP && n < 12) { G.update(STEP); acc -= STEP; n++ }
            if (n >= 12) acc = 0.0
        } else if (G.over) {
            acc += dtReal
            while (acc >= STEP) { for (f in G.fx) f.t += STEP; G.fx.removeAll { it.t >= it.life }; acc -= STEP }
        }
        return dtReal
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xff0b0805.toInt())
        game?.let { render(c, it) }
    }

    // ------------------------------------------------------------ местность
    private val chunks = object : LinkedHashMap<Int, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?) = size > 42
    }

    fun chunksDirty(tx: Int, ty: Int, w: Int, h: Int) {
        for (cy in floorDiv(ty - 1, CH)..floorDiv(ty + h, CH)) for (cx in floorDiv(tx - 1, CH)..floorDiv(tx + w, CH)) chunks.remove(ck(cx, cy))
        // деревья соседнего снизу чанка заходят вверх
        chunks.remove(ck(floorDiv(tx, CH), floorDiv(ty + h, CH) + 1))
    }

    private fun floorDiv(a: Int, b: Int) = Math.floorDiv(a, b)
    private fun ck(cx: Int, cy: Int) = cx * 1000 + cy

    private fun grassCol(G: Game, x: Int, y: Int): Int {
        val g = fbm(x / 7.0, y / 7.0, G.seed + 50)
        val v = hash2(x, y, 3) * 0.015
        return Color.rgb((96 + g * 30 - v * 60).toInt(), (138 + g * 24 - v * 40).toInt(), (62 + g * 12).toInt())
    }

    private fun oval(g: Canvas, x: Float, y: Float, rx: Float, ry: Float, p: Paint) {
        rf.set(x - rx, y - ry, x + rx, y + ry); g.drawOval(rf, p)
    }

    private fun getChunk(G: Game, cx: Int, cy: Int): Bitmap {
        val key = ck(cx, cy)
        chunks[key]?.let { return it }
        val bmp = Bitmap.createBitmap(CH * TILE + 2, CH * TILE + CPAD + 2, Bitmap.Config.ARGB_8888)
        val g = Canvas(bmp)
        g.translate(0f, CPAD.toFloat())
        val x0 = cx * CH
        val y0 = cy * CH
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val pth = Path()
        val T = TILE.toFloat()
        for (ty in y0 until min(G.H, y0 + CH + 1)) for (tx in x0 until min(G.W, x0 + CH + 1)) {
            val t = G.terrain[G.idx(tx, ty)].toInt()
            val px = (tx - x0) * T
            val py = (ty - y0) * T
            if (t == T_WATER) {
                val dpt = fbm(tx / 5.0, ty / 5.0, G.seed + 77)
                p.color = Color.rgb((46 + dpt * 20).toInt(), (92 + dpt * 30).toInt(), (140 + dpt * 25).toInt())
                g.drawRect(px, py, px + T, py + T, p)
                st.color = rgba(200, 230, 255, .25); st.strokeWidth = 1.2f
                val h = hash2(tx, ty, 5).toFloat()
                pth.reset(); pth.moveTo(px + 4 + h * 10, py + 10 + h * 12); pth.quadTo(px + 10 + h * 10, py + 6 + h * 12, px + 16 + h * 10, py + 10 + h * 12)
                g.drawPath(pth, st)
            } else if (t == T_SAND) {
                p.color = grassCol(G, tx, ty); g.drawRect(px, py, px + T, py + T, p)
                p.color = 0xffcdb67e.toInt(); oval(g, px + 16, py + 16, 21f, 21f, p)
                p.color = rgba(120, 90, 40, .25)
                for (k in 0 until 4) {
                    val ax = px + hash2(tx, ty, k).toFloat() * 30
                    val ay = py + hash2(ty, tx, k).toFloat() * 30
                    g.drawRect(ax, ay, ax + 2, ay + 2, p)
                }
            } else {
                p.color = grassCol(G, tx, ty); g.drawRect(px, py, px + T, py + T, p)
                st.color = rgba(40, 80, 20, .35); st.strokeWidth = 1f
                for (k in 0 until 3) {
                    val ax = px + hash2(tx, ty, k + 20).toFloat() * 28 + 2
                    val ay = py + hash2(tx, ty, k + 40).toFloat() * 28 + 4
                    g.drawLine(ax, ay, ax - 1.5f, ay - 4, st); g.drawLine(ax, ay, ax + 1.5f, ay - 4, st)
                }
                fun hh(s: Int) = hash2(tx, ty, s).toFloat()
                if (hh(71) < 0.06) { p.color = rgba(120, 96, 60, .28); oval(g, px + 8 + hh(72) * 16, py + 8 + hh(73) * 16, 7 + hh(74) * 6, 4 + hh(75) * 3, p) }
                if (hh(81) < 0.05) {
                    p.color = 0xff8d8f88.toInt(); oval(g, px + 6 + hh(82) * 20, py + 10 + hh(83) * 16, 2.4f, 1.6f, p)
                    p.color = 0xffb8bab2.toInt(); val ax = px + 5 + hh(82) * 20; val ay = py + 9 + hh(83) * 16; g.drawRect(ax, ay, ax + 1.5f, ay + 1, p)
                }
                if (hh(91) < 0.035) {
                    p.color = 0xff3f6e2a.toInt(); g.drawCircle(px + 16, py + 18, 5f, p); g.drawCircle(px + 21, py + 20, 4f, p)
                    p.color = 0xff5c9440.toInt(); g.drawCircle(px + 15, py + 16, 2.5f, p)
                }
                if (hh(99) < 0.04) {
                    p.color = intArrayOf(0xfff0e070.toInt(), 0xfff4f4f4.toInt(), 0xffd070c0.toInt())[(hh(98) * 3).toInt()]
                    g.drawRect(px + 10, py + 14, px + 13, py + 17, p); g.drawRect(px + 18, py + 20, px + 21, py + 23, p)
                }
            }
        }
        // деревья, сверху вниз (с соседями по краям, чтобы кроны и тени не резались на стыках)
        val pt = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (ty in max(0, y0 - 1) until min(G.H, y0 + CH)) for (tx in max(0, x0 - 2) until min(G.W, x0 + CH + 1)) {
            if (G.terrain[G.idx(tx, ty)].toInt() != T_FOREST) continue
            val px = (tx - x0) * T
            val py = (ty - y0) * T
            if (tx >= x0 && tx < x0 + CH && ty >= y0) { p.color = rgba(30, 55, 20, .16); oval(g, px + 16, py + 17, 17f, 14f, p) }
            val n = if (hash2(tx, ty, 11) < 0.45) 2 else 1
            for (k in 0 until n) {
                val ox = px + (if (n == 1) 10 + hash2(tx, ty, 1).toFloat() * 12 else 4 + k * 16 + hash2(tx, ty, k * 3 + 1).toFloat() * 8)
                val oy = py + 14 + k * 9 + hash2(tx, ty, k * 3 + 2).toFloat() * 8
                val conifer = fbm(tx / 9.0, ty / 9.0, G.seed + 31) < 0.5
                spr.drawTree(g, ox, oy, (if (conifer) 0 else 4) + (hash2(tx, ty, k * 3 + 3) * 4).toInt(), pt)
            }
        }
        chunks[key] = bmp
        return bmp
    }

    // ------------------------------------------------------------ туман
    var fogBmp: Bitmap? = null
        private set
    private var fogVer = -1
    private var fogPx = IntArray(0)

    fun syncFog(G: Game) {
        if (fogBmp == null || fogBmp!!.width != G.W) {
            fogBmp = Bitmap.createBitmap(G.W, G.H, Bitmap.Config.ARGB_8888); fogPx = IntArray(G.W * G.H); fogVer = -1
        }
        if (fogVer == G.fogVer) return
        fogVer = G.fogVer
        for (i in fogPx.indices) fogPx[i] = (if (G.visible[i].toInt() != 0) 0 else if (G.explored[i].toInt() != 0) 110 else 255) shl 24
        fogBmp!!.setPixels(fogPx, 0, G.W, 0, 0, G.W, G.H)
    }

    // ------------------------------------------------------------ кадр
    private val drawList = ArrayList<Ent>()
    private fun depth(e: Ent): Double = when (e) {
        is Mine -> e.y + 32
        is Bld -> (e.ty + e.h) * TILE.toDouble()
        else -> e.y
    }
    private val depthCmp = Comparator<Ent> { a, b -> depth(a).compareTo(depth(b)) }

    private fun unitFrame(e: Unt): Int {
        if (e.working) { val k = floor(e.anim * 5).toInt() % 4; return 5 + (if (k == 3) 1 else k) }
        if (e.aimT > 0) {
            if (e.hitT > 0) return if (e.hitT > 0.17) 5 else if (e.hitT > 0.08) 6 else 7
            if (e.type == U_MUSKET || e.type == U_CANNON) return if (UNIT[e.type].cd - e.cd < 0.25) 6 else 5
            return 5
        }
        if (e.moving) return 1 + (floor(e.anim * (if (e.type == U_CAV) 9 else 7)).toInt() and 3)
        return 0
    }

    private fun alpha(p: Paint, a: Double) { p.alpha = (clamp(a, 0.0, 1.0) * 255).toInt() }

    private fun render(c: Canvas, G: Game) {
        val z = G.camZ
        val t = G.time
        syncFog(G)
        c.save()
        val s = (dens * z).toFloat()
        c.scale(s, s)
        c.translate(-G.camX.toFloat(), -G.camY.toFloat())
        val v = G.viewRect()
        val cs = CH * TILE
        val nCy = ceil(G.H.toDouble() / CH).toInt() - 1
        val nCx = ceil(G.W.toDouble() / CH).toInt() - 1
        for (cy in max(0, floor(v[1] / cs).toInt())..min(nCy, floor((v[3] + CPAD) / cs).toInt()))
            for (cx in max(0, floor(v[0] / cs).toInt())..min(nCx, floor(v[2] / cs).toInt()))
                c.drawBitmap(getChunk(G, cx, cy), (cx * cs).toFloat(), (cy * cs - CPAD).toFloat(), pb)
        // декали
        for (d in G.decals) {
            if (d.x < v[0] - 100 || d.x > v[2] + 100 || d.y < v[1] - 100 || d.y > v[3] + 100) continue
            val a = min(1.0, (d.life - d.t) / 5)
            val x = d.x.toFloat()
            val y = d.y.toFloat()
            when (d.k) {
                D_CORPSE -> {
                    if (d.t < 0.3) { alpha(pb, a); spr.drawUnit(c, d.type, d.team, d.dir, 0, x, y, pb) }
                    else { alpha(pb, a * 0.9); spr.drawUnit(c, d.type, d.team, d.dir, 8, x, y, pb) }
                    pb.alpha = 255
                }
                D_RUIN -> {
                    pf.color = 0xff4a3a2a.toInt(); alpha(pf, a)
                    c.drawRect(x + 4, (y + d.h * 0.3).toFloat(), (x + d.w - 4).toFloat(), (y + d.h * 0.96).toFloat(), pf)
                    pf.color = 0xff2a2018.toInt(); alpha(pf, a)
                    for (k in 0 until 8) {
                        val rx = (x + 6 + hash2(k, d.x.toInt(), 1) * (d.w - 18)).toFloat()
                        val ry = (y + d.h * 0.35 + hash2(k, d.y.toInt(), 2) * d.h * 0.5).toFloat()
                        c.drawRect(rx, ry, rx + 10, ry + 6, pf)
                    }
                }
                D_CRATER -> { pf.color = rgba(40, 30, 20, .45 * a); oval(c, x, y, 9f, 5f, pf) }
                D_STUMP -> {
                    pf.color = 0xff6a4a2a.toInt(); oval(c, x, y, 3.5f, 2f, pf)
                    pf.color = 0xffc8a070.toInt(); oval(c, x, y - 1, 2.5f, 1.3f, pf)
                }
                D_PIT -> { pf.color = rgba(70, 60, 50, .5); oval(c, x, y, 24f, 14f, pf) }
            }
        }
        drawWater(c, G, v, t)
        // площадки зданий
        pf.color = rgba(120, 92, 58, .38)
        for (b in G.blds) {
            if (!G.seen(b) || b.x < v[0] - 160 || b.x > v[2] + 160 || b.y < v[1] - 160 || b.y > v[3] + 160) continue
            val x0 = b.tx * TILE.toFloat()
            val y0 = b.ty * TILE.toFloat()
            val w = b.w * TILE.toFloat()
            val h = b.h * TILE.toFloat()
            oval(c, x0 + w / 2, y0 + h * 0.62f, w * 0.66f, h * 0.5f, pf)
        }
        // сортировка по глубине
        drawList.clear()
        fun inView(x: Double, y: Double, m: Double) = x > v[0] - m && x < v[2] + m && y > v[1] - m && y < v[3] + m + 40
        for (m in G.mines) if (!m.dead && inView(m.x, m.y, 80.0) && G.explored[G.idx(m.tx, m.ty)].toInt() != 0) drawList.add(m)
        for (b in G.blds) if (inView(b.x, b.y, 120.0) && G.seen(b)) drawList.add(b)
        for (u in G.units) if (inView(u.x, u.y, 40.0) && G.seen(u)) drawList.add(u)
        drawList.sortWith(depthCmp)
        val sel = G.sel
        for (e in drawList) {
            val isSel = sel.contains(e)
            if (e is Mine) {
                spr.drawMine(c, e, pb)
                if (isSel) { ps.color = 0xffffe080.toInt(); ps.strokeWidth = 1.5f; c.drawRect(e.tx * TILE.toFloat(), e.ty * TILE.toFloat(), e.tx * TILE + 64f, e.ty * TILE + 64f, ps) }
                continue
            }
            if (e is Bld) {
                if (isSel) {
                    ps.color = if (e.team == 0) 0xff7cff6a.toInt() else 0xffff6a5a.toInt(); ps.strokeWidth = 2f
                    oval(c, e.x.toFloat(), (e.y + e.h * TILE * 0.2).toFloat(), e.w * TILE * 0.62f, e.h * TILE * 0.42f, ps)
                }
                spr.drawBuilding(c, e.type, e.team, e.tx * TILE.toFloat(), e.ty * TILE.toFloat(), if (e.built) 1f else (e.progress / BLD[e.type].time).toFloat(), pb, pf, ps)
                if (e.built && e.hp < e.maxhp * 0.55) drawFire(c, e, t)
                continue
            }
            val u = e as Unt
            val x = u.x.toFloat()
            val y = u.y.toFloat()
            if (isSel) {
                val col = if (u.team == 0) intArrayOf(124, 255, 106) else intArrayOf(255, 106, 90)
                pf.color = rgba(col[0], col[1], col[2], .18); oval(c, x, y, (u.r + 3).toFloat(), ((u.r + 3) * 0.45).toFloat(), pf)
                ps.color = rgba(col[0], col[1], col[2], .95); ps.strokeWidth = 1.4f; oval(c, x, y, (u.r + 3).toFloat(), ((u.r + 3) * 0.45).toFloat(), ps)
            }
            spr.drawUnit(c, u.type, u.team, u.dir, unitFrame(u), x, y, pb)
            if (u.carry > 0 && !u.working && u.carryType >= 0) {
                pf.color = RCOL[u.carryType]; c.drawRect(x - 7, y - 15, x - 2, y - 9, pf)
                ps.color = rgba(30, 20, 10, .7); ps.strokeWidth = 0.8f; c.drawRect(x - 7, y - 15, x - 2, y - 9, ps)
            }
        }
        // полоски здоровья
        for (e in drawList) {
            if (e is Mine) continue
            val isSel = sel.contains(e)
            if (!isSel && e.hp >= e.maxhp && (e !is Bld || e.built)) continue
            val w: Float
            val x: Float
            val y: Float
            if (e is Bld) { w = e.w * TILE * 0.7f; x = e.x.toFloat() - w / 2; y = e.ty * TILE - 12f }
            else { val u = e as Unt; w = 16f; x = u.x.toFloat() - 8; y = u.y.toFloat() - (if (u.type == U_CAV) 38 else if (u.type == U_CANNON) 24 else 30) }
            pf.color = rgba(0, 0, 0, .6); c.drawRect(x - 1, y - 1, x + w + 1, y + 3, pf)
            val f = clamp(e.hp / e.maxhp, 0.0, 1.0).toFloat()
            pf.color = if (f > 0.5) 0xff55cc33.toInt() else if (f > 0.25) 0xffeecc33.toInt() else 0xffee4433.toInt()
            c.drawRect(x, y, x + w * f, y + 2, pf)
            if (e is Bld && !e.built) { pf.color = 0xffe8b84a.toInt(); c.drawRect(x, y + 4, x + w * (e.progress / BLD[e.type].time).toFloat(), y + 6, pf) }
        }
        // снаряды
        for (p in G.proj) {
            val k = min(1.0, p.t / p.dur)
            val L = hyp(p.tx - p.sx, p.ty - p.sy)
            val hgt = (sin(k * PI) * L * 0.18).toFloat()
            pf.color = rgba(0, 0, 0, .25); oval(c, p.x.toFloat(), p.y.toFloat(), 3f, 1.5f, pf)
            pf.color = 0xff1a1a1a.toInt(); c.drawCircle(p.x.toFloat(), p.y.toFloat() - hgt, 3f, pf)
        }
        // эффекты
        for (f in G.fx) {
            val k = f.t / f.life
            val x = f.x.toFloat()
            val y = f.y.toFloat()
            when (f.k) {
                FX_SMOKE -> { pf.color = if (f.dark) rgba(60, 55, 50, 0.55 * (1 - k)) else rgba(235, 235, 230, 0.6 * (1 - k)); c.drawCircle(x, y, (f.r * (1 + k * 1.6)).toFloat(), pf) }
                FX_FLASH -> { pf.color = rgba(255, 220, 120, 1 - k); c.drawCircle(x, y, ((if (f.big) 8 else 4) * (1 - k * 0.5)).toFloat(), pf) }
                FX_TRACER -> { ps.color = rgba(255, 240, 200, 0.7 * (1 - k)); ps.strokeWidth = 0.8f; c.drawLine(x, y, f.x2.toFloat(), f.y2.toFloat(), ps) }
                FX_DUST -> { pf.color = rgba(150, 122, 82, 0.45 * (1 - k)); c.drawCircle(x, y, (f.r * (1 + k * 1.4)).toFloat(), pf) }
                FX_EMBER -> { pf.color = rgba(255, (180 - k * 120).toInt(), 60, 1 - k); c.drawRect(x, y, x + 1.6f, y + 1.6f, pf) }
                FX_BOOM -> { pf.color = rgba(255, (160 - k * 100).toInt(), 40, 0.85 * (1 - k)); c.drawCircle(x, y - 4, (f.r * (0.4 + k * 0.6)).toFloat(), pf) }
            }
        }
        drawClouds(c, G, v, t)
        // туман
        fogBmp?.let { rf.set(0f, 0f, G.W * TILE.toFloat(), G.H * TILE.toFloat()); pb.alpha = 255; c.drawBitmap(it, null, rf, pb) }
        // маркеры и точки сбора
        for (m in G.markers) {
            val k = (m.t / 0.8).toFloat()
            ps.color = m.col; ps.alpha = ((1 - k) * 255).toInt().coerceIn(0, 255); ps.strokeWidth = 2f
            oval(c, m.x.toFloat(), m.y.toFloat(), 4 + k * 12, (4 + k * 12) * 0.5f, ps)
        }
        val s0 = if (sel.size == 1) sel[0] else null
        if (s0 is Bld) {
            val r = s0.rally
            if (r != null) {
                ps.color = rgba(255, 230, 140, .7); ps.strokeWidth = 1.2f; ps.pathEffect = dash
                c.drawLine(s0.x.toFloat(), s0.y.toFloat(), r.x.toFloat(), r.y.toFloat(), ps)
                ps.pathEffect = null
                flag(c, r.x.toFloat(), r.y.toFloat(), TEAM[0].main, t)
            }
        }
        // призрак здания
        G.placing?.let { p ->
            val d = BLD[p.type]
            pb.alpha = (0.6 * 255).toInt()
            spr.drawBuilding(c, p.type, 0, p.tx * TILE.toFloat(), p.ty * TILE.toFloat(), 1f, pb, pf, ps)
            pb.alpha = 255
            val x0 = p.tx * TILE.toFloat()
            val y0 = p.ty * TILE.toFloat()
            pf.color = if (p.ok) rgba(80, 255, 80, .22) else rgba(255, 60, 40, .3)
            c.drawRect(x0, y0, x0 + d.w * TILE, y0 + d.h * TILE, pf)
            ps.color = if (p.ok) 0xff7cff6a.toInt() else 0xffff5040.toInt(); ps.strokeWidth = 2f
            c.drawRect(x0, y0, x0 + d.w * TILE, y0 + d.h * TILE, ps)
        }
        G.ping?.let { pg ->
            val k = (1 - (pg.t % 1)).toFloat()
            ps.color = rgba(255, 60, 40, (1 - k).toDouble()); ps.strokeWidth = 3f
            c.drawCircle(pg.x.toFloat(), pg.y.toFloat(), 20 + k * 40, ps)
        }
        c.restore()
        // экранные слои
        c.save()
        c.scale(dens, dens)
        drawVignette(c)
        val gs = gest
        if (gs != null && gs.mode == M_BOX && gs.moved) {
            val x = min(gs.sx, gs.lx)
            val y = min(gs.sy, gs.ly)
            val w = abs(gs.lx - gs.sx)
            val h = abs(gs.ly - gs.sy)
            pf.color = rgba(124, 255, 106, .12); c.drawRect(x, y, x + w, y + h, pf)
            ps.color = 0xff7cff6a.toInt(); ps.strokeWidth = 1.5f; c.drawRect(x, y, x + w, y + h, ps)
        }
        c.restore()
    }

    private fun flag(c: Canvas, x: Float, y: Float, col: Int, t: Double) {
        ps.color = 0xff3a2a1a.toInt(); ps.strokeWidth = 1.5f
        c.drawLine(x, y, x, y - 20, ps)
        val w = (sin(t * 4 + x) * 1.5).toFloat()
        pf.color = col
        path.reset(); path.moveTo(x, y - 20); path.lineTo(x + 12, y - 17 + w); path.lineTo(x, y - 13); path.close()
        c.drawPath(path, pf)
    }

    private fun drawWater(c: Canvas, G: Game, v: DoubleArray, t: Double) {
        val x0 = max(0, floor(v[0] / TILE).toInt())
        val x1 = min(G.W - 1, floor(v[2] / TILE).toInt())
        val y0 = max(0, floor(v[1] / TILE).toInt())
        val y1 = min(G.H - 1, floor(v[3] / TILE).toInt())
        val T = TILE.toFloat()
        ps.strokeWidth = 1.5f
        for (ty in y0..y1) for (tx in x0..x1) {
            if (G.terrain[ty * G.W + tx].toInt() != T_WATER) continue
            val px = tx * T
            val py = ty * T
            val h = hash2(tx, ty, 7)
            val a = 0.5 + 0.5 * sin(t * 1.6 + h * 12)
            pf.color = rgba(220, 240, 255, 0.22 * a)
            val ax = (px + 4 + h * 18).toFloat()
            val ay = (py + 6 + ((h * 37) % 1) * 18).toFloat()
            c.drawRect(ax, ay, ax + 6, ay + 1.2f, pf)
            val bx = (px + 14 - h * 8).toFloat()
            val by = (py + 20 - ((h * 53) % 1) * 10).toFloat()
            c.drawRect(bx, by, bx + 4, by + 1, pf)
            // пена у берега
            ps.color = rgba(240, 250, 255, 0.25 + 0.2 * sin(t * 2 + h * 6))
            fun land(dx: Int, dy: Int) = G.inB(tx + dx, ty + dy) && G.terrain[(ty + dy) * G.W + tx + dx].toInt() != T_WATER
            val o = (1.5 + sin(t * 1.8 + h * 5)).toFloat()
            if (land(0, -1)) c.drawLine(px, py + o, px + T, py + o, ps)
            if (land(0, 1)) c.drawLine(px, py + T - o, px + T, py + T - o, ps)
            if (land(-1, 0)) c.drawLine(px + o, py, px + o, py + T, ps)
            if (land(1, 0)) c.drawLine(px + T - o, py, px + T - o, py + T, ps)
        }
    }

    private class Cloud(val x: Double, val y: Double, val r: Double, val k: Double)
    private var clouds: List<Cloud>? = null

    private fun drawClouds(c: Canvas, G: Game, v: DoubleArray, t: Double) {
        if (G.auto) return
        val cl = clouds ?: List((G.W / 9.0).let { Math.round(it).toInt() }) {
            Cloud(rnd() * G.W * TILE, rnd() * G.H * TILE, 90 + rnd() * 130, 0.6 + rnd() * 0.6)
        }.also { clouds = it }
        val MW = G.W * TILE.toDouble()
        pf.color = rgba(20, 30, 50, .09)
        for (cd in cl) {
            val x = ((cd.x + t * 9) % (MW + 600)) - 300
            val y = cd.y - t * 3 % 1
            if (x + cd.r * 1.6 < v[0] || x - cd.r * 1.6 > v[2] || y + cd.r < v[1] || y - cd.r > v[3]) continue
            path.reset()
            fun ell(ex: Double, ey: Double, rx: Double, ry: Double) {
                rf.set((ex - rx).toFloat(), (ey - ry).toFloat(), (ex + rx).toFloat(), (ey + ry).toFloat()); path.addOval(rf, Path.Direction.CW)
            }
            ell(x, y, cd.r, cd.r * 0.55 * cd.k)
            ell(x + cd.r * 0.6, y + cd.r * 0.12, cd.r * 0.7, cd.r * 0.4 * cd.k)
            ell(x - cd.r * 0.55, y + cd.r * 0.08, cd.r * 0.6, cd.r * 0.35 * cd.k)
            c.drawPath(path, pf)
        }
    }

    private fun drawFire(c: Canvas, b: Bld, t: Double) {
        val x0 = b.tx * TILE.toFloat()
        val y0 = b.ty * TILE.toFloat()
        val w = b.w * TILE.toFloat()
        val n = if (b.hp < b.maxhp * 0.3) 4 else 2
        for (i in 0 until n) {
            val fx = x0 + w * (0.25f + 0.5f * hash2(b.id, i, 3).toFloat())
            val fy = y0 + 18 + hash2(b.id, i, 4).toFloat() * 20
            val f = (sin(t * 13 + i * 2) * 2).toFloat()
            val hgt = (12 + sin(t * 9 + i) * 3).toFloat()
            pf.color = rgba(255, 120, 30, .85)
            path.reset(); path.moveTo(fx - 6, fy); path.quadTo(fx - 4 + f, fy - hgt * 0.6f, fx + f, fy - hgt); path.quadTo(fx + 4 + f, fy - hgt * 0.5f, fx + 6, fy); path.close()
            c.drawPath(path, pf)
            pf.color = rgba(255, 220, 90, .9)
            path.reset(); path.moveTo(fx - 3, fy); path.quadTo(fx - 2 + f, fy - hgt * 0.4f, fx + f * 0.6f, fy - hgt * 0.65f); path.quadTo(fx + 2, fy - hgt * 0.3f, fx + 3, fy); path.close()
            c.drawPath(path, pf)
        }
    }

    private var vig: Paint? = null
    private fun drawVignette(c: Canvas) {
        val w = vwDp.toFloat()
        val h = vhDp.toFloat()
        val p = vig ?: run {
            val r0 = min(w, h) * 0.35f
            val r1 = max(w, h) * 0.75f
            val p0 = r0 / r1
            Paint().apply {
                shader = RadialGradient(w / 2, h / 2, r1,
                    intArrayOf(rgba(255, 200, 120, 0.0), rgba(255, 200, 120, 0.0), rgba(60, 30, 0, .08), rgba(20, 10, 0, .42)),
                    floatArrayOf(0f, p0, p0 + 0.6f * (1 - p0), 1f), Shader.TileMode.CLAMP)
            }.also { vig = it }
        }
        pf.color = rgba(255, 190, 110, .05); c.drawRect(0f, 0f, w, h, pf)
        c.drawRect(0f, 0f, w, h, p)
    }

    // ------------------------------------------------------------ мини-карта
    private var miniBase: Bitmap? = null
    val mini: Bitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
    private val mc = Canvas(mini)

    fun drawMini(G: Game) {
        if (G.miniDirty || miniBase == null || miniBase!!.width != G.W) {
            val px = IntArray(G.W * G.H)
            for (i in px.indices) {
                px[i] = when (G.terrain[i].toInt()) {
                    T_WATER -> Color.rgb(50, 96, 146); T_FOREST -> Color.rgb(36, 76, 34); T_SAND -> Color.rgb(196, 176, 120); else -> Color.rgb(98, 140, 64)
                }
            }
            val b = miniBase?.takeIf { it.width == G.W } ?: Bitmap.createBitmap(G.W, G.H, Bitmap.Config.ARGB_8888)
            b.setPixels(px, 0, G.W, 0, 0, G.W, G.H)
            miniBase = b; G.miniDirty = false
        }
        syncFog(G)
        val S = 150f
        val s = S / G.W
        mc.drawBitmap(miniBase!!, null, RectF(0f, 0f, S, S), pNoFilter)
        val p = Paint()
        for (m in G.mines) if (!m.dead && (G.auto || G.explored[G.idx(m.tx, m.ty)].toInt() != 0)) {
            p.color = if (m.res == GOLD) 0xffffd84a.toInt() else 0xffc8ccd0.toInt()
            mc.drawRect(m.tx * s, m.ty * s, (m.tx + 2) * s, (m.ty + 2) * s, p)
        }
        for (b in G.blds) if (G.seen(b)) { p.color = if (b.team != 0) 0xffff5a4a.toInt() else 0xff5a9aff.toInt(); mc.drawRect(b.tx * s, b.ty * s, (b.tx + b.w) * s, (b.ty + b.h) * s, p) }
        for (u in G.units) if (G.seen(u)) {
            p.color = if (u.team != 0) 0xffff8a7a.toInt() else 0xffa8d0ff.toInt()
            val x = (u.x / TILE * s).toFloat() - 1
            val y = (u.y / TILE * s).toFloat() - 1
            mc.drawRect(x, y, x + 2, y + 2, p)
        }
        fogBmp?.let { mc.drawBitmap(it, null, RectF(0f, 0f, S, S), Paint(Paint.FILTER_BITMAP_FLAG)) }
        val v = G.viewRect()
        val st = Paint().apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 1f }
        mc.drawRect((v[0] / TILE * s).toFloat(), (v[1] / TILE * s).toFloat(), (v[2] / TILE * s).toFloat(), (v[3] / TILE * s).toFloat(), st)
        G.ping?.let { pg ->
            st.color = 0xffff4030.toInt(); st.strokeWidth = 2f; st.isAntiAlias = true
            mc.drawCircle((pg.x / TILE * s).toFloat(), (pg.y / TILE * s).toFloat(), (6 + (pg.t % 1) * 6).toFloat(), st)
        }
    }

    // ------------------------------------------------------------ жесты
    private class Gest(var mode: Int, val sx: Float, val sy: Float) {
        var lx = sx
        var ly = sy
        var moved = false
        var d0 = 0f
        var z0 = 1.0
        var mx = 0f
        var my = 0f
    }

    private var gest: Gest? = null
    private val longPress = Runnable {
        val g = gest
        if (g != null && g.mode == M_PEND) { g.mode = M_BOX; g.moved = true; performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    }

    var onTouchStart: (() -> Unit)? = null
    var onBoxDone: (() -> Unit)? = null

    private fun toWorldX(G: Game, sx: Float) = G.camX + sx / G.camZ
    private fun toWorldY(G: Game, sy: Float) = G.camY + sy / G.camZ

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val G = game ?: return true
        if (G.over) return true
        val d = dens
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTouchStart?.invoke()
                val x = e.x / d
                val y = e.y / d
                val g = Gest(if (G.boxMode && G.placing == null) M_BOX else M_PEND, x, y)
                gest = g
                if (g.mode == M_PEND && G.placing == null) postDelayed(longPress, 400)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2) {
                    removeCallbacks(longPress)
                    val ax = e.getX(0) / d
                    val ay = e.getY(0) / d
                    val bx = e.getX(1) / d
                    val by = e.getY(1) / d
                    val g = Gest(M_PINCH, ax, ay)
                    g.d0 = max(1f, hyp((ax - bx).toDouble(), (ay - by).toDouble()).toFloat()); g.z0 = G.camZ
                    g.mx = (ax + bx) / 2; g.my = (ay + by) / 2
                    gest = g
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val g = gest ?: return true
                if (g.mode == M_PINCH) {
                    if (e.pointerCount < 2) return true
                    val ax = e.getX(0) / d
                    val ay = e.getY(0) / d
                    val bx = e.getX(1) / d
                    val by = e.getY(1) / d
                    val dd = hyp((ax - bx).toDouble(), (ay - by).toDouble()).toFloat()
                    val mx = (ax + bx) / 2
                    val my = (ay + by) / 2
                    val wx = toWorldX(G, g.mx)
                    val wy = toWorldY(G, g.my)
                    G.camZ = clamp(g.z0 * dd / g.d0, 0.45, 2.0)
                    G.camX = wx - mx / G.camZ; G.camY = wy - my / G.camZ
                    g.mx = mx; g.my = my; G.clampCam()
                    return true
                }
                if (g.mode == M_DEAD) return true
                val x = e.getX(0) / d
                val y = e.getY(0) / d
                val dx = x - g.lx
                val dy = y - g.ly
                val far = hyp((x - g.sx).toDouble(), (y - g.sy).toDouble()) > 9
                if (g.mode == M_PEND && far) { g.mode = M_PAN; removeCallbacks(longPress) }
                if (g.mode == M_PAN) { G.camX -= dx / G.camZ; G.camY -= dy / G.camZ; G.clampCam() }
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
                if (g.mode == M_PEND) G.tapWorld(toWorldX(G, g.sx), toWorldY(G, g.sy))
                else if (g.mode == M_BOX) {
                    if (g.moved) {
                        G.boxSelect(toWorldX(G, g.sx), toWorldY(G, g.sy), toWorldX(G, g.lx), toWorldY(G, g.ly))
                        if (G.boxMode) { G.boxMode = false; G.uiDirty = true }
                        onBoxDone?.invoke()
                    } else G.tapWorld(toWorldX(G, g.sx), toWorldY(G, g.sy))
                }
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); gest = null }
        }
        return true
    }

    companion object {
        const val M_PEND = 0
        const val M_PAN = 1
        const val M_BOX = 2
        const val M_PINCH = 3
        const val M_DEAD = 4
    }
}

/** Мини-карта: показывает кадр, который готовит GameView, тап переносит камеру. */
@SuppressLint("ViewConstructor")
class MiniView(ctx: Context, val gv: GameView) : View(ctx) {
    private val p = Paint()
    private val dst = Rect()
    private val border = Paint().apply { color = 0xff8a6a3a.toInt(); style = Paint.Style.STROKE }

    override fun onDraw(c: Canvas) {
        val b = (2 * gv.dens)
        c.drawColor(Color.BLACK)
        dst.set(b.toInt(), b.toInt(), width - b.toInt(), height - b.toInt())
        c.drawBitmap(gv.mini, null, dst, p)
        border.strokeWidth = b
        c.drawRect(b / 2, b / 2, width - b / 2, height - b / 2, border)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val G = gv.game ?: return true
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            val x = clamp(e.x.toDouble() / width, 0.0, 1.0) * G.W * TILE
            val y = clamp(e.y.toDouble() / height, 0.0, 1.0) * G.H * TILE
            G.centerCam(x, y)
        }
        return true
    }
}
