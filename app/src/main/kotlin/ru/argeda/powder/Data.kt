package ru.argeda.powder

import kotlin.math.atan2
import kotlin.math.floor
import kotlin.random.Random

// ============================================================ константы
const val TILE = 32
const val CH = 16
const val CPAD = 48
const val T_GRASS = 0
const val T_FOREST = 1
const val T_WATER = 2
const val T_SAND = 3

class TeamCol(val main: Int, val dark: Int, val light: Int, val name: String)

val TEAM = arrayOf(
    TeamCol(0xff2e62c4.toInt(), 0xff1b3a78.toInt(), 0xff86aef0.toInt(), "Синие"),
    TeamCol(0xffc0342b.toInt(), 0xff78201a.toInt(), 0xfff08a80.toInt(), "Красные"),
)

const val FOOD = 0
const val WOOD = 1
const val STONE = 2
const val GOLD = 3
val RICON = arrayOf("🌾", "🌲", "⛰️", "💰")
val RNAME = arrayOf("Еда", "Дерево", "Камень", "Золото")
val RCOL = intArrayOf(0xffe0c050.toInt(), 0xff8a5a2a.toInt(), 0xff9aa0a8.toInt(), 0xffffd84a.toInt())
const val CARRY = 10
val GTIME = doubleArrayOf(0.8, 0.9, 1.1, 1.2)
const val POP_MAX = 300

// рынок: начальная цена 100 единиц еды, дерева, камня в золоте
val PRICE0 = doubleArrayOf(100.0, 100.0, 130.0)
const val PRICE_MIN = 25.0
const val PRICE_MAX = 400.0

fun cost(food: Int = 0, wood: Int = 0, stone: Int = 0, gold: Int = 0) = intArrayOf(food, wood, stone, gold)

class UDef(
    val key: String, val name: String, val hp: Int, val atk: Double, val range: Double, val cd: Double,
    val spd: Double, val sight: Int, val cost: IntArray, val time: Double, val pop: Int, val r: Double,
    val desc: String, val vsCav: Double = 0.0, val ranged: Boolean = false, val cav: Boolean = false,
    val splash: Double = 0.0, val vsBld: Double = 0.0, val fly: Boolean = false,
)

const val U_PEASANT = 0
const val U_PIKE = 1
const val U_MUSKET = 2
const val U_CAV = 3
const val U_CANNON = 4
const val U_DRAGON = 5
const val U_GREN = 6
const val U_DRAGOON = 7
const val U_MORTAR = 8
const val U_HERO = 9
const val U_SIEGE = 10
/** радиус, в котором герой воодушевляет своих */
const val HERO_AURA = 5.0 * 32
/** стреляет из ружья или орудия: кадр выстрела по перезарядке */
/** высота дуги снаряда (доля дальности) и его размер: мортира бьёт навесом, граната мелкая */
fun projArc(src: Ent?) = when ((src as? Unt)?.type) { U_MORTAR -> 0.6; U_GREN -> 0.35; else -> 0.18 }
fun projSize(src: Ent?) = if ((src as? Unt)?.type == U_GREN) 2f else 3f
fun isGun(type: Int) = type == U_MUSKET || type == U_CANNON || type == U_DRAGOON || type == U_MORTAR

