package ru.argeda.powder

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Сохранение и загрузка партии в JSON.
 * Сохраняется всё, что нужно для продолжения: карта, лес, разведка, ресурсы, здания с очередями,
 * войска с приказами (добыча, стройка, ремонт, цели движения и атаки), ИИ, рынок, камера.
 * Номера сущностей при загрузке выдаются заново, ссылки переводятся по таблице старых номеров.
 */
object Save {
    const val VERSION = 1

    private fun b64(a: ByteArray): String = Base64.getEncoder().encodeToString(a)
    private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
    private fun dArr(a: DoubleArray) = JSONArray().apply { for (v in a) put(v) }
    private fun iArr(a: IntArray) = JSONArray().apply { for (v in a) put(v) }
    private fun readD(js: JSONArray, out: DoubleArray) { for (i in out.indices) if (i < js.length()) out[i] = js.getDouble(i) }

    fun toJson(g: Game): JSONObject {
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("size", g.size); o.put("diff", g.diff); o.put("seed", g.seed)
        o.put("time", g.time); o.put("speed", g.speed)
        o.put("camX", g.camX); o.put("camY", g.camY); o.put("camZ", g.camZ)
        o.put("saved", System.currentTimeMillis())
        o.put("terrain", b64(g.terrain))
        o.put("wood", b64(ByteArray(g.wood.size) { g.wood[it].coerceIn(0, 127).toByte() }))
        o.put("explored", b64(g.explored))
        o.put("res", JSONArray().put(dArr(g.res[0])).put(dArr(g.res[1])))
        o.put("price", dArr(g.price))
        o.put("tech", JSONArray().put(iArr(g.tech[0])).put(iArr(g.tech[1])))
        o.put("stats", JSONArray().apply {
            for (s in g.stats) put(iArr(intArrayOf(s.killed, s.lost, s.trained, s.built, s.gathered)))
        })
        o.put("ai", JSONArray().apply {
            for (a in g.ai) put(if (a == null) JSONObject.NULL else JSONObject().apply {
                put("diff", a.diff); put("next", a.nextAttack); put("wave", a.wave); put("walls", a.walls)
                put("home", a.hasHome); put("hx", a.hx); put("hy", a.hy)
            })
        })
        o.put("mines", JSONArray().apply {
            for (m in g.mines) put(JSONObject().apply {
                put("id", m.id); put("res", m.res); put("a0", m.amount0); put("a", m.amount)
                put("tx", m.tx); put("ty", m.ty); put("dead", m.dead)
            })
        })
        o.put("blds", JSONArray().apply {
            for (b in g.blds) if (!b.dead) put(JSONObject().apply {
                put("id", b.id); put("type", b.type); put("team", b.team); put("tx", b.tx); put("ty", b.ty)
                put("hp", b.hp); put("built", b.built); put("prog", b.progress); put("qt", b.qt)
                put("queue", JSONArray().apply { for (q in b.queue) put(q) })
                b.target?.let { if (!it.dead) put("target", it.id) }
                b.rally?.let { r ->
                    put("rally", JSONObject().apply {
                        put("x", r.x); put("y", r.y); put("res", r.res); put("tile", r.tile)
                        r.mine?.let { put("mine", it.id) }; r.farm?.let { put("farm", it.id) }
                    })
                }
            })
        })
        o.put("units", JSONArray().apply {
            for (u in g.units) if (!u.dead) put(JSONObject().apply {
                put("id", u.id); put("type", u.type); put("team", u.team); put("x", u.x); put("y", u.y)
                put("hp", u.hp); put("face", u.face); put("dir", u.dir); put("cmd", u.cmd)
                put("carry", u.carry); put("ct", u.carryType); put("ret", u.ret)
                if (u.hasAmove) { put("amX", u.amX); put("amY", u.amY) }
                u.goal?.let { gl -> if (gl.rect == null) { put("gx", gl.x); put("gy", gl.y) } }
                u.target?.let { if (!it.dead && (it is Unt || it is Bld)) put("target", it.id) }
                u.task?.let { t ->
                    put("task", JSONObject().apply {
                        put("type", t.type); put("res", t.res); put("tile", t.tile)
                        t.mine?.let { put("mine", it.id) }; t.farm?.let { put("farm", it.id) }; t.b?.let { put("b", it.id) }
                    })
                }
            })
        })
        o.put("decals", JSONArray().apply {
            for (d in g.decals) if (d.k == D_STUMP || d.k == D_PIT || d.k == D_RUIN) put(JSONObject().apply {
                put("k", d.k); put("x", d.x); put("y", d.y); put("life", d.life); put("t", d.t); put("w", d.w); put("h", d.h)
            })
        })
        return o
    }

