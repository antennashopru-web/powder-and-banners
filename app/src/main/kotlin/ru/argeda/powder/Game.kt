package ru.argeda.powder

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

const val S_SHOT = 0
const val S_CANNON = 1
const val S_IMPACT = 2
const val S_MELEE = 3
const val S_CLICK = 4
const val S_ORDER = 5
const val S_DONE = 6

interface Host {
    fun toast(msg: String)
    fun sfx(kind: Int, x: Double, y: Double)
    fun chunksDirty(tx: Int, ty: Int, w: Int, h: Int)
    fun ended(win: Boolean)
}

object NoHost : Host {
    override fun toast(msg: String) {}
    override fun sfx(kind: Int, x: Double, y: Double) {}
    override fun chunksDirty(tx: Int, ty: Int, w: Int, h: Int) {}
    override fun ended(win: Boolean) {}
}

const val STEP = 1.0 / 30

private class FieldEntry(val ver: Int, val d: DoubleArray, var used: Double)

/** Весь мир и вся логика игры. Не зависит от Android: можно гонять без экрана (ИИ против ИИ). */
class Game(val size: Int, val diff: Int, val auto: Boolean, var host: Host = NoHost, vw: Double = 800.0, vh: Double = 450.0) {
    val W = size
    val H = size
    val seed = (rnd() * 1e9).toInt()
    val terrain = ByteArray(W * H)
    val wood = IntArray(W * H)
    val occ = IntArray(W * H)
    var units = ArrayList<Unt>()
    var blds = ArrayList<Bld>()
    val mines = ArrayList<Mine>()
    var proj = ArrayList<Proj>()
    var fx = ArrayList<Fx>()
    var decals = ArrayList<Decal>()
    var markers = ArrayList<Marker>()
    val byId = HashMap<Int, Ent>()
    var nextId = 1
    var time = 0.0
    var fieldVer = 1
    private val fields = HashMap<String, FieldEntry>()
    val explored = ByteArray(W * H)
    val visible = ByteArray(W * H)
    val res = arrayOf(doubleArrayOf(300.0, 400.0, 200.0, 150.0), doubleArrayOf(300.0, 400.0, 200.0, 150.0))
    val pop = IntArray(2)
    val cap = IntArray(2)
    val stats = arrayOf(Stats(), Stats())
    var sel = ArrayList<Ent>()
    var placing: Placing? = null
    var amove = false
    var boxMode = false
    var camX = 0.0
    var camY = 0.0
    var camZ = 1.0
    var vw = vw
    var vh = vh
    var speed = 1
    var paused = false
    var over = false
    var winner = -1
    var fogT = 0.0
    var aiT = 0.0
    var endT = 0.0
    var lastAlert = -99.0
    var ping: Ping? = null
    var miniDirty = true
    var fogVer = 0
    var uiDirty = true
    private val uHead = IntArray(W * H)
    private var uNext = IntArray(4096)
    val ai = arrayOfNulls<AI>(2)

    init {
        val P = DIFF[diff]
        res[1][FOOD] += P.bonus.toDouble(); res[1][WOOD] += P.bonus.toDouble(); res[1][GOLD] += (P.bonus shr 1).toDouble()
        genMap()
        ai[1] = AI(diff, P.first)
        if (auto) ai[0] = AI(1, DIFF[1].first)
        computePop()
        updateFog()
        val h = blds.first { it.team == 0 }
        camZ = if (vh < 500) 0.95 else 1.15
        centerCam(h.x, h.y)
    }

    /** автопилот: за игрока тоже играет ИИ (для проверки сборки на эмуляторе) */
    fun autopilot() { if (ai[0] == null) ai[0] = AI(1, DIFF[1].first) }

    fun idx(tx: Int, ty: Int) = ty * W + tx
    fun inB(tx: Int, ty: Int) = tx >= 0 && ty >= 0 && tx < W && ty < H
    fun walk(i: Int): Boolean { val t = terrain[i].toInt(); return (t == T_GRASS || t == T_SAND) && occ[i] == 0 }
    fun walkXY(tx: Int, ty: Int) = inB(tx, ty) && walk(ty * W + tx)
    fun walkPx(x: Double, y: Double) = walkXY(fl(x / TILE), fl(y / TILE))

    // ============================================================ генерация карты
    private fun genMap() {
        val bx = intArrayOf(6, W - 10)
        val by = intArrayOf(H - 10, 6)
        val cx = DoubleArray(2) { bx[it] + 1.5 }
        val cy = DoubleArray(2) { by[it] + 1.5 }
        for (y in 0 until H) for (x in 0 until W) {
            val n = fbm(x / 8.0, y / 8.0, seed)
            val m = fbm(x / 13.0, y / 13.0, seed + 101)
            var t = T_GRASS
            if (m > 0.71) t = T_WATER else if (m > 0.665) t = T_SAND else if (n > 0.6) t = T_FOREST
            for (k in 0..1) if (hyp(x - cx[k], y - cy[k]) < 9) t = T_GRASS
            val i = y * W + x
            terrain[i] = t.toByte()
            if (t == T_FOREST) wood[i] = 120
        }
        for (k in 0..1) {
            val sx = sign(W / 2.0 - cx[k])
            val sy = sign(H / 2.0 - cy[k])
            blob(cx[k] + sx * 10, cy[k] + sy * 4, 2.6, T_FOREST)
            blob(cx[k] + sx * 3, cy[k] + sy * 11, 2.8, T_FOREST)
            blob(cx[k] - sx * 2, cy[k] - sy * 6.5, 1.8, T_FOREST)
            addMine(GOLD, Math.round(cx[k] + sx * 7).toInt() - 1, Math.round(cy[k] - sy * 1).toInt() - 1, 2500)
            addMine(STONE, Math.round(cx[k] - sx * 1).toInt() - 1, Math.round(cy[k] + sy * 7).toInt() - 1, 2500)
        }
        val extra = if (W >= 90) 4 else 3
        for (k in 0 until extra) {
            for (tries in 0 until 200) {
                val x = fl(W * 0.2 + rnd() * W * 0.6)
                val y = fl(H * 0.2 + rnd() * H * 0.6)
                if ((0..1).any { hyp(x - cx[it], y - cy[it]) < 16 }) continue
                val x2 = W - 2 - x
                val y2 = H - 2 - y
                if (areaFree(x, y, 2, 2) && areaFree(x2, y2, 2, 2)) {
                    val kind = if (k % 2 == 1) STONE else GOLD
                    addMine(kind, x, y, 3000); addMine(kind, x2, y2, 3000); break
                }
            }
        }
        for (team in 0..1) {
            placeBuilding(B_HALL, team, bx[team], by[team], true)
            val sx = sign(W / 2.0 - cx[team])
            val sy = sign(H / 2.0 - cy[team])
            for (i in 0 until 6) spawnUnit(U_PEASANT, team, (cx[team] + sx * (2.5 + (i % 3))) * TILE, (cy[team] + sy * 2.6 + (i / 3) * sy) * TILE)
            for (i in 0 until 3) spawnUnit(U_PIKE, team, (cx[team] - sx * 2.6) * TILE, (cy[team] + sy * (i - 1)) * TILE)
        }
        val f = computeField(intArrayOf(idx(fl(cx[1]), fl(cy[1]) + 2)))
        val pi = idx(fl(cx[0]), fl(cy[0]) - 2)
        if (f[pi] >= 1e8) carve(cx[0], cy[0], cx[1], cy[1])
    }

    private fun blob(cx: Double, cy: Double, r: Double, t: Int) {
        for (y in fl(cy - r - 1)..fl(cy + r + 1)) for (x in fl(cx - r - 1)..fl(cx + r + 1)) {
            if (!inB(x, y)) continue
            if (hyp(x - cx, y - cy) <= r + hash2(x, y, 9) * 0.8) {
                val i = idx(x, y)
                if (occ[i] == 0) { terrain[i] = t.toByte(); if (t == T_FOREST) wood[i] = 120 }
            }
        }
    }

    private fun areaFree(x: Int, y: Int, w: Int, h: Int): Boolean {
        for (j in y until y + h) for (i in x until x + w) {
            if (!inB(i, j)) return false
            val k = idx(i, j)
            if (occ[k] != 0 || terrain[k].toInt() == T_WATER) return false
        }
        return true
    }

    private fun addMine(kind: Int, x0: Int, y0: Int, amount: Int) {
        val x = clampI(x0, 1, W - 3)
        val y = clampI(y0, 1, H - 3)
        val m = Mine(kind, amount)
        m.id = mines.size + 1; m.tx = x; m.ty = y; m.w = 2; m.h = 2
        m.x = (x + 1.0) * TILE; m.y = (y + 1.0) * TILE
        for (j in y - 1 until y + 3) for (i in x - 1 until x + 3) {
            if (!inB(i, j)) continue
            val k = idx(i, j)
            if (i >= x && j >= y && i < x + 2 && j < y + 2) { terrain[k] = T_GRASS.toByte(); wood[k] = 0; occ[k] = -m.id }
            else if (terrain[k].toInt() != T_GRASS && occ[k] == 0) { terrain[k] = T_GRASS.toByte(); wood[k] = 0 }
        }
        mines.add(m)
    }

