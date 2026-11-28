package ru.argeda.powder

import android.content.res.AssetManager
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * 3D-модели из assets/models (выгружены tools/sprites/bake3d.js).
 * Каждая модель: индексы треугольников, цвета вершин по командам и позиции вершин для каждого кадра
 * (int16, умноженные на 32). Позиции лежат в целочисленной текстуре: ширина 1024, rows строк на кадр,
 * шейдер достаёт вершину по gl_VertexID и смешивает два кадра анимации.
 */
class Model3D(
    val name: String, val n: Int, val tris: Int, val frames: Int, val rows: Int,
    val idx: ByteBuffer, val colors: List<ByteBuffer>, val pos: ShortBuffer,
) {
    // объекты OpenGL (заполняет поток отрисовки)
    var ibo = 0
    val cvbo = IntArray(2)
    val vao = IntArray(2)
    var tex = 0
}

object Models3D {
    // порядок важен: номер модели = индекс в этом списке
    const val M_UNIT = 0                    // + тип отряда
    val M_BLD = UNIT.size                   // + тип здания
    val M_TREE = M_BLD + BLD.size           // + вид дерева (8)
    val M_GOLD = M_TREE + 8
    val M_STONE = M_GOLD + 1
    val M_SCAFFOLD = M_STONE + 1
    val M_WALLSEG = M_SCAFFOLD + 1          // полупролёт стены от центра клетки к соседу
    val COUNT = M_WALLSEG + 1

    // кадры анимации отрядов
    const val F_IDLE = 0
    const val F_WALK = 1          // 8 кадров шага
    const val F_ACT = 9           // 3 кадра удара / выстрела
    const val F_DEAD = 12

    fun names(): List<String> {
        val l = ArrayList<String>()
        for (u in UNIT) l.add("u_" + u.key)
        for (b in BLD) l.add("b_" + b.key)
        for (k in 0 until 8) l.add("tree$k")
        l.add("rock_gold"); l.add("rock_stone"); l.add("scaffold"); l.add("wall_seg")
        return l
    }

    fun load(assets: AssetManager): Array<Model3D> {
        val idx = JSONObject(assets.open("models/models.json").bufferedReader().use { it.readText() })
        val raw = assets.open("models/models.bin").use { it.readBytes() }
        val bin = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val ms = idx.getJSONObject("models")
        return names().map { name ->
            val j = ms.getJSONObject(name)
            val n = j.getInt("n")
            val tris = j.getInt("tris")
            val frames = j.getInt("frames")
            val rows = (n + 1023) / 1024
            fun slice(off: Int, len: Int): ByteBuffer {
                val b = ByteBuffer.allocateDirect(len).order(ByteOrder.nativeOrder())
                b.put(raw, off, len); b.flip(); return b
            }
            val ib = slice(j.getInt("idx"), tris * 3 * 2)
            val cj = j.getJSONArray("colors")
            val cols = (0 until cj.length()).map { slice(cj.getInt(it), n * 3) }
            val pj = j.getJSONArray("pos")
            val pos = ByteBuffer.allocateDirect(frames * rows * 1024 * 3 * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            for (f in 0 until frames) {
                val off = pj.getInt(f)
                pos.position(f * rows * 1024 * 3)
                for (i in 0 until n * 3) pos.put(bin.getShort(off + i * 2))
            }
            pos.position(0)
            Model3D(name, n, tris, frames, rows, ib, cols, pos)
        }.toTypedArray()
    }
}