val UNIT = arrayOf(
    UDef("peasant", "Крестьянин", 45, 3.0, 14.0, 1.2, 54.0, 6, cost(food = 50), 8.0, 1, 6.0, "Добывает ресурсы и строит"),
    UDef("pike", "Пикинёр", 120, 9.0, 22.0, 1.3, 44.0, 6, cost(food = 40, wood = 20), 9.0, 1, 6.0, "Стойкая пехота, сильна против конницы", vsCav = 2.6),
    UDef("musket", "Мушкетёр", 70, 26.0, 190.0, 4.2, 42.0, 8, cost(food = 50, wood = 10, gold = 20), 11.0, 1, 6.0, "Мощный выстрел, долгая перезарядка", ranged = true),
    UDef("cav", "Рейтар", 180, 16.0, 22.0, 1.1, 96.0, 7, cost(food = 80, gold = 50), 13.0, 2, 9.0, "Быстрая тяжёлая конница", cav = true),
    UDef("cannon", "Пушка", 150, 70.0, 340.0, 7.0, 26.0, 9, cost(wood = 150, stone = 80, gold = 120), 20.0, 3, 9.0, "Рушит здания и плотные строи", ranged = true, splash = 46.0, vsBld = 3.0),
    UDef("dragon", "Дракон", 600, 26.0, 70.0, 2.4, 72.0, 9, cost(food = 250, stone = 50, gold = 160), 30.0, 4, 13.0,
        "Летит над лесом и водой, жжёт огнём. Сбить можно только стрельбой", ranged = true, splash = 36.0, vsBld = 1.8, fly = true),
    UDef("grenadier", "Гренадер", 95, 20.0, 100.0, 3.6, 44.0, 7, cost(food = 60, wood = 20, gold = 30), 12.0, 1, 6.0,
        "Бросает гранаты: бьёт по толпе и по зданиям", ranged = true, splash = 30.0, vsBld = 1.6),
    UDef("dragoon", "Драгун", 150, 24.0, 150.0, 3.4, 84.0, 8, cost(food = 80, wood = 10, gold = 60), 15.0, 2, 9.0,
        "Конный стрелок: быстро налетает и стреляет. Боится пик", ranged = true, cav = true),
    UDef("mortar", "Мортира", 120, 55.0, 420.0, 9.0, 22.0, 9, cost(wood = 180, stone = 100, gold = 120), 24.0, 3, 9.0,
        "Навесной огонь издалека: рушит стены и здания, вблизи беззащитна", ranged = true, splash = 50.0, vsBld = 3.5),
    UDef("hero", "Генерал", 900, 40.0, 22.0, 1.0, 60.0, 9, cost(food = 200, gold = 150), 30.0, 3, 8.0,
        "Герой, один на армию. Сильный боец, свои рядом бьют сильнее и держатся крепче"),
    UDef("siege", "Осадная башня", 520, 0.0, 14.0, 1.0, 22.0, 7, cost(wood = 260, stone = 40, gold = 40), 26.0, 2, 12.0,
        "Подкатите к вражеской стене: с башни перекинут мостик, и ваши войска пройдут поверх стены"),
)
// pike 0, musket 1, cav 2, cannon 3, peasant 4
val FORM_ORDER = intArrayOf(4, 0, 1, 2, 3, 5, 1, 2, 3, 1, 5)

class BDef(
    val key: String, val name: String, val w: Int, val h: Int, val hp: Int, val cost: IntArray, val time: Double,
    val sight: Int, val desc: String, val pop: Int = 0, val drop: Boolean = false, val trains: IntArray = IntArray(0),
    val atk: Double = 0.0, val range: Double = 0.0, val cd: Double = 0.0, val farm: Boolean = false,
    val research: IntArray = IntArray(0),
)

const val B_HALL = 0
const val B_HOUSE = 1
const val B_FARM = 2
const val B_STORE = 3
const val B_BARRACKS = 4
const val B_STABLE = 5
const val B_FOUNDRY = 6
const val B_TOWER = 7
const val B_LAIR = 8
const val B_WALL = 9
const val B_GATE = 10
const val B_ARSENAL = 11
const val B_ACADEMY = 12
fun isWall(type: Int) = type == B_WALL || type == B_GATE