    /** null — файл повреждён или от несовместимой версии */
    fun fromJson(o: JSONObject, host: Host, vw: Double, vh: Double): Game? = try {
        load(o, host, vw, vh)
    } catch (e: Exception) {
        null
    }

    private fun load(o: JSONObject, host: Host, vw: Double, vh: Double): Game? {
        if (o.optInt("v") != VERSION) return null
        val g = Game(o.getInt("size"), o.getInt("diff"), false, host, vw, vh, blank = true, seed0 = o.getInt("seed"))
        val N = g.W * g.H
        unb64(o.getString("terrain")).copyInto(g.terrain, 0, 0, N)
        val wd = unb64(o.getString("wood"))
        for (i in 0 until N) g.wood[i] = wd[i].toInt()
        unb64(o.getString("explored")).copyInto(g.explored, 0, 0, N)
        g.time = o.getDouble("time")
        g.speed = o.optInt("speed", 1).coerceIn(1, 3)
        val rs = o.getJSONArray("res")
        readD(rs.getJSONArray(0), g.res[0]); readD(rs.getJSONArray(1), g.res[1])
        readD(o.getJSONArray("price"), g.price)
        val st = o.getJSONArray("stats")
        for (t in 0..1) {
            val a = st.getJSONArray(t)
            g.stats[t].killed = a.getInt(0); g.stats[t].lost = a.getInt(1); g.stats[t].trained = a.getInt(2)
            g.stats[t].built = a.getInt(3); g.stats[t].gathered = a.getInt(4)
        }
        val ai = o.getJSONArray("ai")
        for (t in 0..1) {
            val a = ai.optJSONObject(t) ?: continue
            g.ai[t] = AI(a.getInt("diff"), a.getDouble("next")).apply {
                wave = a.getInt("wave"); walls = a.optInt("walls", 0); hasHome = a.getBoolean("home"); hx = a.getDouble("hx"); hy = a.getDouble("hy")
            }
        }
        // залежи: номер как в игре (на клетках occ = -номер)
        val mineById = HashMap<Int, Mine>()
        val ms = o.getJSONArray("mines")
        for (k in 0 until ms.length()) {
            val j = ms.getJSONObject(k)
            val m = Mine(j.getInt("res"), j.getInt("a0"))
            m.id = j.getInt("id"); m.amount = j.getInt("a"); m.tx = j.getInt("tx"); m.ty = j.getInt("ty"); m.w = 2; m.h = 2
            m.x = (m.tx + 1.0) * TILE; m.y = (m.ty + 1.0) * TILE; m.dead = j.getBoolean("dead")
            if (!m.dead) for (y in m.ty until m.ty + 2) for (x in m.tx until m.tx + 2) if (g.inB(x, y)) g.occ[g.idx(x, y)] = -m.id
            g.mines.add(m); mineById[m.id] = m
        }
        // улучшения: до зданий, от них зависит прочность
        o.optJSONArray("tech")?.let { ta -> for (t in 0..1) { val a = ta.getJSONArray(t); for (i in 0 until minOf(a.length(), TECH.size)) g.tech[t][i] = a.getInt(i) } }
        // здания
        val bById = HashMap<Int, Bld>()
        val bjs = o.getJSONArray("blds")
        for (k in 0 until bjs.length()) {
            val j = bjs.getJSONObject(k)
            val b = g.placeBuilding(j.getInt("type"), j.getInt("team"), j.getInt("tx"), j.getInt("ty"), j.getBoolean("built"))
            b.hp = j.getDouble("hp"); b.progress = j.getDouble("prog"); b.qt = j.getDouble("qt")
            val q = j.getJSONArray("queue")
            for (i in 0 until q.length()) b.queue.add(q.getInt(i))
            bById[j.getInt("id")] = b
        }
        // войска
        val uById = HashMap<Int, Unt>()
        val ujs = o.getJSONArray("units")
        for (k in 0 until ujs.length()) {
            val j = ujs.getJSONObject(k)
            val u = g.spawnUnit(j.getInt("type"), j.getInt("team"), j.getDouble("x"), j.getDouble("y"))
            u.hp = j.getDouble("hp"); u.face = j.getInt("face"); u.dir = j.getInt("dir"); u.cmd = j.getInt("cmd")
            u.carry = j.getInt("carry"); u.carryType = j.getInt("ct"); u.ret = j.getBoolean("ret")
            if (j.has("amX")) { u.hasAmove = true; u.amX = j.getDouble("amX"); u.amY = j.getDouble("amY") }
            uById[j.getInt("id")] = u
        }
        fun ent(id: Int): Ent? = uById[id] ?: bById[id]
        // ссылки: цели, задания, точки сбора
        for (k in 0 until bjs.length()) {
            val j = bjs.getJSONObject(k)
            val b = bById[j.getInt("id")] ?: continue
            if (j.has("target")) b.target = ent(j.getInt("target"))
            j.optJSONObject("rally")?.let { r ->
                b.rally = Rally(r.getDouble("x"), r.getDouble("y")).apply {
                    res = r.getInt("res"); tile = r.getInt("tile")
                    if (r.has("mine")) mine = mineById[r.getInt("mine")]
                    if (r.has("farm")) farm = bById[r.getInt("farm")]
                }
            }
        }
        for (k in 0 until ujs.length()) {
            val j = ujs.getJSONObject(k)
            val u = uById[j.getInt("id")] ?: continue
            if (j.has("target")) u.target = ent(j.getInt("target"))
            j.optJSONObject("task")?.let { t ->
                val tk = Task(t.getInt("type"), t.getInt("res"))
                tk.tile = t.getInt("tile")
                if (t.has("mine")) tk.mine = mineById[t.getInt("mine")]
                if (t.has("farm")) tk.farm = bById[t.getInt("farm")]
                if (t.has("b")) tk.b = bById[t.getInt("b")]
                val ok = when (tk.type) {
                    TASK_BUILD, TASK_REPAIR -> tk.b != null
                    else -> true
                }
                if (ok) u.task = tk
            }
            if (u.task == null && u.target == null && j.has("gx")) g.goalPt(u, j.getDouble("gx"), j.getDouble("gy"))
        }
        val ds = o.optJSONArray("decals")
        if (ds != null) for (k in 0 until ds.length()) {
            val j = ds.getJSONObject(k)
            val d = Decal(j.getInt("k"), j.getDouble("x"), j.getDouble("y"), j.getDouble("life"))
            d.t = j.getDouble("t"); d.w = j.getDouble("w"); d.h = j.getDouble("h")
            g.decals.add(d)
        }
        g.computePop()
        g.updateFog()
        g.miniDirty = true
        g.camZ = o.optDouble("camZ", 1.0).coerceIn(0.45, 2.0)
        g.camX = o.optDouble("camX", 0.0); g.camY = o.optDouble("camY", 0.0)
        g.clampCam()
        return g
    }
}
