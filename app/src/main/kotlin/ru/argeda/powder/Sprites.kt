package ru.argeda.powder

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.json.JSONObject

class SprMeta(val cw: Int, val ch: Int, val ax: Float, val ay: Float, val scale: Int, val cols: Int, val fr: IntArray?)

/** Пререндеренные 3D-спрайты (KayKit, CC0) из assets/sprites. */
class Sprites(am: AssetManager) {
    val units = arrayOfNulls<Bitmap>(UNIT.size)
    val umeta = arrayOfNulls<SprMeta>(UNIT.size)
    val blds = arrayOfNulls<Bitmap>(BLD.size)
    val bmeta = arrayOfNulls<SprMeta>(BLD.size)
    val trees: Bitmap
    val tmeta: SprMeta
    val rocks: Bitmap
    val rmeta: SprMeta
    val unitIcons = arrayOfNulls<Bitmap>(UNIT.size)
    val bldIcons = arrayOfNulls<Bitmap>(BLD.size)

    private val src = Rect()
    private val dst = RectF()

    init {
        val js = JSONObject(am.open("sprites/sprites.json").bufferedReader().readText())
        fun meta(o: JSONObject): SprMeta {
            val fa = o.optJSONArray("fr")
            val fr = if (fa != null) IntArray(fa.length()) { fa.getInt(it) } else null
            return SprMeta(o.getInt("cw"), o.getInt("ch"), o.getDouble("ax").toFloat(), o.getDouble("ay").toFloat(), o.optInt("scale", 2), o.optInt("cols", 1), fr)
        }
        fun load(name: String): Bitmap {
            val o = BitmapFactory.Options()
            o.inScaled = false
            o.inPreferredConfig = Bitmap.Config.ARGB_8888
            return am.open("sprites/$name.png").use { BitmapFactory.decodeStream(it, null, o)!! }
        }
        // Большие атласы держим в видеопамяти (HARDWARE), из обычной копии сначала делаем иконки.
        fun gpu(b: Bitmap): Bitmap {
            val h = b.copy(Bitmap.Config.HARDWARE, false)
            return if (h != null) { b.recycle(); h } else b
        }
        val ju = js.getJSONObject("units")
        for (t in UNIT.indices) {
            val m = meta(ju.getJSONObject(UNIT[t].key))
            umeta[t] = m
            val b = load("u_" + UNIT[t].key)
            val sc = m.scale
            unitIcons[t] = fit(b, 0, 1 * m.ch * sc, m.cw * sc, m.ch * sc)
            units[t] = gpu(b)
        }
        val jb = js.getJSONObject("blds")
        for (t in BLD.indices) {
            val m = meta(jb.getJSONObject(BLD[t].key))
            bmeta[t] = m
            val b = load("b_" + BLD[t].key)
            bldIcons[t] = fit(b, 0, 0, m.cw * m.scale, m.ch * m.scale)
            blds[t] = gpu(b)
        }
        // деревья и камни рисуются в программные холсты местности, поэтому без HARDWARE
        tmeta = meta(js.getJSONObject("trees"))
        trees = load("trees")
        rmeta = meta(js.getJSONObject("rocks"))
        rocks = load("rocks")
    }