    private fun carve(ax: Double, ay: Double, bx: Double, by: Double) {
        val n = ceil(hyp(bx - ax, by - ay)).toInt()
        for (s in 0..n) {
            val x = ax + (bx - ax) * s / n
            val y = ay + (by - ay) * s / n
            for (dy in -1..1) for (dx in -1..1) {
                val tx = fl(x + dx)
                val ty = fl(y + dy)
                if (inB(tx, ty)) { val i = idx(tx, ty); if (occ[i] == 0) { terrain[i] = T_GRASS.toByte(); wood[i] = 0 } }
            }
        }
    }

    // ============================================================ сущности
    fun spawnUnit(type: Int, team: Int, x: Double, y: Double): Unt {
        val d = UNIT[type]
        val u = Unt(type)
        u.id = nextId++; u.team = team; u.x = x; u.y = y; u.hp = d.hp.toDouble(); u.maxhp = u.hp; u.r = d.r
        u.face = if (team != 0) -1 else 1; u.dir = if (team != 0) 4 else 0; u.anim = rnd() * 6; u.scanT = rnd() * 0.4
        if (!walkPx(x, y)) { val p = nearestWalkable(x, y); u.x = p[0]; u.y = p[1] }
        units.add(u); byId[u.id] = u
        return u
    }

    fun placeBuilding(type: Int, team: Int, tx: Int, ty: Int, built: Boolean): Bld {
        val d = BLD[type]
        val b = Bld(type)
        b.id = nextId++; b.team = team; b.tx = tx; b.ty = ty; b.w = d.w; b.h = d.h
        b.x = (tx + d.w / 2.0) * TILE; b.y = (ty + d.h / 2.0) * TILE
        b.hp = if (built) d.hp.toDouble() else max(1.0, d.hp * 0.1); b.maxhp = d.hp.toDouble()
        b.built = built; b.progress = if (built) d.time else 0.0; b.r = d.w * TILE / 2.0
        for (j in ty until ty + d.h) for (i in tx until tx + d.w) { val k = idx(i, j); occ[k] = b.id; terrain[k] = T_GRASS.toByte(); wood[k] = 0 }
        blds.add(b); byId[b.id] = b; fieldVer++; miniDirty = true
        host.chunksDirty(tx, ty, d.w, d.h)
        for (u in units) {
            val ux = fl(u.x / TILE)
            val uy = fl(u.y / TILE)
            if (ux >= tx && uy >= ty && ux < tx + d.w && uy < ty + d.h) { val p = exitPoint(b); u.x = p[0]; u.y = p[1]; u.goal = null }
        }
        return b
    }

    fun nearestWalkable(x: Double, y: Double): DoubleArray {
        val cx = fl(x / TILE)
        val cy = fl(y / TILE)
        for (r in 0 until 12) for (dy in -r..r) for (dx in -r..r) {
            if (max(abs(dx), abs(dy)) != r) continue
            if (walkXY(cx + dx, cy + dy)) return doubleArrayOf((cx + dx + 0.5) * TILE, (cy + dy + 0.5) * TILE)
        }
        return doubleArrayOf(x, y)
    }

    fun exitPoint(b: Bld): DoubleArray {
        val bx = b.tx + b.w / 2.0
        val by = (b.ty + b.h).toDouble()
        var best: DoubleArray? = null
        var bd = 1e9
        var r = 1
        while (r < 6 && best == null) {
            for (j in b.ty - r until b.ty + b.h + r) for (i in b.tx - r until b.tx + b.w + r) {
                if (i >= b.tx - r + 1 && i < b.tx + b.w + r - 1 && j >= b.ty - r + 1 && j < b.ty + b.h + r - 1) continue
                if (!walkXY(i, j)) continue
                val d = hyp(i + 0.5 - bx, j + 0.5 - by)
                if (d < bd) { bd = d; best = doubleArrayOf((i + 0.5) * TILE + (rnd() - 0.5) * 10, (j + 0.5) * TILE + (rnd() - 0.5) * 10) }
            }
            r++
        }
        return best ?: doubleArrayOf(b.x, b.y + b.h * TILE / 2.0 + 8)
    }

    fun entRect(e: Ent) = doubleArrayOf(e.tx * TILE.toDouble(), e.ty * TILE.toDouble(), (e.tx + e.w) * TILE.toDouble(), (e.ty + e.h) * TILE.toDouble())

    fun distRectPt(x: Double, y: Double, r: DoubleArray): Double {
        val dx = max(max(r[0] - x, 0.0), x - r[2])
        val dy = max(max(r[1] - y, 0.0), y - r[3])
        return hyp(dx, dy)
    }

    fun distTo(u: Unt, e: Ent): Double = if (e.kind == K_U) hyp(e.x - u.x, e.y - u.y) - e.r else distRectPt(u.x, u.y, entRect(e))

    fun canAfford(team: Int, c: IntArray): Boolean { val R = res[team]; return (0..3).all { R[it] >= c[it] } }
    fun pay(team: Int, c: IntArray, sign: Double = 1.0) { val R = res[team]; for (r in 0..3) if (c[r] != 0) R[r] -= c[r] * sign }

    fun computePop() {
        pop[0] = 0; pop[1] = 0; cap[0] = 0; cap[1] = 0
        for (u in units) if (!u.dead) pop[u.team] += UNIT[u.type].pop
        for (b in blds) if (!b.dead && b.built && BLD[b.type].pop > 0) cap[b.team] += BLD[b.type].pop
        cap[0] = min(POP_MAX, cap[0]); cap[1] = min(POP_MAX, cap[1])
    }

    fun canPlace(type: Int, tx: Int, ty: Int, team: Int): Boolean {
        val d = BLD[type]
        for (j in ty until ty + d.h) for (i in tx until tx + d.w) {
            if (!inB(i, j)) return false
            val k = idx(i, j)
            if (occ[k] != 0) return false
            val t = terrain[k].toInt()
            if (t != T_GRASS && t != T_SAND) return false
            if (team == 0 && explored[k].toInt() == 0) return false
        }
        return true
    }

    // ============================================================ поля путей
    private fun getField(g: Goal): DoubleArray {
        val f = fields[g.key]
        if (f != null && f.ver == fieldVer) { f.used = time; return f.d }
        val d = computeField(g.seeds)
        fields[g.key] = FieldEntry(fieldVer, d, time)
        if (fields.size > 90) {
            var ok: String? = null
            var ot = 1e18
            for ((k, v) in fields) if (v.used < ot) { ot = v.used; ok = k }
            if (ok != null) fields.remove(ok)
        }
        return d
    }

    fun computeField(seeds: IntArray): DoubleArray {
        val N = W * H
        val d = DoubleArray(N) { 1e9 }
        val isSeed = BooleanArray(N)
        val hp = Heap()
        for (s in seeds) { if (s < 0 || s >= N) continue; d[s] = 0.0; isSeed[s] = true; hp.push(s, 0.0) }
        while (hp.size > 0) {
            val i = hp.pop()
            val c = hp.lastC
            if (c > d[i]) continue
            val x = i % W
            val y = i / W
            for (k in 0 until 8) {
                val dx = DX[k]
                val dy = DY[k]
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                val ni = ny * W + nx
                if (!walk(ni)) continue
                if (dx != 0 && dy != 0) {
                    val a = y * W + nx
                    val b = ny * W + x
                    if (!(walk(a) || isSeed[a]) || !(walk(b) || isSeed[b])) continue
                }
                val nc = c + DC[k]
                if (nc < d[ni]) { d[ni] = nc; hp.push(ni, nc) }
            }
        }
        return d
    }

    fun goalPt(u: Unt, x0: Double, y0: Double, arrive: Double = 3.0) {
        val x = clamp(x0, 4.0, W * TILE - 4.0)
        val y = clamp(y0, 4.0, H * TILE - 4.0)
        var i = idx(fl(x / TILE), fl(y / TILE))
        if (!walk(i)) { val p = nearestWalkable(x, y); i = idx(fl(p[0] / TILE), fl(p[1] / TILE)) }
        u.goal = Goal(x, y, null, "p$i", intArrayOf(i), arrive, null)
    }

    fun goalEnt(u: Unt, e: Ent, arrive: Double = 6.0) {
        val tx = e.tx
        val ty = e.ty
        val w = e.w
        val h = e.h
        val seeds = IntArray(w * h)
        var n = 0
        for (j in ty until ty + h) for (i in tx until tx + w) seeds[n++] = idx(i, j)
        u.goal = Goal(0.0, 0.0, entRect(e), "r$tx,$ty,$w,$h", seeds, arrive, e)
    }

