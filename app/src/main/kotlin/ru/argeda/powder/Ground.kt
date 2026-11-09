package ru.argeda.powder

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Попиксельная земля: плавные берега и песок (контуры по краям клеток со скруглением),
 * рельеф с освещением сверху-слева, текстура травы, лесная подстилка, глубина воды.
 * Чистый Kotlin: заполняет массив ARGB, рисовать его можно где угодно.
 */
object Ground {
    private const val DN = 128
    private val detail: FloatArray by lazy { makeDetail() }

    // зацикленный мелкий шум (зерно травы и песка), значения около [-1, 1]
    private fun makeDetail(): FloatArray {
        val a = FloatArray(DN * DN)
        fun wrapNoise(x: Double, y: Double, p: Int, s: Int): Double {
            val xi = floor(x).toInt()
            val yi = floor(y).toInt()
            val xf = x - xi
            val yf = y - yi
            fun h(i: Int, j: Int) = hash2(Math.floorMod(i, p), Math.floorMod(j, p), s)
            val u = xf * xf * (3 - 2 * xf)
            val v = yf * yf * (3 - 2 * yf)
            val a0 = h(xi, yi)
            val b0 = h(xi + 1, yi)
            val c0 = h(xi, yi + 1)
            val d0 = h(xi + 1, yi + 1)
            return a0 + (b0 - a0) * u + (c0 - a0) * v + (a0 - b0 - c0 + d0) * u * v
        }
        for (y in 0 until DN) for (x in 0 until DN) {
            val n = wrapNoise(x / 4.0, y / 4.0, DN / 4, 1) * 0.45 + wrapNoise(x / 2.0, y / 1.2, DN / 2, 2) * 0.35 +
                wrapNoise(x.toDouble(), y.toDouble(), DN, 3) * 0.2
            a[y * DN + x] = ((n - 0.5) * 2.4).toFloat()
        }
        return a
    }

    private fun det(x: Int, y: Int) = detail[(y and (DN - 1)) * DN + (x and (DN - 1))]