val BLD = arrayOf(
    BDef("hall", "Ратуша", 3, 3, 2400, cost(wood = 300, stone = 200), 60.0, 9, "Сердце поселения: крестьяне, герой и склад", pop = 15, drop = true, trains = intArrayOf(U_PEASANT, U_HERO), atk = 8.0, range = 200.0, cd = 2.0),
    BDef("house", "Дом", 2, 2, 500, cost(wood = 60), 15.0, 4, "+10 к населению", pop = 10),
    BDef("farm", "Ферма", 3, 3, 400, cost(wood = 80), 18.0, 4, "Даёт еду, до 6 крестьян", farm = true),
    BDef("store", "Рынок", 2, 2, 600, cost(wood = 100), 18.0, 5, "Торговля ресурсами; сюда сдают добычу", drop = true),
    BDef("barracks", "Казарма", 3, 3, 1300, cost(wood = 150, stone = 50), 35.0, 6, "Пикинёры, мушкетёры и гренадеры", trains = intArrayOf(U_PIKE, U_MUSKET, U_GREN)),
    BDef("stable", "Конюшня", 3, 3, 1200, cost(wood = 200, stone = 50, gold = 50), 40.0, 6, "Рейтары и драгуны", trains = intArrayOf(U_CAV, U_DRAGOON)),
    BDef("foundry", "Литейня", 3, 3, 1400, cost(wood = 200, stone = 150, gold = 100), 50.0, 6, "Пушки, мортиры и осадные башни", trains = intArrayOf(U_CANNON, U_MORTAR, U_SIEGE)),
    BDef("tower", "Башня", 2, 2, 1000, cost(wood = 50, stone = 150), 30.0, 9, "Стреляет по врагам", atk = 15.0, range = 230.0, cd = 1.8),
    BDef("lair", "Логово дракона", 3, 3, 1600, cost(wood = 250, stone = 250), 55.0, 7, "Драконы: летают и жгут огнём", trains = intArrayOf(U_DRAGON)),
    BDef("wall", "Стена", 1, 1, 600, cost(stone = 10), 5.0, 2, "Враг не пройдёт, пока не проломит. Проведите пальцем, чтобы протянуть стену"),
    BDef("gate", "Ворота", 1, 1, 1100, cost(wood = 20, stone = 20), 12.0, 3, "Свои проходят, чужие нет. Можно поставить прямо на стену"),
    BDef("arsenal", "Арсенал", 3, 3, 1200, cost(wood = 175, stone = 75), 40.0, 6, "Улучшения: клинки, кирасы, порох", research = intArrayOf(0, 1, 2)),
    BDef("academy", "Академия", 3, 3, 1300, cost(wood = 150, stone = 150, gold = 50), 45.0, 6, "Улучшения: пушки, каменщики, строевая выучка", research = intArrayOf(3, 4, 5)),
)

/** улучшение: цена и время растут со ступенью (ступень 2 стоит вдвое дороже) */
class TDef(val key: String, val name: String, val short: String, val desc: String, val levels: Int, val cost: IntArray, val time: Double, val icon: String)

const val T_BLADES = 0
const val T_ARMOR = 1
const val T_POWDER = 2
const val T_ARTY = 3
const val T_MASON = 4
const val T_DRILL = 5

val TECH = arrayOf(
    TDef("blades", "Закалённые клинки", "Клинки", "Пикинёры, рейтары и крестьяне бьют сильнее: +15% за ступень", 2, cost(wood = 80, gold = 80), 35.0, "⚔️"),
    TDef("armor", "Кирасы", "Кирасы", "Войска на земле получают меньше урона: −12% за ступень", 2, cost(food = 100, gold = 100), 40.0, "🛡️"),
    TDef("powder", "Зернистый порох", "Порох", "Мушкетёры, башни и ратуша стреляют сильнее: +15% за ступень", 2, cost(wood = 60, gold = 120), 40.0, "💥"),
    TDef("arty", "Артиллерийская школа", "Артиллерия", "Пушки бьют сильнее и дальше: +20% урона и +12% дальности за ступень", 2, cost(wood = 120, stone = 60, gold = 120), 45.0, "🎯"),
    TDef("mason", "Каменщики", "Каменщики", "Здания и стены прочнее на треть", 1, cost(wood = 100, stone = 200), 45.0, "🧱"),
    TDef("drill", "Строевая выучка", "Выучка", "Войска ходят быстрее: +10% за ступень", 2, cost(food = 150, gold = 60), 35.0, "🥁"),
)
/** в очереди здания: обучение отряда (номер < Q_TECH) или улучшение (Q_TECH + номер улучшения) */
const val Q_TECH = 100
val BUILD_LIST = intArrayOf(B_HOUSE, B_FARM, B_STORE, B_BARRACKS, B_STABLE, B_FOUNDRY, B_LAIR, B_ARSENAL, B_ACADEMY, B_TOWER, B_WALL, B_GATE, B_HALL)

