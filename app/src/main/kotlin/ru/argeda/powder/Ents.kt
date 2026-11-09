package ru.argeda.powder

const val K_U = 0
const val K_B = 1
const val K_M = 2
const val K_TREE = 3

const val CMD_NONE = 0
const val CMD_MOVE = 1
const val CMD_AMOVE = 2
const val CMD_ATTACK = 3

abstract class Ent(val kind: Int) {
    var id = 0
    var team = -1
    var x = 0.0
    var y = 0.0
    var hp = 0.0
    var maxhp = 0.0
    var r = 0.0
    var dead = false
    var tx = 0
    var ty = 0
    var w = 1
    var h = 1
}

class Goal(
    val x: Double, val y: Double, val rect: DoubleArray?, val key: String, val seeds: IntArray,
    val arrive: Double, val ent: Ent?,
) {
    var best = 1e9
    var stuckT = 0.0
}

const val TASK_GATHER = 0
const val TASK_BUILD = 1

class Task(val type: Int, val res: Int = -1) {
    var tile = -1
    var mine: Mine? = null
    var farm: Bld? = null
    var b: Bld? = null
    var fail = 0
}

class Unt(val type: Int) : Ent(K_U) {
    var face = 1
    var dir = 0
    var anim = 0.0
    var cd = 0.0
    var goal: Goal? = null
    var target: Ent? = null
    var cmd = CMD_NONE
    var task: Task? = null
    var carry = 0
    var carryType = -1
    var ret = false // phase: false = go, true = ret
    var workT = 0.0
    var scanT = 0.0
    var chaseT = 0.0
    var moving = false
    var working = false
    var aimT = 0.0
    var hitT = 0.0
    var hasAmove = false
    var amX = 0.0
    var amY = 0.0
}

class Rally(val x: Double, val y: Double) {
    var res = -1
    var tile = -1
    var mine: Mine? = null
    var farm: Bld? = null
}

class Bld(val type: Int) : Ent(K_B) {
    var built = false
    var progress = 0.0
    val queue = ArrayList<Int>()
    var qt = 0.0
    var rally: Rally? = null
    var cd = 0.0
    var target: Ent? = null
    var scanT = 0.0
    var smokeT = -1.0
    var popBlocked = false
    var lastProg = -1.0
    var stallT = 0
}

class Mine(val res: Int, val amount0: Int) : Ent(K_M) {
    var amount = amount0
    val max = amount0
}

class TreeRef(val tile: Int) : Ent(K_TREE)

const val FX_SMOKE = 0
const val FX_FLASH = 1
const val FX_TRACER = 2
const val FX_DUST = 3
const val FX_EMBER = 4
const val FX_BOOM = 5

class Fx(val k: Int, var x: Double, var y: Double, val life: Double) {
    var vx = 0.0
    var vy = 0.0
    var t = 0.0
    var r = 0.0
    var dark = false
    var big = false
    var x2 = 0.0
    var y2 = 0.0
}

const val D_CORPSE = 0
const val D_RUIN = 1
const val D_CRATER = 2
const val D_STUMP = 3
const val D_PIT = 4

class Decal(val k: Int, val x: Double, val y: Double, val life: Double) {
    var t = 0.0
    var w = 0.0
    var h = 0.0
    var type = 0
    var team = 0
    var face = 1
    var dir = 0
}

class Proj(
    val sx: Double, val sy: Double, val tx: Double, val ty: Double, val dur: Double, val team: Int,
    val dmg: Double, val splash: Double, val src: Ent, val vs: Double,
) {
    var x = sx
    var y = sy
    var t = 0.0
    var done = false
}

class Marker(val x: Double, val y: Double, val col: Int) {
    var t = 0.0
}

class Ping(val x: Double, val y: Double) {
    var t = 3.0
}

class Placing(val type: Int) {
    var tx = 0
    var ty = 0
    var ok = false
}

class Stats {
    var killed = 0
    var lost = 0
    var trained = 0
    var built = 0
    var gathered = 0
}

class AI(val diff: Int, var nextAttack: Double) {
    var wave = 0
    var hasHome = false
    var hx = 0.0
    var hy = 0.0
}
