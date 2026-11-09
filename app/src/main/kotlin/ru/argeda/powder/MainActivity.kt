package ru.argeda.powder

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

private const val CREAM = 0xfff3e6c8.toInt()
private const val GOLDTXT = 0xffffd77a.toInt()
private const val BROWN = 0xff5a3f22.toInt()
private const val BORDER = 0xffb08a50.toInt()
private const val DARK = 0xff3d2a17.toInt()

/** Вся игра — нативное Android-приложение на Kotlin: поле боя на Canvas, интерфейс из обычных View. */
class MainActivity : Activity(), Host, Choreographer.FrameCallback {
    private lateinit var root: FrameLayout
    private var spr: Sprites? = null
    private var sfx: Sfx? = null
    private var gv: GameView? = null
    private var miniV: MiniView? = null
    private val G get() = gv?.game

    private var optDiff = 1
    private var optSize = 80
    private var soundOn = true

    // экраны
    private lateinit var menu: View
    private lateinit var pauseM: View
    private lateinit var endM: View
    private lateinit var endTitle: TextView
    private lateinit var endStats: LinearLayout
    private lateinit var startBtn: TextView
    private lateinit var soundBtn: TextView

    // HUD
    private lateinit var hud: FrameLayout
    private val resTv = arrayOfNulls<TextView>(5)
    private lateinit var speedBtn: TextView
    private lateinit var boxBtn: TextView
    private lateinit var info: LinearLayout
    private lateinit var acts: LinearLayout
    private lateinit var place: View
    private lateinit var toastTv: TextView
    private var qBar: View? = null
    private var uiSig = ""
    private var uiT = 0.0
    private var miniT = 0.0

    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val serifB: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    // ------------------------------------------------------------ размеры и оформление
    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(v: Int) = dp(v.toFloat())

    private fun box(bg: Int, border: Int, radius: Float, bw: Float = 1f) = GradientDrawable().apply {
        setColor(bg); if (bw > 0) setStroke(dp(bw), border); cornerRadius = dp(radius).toFloat()
    }

    private fun tv(text: CharSequence, size: Float, color: Int = CREAM, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(color)
        typeface = if (bold) serifB else serif
        includeFontPadding = false
    }

    private fun btn(text: CharSequence, size: Float, bg: Drawable, padV: Int, padH: Int, bold: Boolean = false, color: Int = CREAM, click: () -> Unit): TextView =
        tv(text, size, color, bold).apply {
            background = bg
            gravity = Gravity.CENTER
            setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
            isClickable = true
            setOnClickListener { click() }
        }