class Diff(val mult: Double, val peas: Int, val wave: Int, val grow: Int, val interval: Double, val first: Double, val pace: Double, val cannons: Int, val bonus: Int, val dragons: Int, val walls: Int)

val DIFF = arrayOf(
    Diff(0.7, 16, 6, 3, 260.0, 620.0, 1.5, 1, 0, 0, 0),
    Diff(1.0, 24, 10, 4, 210.0, 450.0, 1.0, 2, 100, 1, 1),
    Diff(1.4, 32, 12, 5, 170.0, 330.0, 0.75, 4, 300, 2, 2),
)

val DX = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
val DY = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
val DX4 = intArrayOf(1, 0, -1, 0)
val DY4 = intArrayOf(0, 1, 0, -1)
val DC = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.414, 1.414, 1.414, 1.414)

// ============================================================ утилиты
fun clamp(v: Double, a: Double, b: Double) = if (v < a) a else if (v > b) b else v
fun clampI(v: Int, a: Int, b: Int) = if (v < a) a else if (v > b) b else v
fun rnd() = Random.nextDouble()
fun fl(v: Double) = floor(v).toInt()
fun hyp(x: Double, y: Double) = kotlin.math.sqrt(x * x + y * y)

fun hash2(x: Int, y: Int, s: Int): Double {
    var h = (x * 374761393) xor (y * 668265263) xor (s * 982451653)
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h.toLong() and 0xffffffffL).toDouble() / 4294967296.0
}

fun hash2(x: Double, y: Double, s: Int) = hash2(x.toInt(), y.toInt(), s)

fun vnoise(x: Double, y: Double, s: Int): Double {
    val xi = fl(x)
    val yi = fl(y)
    val xf = x - xi
    val yf = y - yi
    val u = xf * xf * (3 - 2 * xf)
    val v = yf * yf * (3 - 2 * yf)
    val a = hash2(xi, yi, s)
    val b = hash2(xi + 1, yi, s)
    val c = hash2(xi, yi + 1, s)
    val d = hash2(xi + 1, yi + 1, s)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
}

fun fbm(x: Double, y: Double, s: Int) = vnoise(x, y, s) * 0.6 + vnoise(x * 2, y * 2, s + 7) * 0.3 + vnoise(x * 4, y * 4, s + 13) * 0.1

fun dirOf(dx: Double, dy: Double) = fl(atan2(dy, dx) / (Math.PI / 4) + 0.5) and 7

fun costStr(c: IntArray): String = (0..3).filter { c[it] > 0 }.joinToString(" ") { RICON[it] + c[it] }

class Heap {
    private var a = IntArray(256)
    private var c = DoubleArray(256)
    var size = 0
        private set
    var lastC = 0.0

    fun push(v: Int, cv: Double) {
        if (size == a.size) {
            a = a.copyOf(size * 2); c = c.copyOf(size * 2)
        }
        var i = size++
        while (i > 0) {
            val p = (i - 1) shr 1
            if (c[p] <= cv) break
            a[i] = a[p]; c[i] = c[p]; i = p
        }
        a[i] = v; c[i] = cv
    }

    fun pop(): Int {
        val top = a[0]
        val tc = c[0]
        size--
        val n = size
        if (n > 0) {
            val lv = a[n]
            val lc = c[n]
            var i = 0
            while (true) {
                val l = 2 * i + 1
                val r = l + 1
                var m = i
                var mc = lc
                if (l < n && c[l] < mc) { m = l; mc = c[l] }
                if (r < n && c[r] < mc) { m = r; mc = c[r] }
                if (m == i) break
                a[i] = a[m]; c[i] = c[m]; i = m
            }
            a[i] = lv; c[i] = lc
        }
        lastC = tc
        return top
    }
}