    private fun lineClear(x0: Double, y0: Double, x1: Double, y1: Double, rect: DoubleArray?): Boolean {
        val L = hyp(x1 - x0, y1 - y0)
        val n = ceil(L / 10).toInt()
        for (s in 1..n) {
            val x = x0 + (x1 - x0) * s / n
            val y = y0 + (y1 - y0) * s / n
            if (walkPx(x, y)) continue
            if (rect != null && x >= rect[0] - 1 && x <= rect[2] + 1 && y >= rect[1] - 1 && y <= rect[3] + 1) continue
            return false
        }
        return true
    }

    private fun moveBy(u: Unt, dx: Double, dy: Double): Boolean {
        val nx = u.x + dx
        val ny = u.y + dy
        if (walkPx(nx, ny)) { u.x = nx; u.y = ny; return true }
        if (dx != 0.0 && walkPx(nx, u.y)) { u.x = nx; return true }
        if (dy != 0.0 && walkPx(u.x, ny)) { u.y = ny; return true }
        return false
    }

    /** true, когда цель достигнута (или недостижима) */
    private fun steer(u: Unt, dt: Double): Boolean {
        val g = u.goal ?: return true
        val gx: Double
        val gy: Double
        val rect = g.rect
        if (rect != null) { gx = clamp(u.x, rect[0], rect[2]); gy = clamp(u.y, rect[1], rect[3]) } else { gx = g.x; gy = g.y }
        var dx = gx - u.x
        var dy = gy - u.y
        val dist = hyp(dx, dy)
        if (dist <= g.arrive + (if (rect != null) u.r else 0.0)) { u.goal = null; return true }
        var tx = gx
        var ty = gy
        var prog = dist
        if (!(dist < 170 && lineClear(u.x, u.y, gx, gy, rect))) {
            val f = getField(g)
            val ux = fl(u.x / TILE)
            val uy = fl(u.y / TILE)
            val i = uy * W + ux
            prog = f[i] * TILE
            var best = f[i]
            var bi = -1
            for (k in 0 until 8) {
                val ddx = DX[k]
                val ddy = DY[k]
                val nx = ux + ddx
                val ny = uy + ddy
                if (!inB(nx, ny)) continue
                val ni = ny * W + nx
                if (f[ni] < best && walk(ni)) {
                    if (ddx != 0 && ddy != 0 && (!walkXY(ux + ddx, uy) || !walkXY(ux, uy + ddy))) continue
                    best = f[ni]; bi = ni
                }
            }
            if (bi < 0) { if (f[i] >= 1e8) { u.goal = null; return true } }
            else { tx = (bi % W + 0.5) * TILE; ty = (bi / W + 0.5) * TILE }
        }
        if (prog < g.best - 2) { g.best = prog; g.stuckT = 0.0 }
        else { g.stuckT += dt; if (g.stuckT > (if (dist < 40) 1.0 else 3.0)) { u.goal = null; return true } }
        dx = tx - u.x; dy = ty - u.y
        val L = hyp(dx, dy).let { if (it == 0.0) 1.0 else it }
        val step = min(UNIT[u.type].spd * dt, L)
        moveBy(u, dx / L * step, dy / L * step)
        if (abs(dx) > 0.5) u.face = if (dx > 0) 1 else -1
        if (L > 2) u.dir = dirOf(dx, dy)
        u.moving = true; u.anim += dt
        return false
    }

    // ============================================================ пространственный хеш
    private fun rebuildHash() {
        uHead.fill(-1)
        if (uNext.size < units.size) uNext = IntArray(units.size * 2)
        for (k in units.indices) {
            val u = units[k]
            val tx = clampI(fl(u.x / TILE), 0, W - 1)
            val ty = clampI(fl(u.y / TILE), 0, H - 1)
            val i = ty * W + tx
            uNext[k] = uHead[i]; uHead[i] = k
        }
    }