    private fun lp(w: Int, h: Int, gravity: Int = Gravity.NO_GRAVITY) = FrameLayout.LayoutParams(w, h, gravity)
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    // ------------------------------------------------------------ запуск
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)
        hideBars()
        buildMenu()
        Thread {
            val s = Sprites(assets)
            val a = try { Sfx(this) } catch (e: Exception) { null }
            runOnUiThread { onLoaded(s, a) }
        }.start()
    }

    private fun onLoaded(s: Sprites, a: Sfx?) {
        if (isFinishing) return
        spr = s; sfx = a
        val v = GameView(this, s)
        gv = v
        root.addView(v, 0, lp(MP, MP))
        buildHud(s)
        root.addView(hud, 1, lp(MP, MP))
        hud.visibility = View.GONE
        pauseM = buildPause(); root.addView(pauseM, lp(MP, MP)); pauseM.visibility = View.GONE
        endM = buildEnd(); root.addView(endM, lp(MP, MP)); endM.visibility = View.GONE
        v.onTouchStart = null
        startBtn.text = "Начать битву"
        startBtn.alpha = 1f
        root.setOnApplyWindowInsetsListener { _, ins ->
            if (Build.VERSION.SDK_INT >= 28) {
                val c = ins.displayCutout
                hud.setPadding(c?.safeInsetLeft ?: 0, 0, c?.safeInsetRight ?: 0, 0)
            }
            ins
        }
        root.requestApplyInsets()
        // для проверки на эмуляторе: am start ... --ei start 1 --ez autopilot true --ei speed 3
        val it = intent
        if (it != null && it.hasExtra("start")) {
            optDiff = it.getIntExtra("start", 1).coerceIn(0, 2)
            optSize = it.getIntExtra("size", 80)
            startGame()
            G?.let { g ->
                g.speed = it.getIntExtra("speed", 1).coerceIn(1, 3)
                if (it.getBooleanExtra("autopilot", false)) { g.autopilot(); autoCam = true }
            }
        }
    }

    private var autoCam = false
    private var autoCamT = 0.0

    @Suppress("DEPRECATION")
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onPause() {
        super.onPause()
        val g = G
        if (g != null && !g.paused && !g.over) togglePause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Choreographer.getInstance().removeFrameCallback(this)
        sfx?.release()
    }

    @Deprecated("Системная кнопка «Назад» сначала спрашивает игру")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!gameBack()) super.onBackPressed()
    }

    private fun gameBack(): Boolean {
        val g = G ?: return false
        if (g.over) { toMenu(); return true }
        if (g.placing != null) { g.placing = null; g.uiDirty = true; return true }
        if (g.amove) { g.amove = false; g.uiDirty = true; return true }
        if (g.sel.isNotEmpty() && !g.paused) { g.sel = ArrayList(); g.uiDirty = true; return true }
        togglePause(); return true
    }

    // ------------------------------------------------------------ экраны меню
    private fun screenBg(): Drawable = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(0xee3a2814.toInt(), 0xf5120b05.toInt())
        gradientRadius = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 0.6f
    }

    private fun card(maxW: Int): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.background = LayerDrawable(arrayOf(box(0xff2b1d12.toInt(), BORDER, 10f, 3f), box(Color.TRANSPARENT, BORDER, 8f, 1f))).apply {
            setLayerInset(1, dp(4), dp(4), dp(4), dp(4))
        }
        c.setPadding(dp(22), dp(16), dp(22), dp(16))
        c.elevation = dp(10).toFloat()
        val w = min(dp(maxW), resources.displayMetrics.widthPixels - dp(24))
        c.layoutParams = FrameLayout.LayoutParams(w, WC, Gravity.CENTER)
        return c
    }

    private fun screen(c: LinearLayout): View {
        val s = ScrollView(this)
        s.isFillViewport = true
        s.background = screenBg()
        s.isClickable = true
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12))
        wrap.addView(c)
        s.addView(wrap, ViewGroup.LayoutParams(MP, WC))
        return s
    }

    private fun bigBtn(text: String, click: () -> Unit): TextView {
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xffa8352b.toInt(), 0xff6e1d16.toInt())).apply {
            setStroke(dp(2), 0xffe8b84a.toInt()); cornerRadius = dp(8).toFloat()
        }
        return btn(text, 19f, bg, 12, 12, true, 0xfffff3d6.toInt(), click).apply {
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(12) }
        }
    }

    private fun midBtn(text: String, click: () -> Unit) = btn(text, 15f, box(BROWN, BORDER, 8f), 10, 10, click = click).apply {
        layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(10) }
    }

    private fun seg(labels: Array<String>, on: Int, pick: (Int) -> Unit): LinearLayout {
        val s = LinearLayout(this)
        s.orientation = LinearLayout.HORIZONTAL
        s.background = box(DARK, BORDER, 8f)
        s.outlineProvider = ViewOutlineProvider.BACKGROUND
        s.clipToOutline = true
        s.setPadding(dp(1), dp(1), dp(1), dp(1))
        val bs = ArrayList<TextView>()
        fun paint(k: Int) {
            bs.forEachIndexed { i, b ->
                b.setBackgroundColor(if (i == k) BORDER else DARK)
                b.setTextColor(if (i == k) 0xff1d140b.toInt() else CREAM)
                b.typeface = if (i == k) serifB else serif
            }
        }
        labels.forEachIndexed { i, l ->
            val b = tv(l, 14f).apply {
                gravity = Gravity.CENTER; setPadding(dp(12), dp(8), dp(12), dp(8)); isClickable = true
                setOnClickListener { paint(i); pick(i) }
            }
            bs.add(b); s.addView(b)
        }
        paint(on)
        return s
    }

    private fun row(label: String, right: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8); bottomMargin = dp(8) }
        r.addView(tv(label, 16f), LinearLayout.LayoutParams(0, WC, 1f))
        r.addView(right)
        return r
    }

    @Suppress("DEPRECATION")
    private fun html(s: String): CharSequence = if (Build.VERSION.SDK_INT >= 24) Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(s)

    private fun buildMenu() {
        val c = card(560)
        c.addView(tv("Порох и Знамёна", 34f, GOLDTXT, true).apply {
            letterSpacing = 0.06f; setShadowLayer(0.1f, 0f, dp(2).toFloat(), 0xff6a3a10.toInt()); gravity = Gravity.CENTER
        })
        c.addView(tv("Стратегия эпохи пик и мушкетов", 16f).apply {
            setTypeface(serif, Typeface.ITALIC); alpha = 0.8f; setPadding(0, dp(4), 0, dp(12))
        })
        c.addView(row("Противник", seg(arrayOf("Лёгкий", "Обычный", "Трудный"), 1) { optDiff = it }))
        c.addView(row("Карта", seg(arrayOf("Малая", "Средняя", "Большая"), 1) { optSize = intArrayOf(64, 80, 100)[it] }))
        startBtn = bigBtn("Загрузка…") { startGame() }
        startBtn.alpha = 0.6f
        c.addView(startBtn)
        val help = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        c.addView(tv("Как играть", 14f).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG; alpha = 0.85f; setPadding(dp(8), dp(12), dp(8), dp(4)); isClickable = true
            setOnClickListener { help.visibility = if (help.visibility == View.GONE) View.VISIBLE else View.GONE }
        })
        val ps = arrayOf(
            "<b>Цель:</b> разрушить все здания и войска противника.",
            "<b>Выбор:</b> тап по своему отряду или зданию. Двойной тап выбирает всех таких же на экране. Чтобы выделить рамкой, задержите палец и ведите (или нажмите «Рамка»).",
            "<b>Приказы:</b> тап по земле — идти строем, по врагу — атаковать, по лесу, шахте или ферме — добывать (крестьяне). «Натиск» — идти и бить всех по пути.",
            "<b>Карта:</b> одним пальцем — двигать, двумя — масштаб, тап по мини-карте — перейти.",
            "<b>Хозяйство:</b> крестьяне рубят лес, ломают камень, моют золото и работают на фермах. Дома дают место для войск, склад сокращает путь с грузом.",
            "<b>Войска:</b> пикинёры держат конницу, мушкетёры бьют залпом издалека, рейтары быстры, пушки рушат здания и строи.",
        )
        for (p in ps) help.addView(tv(html(p), 13.5f).apply { setLineSpacing(0f, 1.25f); setPadding(0, dp(6), 0, 0) })
        help.addView(tv(html("<b>Графика:</b> 3D-модели персонажей и зданий — KayKit (Kay Lousberg, www.kaylousberg.com), лицензия CC0. Пики, мушкеты, кони и пушки — свои."), 12f).apply {
            alpha = 0.75f; setLineSpacing(0f, 1.25f); setPadding(0, dp(6), 0, 0)
        })
        c.addView(help, LinearLayout.LayoutParams(MP, WC))
        menu = screen(c)
        root.addView(menu, lp(MP, MP))
    }

    private fun buildPause(): View {
        val c = card(380)
        c.addView(tv("Пауза", 24f, GOLDTXT, true).apply { setPadding(0, 0, 0, dp(10)) })
        c.addView(bigBtn("Продолжить") { togglePause() })
        soundBtn = midBtn("Звук: вкл") {
            soundOn = !soundOn; sfx?.on = soundOn; soundBtn.text = "Звук: " + if (soundOn) "вкл" else "выкл"
        }
        c.addView(soundBtn)
        c.addView(midBtn("Сдаться и выйти в меню") { toMenu() })
        return screen(c)
    }

    private fun buildEnd(): View {
        val c = card(380)
        endTitle = tv("", 24f, GOLDTXT, true).apply { setPadding(0, 0, 0, dp(10)) }
        c.addView(endTitle)
        endStats = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        c.addView(endStats)
        c.addView(bigBtn("В меню") { toMenu() })
        return screen(c)
    }

    // ------------------------------------------------------------ HUD
    private fun buildHud(s: Sprites) {
        hud = FrameLayout(this)
        // верхняя полоса: ресурсы, скорость, пауза
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(8), 0, dp(8), dp(2))
        top.background = LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(0xff8a6a3a.toInt()) },
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xee2b1d12.toInt(), 0xbb2b1d12.toInt())),
        )).apply { setLayerInset(1, 0, 0, 0, dp(2)) }
        val resRow = LinearLayout(this)
        for (i in 0 until 5) {
            val t = tv("", 15f).apply { maxLines = 1 }
            resTv[i] = t
            resRow.addView(t, LinearLayout.LayoutParams(WC, WC).apply { marginEnd = dp(12) })
        }
        top.addView(resRow, LinearLayout.LayoutParams(0, WC, 1f))
        fun tb(t: String, click: () -> Unit) = btn(t, 14f, box(BROWN, BORDER, 6f), 0, 6, click = click).apply {
            minWidth = dp(40)
        }
        speedBtn = tb("×1") { G?.let { it.speed = if (it.speed >= 3) 1 else it.speed + 1; syncHudButtons(it) } }
        top.addView(speedBtn, LinearLayout.LayoutParams(WC, dp(26)))
        top.addView(tb("❚❚") { togglePause() }, LinearLayout.LayoutParams(WC, dp(26)).apply { marginStart = dp(6) })
        hud.addView(top, lp(MP, dp(36), Gravity.TOP))

        // быстрые кнопки справа
        val quick = LinearLayout(this)
        quick.orientation = LinearLayout.VERTICAL
        fun qb(t: String, click: () -> Unit) = btn(t, 13f, box(0xd93d2a17.toInt(), BORDER, 8f), 7, 9, click = click).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        quick.addView(qb("⚔ Армия") { quickArmy() }, LinearLayout.LayoutParams(MP, WC))
        quick.addView(qb("⛏ Без дела") { quickIdle() }, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        boxBtn = qb("▭ Рамка") { G?.let { it.boxMode = !it.boxMode; it.uiDirty = true; syncHudButtons(it) } }
        quick.addView(boxBtn, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        hud.addView(quick, lp(WC, WC, Gravity.TOP or Gravity.END).apply { topMargin = dp(42); marginEnd = dp(6) })

        // мини-карта
        val mv = MiniView(this, gv!!)
        miniV = mv
        hud.addView(mv, lp(dp(112), dp(112), Gravity.BOTTOM or Gravity.START).apply { leftMargin = dp(4); bottomMargin = dp(4) })

        // нижняя панель
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.HORIZONTAL
        panel.background = box(0xe62b1d12.toInt(), 0xff8a6a3a.toInt(), 8f, 2f)
        panel.setPadding(dp(6), dp(6), dp(6), dp(6))
        info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(info, LinearLayout.LayoutParams(dp(190), MP))
        buildInfo()
        val sc = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        acts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sc.addView(acts, ViewGroup.LayoutParams(WC, MP))
        panel.addView(sc, LinearLayout.LayoutParams(0, MP, 1f).apply { marginStart = dp(6) })
        hud.addView(panel, lp(MP, dp(92), Gravity.BOTTOM).apply { leftMargin = dp(122); rightMargin = dp(4); bottomMargin = dp(4) })

        // подтверждение постройки
        val pl = LinearLayout(this)
        pl.orientation = LinearLayout.HORIZONTAL
        val pbg = { box(BROWN, 0xffe8b84a.toInt(), 8f, 2f) }
        pl.addView(btn("✓ Строить здесь", 15f, pbg(), 10, 16) { G?.confirmPlace() })
        pl.addView(btn("✕ Отмена", 15f, pbg(), 10, 16) { G?.let { it.placing = null; it.uiDirty = true } },
            LinearLayout.LayoutParams(WC, WC).apply { marginStart = dp(10) })
        place = pl
        hud.addView(pl, lp(WC, WC, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(104) })

        toastTv = tv("", 14f).apply {
            background = box(0xaa000000.toInt(), 0, 14f, 0f); setPadding(dp(14), dp(6), dp(14), dp(6)); alpha = 0f; maxLines = 1
        }
        hud.addView(toastTv, lp(WC, WC, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(44) })
    }

    private fun syncHudButtons(g: Game) {
        boxBtn.background = if (g.boxMode) box(BORDER, BORDER, 8f) else box(0xd93d2a17.toInt(), BORDER, 8f)
        boxBtn.setTextColor(if (g.boxMode) 0xff1d140b.toInt() else CREAM)
        place.visibility = if (g.placing != null) View.VISIBLE else View.GONE
        speedBtn.text = "×" + g.speed
    }

    private fun updateTop(g: Game) {
        val R = g.res[0]
        for (r in 0..3) resTv[r]!!.text = RICON[r] + " " + floor(R[r]).toInt()
        resTv[4]!!.text = "👥 " + g.pop[0] + "/" + g.cap[0]
        resTv[4]!!.setTextColor(if (g.pop[0] >= g.cap[0]) 0xffff8a7a.toInt() else CREAM)
    }

    // полоска (здоровье, прогресс)
    private class Bar(ctx: Context, var frac: Float, val bg: Int, val fg: Int) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val r = height / 2f
            p.color = bg; c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
            p.color = fg; c.drawRect(0f, 0f, width * frac.coerceIn(0f, 1f), height.toFloat(), p)
        }
    }

    private lateinit var iName: TextView
    private lateinit var iBar1: Bar
    private lateinit var iL1: TextView
    private lateinit var iBar2: Bar
    private lateinit var iL2: TextView
    private lateinit var iL3: TextView

    private fun buildInfo() {
        iName = tv("", 14f, 0xffffe2a0.toInt(), true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        iBar1 = Bar(this, 1f, 0xff551111.toInt(), 0xff55cc33.toInt())
        iBar2 = Bar(this, 0f, 0xff551111.toInt(), 0xffe8b84a.toInt())
        fun line() = tv("", 12.5f).apply { setLineSpacing(0f, 1.15f) }
        iL1 = line(); iL2 = line(); iL3 = line()
        info.addView(iName)
        info.addView(iBar1, LinearLayout.LayoutParams(MP, dp(6)).apply { topMargin = dp(3); bottomMargin = dp(3) })
        info.addView(iL1)
        info.addView(iBar2, LinearLayout.LayoutParams(MP, dp(6)).apply { topMargin = dp(3); bottomMargin = dp(3) })
        info.addView(iL2)
        info.addView(iL3)
    }

    /** name, полоска здоровья (или <0), строка, полоска очереди (или <0), ещё две строки */
    private fun setInfo(name: String, hp: Double, l1: CharSequence?, q: Double, l2: CharSequence? = null, l3: CharSequence? = null) {
        if (iName.text.toString() != name) iName.text = name
        fun bar(b: Bar, f: Double) {
            val vis = if (f >= 0) View.VISIBLE else View.GONE
            if (b.visibility != vis) b.visibility = vis
            if (f >= 0 && b.frac != f.toFloat()) { b.frac = f.toFloat(); b.invalidate() }
        }
        fun line(t: TextView, s: CharSequence?) {
            val vis = if (s != null) View.VISIBLE else View.GONE
            if (t.visibility != vis) t.visibility = vis
            if (s != null && t.text.toString() != s.toString()) t.text = s
        }
        bar(iBar1, hp); line(iL1, l1); bar(iBar2, q); line(iL2, l2); line(iL3, l3)
    }

    private fun updatePanel(g: Game) {
        val sel = g.sel
        val sb = StringBuilder()
        g.placing?.let { sb.append("P").append(it.type) }
        if (g.amove) sb.append('A')
        for (e in sel) {
            sb.append(e.id)
            if (e is Bld) { sb.append(if (e.built) 'b' else 'u'); for (q in e.queue) sb.append(q) }
            sb.append(',')
        }
        sb.append('|'); for (r in 0..3) sb.append(floor(g.res[0][r] / 10).toInt()).append(',')
        val sig = sb.toString()

        val pl = g.placing
        if (pl != null) {
            setInfo(BLD[pl.type].name, -1.0, BLD[pl.type].desc, -1.0, "Нажмите на карту, чтобы выбрать место")
        } else if (sel.isEmpty()) {
            setInfo("Ничего не выбрано", -1.0, "Тап по своему отряду или зданию. Удерживайте палец, чтобы выделить рамкой.", -1.0)
        } else if (sel.size == 1) {
            val e = sel[0]
            if (e is Unt) {
                val d = UNIT[e.type]
                val t = e.task
                val st = if (t != null && t.type == TASK_BUILD) "Строит"
                else if (t != null) "Добывает: " + RNAME[t.res].lowercase() + (if (e.carry > 0) " (${e.carry})" else "")
                else if (e.target != null) "В бою" else if (e.goal != null) "В пути" else "Ожидает"
                setInfo(d.name + if (e.team != 0) " (враг)" else "", e.hp / e.maxhp, "${ceil(e.hp).toInt()}/${e.maxhp.toInt()} · атака ${d.atk.toInt()}", -1.0, if (e.team != 0) d.desc else st)
            } else if (e is Bld) {
                val d = BLD[e.type]
                val nm = d.name + if (e.team != 0) " (враг)" else ""
                val hpT = "${ceil(e.hp).toInt()}/${e.maxhp.toInt()}"
                if (!e.built) setInfo(nm, e.hp / e.maxhp, hpT, -1.0, "Строится: ${floor(e.progress / d.time * 100).toInt()}%")
                else if (e.queue.isNotEmpty()) setInfo(nm, e.hp / e.maxhp, hpT, e.qt / UNIT[e.queue[0]].time, if (e.popBlocked) "Нужны дома!" else "Обучение: " + UNIT[e.queue[0]].name)
                else setInfo(nm, e.hp / e.maxhp, hpT, -1.0, d.desc)
            } else if (e is Mine) {
                setInfo(if (e.res == GOLD) "Золотая жила" else "Каменоломня", -1.0, "Осталось: ${e.amount}", -1.0, "Отправьте сюда крестьян")
            }
        } else {
            val cnt = LinkedHashMap<Int, Int>()
            for (e in sel) if (e is Unt) cnt[e.type] = (cnt[e.type] ?: 0) + 1
            setInfo("Отряд: ${sel.size}", -1.0, cnt.entries.joinToString("   ") { UNIT[it.key].name + " ×" + it.value }, -1.0, if (g.amove) "Натиск: тап по карте" else null)
        }

        if (sig == uiSig && !g.uiDirty) {
            val b = sel.firstOrNull()
            if (b is Bld && b.queue.isNotEmpty()) qBar?.scaleX = (b.qt / UNIT[b.queue[0]].time).toFloat()
            return
        }
        uiSig = sig; g.uiDirty = false
        acts.removeAllViews(); qBar = null
        val own = sel.filter { it.team == 0 }
        if (g.placing != null) txtBtn("✕ Отмена") { g.placing = null }
        else if (own.isNotEmpty() && own.all { it is Unt }) {
            if (own.all { (it as Unt).type == U_PEASANT }) {
                for (t in BUILD_LIST) actBtn(spr!!.bldIcons[t], BLD[t].name, BLD[t].cost, 0) { g.startPlacing(t) }
            } else {
                txtBtn("▦ Строй") { formation(g) }
                txtBtn(if (g.amove) "⚔ Натиск…" else "⚔ Натиск") { g.amove = !g.amove }
            }
            txtBtn("■ Стоп") { for (u in g.ownSelUnits()) { u.goal = null; u.target = null; u.task = null; u.cmd = CMD_NONE } }
            txtBtn("✕ Снять") { g.sel = ArrayList(); g.amove = false }
        } else if (own.size == 1 && own[0] is Bld) {
            val b = own[0] as Bld
            val d = BLD[b.type]
            if (b.built && d.trains.isNotEmpty()) {
                for (t in d.trains) {
                    val q = b.queue.count { it == t }
                    actBtn(spr!!.unitIcons[t], UNIT[t].name, UNIT[t].cost, q) {
                        if (!g.enqueue(b, t)) toast(if (b.queue.size >= 6) "Очередь заполнена" else "Не хватает: " + g.missing(UNIT[t].cost))
                        else sfx(S_CLICK, Double.NaN, Double.NaN)
                    }
                }
                b.queue.forEachIndexed { k, t -> qItem(spr!!.unitIcons[t], if (k == 0) b.qt / UNIT[t].time else -1.0) {
                    if (k < b.queue.size) { val tt = b.queue.removeAt(k); g.pay(0, UNIT[tt].cost, -1.0); if (k == 0) b.qt = 0.0 }
                } }
            }
            txtBtn("✕ Снять") { g.sel = ArrayList(); g.amove = false }
        } else if (sel.isNotEmpty()) txtBtn("✕ Снять") { g.sel = ArrayList(); g.amove = false }
    }

    private fun actLp() = LinearLayout.LayoutParams(dp(80), MP).apply { marginEnd = dp(4) }

    private fun clickAct(f: () -> Unit) { f(); G?.uiDirty = true }

    private fun txtBtn(label: String, f: () -> Unit) {
        acts.addView(btn(label, 13f, box(BROWN, BORDER, 6f), 2, 2) { clickAct(f) }, actLp())
    }

    private fun actBtn(icon: Bitmap?, name: String, cost: IntArray, badge: Int, f: () -> Unit) {
        val fl = FrameLayout(this)
        fl.background = box(BROWN, BORDER, 6f)
        fl.isClickable = true
        fl.setOnClickListener { clickAct(f) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(2), dp(2), dp(2), dp(2)) }
        col.addView(ImageView(this).apply { setImageBitmap(icon) }, LinearLayout.LayoutParams(dp(32), dp(32)))
        col.addView(tv(name, 11f, bold = true).apply { maxLines = 1; gravity = Gravity.CENTER; setPadding(0, dp(1), 0, dp(1)) })
        col.addView(tv(costStr(cost), 9.5f).apply { gravity = Gravity.CENTER; alpha = 0.9f; maxLines = 2 })
        fl.addView(col, lp(MP, MP))
        if (badge > 0) fl.addView(tv(badge.toString(), 11f, 0xff1d140b.toInt(), true).apply {
            background = box(0xffe8b84a.toInt(), 0, 8f, 0f); setPadding(dp(5), dp(1), dp(5), dp(1))
        }, lp(WC, WC, Gravity.TOP or Gravity.END).apply { topMargin = dp(2); marginEnd = dp(3) })
        if (!G!!.canAfford(0, cost)) fl.alpha = 0.45f
        acts.addView(fl, actLp())
    }

    private fun qItem(icon: Bitmap?, frac: Double, f: () -> Unit) {
        val fl = FrameLayout(this)
        fl.background = box(DARK, 0xff8a6a3a.toInt(), 6f)
        fl.isClickable = true
        fl.setOnClickListener { clickAct(f) }
        fl.addView(ImageView(this).apply { setImageBitmap(icon) }, lp(dp(32), dp(32), Gravity.CENTER))
        if (frac >= 0) {
            val bar = View(this).apply { background = box(0xffe8b84a.toInt(), 0, 2f, 0f); pivotX = 0f; scaleX = frac.toFloat() }
            fl.addView(bar, lp(MP, dp(4), Gravity.BOTTOM).apply { setMargins(dp(2), 0, dp(2), dp(2)) })
            qBar = bar
        }
        acts.addView(fl, LinearLayout.LayoutParams(dp(44), MP).apply { marginEnd = dp(4) })
    }

    private fun formation(g: Game) {
        val us = g.ownSelUnits()
        if (us.isEmpty()) return
        var cx = 0.0
        var cy = 0.0
        for (u in us) { cx += u.x; cy += u.y }
        cx /= us.size; cy /= us.size
        val lead = us.firstOrNull { it.goal != null } ?: us[0]
        val fx = lead.face
        val fd = lead.dir
        g.groupMove(us, cx, cy, CMD_MOVE)
        for (u in us) { u.face = fx; u.dir = fd }
    }

    private fun quickArmy() {
        val g = G ?: return
        g.sel = ArrayList(g.units.filter { it.team == 0 && it.type != U_PEASANT })
        if (g.sel.isNotEmpty()) {
            var cx = 0.0
            var cy = 0.0
            for (u in g.sel) { cx += u.x; cy += u.y }
            g.centerCam(cx / g.sel.size, cy / g.sel.size)
        } else toast("Войск пока нет")
        g.uiDirty = true
    }

    private fun quickIdle() {
        val g = G ?: return
        val idle = g.units.filter { it.team == 0 && it.type == U_PEASANT && it.task == null && it.goal == null && it.target == null }
        if (idle.isNotEmpty()) { g.sel = ArrayList(idle); g.centerCam(idle[0].x, idle[0].y) } else toast("Все крестьяне при деле")
        g.uiDirty = true
    }

    // ------------------------------------------------------------ игра
    private fun startGame() {
        val v = gv ?: return
        val g = Game(optSize, optDiff, false, this, v.vwDp, v.vhDp)
        v.game = g
        menu.visibility = View.GONE
        hud.visibility = View.VISIBLE
        uiSig = ""; uiT = 0.0; miniT = 0.0
        syncHudButtons(g)
        startLoop()
        hud.postDelayed({ if (G === g) toast("Выделите крестьян и постройте ферму и дома") }, 800)
    }

    private fun togglePause() {
        val g = G ?: return
        if (g.over) return
        g.paused = !g.paused
        pauseM.visibility = if (g.paused) View.VISIBLE else View.GONE
    }

    private fun toMenu() {
        gv?.game = null
        pauseM.visibility = View.GONE; endM.visibility = View.GONE; hud.visibility = View.GONE; menu.visibility = View.VISIBLE
    }

    private var looping = false

    private fun startLoop() {
        if (looping) return
        looping = true
        gv?.step()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        val v = gv
        if (v?.game == null || isDestroyed) { looping = false; return }
        onFrame(v.step())
        v.invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun onFrame(dtReal: Double) {
        val g = G ?: return
        uiT -= dtReal
        if (uiT <= 0 || g.uiDirty) { uiT = 0.25; updateTop(g); updatePanel(g); syncHudButtons(g) }
        if (autoCam) {
            autoCamT -= dtReal
            if (autoCamT <= 0) {
                autoCamT = 4.0
                val army = g.units.filter { it.team == 0 && it.type != U_PEASANT }
                val fight = army.filter { it.target != null }
                val list = if (fight.isNotEmpty()) fight else army
                if (list.isNotEmpty()) g.centerCam(list.sumOf { it.x } / list.size, list.sumOf { it.y } / list.size)
                g.sel = ArrayList(list.take(6)); g.uiDirty = true
            }
        }
        miniT -= dtReal
        if (miniT <= 0) { miniT = 0.3; gv!!.drawMini(g); miniV?.invalidate() }
    }

    // ------------------------------------------------------------ Host
    private val hideToast = Runnable { toastTv.animate().alpha(0f).setDuration(400).start() }

    override fun toast(msg: String) {
        val g = G
        if (g == null || g.auto) return
        toastTv.text = msg
        toastTv.animate().cancel()
        toastTv.animate().alpha(1f).setDuration(200).start()
        toastTv.removeCallbacks(hideToast)
        toastTv.postDelayed(hideToast, 2400)
    }

    override fun sfx(kind: Int, x: Double, y: Double) {
        val s = sfx ?: return
        val g = G ?: return
        var vol = 1.0
        if (!x.isNaN()) {
            val v = g.viewRect()
            val cx = (v[0] + v[2]) / 2
            val cy = (v[1] + v[3]) / 2
            val d = hyp(x - cx, y - cy) / ((v[2] - v[0]) * 0.8)
            vol = clamp(1.2 - d, 0.0, 1.0)
        }
        s.play(kind, vol.toFloat())
    }

    override fun chunksDirty(tx: Int, ty: Int, w: Int, h: Int) { gv?.chunksDirty(tx, ty, w, h) }

    override fun ended(win: Boolean) {
        val g = G ?: return
        endTitle.text = if (win) "Победа!" else "Поражение"
        endStats.removeAllViews()
        val m = (g.time / 60).toInt()
        val sec = (g.time % 60).toInt()
        endStats.addView(tv("Время битвы: $m:${sec.toString().padStart(2, '0')}", 14f).apply { setPadding(0, dp(4), 0, dp(6)) })
        val tbl = TableLayout(this)
        val s = g.stats
        val rows = arrayOf(
            arrayOf("", "Вы", "Враг"),
            arrayOf("Добыто", "${s[0].gathered}", "${s[1].gathered}"),
            arrayOf("Обучено", "${s[0].trained}", "${s[1].trained}"),
            arrayOf("Построено", "${s[0].built}", "${s[1].built}"),
            arrayOf("Уничтожено", "${s[0].killed}", "${s[1].killed}"),
            arrayOf("Потеряно", "${s[0].lost}", "${s[1].lost}"),
        )
        for (r in rows) {
            val tr = TableRow(this)
            for (cell in r) tr.addView(tv(cell, 14f).apply {
                setPadding(dp(10), dp(3), dp(10), dp(3))
                background = LayerDrawable(arrayOf(GradientDrawable().apply { setColor(BROWN) }, GradientDrawable().apply { setColor(0xff2b1d12.toInt()) })).apply { setLayerInset(1, 0, 0, 0, dp(1)) }
            })
            tbl.addView(tr)
        }
        endStats.addView(tbl, LinearLayout.LayoutParams(WC, WC).apply { bottomMargin = dp(6) })
        root.postDelayed({ if (G === g) endM.visibility = View.VISIBLE }, 1200)
        sfx(if (win) S_DONE else S_CANNON, Double.NaN, Double.NaN)
    }
}
