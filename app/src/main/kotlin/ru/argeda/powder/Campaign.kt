package ru.argeda.powder

import kotlin.math.max
import kotlin.math.sign

/** Миссия кампании: карта создаётся обычным генератором по своему зерну, меняются расстановка и цель. */
class Mission(val title: String, val story: String, val goal: String, val size: Int, val diff: Int, val seed: Int)

/**
 * Кампания «Знамёна над рекой»: пять миссий с разными целями.
 * Номер миссии хранится в [Game.mission]; обычное условие (враг уничтожен — победа, свои — поражение) действует всегда.
 */
object Campaign {
    const val HOLD = 600.0      // «Застава»: сколько секунд продержаться
    const val GOLD_NEED = 1500  // «Казна»: сколько золота собрать
    const val GOLD_TIME = 1080.0

    val M = arrayOf(
        Mission("Новый лагерь",
            "Весна. Полковник велел вам занять брод и поставить лагерь, пока неприятель далеко. Людей мало, зато есть топоры и руки.",
            "Постройте ферму, два дома и казарму, доведите войско до 10 солдат.", 64, 0, 1701),
        Mission("Застава на тракте",
            "Лагерь заметили. По тракту идут вражеские отряды, а подкрепление будет только к вечеру. Держите ратушу любой ценой.",
            "Продержитесь 10 минут и не потеряйте ратушу.", 64, 1, 1702),
        Mission("Казна короны",
            "Армии нечем платить. В здешних холмах есть золото: намойте его раньше, чем жалованье потребуют с оружием в руках.",
            "Накопите 1500 золота за 18 минут.", 80, 1, 1703),
        Mission("Осада крепости",
            "Неприятель заперся за стенами на другом берегу. Вам дали пушки и лучших мушкетёров. Возьмите крепость.",
            "Разрушьте вражескую ратушу.", 80, 1, 1704),
        Mission("Генеральное сражение",
            "Обе армии сошлись в поле. С вами генерал и рейтары, но враг силён и богат. Сегодня решится всё.",
            "Уничтожьте все войска и здания противника.", 100, 2, 1705),
    )

    private fun hall(g: Game, team: Int) = g.blds.firstOrNull { it.team == team && it.type == B_HALL && !it.dead }

    /** отряд рядом с ратушей, со стороны центра карты */
    private fun squad(g: Game, team: Int, types: IntArray) {
        val h = hall(g, team) ?: return
        val sx = sign(g.W * TILE / 2.0 - h.x).let { if (it == 0.0) 1.0 else it }
        val sy = sign(g.H * TILE / 2.0 - h.y).let { if (it == 0.0) 1.0 else it }
        for ((i, t) in types.withIndex()) g.spawnUnit(t, team, h.x + sx * TILE * (4.0 + i % 4), h.y + sy * TILE * (4.0 + i / 4))
    }
    private fun rep(n: Int, t: Int) = IntArray(n) { t }

    fun setup(g: Game, m: Int) {
        g.mission = m
        val R = g.res
        when (m) {
            0 -> { g.ai[1] = null; R[0][FOOD] += 200.0; R[0][WOOD] += 200.0 }
            1 -> {
                g.ai[1]?.nextAttack = 150.0
                R[0][WOOD] += 200.0; R[0][STONE] += 200.0
                squad(g, 0, rep(4, U_MUSKET) + rep(3, U_PIKE))
                squad(g, 1, rep(4, U_PIKE) + rep(4, U_MUSKET))
            }
            2 -> { g.ai[1]?.nextAttack = 420.0; squad(g, 0, rep(4, U_PIKE) + rep(2, U_PEASANT)) }
            3 -> {
                g.ai[1]?.nextAttack = 900.0
                for (r in 0..3) { R[1][r] += 1200.0; R[0][r] += 300.0 }
                squad(g, 0, rep(2, U_CANNON) + rep(6, U_MUSKET) + rep(6, U_PIKE))
                squad(g, 1, rep(8, U_MUSKET) + rep(6, U_PIKE))
            }
            4 -> {
                for (r in 0..3) R[0][r] += 400.0
                squad(g, 0, intArrayOf(U_HERO) + rep(4, U_CAV) + rep(6, U_MUSKET) + rep(6, U_PIKE))
                squad(g, 1, rep(4, U_CAV) + rep(6, U_MUSKET) + rep(6, U_PIKE))
            }
        }
        g.computePop(); g.updateFog()
    }

    private fun count(g: Game, type: Int) = g.blds.count { it.team == 0 && it.type == type && it.built && !it.dead }
    private fun soldiers(g: Game) = g.units.count { it.team == 0 && !it.dead && it.type != U_PEASANT }

    /** 1 — миссия выполнена, -1 — провалена, 0 — продолжается */
    fun check(g: Game): Int = when (g.mission) {
        0 -> if (count(g, B_FARM) >= 1 && count(g, B_HOUSE) >= 2 && count(g, B_BARRACKS) >= 1 && soldiers(g) >= 10) 1 else 0
        1 -> if (hall(g, 0) == null) -1 else if (g.time >= HOLD) 1 else 0
        2 -> if (g.res[0][GOLD] >= GOLD_NEED) 1 else if (g.time >= GOLD_TIME) -1 else 0
        3 -> if (hall(g, 1) == null) 1 else 0
        else -> 0
    }

    private fun clock(s: Double): String { val t = max(0.0, s).toInt(); return "${t / 60}:${(t % 60).toString().padStart(2, '0')}" }

    /** строка цели для экрана: что сделано и сколько осталось */
    fun status(g: Game): String = when (g.mission) {
        0 -> "Ферма ${minOf(1, count(g, B_FARM))}/1 · Дома ${minOf(2, count(g, B_HOUSE))}/2 · Казарма ${minOf(1, count(g, B_BARRACKS))}/1 · Солдаты ${minOf(10, soldiers(g))}/10"
        1 -> "Удержать ратушу: осталось ${clock(HOLD - g.time)}"
        2 -> "Золото ${g.res[0][GOLD].toInt()}/$GOLD_NEED · осталось ${clock(GOLD_TIME - g.time)}"
        3 -> "Разрушьте вражескую ратушу"
        4 -> "Уничтожьте противника"
        else -> ""
    }
}