    /** вписать кадр в иконку 72×72 по границам непрозрачных пикселей */
    private fun fit(im: Bitmap, sx: Int, sy: Int, sw: Int, sh: Int): Bitmap {
        val px = IntArray(sw * sh)
        im.getPixels(px, 0, sw, sx, sy, sw, sh)
        var x0 = sw
        var y0 = sh
        var x1 = 0
        var y1 = 0
        for (y in 0 until sh) for (x in 0 until sw) if ((px[y * sw + x] ushr 24) > 150) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < x0) { x0 = 0; y0 = 0; x1 = sw - 1; y1 = sh - 1 }
        val c = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        val k = minOf(66f / bw, 66f / bh)
        Canvas(c).drawBitmap(im, Rect(sx + x0, sy + y0, sx + x1 + 1, sy + y1 + 1),
            RectF(36 - bw * k / 2, 36 - bh * k / 2, 36 + bw * k / 2, 36 + bh * k / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return c
    }

    /** кадры: 0 стоит, 1-4 шаг, 5-7 действие, 8 убит; (x, y) — точка у ног */
    fun drawUnit(g: Canvas, type: Int, team: Int, dir: Int, f: Int, x: Float, y: Float, p: Paint) {
        val m = umeta[type]!!
        val sc = m.scale
        val row = (if (team != 0) 8 else 0) + dir
        val fr = m.fr
        if (fr != null) {
            val k = (row * m.cols + f) * 4
            val fx = fr[k]
            val fy = fr[k + 1]
            val fw = fr[k + 2]
            val fh = fr[k + 3]
            if (fw <= 0 || fh <= 0) return
            val sx = f * m.cw * sc + fx
            val sy = row * m.ch * sc + fy
            src.set(sx, sy, sx + fw, sy + fh)
            val dx = x - m.ax + fx.toFloat() / sc
            val dy = y - m.ay + fy.toFloat() / sc
            dst.set(dx, dy, dx + fw.toFloat() / sc, dy + fh.toFloat() / sc)
        } else {
            src.set(f * m.cw * sc, row * m.ch * sc, (f + 1) * m.cw * sc, (row + 1) * m.ch * sc)
            dst.set(x - m.ax, y - m.ay, x - m.ax + m.cw, y - m.ay + m.ch)
        }
        g.drawBitmap(units[type]!!, src, dst, p)
    }

    /** prog < 1: здание «растёт» снизу вверх */
    fun drawBuilding(g: Canvas, type: Int, team: Int, x0: Float, y0: Float, prog: Float, p: Paint, fill: Paint, stroke: Paint) {
        val m = bmeta[type]!!
        val d = BLD[type]
        val sc = m.scale
        val w = d.w * TILE.toFloat()
        val h = d.h * TILE.toFloat()
        val cx = x0 + w / 2
        val cy = y0 + h / 2
        val bmp = blds[type]!!
        val col = if (team != 0) m.cw else 0
        if (prog < 1) {
            fill.color = 0xbf8a6a44.toInt()
            g.drawRect(x0 + 2, y0 + 2, x0 + w - 2, y0 + h - 2, fill)
            val top = cy - m.ay
            val full = m.ch.toFloat()
            val vis = maxOf(2f, full * (0.15f + 0.85f * prog))
            val a = p.alpha
            p.alpha = (a * 0.9f).toInt()
            src.set(col * sc, ((full - vis) * sc).toInt(), (col + m.cw) * sc, m.ch * sc)
            dst.set(cx - m.ax, top + full - vis, cx - m.ax + m.cw, top + full)
            g.drawBitmap(bmp, src, dst, p)
            p.alpha = a
            stroke.color = 0xff6a4a28.toInt(); stroke.strokeWidth = 2f
            val n = d.w * 2
            for (k in 0..n) {
                val xx = x0 + 3 + k * (w - 6) / n
                g.drawLine(xx, y0 + h, xx, y0 + 6 - 20 * prog, stroke)
            }
            var yy = y0 + h - 4
            while (yy > y0 + 6 - 20 * prog) { g.drawLine(x0 + 3, yy, x0 + w - 3, yy, stroke); yy -= 14 }
            return
        }
        src.set(col * sc, 0, (col + m.cw) * sc, m.ch * sc)
        dst.set(cx - m.ax, cy - m.ay, cx - m.ax + m.cw, cy - m.ay + m.ch)
        g.drawBitmap(bmp, src, dst, p)
    }

    fun drawTree(g: Canvas, x: Float, y: Float, k: Int, p: Paint) {
        val m = tmeta
        val sc = m.scale
        src.set(k * m.cw * sc, 0, (k + 1) * m.cw * sc, m.ch * sc)
        dst.set(x - m.ax, y - m.ay, x - m.ax + m.cw, y - m.ay + m.ch)
        g.drawBitmap(trees, src, dst, p)
    }

    fun drawMine(g: Canvas, mn: Mine, p: Paint) {
        val m = rmeta
        val sc = m.scale
        val cx = mn.tx * TILE + 32f
        val cy = mn.ty * TILE + 32f
        val s = (0.6 + 0.4 * kotlin.math.sqrt(clamp(mn.amount.toDouble() / mn.max, 0.0, 1.0))).toFloat()
        val col = if (mn.res == GOLD) 0 else m.cw
        src.set(col * sc, 0, (col + m.cw) * sc, m.ch * sc)
        dst.set(cx - m.ax * s, cy - m.ay * s, cx - m.ax * s + m.cw * s, cy - m.ay * s + m.ch * s)
        g.drawBitmap(rocks, src, dst, p)
    }
}