    fun findEnemyAt(x: Double, y: Double, team: Int, radius: Double, unitsOnly: Boolean): Ent? {
        val r = ceil(radius / TILE).toInt()
        val cx = fl(x / TILE)
        val cy = fl(y / TILE)
        var best: Ent? = null
        var bd = radius * radius
        val x0 = max(0, cx - r)
        val x1 = min(W - 1, cx + r)
        val y0 = max(0, cy - r)
        val y1 = min(H - 1, cy + r)
        for (ty in y0..y1) for (tx in x0..x1) {
            var k = uHead[ty * W + tx]
            while (k >= 0) {
                val v = units[k]
                k = uNext[k]
                if (v.team == team || v.dead) continue
                val d2 = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y)
                if (d2 < bd) { bd = d2; best = v }
            }
        }
        if (best != null || unitsOnly) return best
        var bb: Ent? = null
        var bbd = radius
        for (b in blds) {
            if (b.team == team || b.dead) continue
            val d = distRectPt(x, y, entRect(b))
            if (d < bbd) { bbd = d; bb = b }
        }
        return bb
    }

    // ============================================================ обновление
    fun update(dt: Double) {
        time += dt
        if (units.any { it.dead }) units.removeAll { it.dead }
        if (blds.any { it.dead }) blds.removeAll { it.dead }
        computePop()
        rebuildHash()
        for (k in units.indices) { val u = units[k]; if (!u.dead) updateUnit(u, dt) }
        separate(dt)
        for (k in blds.indices) { val b = blds[k]; if (!b.dead) updateBuilding(b, dt) }
        updateProj(dt)
        for (f in fx) { f.t += dt; if (f.vx != 0.0 || f.vy != 0.0) { f.x += f.vx * dt; f.y += f.vy * dt } }
        fx.removeAll { it.t >= it.life }
        for (d in decals) d.t += dt
        decals.removeAll { it.t >= it.life }
        for (m in markers) m.t += dt
        markers.removeAll { it.t >= 0.8 }
        ping?.let { it.t -= dt; if (it.t <= 0) ping = null }
        if (sel.isNotEmpty() && sel.any { it.dead }) sel.removeAll { it.dead }
        fogT -= dt; if (fogT <= 0) { fogT = 0.25; updateFog() }
        aiT -= dt; if (aiT <= 0) { aiT = 1.0; if (ai[1] != null) aiTick(1); if (ai[0] != null) aiTick(0) }
        endT -= dt; if (endT <= 0) { endT = 1.0; checkEnd() }
    }

    private fun updateUnit(u: Unt, dt: Double) {
        val d = UNIT[u.type]
        u.cd -= dt; u.aimT -= dt; u.hitT -= dt; u.moving = false; u.working = false
        val tg = u.target
        if (tg != null && (tg.dead || tg.kind == K_M)) {
            u.target = null; u.goal = null
            if (u.cmd == CMD_AMOVE && u.hasAmove) goalPt(u, u.amX, u.amY, 10.0)
            else if (u.cmd == CMD_ATTACK) u.cmd = CMD_NONE
        }
        u.scanT -= dt
        if (u.scanT <= 0) {
            u.scanT = 0.35 + rnd() * 0.2
            if (u.type != U_PEASANT && u.cmd != CMD_MOVE) {
                val t = u.target
                if (t == null) {
                    val e = findEnemyAt(u.x, u.y, u.team, d.sight * TILE.toDouble(), false)
                    if (e != null) { u.target = e; u.goal = null }
                } else if (t.kind == K_B && u.cmd != CMD_ATTACK) {
                    val e = findEnemyAt(u.x, u.y, u.team, d.sight * TILE.toDouble(), true)
                    if (e != null) { u.target = e; u.goal = null }
                }
            }
        }
        if (u.target != null) doCombat(u, dt)
        else if (u.task != null) peasantTask(u, dt)
        else if (u.goal != null && steer(u, dt) && (u.cmd == CMD_MOVE || u.cmd == CMD_AMOVE)) u.cmd = CMD_NONE
        if (u.moving && !auto && fx.size < 600 && rnd() < (if (u.type == U_CAV) 0.35 else if (u.type == U_CANNON) 0.2 else 0.03)) {
            val f = Fx(FX_DUST, u.x - u.face * 6 + (rnd() - 0.5) * 6, u.y - 1, 0.7 + rnd() * 0.4)
            f.vx = -u.face * 6.0; f.vy = -4.0
            f.r = if (u.type == U_PEASANT || u.type == U_PIKE || u.type == U_MUSKET) 1.5 else 2.5
            fx.add(f)
        }
    }

    private fun dmgMult(a: Unt, t: Ent): Double {
        val d = UNIT[a.type]
        if (t.kind == K_B) return if (d.vsBld > 0) d.vsBld else if (d.ranged) 0.35 else 0.6
        if (t !is Unt) return 1.0
        var m = 1.0
        if (d.vsCav > 0 && UNIT[t.type].cav) m = d.vsCav
        if (a.type == U_CAV && (t.type == U_MUSKET || t.type == U_CANNON)) m = 1.4
        if (a.type == U_PIKE && t.type == U_CANNON) m = 1.5
        return m
    }

    private fun doCombat(u: Unt, dt: Double) {
        val t = u.target!!
        val d = UNIT[u.type]
        val dist = distTo(u, t)
        if (dist <= d.range) {
            u.goal = null
            if (abs(t.x - u.x) > 1) u.face = if (t.x > u.x) 1 else -1
            u.dir = dirOf(t.x - u.x, t.y - u.y)
            u.aimT = 0.4
            if (u.cd <= 0) { attack(u, t, dist); u.cd = d.cd * (0.85 + rnd() * 0.3) }
            return
        }
        if (u.cmd != CMD_ATTACK && dist > d.sight * TILE * 1.7) {
            u.target = null; u.goal = null
            if (u.cmd == CMD_AMOVE && u.hasAmove) goalPt(u, u.amX, u.amY, 10.0)
            return
        }
        u.chaseT -= dt
        if (u.goal == null || u.chaseT <= 0) {
            u.chaseT = 0.6
            val ar = max(2.0, d.range - 8)
            if (t.kind == K_B) goalEnt(u, t, ar) else goalPt(u, t.x, t.y, ar)
        }
        steer(u, dt)
    }

    private fun smoke(x: Double, y: Double, vx: Double, vy: Double, life: Double, r: Double, dark: Boolean = false) {
        val f = Fx(FX_SMOKE, x, y, life)
        f.vx = vx; f.vy = vy; f.r = r; f.dark = dark
        fx.add(f)
    }

    private fun flash(x: Double, y: Double, life: Double, big: Boolean = false) {
        val f = Fx(FX_FLASH, x, y, life); f.big = big; fx.add(f)
    }

    private fun tracer(x: Double, y: Double, x2: Double, y2: Double) {
        val f = Fx(FX_TRACER, x, y, 0.08); f.x2 = x2; f.y2 = y2; fx.add(f)
    }

    private fun boom(x: Double, y: Double, life: Double, r: Double) {
        val f = Fx(FX_BOOM, x, y, life); f.r = r; fx.add(f)
    }

    private fun attack(u: Unt, t: Ent, dist: Double) {
        val d = UNIT[u.type]
        val m = dmgMult(u, t)
        if (u.type == U_MUSKET) {
            val mx = u.x + u.face * 12
            val my = u.y - 10
            flash(mx, my, 0.12)
            for (i in 0 until 3) smoke(mx + u.face * i * 3, my, u.face * (8 + rnd() * 10), -6 - rnd() * 6, 1.4 + rnd(), 4 + rnd() * 3)
            tracer(mx, my, t.x, if (t.kind == K_U) t.y - 8 else t.y)
            host.sfx(S_SHOT, u.x, u.y)
            val hitP = clamp(0.95 - dist / 450, 0.45, 0.95)
            if (rnd() < hitP) damage(t, d.atk * m, u)
        } else if (u.type == U_CANNON) {
            val tx = t.x + (rnd() - 0.5) * 22
            val ty = t.y + (rnd() - 0.5) * 22
            val sx = u.x + u.face * 14
            val sy = u.y - 9
            proj.add(Proj(sx, sy, tx, ty, max(0.35, dist / 330), u.team, d.atk, d.splash, u, d.vsBld))
            for (i in 0 until 5) smoke(sx, sy, u.face * (10 + rnd() * 25), -8 - rnd() * 10, 1.8 + rnd(), 6 + rnd() * 5)
            flash(sx, sy, 0.15, true)
            host.sfx(S_CANNON, u.x, u.y)
        } else {
            damage(t, d.atk * m * (0.85 + rnd() * 0.3), u)
            u.hitT = 0.25
            if (rnd() < 0.5) host.sfx(S_MELEE, u.x, u.y)
        }
    }

    fun damage(t: Ent, amt: Double, src: Ent?) {
        if (t.dead) return
        t.hp -= amt
        if (t is Unt && src != null && !src.dead && src.kind == K_U && t.target == null && t.cmd != CMD_MOVE) {
            if (t.type != U_PEASANT || t.task == null) t.target = src
        }
        if (t.team == 0 && time - lastAlert > 15) {
            lastAlert = time; host.toast("⚔ Нас атакуют!"); ping = Ping(t.x, t.y)
        }
        if (t.hp <= 0) kill(t, src)
    }

    fun kill(t: Ent, src: Ent?) {
        t.dead = true; t.hp = 0.0
        if (src != null && src.team != t.team && src.team >= 0) stats[src.team].killed++
        if (t.team >= 0) stats[t.team].lost++
        if (t is Unt) {
            val d = Decal(D_CORPSE, t.x, t.y, 40.0)
            d.type = t.type; d.team = t.team; d.face = t.face; d.dir = t.dir
            decals.add(d)
        } else if (t is Bld) {
            for (j in t.ty until t.ty + t.h) for (i in t.tx until t.tx + t.w) occ[idx(i, j)] = 0
            fieldVer++; miniDirty = true
            val d = Decal(D_RUIN, t.tx * TILE.toDouble(), t.ty * TILE.toDouble(), 90.0)
            d.w = t.w * TILE.toDouble(); d.h = t.h * TILE.toDouble()
            decals.add(d)
            for (i in 0 until 14) smoke(t.x + (rnd() - 0.5) * t.w * TILE, t.y + (rnd() - 0.5) * t.h * TILE, (rnd() - 0.5) * 10, -10 - rnd() * 15, 2 + rnd() * 2, 8 + rnd() * 8, true)
            boom(t.x, t.y, 0.5, 40.0)
            host.sfx(S_CANNON, t.x, t.y)
            if (t.team == 0) host.toast("Разрушено: " + BLD[t.type].name)
        }
    }

    private fun updateProj(dt: Double) {
        for (p in proj) {
            p.t += dt
            val k = min(1.0, p.t / p.dur)
            p.x = p.sx + (p.tx - p.sx) * k; p.y = p.sy + (p.ty - p.sy) * k
            if (p.t >= p.dur) {
                p.done = true
                boom(p.tx, p.ty, 0.4, 22.0)
                for (i in 0 until 4) smoke(p.tx, p.ty, (rnd() - 0.5) * 30, -10 - rnd() * 10, 1.2, 5 + rnd() * 4, true)
                decals.add(Decal(D_CRATER, p.tx, p.ty, 25.0))
                val r = ceil(p.splash / TILE).toInt()
                val cx = fl(p.tx / TILE)
                val cy = fl(p.ty / TILE)
                val hit = ArrayList<Unt>()
                for (ty in cy - r..cy + r) for (tx in cx - r..cx + r) {
                    if (!inB(tx, ty)) continue
                    var q = uHead[ty * W + tx]
                    while (q >= 0) {
                        val v = units.getOrNull(q)
                        q = uNext[q]
                        if (v != null && v.team != p.team && !v.dead) hit.add(v)
                    }
                }
                for (v in hit) { val d = hyp(v.x - p.tx, v.y - p.ty); if (d < p.splash) damage(v, p.dmg * (1 - d / p.splash * 0.6), p.src) }
                for (b in ArrayList(blds)) if (b.team != p.team && !b.dead && distRectPt(p.tx, p.ty, entRect(b)) < 10) damage(b, p.dmg * (if (p.vs > 0) p.vs else 1.0), p.src)
                host.sfx(S_IMPACT, p.tx, p.ty)
            }
        }
        proj.removeAll { it.done }
    }

    private fun separate(dt: Double) {
        for (k in units.indices) {
            val u = units[k]
            if (u.dead) continue
            val cx = fl(u.x / TILE)
            val cy = fl(u.y / TILE)
            for (ty in max(0, cy - 1)..min(H - 1, cy + 1)) for (tx in max(0, cx - 1)..min(W - 1, cx + 1)) {
                var q = uHead[ty * W + tx]
                while (q >= 0) {
                    val qq = q
                    q = uNext[q]
                    if (qq <= k) continue
                    val v = units[qq]
                    if (v.dead) continue
                    var dx = v.x - u.x
                    var dy = v.y - u.y
                    val rr = u.r + v.r
                    val d2 = dx * dx + dy * dy
                    if (d2 >= rr * rr) continue
                    var d = sqrt(d2)
                    if (d < 0.01) { dx = rnd() - 0.5; dy = rnd() - 0.5; d = hyp(dx, dy) }
                    val push = min((rr - d) * 0.5, 60 * dt)
                    val nx = dx / d * push
                    val ny = dy / d * push
                    val wu = if (u.working) 0.2 else if (u.moving) 0.6 else 1.0
                    val wv = if (v.working) 0.2 else if (v.moving) 0.6 else 1.0
                    moveBy(u, -nx * wu, -ny * wu); moveBy(v, nx * wv, ny * wv)
                }
            }
        }
    }

    // ============================================================ крестьяне
    private fun sourceOf(t: Task): Ent? = when (t.res) {
        WOOD -> TreeRef(t.tile).also { it.tx = t.tile % W; it.ty = t.tile / W }
        FOOD -> t.farm
        else -> t.mine
    }

    private fun sourceValid(t: Task, team: Int): Boolean = when (t.res) {
        WOOD -> t.tile >= 0 && terrain[t.tile].toInt() == T_FOREST && wood[t.tile] > 0
        FOOD -> t.farm.let { it != null && !it.dead && it.built && it.team == team }
        else -> t.mine.let { it != null && !it.dead && it.amount > 0 }
    }

    fun farmWorkers(f: Bld, except: Unt?): Int {
        var n = 0
        for (u in units) if (u !== except && !u.dead && u.task?.farm === f) n++
        return n
    }

    fun nearestTree(x: Double, y: Double, rad: Int): Int {
        val cx = fl(x / TILE)
        val cy = fl(y / TILE)
        var best = -1
        var bd = 1e18
        val ts = (time * 3).toInt()
        for (ty in max(0, cy - rad)..min(H - 1, cy + rad)) for (tx in max(0, cx - rad)..min(W - 1, cx + rad)) {
            val i = ty * W + tx
            if (terrain[i].toInt() != T_FOREST || wood[i] == 0) continue
            if (!(walkXY(tx + 1, ty) || walkXY(tx - 1, ty) || walkXY(tx, ty + 1) || walkXY(tx, ty - 1))) continue
            val d = ((tx - cx) * (tx - cx) + (ty - cy) * (ty - cy)) * (1 + hash2(tx, ty, ts) * 0.6)
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    fun nearestMine(x: Double, y: Double, r: Int, rad: Int): Mine? {
        var best: Mine? = null
        var bd = rad * TILE.toDouble()
        for (m in mines) { if (m.dead || m.res != r) continue; val d = hyp(m.x - x, m.y - y); if (d < bd) { bd = d; best = m } }
        return best
    }

    private fun nearestFarm(u: Unt, x: Double, y: Double): Bld? {
        var best: Bld? = null
        var bd = 1e9
        for (b in blds) {
            if (b.dead || b.team != u.team || b.type != B_FARM || !b.built) continue
            if (farmWorkers(b, u) >= 6) continue
            val d = hyp(b.x - x, b.y - y)
            if (d < bd) { bd = d; best = b }
        }
        return best
    }

    private fun findNewSource(u: Unt): Boolean {
        val t = u.task!!
        if (t.res == WOOD) {
            val ox = if (t.tile >= 0) (t.tile % W + 0.5) * TILE else u.x
            val oy = if (t.tile >= 0) (t.tile / W + 0.5) * TILE else u.y
            val i = nearestTree(ox, oy, 12)
            if (i >= 0) { t.tile = i; u.goal = null; return true }
            return false
        }
        if (t.res == FOOD) { val f = nearestFarm(u, u.x, u.y); if (f != null) { t.farm = f; u.goal = null; return true }; return false }
        val m = nearestMine(u.x, u.y, t.res, 30)
        if (m != null) { t.mine = m; u.goal = null; return true }
        return false
    }

    private fun nearestDrop(u: Unt): Bld? {
        var best: Bld? = null
        var bd = 1e9
        for (b in blds) {
            if (b.dead || b.team != u.team || !b.built || !BLD[b.type].drop) continue
            val d = distRectPt(u.x, u.y, entRect(b))
            if (d < bd) { bd = d; best = b }
        }
        return best
    }

    fun orderGather(u: Unt, r: Int, tile: Int = -1, mine: Mine? = null, farm: Bld? = null) {
        u.target = null; u.goal = null; u.cmd = CMD_NONE; u.workT = 0.0
        if (u.carryType != r) u.carry = 0
        u.carryType = r
        val t = Task(TASK_GATHER, r)
        t.tile = tile; t.mine = mine; t.farm = farm
        u.task = t
        u.ret = u.carry >= CARRY
    }

    fun orderBuild(u: Unt, b: Bld) {
        u.target = null; u.goal = null; u.cmd = CMD_NONE
        u.task = Task(TASK_BUILD).also { it.b = b }
    }

    private fun deposit(u: Unt) {
        if (u.carry > 0 && u.carryType >= 0) {
            val a = ai[u.team]
            val mult = if (a != null) DIFF[a.diff].mult else 1.0
            val amt = Math.round(u.carry * mult).toInt()
            res[u.team][u.carryType] += amt.toDouble(); stats[u.team].gathered += amt
        }
        u.carry = 0
    }

    private fun peasantTask(u: Unt, dt: Double) {
        val t = u.task!!
        if (t.type == TASK_BUILD) {
            val b = t.b!!
            if (b.dead || b.built) { u.task = null; u.goal = null; afterBuild(u, b); return }
            if (distTo(u, b) <= u.r + 8) {
                u.goal = null; u.working = true; u.anim += dt
                if (abs(b.x - u.x) > 1) u.face = if (b.x > u.x) 1 else -1
                u.dir = dirOf(b.x - u.x, b.y - u.y)
                val bd = BLD[b.type]
                b.progress += dt; b.hp = min(b.maxhp, b.hp + b.maxhp * 0.9 * dt / bd.time)
                if (b.progress >= bd.time) finishBuilding(b)
            } else {
                if (u.goal == null) goalEnt(u, b, 6.0)
                if (steer(u, dt) && distTo(u, b) > u.r + 8) { t.fail++; if (t.fail > 6) u.task = null }
            }
            return
        }
        if (u.ret) {
            val dp = nearestDrop(u)
            if (dp == null) { u.task = null; u.goal = null; return }
            if (distTo(u, dp) <= u.r + 8) { deposit(u); u.ret = false; u.goal = null }
            else { if (u.goal == null || u.goal!!.ent !== dp) goalEnt(u, dp, 6.0); steer(u, dt) }
            return
        }
        if (!sourceValid(t, u.team)) {
            if (!findNewSource(u)) {
                if (u.carry > 0) { u.ret = true; u.goal = null } else { u.task = null; u.goal = null }
                return
            }
        }
        val src = sourceOf(t)!!
        if (distTo(u, src) <= u.r + 8) {
            u.goal = null; u.working = true; u.anim += dt
            val sx = (src.tx + src.w / 2.0) * TILE
            if (abs(sx - u.x) > 1) u.face = if (sx > u.x) 1 else -1
            val sy = (src.ty + src.h / 2.0) * TILE
            u.dir = dirOf(sx - u.x, sy - u.y)
            u.workT += dt
            val gt = GTIME[t.res]
            while (u.workT >= gt) {
                u.workT -= gt; u.carry++
                if (t.res == WOOD) {
                    wood[t.tile]--
                    if (wood[t.tile] == 0) {
                        terrain[t.tile] = T_GRASS.toByte(); host.chunksDirty(src.tx, src.ty, 1, 1); miniDirty = true
                        decals.add(Decal(D_STUMP, (src.tx + 0.5) * TILE, (src.ty + 0.6) * TILE, 9999.0))
                        break
                    }
                } else if (t.res != FOOD) {
                    val mn = t.mine!!
                    mn.amount--
                    if (mn.amount <= 0) { exhaustMine(mn); break }
                }
                if (u.carry >= CARRY) break
            }
            if (u.carry >= CARRY) { u.ret = true; u.goal = null }
        } else {
            if (u.goal == null) goalEnt(u, src, 6.0)
            if (steer(u, dt) && distTo(u, src) > u.r + 8) {
                t.fail++
                if (t.fail > 3) {
                    t.fail = 0
                    if (t.res == WOOD) { val i = nearestTree(u.x, u.y, 10); if (i >= 0 && i != t.tile) t.tile = i }
                }
            }
        }
    }

    private fun exhaustMine(m: Mine) {
        m.dead = true
        for (j in m.ty until m.ty + 2) for (i in m.tx until m.tx + 2) occ[idx(i, j)] = 0
        fieldVer++; miniDirty = true
        decals.add(Decal(D_PIT, m.x, m.y, 9999.0))
    }

    private fun finishBuilding(b: Bld) {
        val d = BLD[b.type]
        b.built = true; b.progress = d.time; b.hp = min(b.maxhp, b.hp + b.maxhp * 0.1)
        stats[b.team].built++; miniDirty = true
        if (b.team == 0) { host.toast("Построено: " + d.name); host.sfx(S_DONE, Double.NaN, Double.NaN); if (sel.contains(b)) uiDirty = true }
    }

    private fun afterBuild(u: Unt, b: Bld) {
        if (b.dead) return
        if (b.type == B_FARM && farmWorkers(b, u) < 6) { orderGather(u, FOOD, farm = b); return }
        for (o in blds) if (!o.dead && !o.built && o.team == u.team && hyp(o.x - u.x, o.y - u.y) < 10 * TILE) { orderBuild(u, o); return }
        if (b.type == B_STORE || b.type == B_HALL) {
            val i = nearestTree(b.x, b.y, 10)
            if (i >= 0) { orderGather(u, WOOD, tile = i); return }
            val m = nearestMine(b.x, b.y, GOLD, 10) ?: nearestMine(b.x, b.y, STONE, 10)
            if (m != null) orderGather(u, m.res, mine = m)
        }
    }

    // ============================================================ здания
    fun enqueue(b: Bld, type: Int): Boolean {
        val d = UNIT[type]
        if (b.queue.size >= 6) return false
        if (!canAfford(b.team, d.cost)) return false
        pay(b.team, d.cost); b.queue.add(type)
        return true
    }

    private fun updateBuilding(b: Bld, dt: Double) {
        if (!b.built) return
        if (!auto && fx.size < 600) {
            if (b.smokeT == 0.0 || b.smokeT == -1.0) b.smokeT = rnd()
            b.smokeT -= dt
            if (b.smokeT <= 0) {
                val x0 = b.tx * TILE.toDouble()
                val y0 = b.ty * TILE.toDouble()
                if (b.type == B_HOUSE) { b.smokeT = 0.5; smoke(x0 + 47, y0 + 1, 4 + rnd() * 3, -9.0, 2.6, 2.5) }
                else if (b.type == B_FOUNDRY) { b.smokeT = 0.3; smoke(x0 + 76, y0 - 8, 5 + rnd() * 4, -12.0, 3.0, 4.0, true) }
                else b.smokeT = 2.0
                if (b.hp < b.maxhp * 0.55) {
                    b.smokeT = min(b.smokeT, 0.25)
                    smoke(b.x + (rnd() - 0.5) * b.w * 16, b.ty * TILE + 10.0, (rnd() - 0.3) * 8, -14.0, 2.5, 5.0, true)
                    val e = Fx(FX_EMBER, b.x + (rnd() - 0.5) * b.w * 16, b.ty * TILE + 14.0, 0.9)
                    e.vx = (rnd() - 0.5) * 14; e.vy = -24.0
                    fx.add(e)
                }
            }
        }
        if (b.queue.isNotEmpty()) {
            val type = b.queue[0]
            val d = UNIT[type]
            if (pop[b.team] + d.pop <= cap[b.team]) {
                b.popBlocked = false
                b.qt += dt
                if (b.qt >= d.time) {
                    b.qt = 0.0; b.queue.removeAt(0)
                    val p = exitPoint(b)
                    val u = spawnUnit(type, b.team, p[0], p[1])
                    pop[b.team] += d.pop; stats[b.team].trained++
                    applyRally(u, b)
                    if (b.team == 0 && sel.contains(b)) uiDirty = true
                }
            } else {
                if (!b.popBlocked && b.team == 0) host.toast("Мало места: постройте дома")
                b.popBlocked = true
            }
        }
        val bd = BLD[b.type]
        if (bd.atk > 0) {
            b.cd -= dt; b.scanT -= dt
            val bt = b.target
            if (bt != null && (bt.dead || distRectPt(bt.x, bt.y, entRect(b)) > bd.range)) b.target = null
            if (b.target == null && b.scanT <= 0) { b.scanT = 0.5; b.target = findEnemyAt(b.x, b.y, b.team, bd.range + b.w * TILE / 2.0, true) }
            val tg = b.target
            if (tg != null && b.cd <= 0) {
                b.cd = bd.cd
                val mx = b.x
                val my = b.ty * TILE + 6.0
                flash(mx, my, 0.1)
                smoke(mx, my, (rnd() - 0.5) * 10, -8.0, 1.2, 4.0)
                tracer(mx, my, tg.x, tg.y - 8)
                host.sfx(S_SHOT, b.x, b.y)
                if (rnd() < 0.8) damage(tg, bd.atk, b)
            }
        }
    }

    private fun applyRally(u: Unt, b: Bld) {
        val r = b.rally
        if (r == null) { goalPt(u, u.x + (rnd() - 0.5) * 30, u.y + 20 + rnd() * 20); return }
        if (u.type == U_PEASANT && r.res >= 0) {
            if (r.res == WOOD) {
                val i = if (sourceValidTile(r.tile)) r.tile else nearestTree((r.tile % W + 0.5) * TILE, (r.tile / W + 0.5) * TILE, 10)
                if (i >= 0) { orderGather(u, WOOD, tile = i); return }
            } else if (r.res == FOOD) {
                val f = r.farm
                if (f != null && !f.dead) { orderGather(u, FOOD, farm = f); return }
            } else {
                val m = r.mine
                if (m != null && !m.dead) { orderGather(u, r.res, mine = m); return }
            }
        }
        goalPt(u, r.x + (rnd() - 0.5) * 24, r.y + (rnd() - 0.5) * 24)
        u.cmd = CMD_MOVE
    }

    private fun sourceValidTile(i: Int) = i >= 0 && terrain[i].toInt() == T_FOREST && wood[i] > 0

    // ============================================================ туман войны
    fun updateFog() {
        visible.fill(0)
        fun mark(x: Double, y: Double, r: Int) {
            val cx = fl(x / TILE)
            val cy = fl(y / TILE)
            val r2 = r * r
            for (dy in -r..r) {
                val ty = cy + dy
                if (ty < 0 || ty >= H) continue
                for (dx in -r..r) {
                    if (dx * dx + dy * dy > r2) continue
                    val tx = cx + dx
                    if (tx < 0 || tx >= W) continue
                    val i = ty * W + tx
                    visible[i] = 1; explored[i] = 1
                }
            }
        }
        for (u in units) if (u.team == 0) mark(u.x, u.y, UNIT[u.type].sight)
        for (b in blds) if (b.team == 0) mark(b.x, b.y, BLD[b.type].sight + 1)
        fogVer++
    }

    fun seen(e: Ent): Boolean {
        if (e.team == 0 || auto) return true
        val tx = clampI(fl(e.x / TILE), 0, W - 1)
        val ty = clampI(fl(e.y / TILE), 0, H - 1)
        if (e.kind == K_U) return visible[ty * W + tx].toInt() == 1
        return explored[e.ty * W + e.tx].toInt() == 1 || explored[ty * W + tx].toInt() == 1
    }

    // ============================================================ ИИ
    private val ORDER_T = intArrayOf(B_FARM, B_BARRACKS, B_FARM, B_STORE, B_FARM, B_TOWER, B_STABLE, B_BARRACKS, B_FARM, B_FOUNDRY, B_TOWER, B_FARM, B_STABLE)
    private val ORDER_AT = intArrayOf(0, 45, 90, 110, 170, 220, 240, 340, 360, 420, 480, 520, 600)
    private val ORDER_N = intArrayOf(1, 1, 2, 1, 3, 1, 1, 2, 4, 1, 2, 5, 2)

    private fun aiTick(team: Int) {
        val A = ai[team]!!
        val P = DIFF[A.diff]
        val R = res[team]
        val myUnits = units.filter { it.team == team && !it.dead }
        val peas = myUnits.filter { it.type == U_PEASANT }
        val army = myUnits.filter { it.type != U_PEASANT }
        val myB = blds.filter { it.team == team && !it.dead }
        val halls = myB.filter { it.type == B_HALL }
        if (halls.isNotEmpty()) { A.hx = halls[0].x; A.hy = halls[0].y; A.hasHome = true }
        if (!A.hasHome) { if (myUnits.isNotEmpty()) { A.hx = myUnits[0].x; A.hy = myUnits[0].y; A.hasHome = true } else return }
        fun count(t: Int) = myB.count { it.type == t }
        val pend = myB.filter { !it.built }
        val T = time / P.pace

        for (b in pend) {
            if (b.lastProg != b.progress) { b.lastProg = b.progress; b.stallT = 0 } else b.stallT++
            if (b.stallT > 45) { pay(team, BLD[b.type].cost, -0.5); kill(b, null) }
        }
        for (b in pend) {
            if (b.dead) continue
            val n = peas.count { it.task?.type == TASK_BUILD && it.task?.b === b }
            if (n < 2) {
                val cand = peas.filter { it.task?.type != TASK_BUILD }.sortedBy { hyp(it.x - b.x, it.y - b.y) }
                for (i in 0 until min(2 - n, cand.size)) orderBuild(cand[i], b)
            }
        }
        if (halls.isEmpty() && peas.isNotEmpty() && canAfford(team, BLD[B_HALL].cost) && pend.none { it.type == B_HALL }) aiBuild(team, B_HALL, A)
        if (cap[team] < POP_MAX && cap[team] - pop[team] < 6 && pend.none { it.type == B_HOUSE }) aiBuild(team, B_HOUSE, A)
        if (pend.size < 2) {
            for (k in ORDER_T.indices) {
                val type = ORDER_T[k]
                if (T >= ORDER_AT[k] && count(type) < ORDER_N[k]) { if (canAfford(team, BLD[type].cost)) aiBuild(team, type, A); break }
            }
        }
        val queued = halls.sumOf { h -> h.queue.count { it == U_PEASANT } }
        for (h in halls) if (h.built && peas.size + queued < P.peas && h.queue.size < 2) enqueue(h, U_PEASANT)
        val have = IntArray(4)
        for (u in peas) { val t = u.task; if (t != null && t.type == TASK_GATHER) have[t.res]++ }
        val late = count(B_BARRACKS) > 0
        val ratio = if (late) doubleArrayOf(0.32, 0.38, 0.1, 0.2) else doubleArrayOf(0.42, 0.43, 0.07, 0.08)
        if (R[STONE] > 500) ratio[STONE] = 0.02
        if (R[GOLD] > 900) ratio[GOLD] = 0.05
        if (R[FOOD] > 1200) ratio[FOOD] = 0.15
        if (R[WOOD] < 150) ratio[WOOD] += 0.15
        for (u in peas) {
            if (u.task != null || u.goal != null || u.target != null) continue
            var best = WOOD
            var bv = -1e9
            for (r in 0..3) { val v = ratio[r] * peas.size - have[r]; if (v > bv) { bv = v; best = r } }
            if (!aiAssign(u, best, A)) { if (!aiAssign(u, WOOD, A)) aiAssign(u, GOLD, A) }
            u.task?.let { have[it.res]++ }
        }
        val cannons = army.count { it.type == U_CANNON }
        val reserve = pend.isEmpty()
        val needHouse = cap[team] < POP_MAX && cap[team] - pop[team] < 6
        for (b in myB) {
            if (!b.built || b.queue.size >= 2) continue
            if (needHouse && R[WOOD] < 120 && b.type != B_HALL) continue
            if (b.type == B_BARRACKS) {
                val mus = army.count { it.type == U_MUSKET }
                val pk = army.count { it.type == U_PIKE }
                val want = if (mus < pk) U_MUSKET else U_PIKE
                if (!enqueue(b, want)) enqueue(b, if (want == U_MUSKET) U_PIKE else U_MUSKET)
            } else if (b.type == B_STABLE && reserve) enqueue(b, U_CAV)
            else if (b.type == B_FOUNDRY && cannons < P.cannons) enqueue(b, U_CANNON)
        }
        val threat = findEnemyAt(A.hx, A.hy, team, 15.0 * TILE, true)
        if (threat != null) {
            for (u in army) if (u.target == null && u.cmd != CMD_AMOVE) { u.target = threat; u.goal = null }
            if (army.size < 3) for (u in peas) if (hyp(u.x - threat.x, u.y - threat.y) < 5 * TILE && u.target == null) u.target = threat
        }
        val idle = army.filter { it.target == null && it.cmd != CMD_AMOVE }
        val need = P.wave + A.wave * P.grow
        if ((time >= A.nextAttack && idle.size >= need) || (idle.size >= 60 && time >= A.nextAttack - P.interval * 0.6)) {
            var tgt: Ent? = null
            var bd = 1e18
            for (b in blds) if (b.team != team && !b.dead) { val d = hyp(b.x - A.hx, b.y - A.hy); if (d < bd) { bd = d; tgt = b } }
            if (tgt == null) for (u in units) if (u.team != team && !u.dead) { val d = hyp(u.x - A.hx, u.y - A.hy); if (d < bd) { bd = d; tgt = u } }
            if (tgt != null) {
                groupMove(idle, tgt.x, tgt.y, CMD_AMOVE)
                A.wave++; A.nextAttack = time + P.interval
                if (team == 1 && !auto) host.toast("Разведка: враг выступил в поход!")
            }
        }
    }

    private fun aiAssign(u: Unt, r: Int, A: AI): Boolean {
        if (r == FOOD) { val f = nearestFarm(u, A.hx, A.hy); if (f != null) { orderGather(u, FOOD, farm = f); return true }; return false }
        if (r == WOOD) { val i = nearestTree(A.hx, A.hy, 22); if (i >= 0) { orderGather(u, WOOD, tile = i); return true }; return false }
        val m = nearestMine(A.hx, A.hy, r, 40)
        if (m != null) { orderGather(u, r, mine = m); return true }
        return false
    }

    private fun aiBuild(team: Int, type: Int, A: AI): Bld? {
        val d = BLD[type]
        if (!canAfford(team, d.cost)) return null
        var cx = fl(A.hx / TILE)
        var cy = fl(A.hy / TILE)
        if (type == B_STORE) { val i = nearestTree(A.hx, A.hy, 20); if (i >= 0) { cx = i % W; cy = i / W } }
        if (type == B_TOWER) { cx += sign(W / 2.0 - cx).toInt() * 5; cy += sign(H / 2.0 - cy).toInt() * 5 }
        for (r in (if (type == B_STORE) 2 else 3) until 26) {
            val cands = ArrayList<IntArray>()
            for (dy in -r..r) for (dx in -r..r) {
                if (max(abs(dx), abs(dy)) != r) continue
                val tx = cx + dx - d.w / 2
                val ty = cy + dy - d.h / 2
                if (spotOk(type, tx, ty)) cands.add(intArrayOf(tx, ty))
            }
            if (cands.isNotEmpty()) {
                val c = cands[(rnd() * cands.size).toInt()]
                pay(team, d.cost)
                return placeBuilding(type, team, c[0], c[1], false)
            }
        }
        return null
    }

    private fun spotOk(type: Int, tx: Int, ty: Int): Boolean {
        val d = BLD[type]
        for (j in ty - 1 until ty + d.h + 1) for (i in tx - 1 until tx + d.w + 1) {
            if (!inB(i, j)) return false
            val k = idx(i, j)
            val inner = i >= tx && j >= ty && i < tx + d.w && j < ty + d.h
            if (inner) { if (occ[k] != 0 || (terrain[k].toInt() != T_GRASS && terrain[k].toInt() != T_SAND)) return false }
            else if (!walk(k)) return false
        }
        return true
    }

    // ============================================================ приказы игрока
    fun groupMove(list: List<Unt>, x: Double, y: Double, cmd: Int) {
        if (list.isEmpty()) return
        var cx = 0.0
        var cy = 0.0
        for (u in list) { cx += u.x; cy += u.y }
        cx /= list.size; cy /= list.size
        var dx = x - cx
        var dy = y - cy
        val L = hyp(dx, dy)
        if (L < 1) { dx = 1.0; dy = 0.0 } else { dx /= L; dy /= L }
        val px = -dy
        val py = dx
        val sorted = list.sortedBy { FORM_ORDER[it.type] }
        val n = sorted.size
        val cols = if (n <= 3) n else max(3, ceil(sqrt(n * 2.2)).toInt())
        val sp = if (list.any { it.r > 7 }) 24.0 else 18.0
        val rows = (n + cols - 1) / cols
        for (k in 0 until n) {
            val u = sorted[k]
            val row = k / cols
            val col = k % cols
            val inRow = if (row == rows - 1) n - row * cols else cols
            val off = (col - (inRow - 1) / 2.0) * sp
            var sx = x + px * off - dx * row * sp
            var sy = y + py * off - dy * row * sp
            if (!walkPx(sx, sy)) { val p = nearestWalkable(sx, sy); sx = p[0]; sy = p[1] }
            u.task = null; u.target = null; u.cmd = cmd
            u.hasAmove = cmd == CMD_AMOVE; u.amX = sx; u.amY = sy
            goalPt(u, sx, sy, 3.0)
        }
    }

    fun ownSelUnits(): List<Unt> = sel.filter { it is Unt && it.team == 0 && !it.dead }.map { it as Unt }

    fun pickAt(wx: Double, wy: Double): Ent? {
        var best: Ent? = null
        var bd = 16.0
        for (u in units) {
            if (u.dead || !seen(u)) continue
            val d = hyp(u.x - wx, u.y - 8 - wy)
            if (d < bd + u.r * 0.5) { bd = d; best = u }
        }
        if (best != null) return best
        for (b in blds) {
            if (b.dead || !seen(b)) continue
            val r = entRect(b)
            if (wx >= r[0] && wx <= r[2] && wy >= r[1] - 14 && wy <= r[3]) return b
        }
        for (m in mines) {
            if (m.dead) continue
            val r = entRect(m)
            if (wx >= r[0] - 4 && wx <= r[2] + 4 && wy >= r[1] - 8 && wy <= r[3] + 4) return if (explored[idx(m.tx, m.ty)].toInt() != 0) m else null
        }
        val tx = fl(wx / TILE)
        val ty = fl((wy + 6) / TILE)
        if (inB(tx, ty) && terrain[idx(tx, ty)].toInt() == T_FOREST && explored[idx(tx, ty)].toInt() != 0) {
            val t = TreeRef(idx(tx, ty))
            t.tx = tx; t.ty = ty; t.x = (tx + 0.5) * TILE; t.y = (ty + 0.5) * TILE
            return t
        }
        return null
    }

    private var lastTapT = 0L
    private var lastTapId = 0

    fun tapWorld(wx: Double, wy: Double) {
        if (placing != null) { setGhost(wx, wy); return }
        val hit = pickAt(wx, wy)
        val sel0 = ownSelUnits()
        val now = System.nanoTime() / 1_000_000
        if (hit is Unt && hit.team == 0 && !amove) {
            if (lastTapId == hit.id && now - lastTapT < 350) {
                val v = viewRect()
                sel = ArrayList(units.filter { it.team == 0 && it.type == hit.type && it.x > v[0] && it.x < v[2] && it.y > v[1] && it.y < v[3] })
            } else sel = arrayListOf(hit)
            lastTapT = now; lastTapId = hit.id; uiDirty = true; host.sfx(S_CLICK, Double.NaN, Double.NaN); return
        }
        if (hit is Bld && hit.team == 0) {
            val peas = sel0.filter { it.type == U_PEASANT }
            if (peas.isNotEmpty() && peas.size == sel0.size) {
                if (!hit.built) { for (u in peas) orderBuild(u, hit); marker(hit.x, hit.y, 0xffe8b84a.toInt()); return }
                if (hit.type == B_FARM) { for (u in peas) orderGather(u, FOOD, farm = hit); marker(hit.x, hit.y, 0xffe8b84a.toInt()); return }
                if (BLD[hit.type].drop && peas.any { it.carry > 0 }) {
                    for (u in peas) if (u.carry > 0) {
                        if (u.task == null) u.task = Task(TASK_GATHER, u.carryType)
                        u.ret = true; u.goal = null
                    }
                    return
                }
            }
            if (sel.size == 1 && sel[0] === hit) return
            sel = arrayListOf(hit); uiDirty = true; host.sfx(S_CLICK, Double.NaN, Double.NaN); return
        }
        if (sel0.isNotEmpty()) {
            if (hit != null && (hit.kind == K_U || hit.kind == K_B) && hit.team == 1) {
                for (u in sel0) { u.task = null; u.goal = null; u.target = hit; u.cmd = CMD_ATTACK }
                marker(hit.x, hit.y - 6, 0xffff4a3a.toInt()); host.sfx(S_ORDER, Double.NaN, Double.NaN); amove = false; uiDirty = true; return
            }
            val peas = sel0.filter { it.type == U_PEASANT }
            val rest = sel0.filter { it.type != U_PEASANT }
            if (hit != null && peas.isNotEmpty() && !amove && (hit.kind == K_M || hit.kind == K_TREE)) {
                if (hit is Mine) for (u in peas) orderGather(u, hit.res, mine = hit)
                else if (hit is TreeRef) {
                    peas.forEachIndexed { k, u ->
                        var i = hit.tile
                        if (k > 0) {
                            val j = nearestTree(hit.tx * TILE + 16.0 + (k % 3 - 1) * 40, hit.ty * TILE + 16.0 + ((k / 3) % 3 - 1) * 40, 3)
                            if (j >= 0) i = j
                        }
                        orderGather(u, WOOD, tile = i)
                    }
                }
                marker(hit.x, hit.y, 0xffe8b84a.toInt())
                if (rest.isNotEmpty()) groupMove(rest, wx, wy, CMD_MOVE)
                host.sfx(S_ORDER, Double.NaN, Double.NaN); return
            }
            groupMove(sel0, wx, wy, if (amove) CMD_AMOVE else CMD_MOVE)
            marker(wx, wy, if (amove) 0xffff4a3a.toInt() else 0xff7cff6a.toInt()); host.sfx(S_ORDER, Double.NaN, Double.NaN)
            if (amove) { amove = false; uiDirty = true }
            return
        }
        val s0 = if (sel.size == 1) sel[0] else null
        if (s0 is Bld && s0.team == 0 && BLD[s0.type].trains.isNotEmpty()) {
            if (hit == null) { s0.rally = Rally(wx, wy); marker(wx, wy, 0xffe8b84a.toInt()); return }
            val r = Rally(hit.x, hit.y)
            if (hit is TreeRef) { r.res = WOOD; r.tile = hit.tile }
            else if (hit is Mine) { r.res = hit.res; r.mine = hit }
            else if (hit is Bld && hit.type == B_FARM && hit.team == 0) { r.res = FOOD; r.farm = hit }
            s0.rally = r; marker(r.x, r.y, 0xffe8b84a.toInt()); return
        }
        if (hit != null && (hit.kind == K_U || hit.kind == K_B || hit.kind == K_M)) { sel = arrayListOf(hit); uiDirty = true; return }
        if (sel.isNotEmpty()) { sel = ArrayList(); uiDirty = true }
    }

    fun marker(x: Double, y: Double, col: Int) { markers.add(Marker(x, y, col)) }

    fun boxSelect(x0: Double, y0: Double, x1: Double, y1: Double) {
        val a = min(x0, x1)
        val b = min(y0, y1)
        val c = max(x0, x1)
        val d = max(y0, y1)
        val list = units.filter { it.team == 0 && !it.dead && it.x >= a && it.x <= c && it.y - 6 >= b - 6 && it.y - 6 <= d + 6 }
        val mil = list.filter { it.type != U_PEASANT }
        sel = ArrayList(if (mil.isNotEmpty() && mil.size < list.size && mil.size >= 3) mil else list)
        uiDirty = true
        if (sel.isNotEmpty()) host.sfx(S_CLICK, Double.NaN, Double.NaN)
    }

    fun setGhost(wx: Double, wy: Double) {
        val p = placing ?: return
        val d = BLD[p.type]
        p.tx = fl(wx / TILE - d.w / 2.0 + 0.5); p.ty = fl(wy / TILE - d.h / 2.0 + 0.5)
        p.ok = canPlace(p.type, p.tx, p.ty, 0)
    }

    fun startPlacing(type: Int) {
        if (!canAfford(0, BLD[type].cost)) { host.toast("Не хватает: " + missing(BLD[type].cost)); return }
        placing = Placing(type)
        val v = viewRect()
        setGhost((v[0] + v[2]) / 2, (v[1] + v[3]) / 2)
        uiDirty = true
    }

    fun confirmPlace() {
        val p = placing ?: return
        if (!p.ok) { host.toast("Сюда нельзя: нужно свободное открытое место"); return }
        val d = BLD[p.type]
        if (!canAfford(0, d.cost)) { host.toast("Не хватает: " + missing(d.cost)); return }
        pay(0, d.cost)
        val b = placeBuilding(p.type, 0, p.tx, p.ty, false)
        for (u in ownSelUnits()) if (u.type == U_PEASANT) orderBuild(u, b)
        placing = null; uiDirty = true; host.sfx(S_ORDER, Double.NaN, Double.NaN)
    }

    fun missing(c: IntArray): String { val R = res[0]; return (0..3).filter { c[it] > R[it] }.joinToString(", ") { RNAME[it].lowercase() } }

    // ============================================================ конец игры
    private fun checkEnd() {
        if (over) return
        val alive = BooleanArray(2) { t ->
            blds.any { it.team == t && !it.dead } || units.any { it.team == t && !it.dead && it.type != U_PEASANT } ||
                units.any { it.team == t && !it.dead && it.type == U_PEASANT && blds.any { b -> b.team == t } }
        }
        val aliveU = BooleanArray(2) { t -> blds.any { it.team == t } || units.any { it.team == t } }
        if (!aliveU[1] || !alive[1]) endGame(true)
        else if (!aliveU[0] || !alive[0]) endGame(false)
    }

    private fun endGame(win: Boolean) {
        over = true; winner = if (win) 0 else 1
        host.ended(win)
    }

    // ============================================================ камера
    fun viewRect() = doubleArrayOf(camX, camY, camX + vw / camZ, camY + vh / camZ)

    fun clampCam() {
        val z = camZ
        val w = vw / z
        val h = vh / z
        val MW = W * TILE.toDouble()
        val MH = H * TILE.toDouble()
        camX = if (w >= MW) (MW - w) / 2 else clamp(camX, -60 / z, MW - w + 60 / z)
        camY = if (h >= MH) (MH - h) / 2 else clamp(camY, -50 / z, MH - h + 110 / z)
    }

    fun centerCam(x: Double, y: Double) { camX = x - vw / camZ / 2; camY = y - vh / camZ / 2; clampCam() }
}