    /**
     * Залить out (ширина stride) землёй для клеток [tx0, tx0+tw) × [ty0, ty0+th), начиная с пикселя (ox, oy),
     * масштаб s пикселей на мировой пиксель.
     */
    fun paint(G: Game, tx0: Int, ty0: Int, tw: Int, th: Int, s: Int, out: IntArray, stride: Int, ox: Int, oy: Int, maxW: Int = Int.MAX_VALUE, maxH: Int = Int.MAX_VALUE) {
        // локальные поля по центрам клеток с запасом 3 клетки
        val P = 3
        val lw = tw + 2 * P
        val lh = th + 2 * P
        val water = FloatArray(lw * lh)
        val sand = FloatArray(lw * lh)
        val forest = FloatArray(lw * lh)
        val height = FloatArray(lw * lh)
        val gcol = FloatArray(lw * lh * 3)
        for (j in 0 until lh) for (i in 0 until lw) {
            val tx = clampI(tx0 - P + i, 0, G.W - 1)
            val ty = clampI(ty0 - P + j, 0, G.H - 1)
            val t = G.terrain[ty * G.W + tx].toInt()
            val k = j * lw + i
            water[k] = if (t == T_WATER) 1f else 0f
            sand[k] = if (t == T_SAND || t == T_WATER) 1f else 0f
            forest[k] = if (t == T_FOREST) 1f else 0f
            val gx = tx0 - P + i
            val gy = ty0 - P + j
            height[k] = (fbm(gx / 9.0, gy / 9.0, G.seed + 211) * 1.6 + fbm(gx / 3.5, gy / 3.5, G.seed + 223) * 0.35).toFloat()
            val g = fbm(gx / 7.0, gy / 7.0, G.seed + 50)
            val dry = fbm(gx / 16.0, gy / 16.0, G.seed + 61)
            gcol[k * 3] = (88 + g * 34 + dry * 26).toFloat()
            gcol[k * 3 + 1] = (136 + g * 26 - dry * 6).toFloat()
            gcol[k * 3 + 2] = (56 + g * 12 - dry * 6).toFloat()
        }
        // наклоны рельефа по центральным разностям: плавный свет без ступенек на стыках клеток
        val gxA = FloatArray(lw * lh)
        val gyA = FloatArray(lw * lh)
        for (j in 0 until lh) for (i in 0 until lw) {
            val k = j * lw + i
            gxA[k] = (height[j * lw + min(lw - 1, i + 1)] - height[j * lw + max(0, i - 1)]) * 0.5f
            gyA[k] = (height[min(lh - 1, j + 1) * lw + i] - height[max(0, j - 1) * lw + i]) * 0.5f
        }
        // размытая вода — для глубины
        val deep = blur(blur(water, lw, lh), lw, lh)
        // контуры берега и песка: смесь резкого и размытого поля, чтобы углы скруглялись
        val wb = blur(water, lw, lh)
        val sb = blur(sand, lw, lh)
        for (k in water.indices) { water[k] = water[k] * 0.55f + wb[k] * 0.45f; sand[k] = sand[k] * 0.5f + sb[k] * 0.5f }
        val forestB = blur(forest, lw, lh)
        val T = TILE * s
        val W = min(tw * T, maxW)
        val H = min(th * T, maxH)
        val inv = 1f / T
        for (py in 0 until H) {
            val fy0 = (py + 0.5f) * inv - 0.5f + P
            val row = (oy + py) * stride + ox
            val wy = ty0 * TILE * s + py
            for (px in 0 until W) {
                val wx0 = tx0 * TILE * s + px
                // лёгкое искажение координат — берега и опушки становятся извилистыми
                val warpX = det(wx0 / (s * 5), wy / (s * 5) + 71) * 0.22f + det(wx0 / (s * 2), wy / (s * 2) + 13) * 0.06f
                val warpY = det(wx0 / (s * 5) + 33, wy / (s * 5)) * 0.22f + det(wx0 / (s * 2) + 91, wy / (s * 2)) * 0.06f
                val fx = (px + 0.5f) * inv - 0.5f + P + warpX
                val fy = fy0 + warpY
                val i0 = floor(fx).toInt().coerceIn(0, lw - 2)
                val ax = (fx - i0).coerceIn(0f, 1f)
                val j0 = floor(fy).toInt().coerceIn(0, lh - 2)
                val ay = (fy - j0).coerceIn(0f, 1f)
                val k = j0 * lw + i0
                val w00 = (1 - ax) * (1 - ay)
                val w10 = ax * (1 - ay)
                val w01 = (1 - ax) * ay
                val w11 = ax * ay
                fun bl(a: FloatArray) = a[k] * w00 + a[k + 1] * w10 + a[k + lw] * w01 + a[k + lw + 1] * w11
                val wx = wx0
                val d = det(wx / s, wy / s)
                val d2 = det(wx / s + 57, wy / s + 31)
                val wr = bl(water) + d2 * 0.045f
                val sr = bl(sand) + d2 * 0.09f
                // свет от рельефа: производная высоты по клетке
                val hx = bl(gxA)
                val hy = bl(gyA)
                // крупные пятна (сухая/сочная трава) и мелкое зерно
                val big = det(wx / (s * 6), wy / (s * 6)) * 0.6f + det(wx / (s * 3) + 40, wy / (s * 3) + 17) * 0.4f
                var light = 1f - (hx + hy) * 0.6f + big * 0.035f
                light = light.coerceIn(0.78f, 1.18f)
                var r: Float
                var g: Float
                var b: Float
                    val gr0 = gcol[k * 3] * w00 + gcol[(k + 1) * 3] * w10 + gcol[(k + lw) * 3] * w01 + gcol[(k + lw + 1) * 3] * w11
                    val gg0 = gcol[k * 3 + 1] * w00 + gcol[(k + 1) * 3 + 1] * w10 + gcol[(k + lw) * 3 + 1] * w01 + gcol[(k + lw + 1) * 3 + 1] * w11
                    val gb0 = gcol[k * 3 + 2] * w00 + gcol[(k + 1) * 3 + 2] * w10 + gcol[(k + lw) * 3 + 2] * w01 + gcol[(k + lw + 1) * 3 + 2] * w11
                    // трава: зерно и полосы
                    val tex = 1f + d * 0.13f
                    r = gr0 * tex + big * 9; g = gg0 * (1f + d * 0.1f) + big * 4; b = gb0 * tex
                    // травинки: тонкие тёмные и светлые штрихи
                    val st = det(wx / s * 3, wy / s / 2 + (wx / s) / 7)
                    if (st > 0.72f) { r *= 0.86f; g *= 0.9f; b *= 0.86f } else if (st < -0.78f) { r *= 1.08f; g *= 1.08f; b *= 1.02f }
                    // лесная подстилка
                    val f = bl(forestB)
                    if (f > 0f) {
                        val m = (f * 1.25f).coerceIn(0f, 0.85f)
                        r += (62 + d * 10 - r) * m; g += (88 + d * 12 - g) * m; b += (40 + d * 6 - b) * m
                    }
                    // песок: мягкая граница, у воды мокрый
                    val sm = ((sr - 0.42f) / 0.16f).coerceIn(0f, 1f)
                    if (sm > 0f) {
                        val wet = ((wr - 0.2f) / 0.3f).coerceIn(0f, 1f)
                        val sr2 = 206 + d * 12 - wet * 46
                        val sg2 = 184 + d * 10 - wet * 44
                        val sb2 = 128 + d * 8 - wet * 30
                        r += (sr2 - r) * sm; g += (sg2 - g) * sm; b += (sb2 - b) * sm
                    }
                    // тень вдоль берега
                    if (wr > 0.38f) { val e = (wr - 0.38f) / 0.12f; r *= 1 - e * 0.18f; g *= 1 - e * 0.15f; b *= 1 - e * 0.1f }
                    r *= light; g *= light; b *= light
                                if (wr > 0.45f) {
                    val lr = r
                    val lg = g
                    val lb = b
                    val dp = bl(deep)
                    val t = ((dp - 0.35f) * 1.6f).coerceIn(0f, 1f)
                    r = 78 - 46 * t; g = 150 - 72 * t; b = 168 - 34 * t
                    val rip = det(wx / s * 2 + (wy / s) / 3, wy / s * 3)
                    r += rip * 6; g += rip * 8; b += rip * 8
                    // пена и мелководье у берега
                    val e = ((wr - 0.52f) / 0.08f).coerceIn(0f, 1f)
                    val foam = (1 - e) * (0.55f + 0.45f * d)
                    r += (220 - r) * foam * 0.75f; g += (234 - g) * foam * 0.75f; b += (236 - b) * foam * 0.75f
                    val a = ((wr - 0.47f) / 0.05f).coerceIn(0f, 1f)
                    r = lr + (r - lr) * a; g = lg + (g - lg) * a; b = lb + (b - lb) * a
                }
                out[row + px] = (0xff shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
            }
        }
    }

    private fun blur(a: FloatArray, w: Int, h: Int): FloatArray {
        val o = FloatArray(a.size)
        for (j in 0 until h) for (i in 0 until w) {
            var s = 0f
            var n = 0
            for (dj in -1..1) for (di in -1..1) {
                val x = i + di
                val y = j + dj
                if (x < 0 || y < 0 || x >= w || y >= h) continue
                s += a[y * w + x]; n++
            }
            o[j * w + i] = s / n
        }
        return o
    }

    @Suppress("unused")
    private fun len(x: Float, y: Float) = sqrt(x * x + y * y).let { max(0f, min(1f, it)) }
}
