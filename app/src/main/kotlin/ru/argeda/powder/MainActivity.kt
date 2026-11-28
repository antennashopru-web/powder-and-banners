package ru.argeda.powder

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.SystemClock
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
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

private const val CREAM = 0xfff3e6c8.toInt()
private const val GOLDTXT = 0xffffd77a.toInt()
private const val BROWN = 0xff5a3f22.toInt()
private const val BORDER = 0xffb08a50.toInt()
private const val DARK = 0xff3d2a17.toInt()
private const val GILT = 0xffd9a84a.toInt()
private const val GOLD_L = 0xffffe39a.toInt()

/** Вся игра — нативное Android-приложение на Kotlin: поле боя на Canvas, интерфейс из обычных View. */
class MainActivity : Activity(), Host, Choreographer.FrameCallback {
    private lateinit var root: FrameLayout
    private var spr: Sprites? = null
    private var sfx: Sfx? = null
    private var gv: GameView? = null
    private var v3: View3D? = null
    private var models: Array<Model3D>? = null
    private val prefs by lazy { getSharedPreferences("powder", Context.MODE_PRIVATE) }
    private val gl3 by lazy { (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion >= 0x30000 }
    /** настоящее 3D (OpenGL ES 3) или прежняя 2D-картинка */
    private var use3d = true
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
    private lateinit var objTv: TextView
    private lateinit var endNext: TextView
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

    private fun grad(top: Int, bot: Int, r: Float) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bot)).apply { cornerRadius = dp(r).toFloat() }

    /** кнопка «дерево с золотым кантом»; при нажатии светлеет */
    private fun woodBtn(r: Float = 8f, on: Boolean = false): Drawable {
        fun layer(top: Int, bot: Int) = LayerDrawable(arrayOf(
            box(0xff120b05.toInt(), 0, r, 0f),
            grad(top, bot, r - 1).apply { setStroke(dp(1.5f), if (on) GOLD_L else GILT) },
            box(Color.TRANSPARENT, 0x30ffffff, r - 2.5f, 1f),
        )).apply { setLayerInset(1, dp(1), dp(1), dp(1), dp(1)); setLayerInset(2, dp(3), dp(3), dp(3), dp(3)) }
        val normal = if (on) layer(0xffe8be62.toInt(), 0xffa8772e.toInt()) else layer(0xff74512b.toInt(), 0xff3b2613.toInt())
        val pressed = if (on) layer(0xfff6d488.toInt(), 0xffc89848.toInt()) else layer(0xffa07040.toInt(), 0xff5e3d1e.toInt())
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /** панель: тёмное дерево, золотая рамка и тонкий внутренний кант */
    private fun panelBg(r: Float = 10f, top: Int = 0xf2402a16.toInt(), bot: Int = 0xf222160b.toInt()): Drawable = LayerDrawable(arrayOf(
        box(0xff0e0905.toInt(), 0, r, 0f),
        grad(top, bot, r - 1).apply { setStroke(dp(2f), GILT) },
        box(Color.TRANSPARENT, 0x55ffe39a, r - 4, 1f),
    )).apply { setLayerInset(1, dp(1), dp(1), dp(1), dp(1)); setLayerInset(2, dp(5), dp(5), dp(5), dp(5)) }

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
        root.post { hideBars() }
        buildMenu()
        buildSplash()
        Thread {
            val s = Sprites(assets)
            val a = try { Sfx(this) } catch (e: Exception) { null }
            if (gl3) models = try { Models3D.load(assets) } catch (e: Throwable) { android.util.Log.e("Powder3D", "models", e); null }
            runOnUiThread { onLoaded(s, a) }
        }.start()
    }

    private lateinit var splash: View
    private val splashT0 = SystemClock.uptimeMillis()

    /** заставка с названием игры */
    private fun buildSplash() {
        val f = FrameLayout(this)
        f.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(0xff3a2612.toInt(), 0xff0c0703.toInt())
            gradientRadius = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 0.55f
        }
        f.isClickable = true
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val logo = tv("Порох и Знамёна", 38f, GOLD_L, true).apply {
            letterSpacing = 0.06f; gravity = Gravity.CENTER; setSingleLine(true); setPadding(0, dp(4), 0, dp(4))
            setShadowLayer(dp(10).toFloat(), 0f, 0f, 0xaaffa030.toInt())
        }
        col.addView(logo, LinearLayout.LayoutParams(MP, WC))
        col.addView(View(this).apply { background = grad(0x00d9a84a, 0x00d9a84a, 0f).apply {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT; colors = intArrayOf(0x00d9a84a, GILT, 0x00d9a84a)
        } }, LinearLayout.LayoutParams(dp(220), dp(2)).apply { topMargin = dp(10); bottomMargin = dp(10) })
        col.addView(tv("стратегия эпохи пик и мушкетов", 17f, CREAM).apply { gravity = Gravity.CENTER; setTypeface(serif, Typeface.ITALIC); letterSpacing = 0.08f; alpha = 0.9f })
        f.addView(col, lp(MP, WC, Gravity.CENTER))
        col.alpha = 0f; col.scaleX = 0.92f; col.scaleY = 0.92f
        col.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).start()
        splash = f
        root.addView(f, lp(MP, MP))
    }

    private fun hideSplash() {
        val wait = (2200 - (SystemClock.uptimeMillis() - splashT0)).coerceAtLeast(0)
        root.postDelayed({
            splash.animate().alpha(0f).setDuration(600).withEndAction { root.removeView(splash) }.start()
        }, wait)
    }

    private fun onLoaded(s: Sprites, a: Sfx?) {
        if (isFinishing) return
        spr = s; sfx = a
        val v = GameView(this, s)
        gv = v
        root.addView(v, 0, lp(MP, MP))
        var at = 1
        models?.let { ms ->
            val w = View3D(this, v, ms)
            w.renderer.onFail = { msg -> runOnUiThread { toast("3D не работает на этом устройстве, включена 2D-графика"); set3d(false, save = false) } }
            v3 = w
            root.addView(w, at++, lp(MP, MP))
        }
        buildHud(s)
        root.addView(hud, at, lp(MP, MP))
        set3d(use3d && v3 != null, save = false)
        hud.visibility = View.GONE
        pauseM = buildPause(); root.addView(pauseM, lp(MP, MP)); pauseM.visibility = View.GONE
        endM = buildEnd(); root.addView(endM, lp(MP, MP)); endM.visibility = View.GONE
        v.onTouchStart = null
        startBtn.text = "Начать битву"
        startBtn.alpha = 1f
        for (t in intArrayOf(U_PIKE, U_MUSKET, U_CAV, U_CANNON)) menuIcons.addView(ImageView(this).apply { setImageBitmap(s.unitIcons[t]) },
            LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(4); marginEnd = dp(4) })
        root.setOnApplyWindowInsetsListener { _, ins ->
            // интерфейс не должен уходить под вырез экрана и системные кнопки, если они видны
            var l = 0
            var t = 0
            var r = 0
            var b = 0
            if (Build.VERSION.SDK_INT >= 30) {
                val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                l = i.left; t = i.top; r = i.right; b = i.bottom
            } else if (Build.VERSION.SDK_INT >= 28) {
                val c = ins.displayCutout
                l = c?.safeInsetLeft ?: 0; r = c?.safeInsetRight ?: 0
            }
            hud.setPadding(l, t, r, b)
            ins
        }
        root.requestApplyInsets()
        hideSplash()
        refreshContinue()
        // для проверки на эмуляторе: am start ... --ei start 1 --ez autopilot true --ei speed 3
        val it = intent
        if (it != null && it.hasExtra("oldbuild")) Updater.pretend = it.getIntExtra("oldbuild", 0).toLong()
        val ciCamp = it != null && (it.hasExtra("mission") || it.getBooleanExtra("campaign", false))
        if ((it == null || !it.hasExtra("start")) && !ciCamp) Updater.check(this, false)
        // для проверки сборки: --ei mission N запускает миссию, --ez campaign true открывает список миссий
        if (it != null && it.hasExtra("mission")) {
            if (it.getBooleanExtra("gfx2d", false)) set3d(false, save = false)
            startMission(it.getIntExtra("mission", 0).coerceIn(0, Campaign.M.size - 1))
            G?.speed = it.getIntExtra("speed", 1).coerceIn(1, 3)
        } else if (it != null && it.getBooleanExtra("campaign", false)) showCampaign()
        if (it != null && it.hasExtra("start")) {
            optDiff = it.getIntExtra("start", 1).coerceIn(0, 2)
            optSize = it.getIntExtra("size", 80)
            if (it.getBooleanExtra("gfx2d", false)) set3d(false, save = false)
            v3?.cam?.yaw = it.getFloatExtra("yaw", 0f).toDouble()
            startGame()
            G?.let { g ->
                g.speed = it.getIntExtra("speed", 1).coerceIn(1, 3)
                if (it.getBooleanExtra("autopilot", false)) { g.autopilot(); autoCam = true }
                if (it.getBooleanExtra("dragons", false)) {
                    // для проверки: свои драконы у ратуши и вражеский дракон рядом
                    val h = g.blds.first { b -> b.team == 0 && b.type == B_HALL }
                    for (k in 0 until 3) g.spawnUnit(U_DRAGON, 0, h.x + 70 + k * 30, h.y + 60 + (k % 2) * 25)
                    g.spawnUnit(U_DRAGON, 1, h.x + 260, h.y - 120)
                    g.computePop()
                }
                if (it.getBooleanExtra("upgrades", false)) {
                    // для проверки: арсенал и академия у ратуши, выбран арсенал с его улучшениями
                    val h = g.blds.first { b -> b.team == 0 && b.type == B_HALL }
                    var ars: Bld? = null
                    for (type in intArrayOf(B_ARSENAL, B_ACADEMY)) {
                        search@ for (r in 4..12) for (dy in -r..r) for (dx in -r..r) {
                            if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
                            if (g.canPlace(type, h.tx + dx, h.ty + dy, 1)) { val b = g.placeBuilding(type, 0, h.tx + dx, h.ty + dy, true); if (type == B_ARSENAL) ars = b; break@search }
                        }
                    }
                    g.res[0] = doubleArrayOf(900.0, 900.0, 900.0, 900.0)
                    ars?.let { b -> g.enqueueTech(b, T_BLADES); g.tech[0][T_ARMOR] = 1; g.sel = arrayListOf(b); g.uiDirty = true }
                }
                if (it.getBooleanExtra("walls", false)) {
                    // для проверки: кольцо стен с воротами вокруг ратуши и отряд врага у стены
                    val h = g.blds.first { b -> b.team == 0 && b.type == B_HALL }
                    for (j in h.ty - 3..h.ty + 5) for (i in h.tx - 3..h.tx + 5) {
                        if (i != h.tx - 3 && i != h.tx + 5 && j != h.ty - 3 && j != h.ty + 5) continue
                        val type = if (i == h.tx + 1 || j == h.ty + 1) B_GATE else B_WALL
                        if (g.canPlace(type, i, j, 1)) g.placeBuilding(type, 0, i, j, true)
                    }
                    for (k in 0 until 6) g.spawnUnit(U_PIKE, 1, h.x + 200 + (k % 3) * 16, h.y - 40 + (k / 3) * 16).let { u -> u.cmd = CMD_AMOVE; u.hasAmove = true; u.amX = h.x; u.amY = h.y; g.walkTeam = 1; g.goalPt(u, h.x, h.y, 10.0); g.walkTeam = -1 }
                    g.computePop()
                }
                if (it.getBooleanExtra("troops", false)) {
                    // для проверки: гренадеры, драгуны и мортиры против вражеской стены и пикинёров
                    val h = g.blds.first { b -> b.team == 0 && b.type == B_HALL }
                    var wall: Bld? = null
                    for (j in h.ty - 3..h.ty + 3) if (g.canPlace(B_WALL, h.tx + 9, j, 1)) g.placeBuilding(B_WALL, 1, h.tx + 9, j, true).let { b -> if (j == h.ty) wall = b }
                    val bx = h.x + 70; val by = h.y
                    for (k in 0 until 4) g.spawnUnit(U_GREN, 0, bx + 40, by - 36 + k * 18)
                    for (k in 0 until 3) g.spawnUnit(U_DRAGOON, 0, bx + 10, by - 60 + k * 24 + (if (k > 0) 60 else 0))
                    g.spawnUnit(U_HERO, 0, bx + 16, by)
                    g.spawnUnit(U_SIEGE, 0, bx + 60, by + 70).let { u -> wall?.let { w -> u.target = w } }
                    for (k in 0 until 2) g.spawnUnit(U_MORTAR, 0, bx - 20, by - 30 + k * 60).let { u -> wall?.let { w -> u.target = w } }
                    for (k in 0 until 6) g.spawnUnit(U_PIKE, 1, bx + 150 + (k % 2) * 16, by - 40 + (k / 2) * 18).let { u -> u.cmd = CMD_AMOVE; u.hasAmove = true; u.amX = h.x; u.amY = h.y; g.walkTeam = 1; g.goalPt(u, h.x, h.y, 10.0); g.walkTeam = -1 }
                    g.computePop()
                    if (it.hasExtra("zoom")) g.camZ = it.getFloatExtra("zoom", 1f).toDouble().coerceIn(0.45, 2.0)
                    g.centerCam(bx + 50, by)
                }
                if (it.hasExtra("zoom") && !it.getBooleanExtra("troops", false)) { g.camZ = it.getFloatExtra("zoom", 1f).toDouble().coerceIn(0.45, 2.0); g.clampCam() }
            }
        } else if (it == null || !it.hasExtra("mission")) startDemo()
    }

    /** за меню идёт живое сражение двух ИИ */
    private var demo = false

    private fun startDemo() {
        val v = gv ?: return
        val g = Game(64, 1, true, this, v.vwDp, v.vhDp)
        g.camZ = 1.05; g.clampCam()
        demo = true; autoCam = true; autoCamT = 0.0
        g.speed = 2
        v.game = g
        startLoop()
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

    override fun onResume() {
        super.onResume()
        v3?.onResume()
        hideBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onPause() {
        super.onPause()
        v3?.onPause()
        val g = G
        if (g != null && !demo && !g.over) saveGame()
        if (g != null && !demo && !g.paused && !g.over) togglePause()
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
        if (demo) return false
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
        c.background = panelBg(12f, 0xf03a2714.toInt(), 0xf01e1309.toInt())
        c.setPadding(dp(24), dp(18), dp(24), dp(16))
        c.elevation = dp(10).toFloat()
        val w = min(dp(maxW), resources.displayMetrics.widthPixels - dp(24))
        c.layoutParams = FrameLayout.LayoutParams(w, WC, Gravity.CENTER)
        return c
    }

    private fun screen(c: LinearLayout, see: Boolean = false): View {
        val s = ScrollView(this)
        s.isFillViewport = true
        s.background = if (!see) screenBg() else GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(0x55000000, 0x88100a04.toInt(), 0xdd0b0703.toInt())
            gradientRadius = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 0.62f
        }
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
        val st = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xffc8483a.toInt(), 0xff8a281e.toInt())).apply {
                setStroke(dp(2), GOLD_L); cornerRadius = dp(8).toFloat() })
            addState(intArrayOf(), bg)
        }
        return btn(text, 19f, st, 12, 12, true, 0xfffff3d6.toInt(), click).apply {
            letterSpacing = 0.04f; setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xaa000000.toInt())
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(12) }
        }
    }

    private fun midBtn(text: String, click: () -> Unit) = btn(text, 15f, woodBtn(), 10, 10, click = click).apply {
        layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(10) }
    }

    private fun seg(labels: Array<String>, on: Int, pick: (Int) -> Unit): LinearLayout {
        val s = LinearLayout(this)
        s.orientation = LinearLayout.HORIZONTAL
        s.background = box(DARK, GILT, 8f, 1.5f)
        s.outlineProvider = ViewOutlineProvider.BACKGROUND
        s.clipToOutline = true
        s.setPadding(dp(1), dp(1), dp(1), dp(1))
        val bs = ArrayList<TextView>()
        fun paint(k: Int) {
            bs.forEachIndexed { i, b ->
                b.background = if (i == k) grad(0xffeec46a.toInt(), 0xffb88a3c.toInt(), 0f) else grad(0xff3e2a16.toInt(), 0xff2a1c0e.toInt(), 0f)
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
        c.addView(tv("Порох и Знамёна", 36f, GOLDTXT, true).apply {
            letterSpacing = 0.05f; gravity = Gravity.CENTER
            setShadowLayer(dp(3).toFloat(), 0f, dp(2).toFloat(), 0xff3a1c05.toInt())
            paint.shader = LinearGradient(0f, 0f, 0f, textSize * 1.1f, intArrayOf(0xfffff0b8.toInt(), 0xffffd77a.toInt(), 0xffc8892e.toInt()), null, Shader.TileMode.CLAMP)
        })
        c.addView(ornament(), LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6); bottomMargin = dp(2) })
        c.addView(tv("Стратегия эпохи пик и мушкетов", 16f).apply {
            setTypeface(serif, Typeface.ITALIC); alpha = 0.85f; setPadding(0, dp(2), 0, dp(6))
        })
        menuIcons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        c.addView(menuIcons, LinearLayout.LayoutParams(WC, WC).apply { bottomMargin = dp(4) })
        c.addView(row("Противник", seg(arrayOf("Лёгкий", "Обычный", "Трудный"), 1) { optDiff = it }))
        c.addView(row("Карта", seg(arrayOf("Малая", "Средняя", "Большая"), 1) { optSize = intArrayOf(64, 80, 100)[it] }))
        use3d = gl3 && prefs.getBoolean("3d", true)
        gfxRow = row("Графика", seg(arrayOf("3D", "2D"), if (use3d) 0 else 1) { set3d(it == 0) })
        gfxRow.visibility = if (gl3) View.VISIBLE else View.GONE
        c.addView(gfxRow)
        startBtn = bigBtn("Загрузка…") { startGame() }
        startBtn.alpha = 0.6f
        c.addView(startBtn)
        c.addView(midBtn("⚔ Кампания") { showCampaign() })
        contBtn = midBtn("▶ Продолжить") { loadGame() }
        contBtn.visibility = View.GONE
        c.addView(contBtn)
        c.addView(midBtn("⟳ Обновить игру") { Updater.check(this, true) })
        val help = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        c.addView(tv("Как играть", 14f).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG; alpha = 0.85f; setPadding(dp(8), dp(12), dp(8), dp(4)); isClickable = true
            setOnClickListener { help.visibility = if (help.visibility == View.GONE) View.VISIBLE else View.GONE }
        })
        val ps = arrayOf(
            "<b>Цель:</b> разрушить все здания и войска противника.",
            "<b>Кампания:</b> пять миссий подряд, у каждой своя задача: построить лагерь, удержать заставу, собрать казну, взять крепость и выиграть генеральное сражение. Задача миссии видна под ресурсами. Следующая миссия открывается после победы.",
            "<b>Выбор:</b> тап по своему отряду или зданию. Двойной тап выбирает всех таких же на экране. Чтобы выделить рамкой, задержите палец и ведите (или нажмите «Рамка»).",
            "<b>Приказы:</b> тап по земле — идти строем, по врагу — атаковать, по лесу, шахте или ферме — добывать (крестьяне). «Натиск» — идти и бить всех по пути.",
            "<b>Карта:</b> одним пальцем — двигать, двумя — масштаб, тап по мини-карте — перейти. В 3D поверните два пальца, чтобы развернуть камеру, а кнопка «🧭» вернёт её на север.",
            "<b>Хозяйство:</b> крестьяне рубят лес, ломают камень, моют золото и работают на фермах. Дома дают место для войск, рынок принимает добычу.",
            "<b>Рынок:</b> выберите рынок, чтобы купить или продать по 100 еды, дерева или камня за золото. Цены меняются от сделок.",
            "<b>Ремонт и снос:</b> выберите крестьян и тапните по повреждённому зданию — починят за дерево. У своего здания есть кнопка «Снести» (вернётся треть цены).",
            "<b>Отряды 1·2·3:</b> выделите войска и нажмите кнопку с номером — отряд запомнится. Потом одно нажатие выбирает его, второе подряд — переносит к нему камеру. Удержание — запомнить заново.",
            "<b>Сохранение:</b> в паузе есть «Сохранить игру», а при сворачивании игра сохраняется сама. Продолжить можно из главного меню.",
            "<b>Войска:</b> пикинёры держат конницу, мушкетёры бьют залпом издалека, рейтары быстры, пушки рушат здания и строи.",
            "<b>Улучшения:</b> Арсенал изучает закалённые клинки, кирасы и зернистый порох, Академия учит артиллеристов, каменщиков и строевой выучке. Улучшение действует на все ваши войска сразу, у многих две ступени.",
            "<b>Новые войска:</b> в Казарме — гренадеры, их гранаты рвутся и задевают всех рядом. В Конюшне — драгуны, конные стрелки. В Литейной — мортиры: бьют навесом очень далеко и ломают стены, но вблизи беззащитны.",
            "<b>Герой Генерал:</b> нанимается в Ратуше, один на армию. Сильный боец, со временем сам залечивает раны. Свои войска рядом с ним бьют на четверть сильнее и получают меньше урона. Вокруг него видно золотое кольцо.",
            "<b>Осадная башня:</b> строится в Литейной. Отправьте её к вражеской стене: когда башня встанет вплотную, с неё перекинут мостик, и ваши войска пройдут поверх стены, не ломая её. Башню берегите от пушек.",
            "<b>Стены и ворота:</b> выберите «Стена» и проведите пальцем по земле: стена тянется по прямой, по 10 камня за кусок. Ворота пропускают только своих, их можно поставить прямо на стену. Враг стену не обходит, а проламывает, пушки делают это быстрее всех.",
            "<b>Драконы:</b> стройте логово дракона. Драконы летают над лесом и водой и жгут огнём всё вокруг цели. Пикинёры, рейтары и пушки до них не достают: сбивают их мушкетёры, башни и ратуша.",
        )
        for (p in ps) help.addView(tv(html(p), 13.5f).apply { setLineSpacing(0f, 1.25f); setPadding(0, dp(6), 0, 0) })
        help.addView(tv(html("<b>Графика:</b> 3D-модели персонажей и зданий — KayKit (Kay Lousberg, www.kaylousberg.com), лицензия CC0. Пики, мушкеты и мундиры — свои." +
            "<br><b>Модели со Sketchfab (лицензия CC BY 4.0):</b> солдаты — «European Soldier 17th Century», HaiPham; пушка — «Canon 17th Century», iZnoGouD; мортира — «Cannon Low-Poly», derdaryen; " +
            "конь — «Horse», MAXDESIGN-3D; ратуша — «Rauma Town Hall», Karumaki; дом — «Half-timbered House», Kovonic; склад — «Old European House», Valkirin1; казарма — «Viking town Hall», Bucks (Its_Bucks); " +
            "конюшня — «Old Wooden Barn (House 4)», Blenderust (narighillya); литейная — «Old Smithy», lucasprs51450; арсенал — «Medieval house, Generic Textures», by__Rx; академия — «Low Poly Medieval Gothic Church», AlexFerrart3D; " +
            "башня — «Medieval Tower», Den (dfomenco); " +
            "мельница — «Windmill Low Poly», Aegis_Wolf; ели — «Pine Tree [Game-ready]», Wenedi; лиственные деревья — «Leaf Tree - PS1 Low Poly», Wersaus33; " +
            "берёзы — «Birch Trees Pack, PS1 Low Poly», Vova Truestory (FlowOff). Модели уменьшены и подогнаны под игру, добавлены цвета команд."), 12f).apply {
            alpha = 0.75f; setLineSpacing(0f, 1.25f); setPadding(0, dp(6), 0, 0)
        })
        c.addView(help, LinearLayout.LayoutParams(MP, WC))
        c.addView(tv("Установлена сборка " + Updater.installed(this), 12.5f, GILT).apply { alpha = 0.85f; setPadding(0, dp(8), 0, dp(4)) })
        menu = screen(c, true)
        root.addView(menu, lp(MP, MP))
    }

    private lateinit var menuIcons: LinearLayout
    private lateinit var contBtn: TextView

    // ------------------------------------------------------------ сохранение
    private val saveFile get() = File(filesDir, "save.json")
    private val timeFmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

    private fun refreshContinue() {
        val f = saveFile
        if (spr != null && f.exists()) {
            contBtn.text = "▶ Продолжить битву (" + timeFmt.format(Date(f.lastModified())) + ")"
            contBtn.visibility = View.VISIBLE
        } else contBtn.visibility = View.GONE
    }

    private fun saveGame(): Boolean {
        val g = G ?: return false
        if (demo || g.over || g.auto) return false
        return try {
            val tmp = File(filesDir, "save.tmp")
            tmp.writeText(Save.toJson(g).toString())
            tmp.renameTo(saveFile)
        } catch (e: Exception) { false }
    }

    private fun loadGame() {
        val v = gv ?: return
        val g = try { Save.fromJson(JSONObject(saveFile.readText()), this, v.vwDp, v.vhDp) } catch (e: Exception) { null }
        if (g == null) { contBtn.text = "Сохранение повреждено"; return }
        launch(g)
        hud.postDelayed({ if (G === g) toast("Игра загружена") }, 400)
    }

    /** золотой орнамент-разделитель: ——— ⚔ ——— */
    private fun ornament(): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun line(l2r: Boolean) = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                if (l2r) intArrayOf(0x00d9a84a, GILT) else intArrayOf(GILT, 0x00d9a84a))
        }
        r.addView(line(true), LinearLayout.LayoutParams(0, dp(1.5f), 1f))
        r.addView(tv("⚔", 16f, GILT).apply { setPadding(dp(10), 0, dp(10), 0) })
        r.addView(line(false), LinearLayout.LayoutParams(0, dp(1.5f), 1f))
        return r
    }

    private lateinit var saveBtn: TextView

    private fun buildPause(): View {
        val c = card(380)
        c.addView(tv("Пауза", 26f, GOLDTXT, true).apply { setPadding(0, 0, 0, dp(6)) })
        c.addView(ornament(), LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(4) })
        c.addView(bigBtn("Продолжить") { togglePause() })
        saveBtn = midBtn("💾 Сохранить игру") {
            saveBtn.text = if (saveGame()) "✓ Сохранено " + timeFmt.format(Date()) else "Не удалось сохранить"
        }
        c.addView(saveBtn)
        soundBtn = midBtn("Звук: вкл") {
            soundOn = !soundOn; sfx?.on = soundOn; soundBtn.text = "Звук: " + if (soundOn) "вкл" else "выкл"
        }
        c.addView(soundBtn)
        c.addView(midBtn("Сдаться и выйти в меню") { toMenu() })
        return screen(c)
    }

    private fun buildEnd(): View {
        val c = card(380)
        endTitle = tv("", 28f, GOLDTXT, true).apply { setPadding(0, 0, 0, dp(6)) }
        c.addView(endTitle)
        c.addView(ornament(), LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(6) })
        endStats = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        c.addView(endStats)
        endNext = midBtn("") { }
        endNext.visibility = View.GONE
        c.addView(endNext)
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
        top.setPadding(dp(6), 0, dp(6), dp(3))
        top.background = LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(0xff0e0905.toInt()) },
            GradientDrawable().apply { setColor(GILT) },
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xf5241709.toInt(), 0xe8382412.toInt())),
        )).apply { setLayerInset(1, 0, 0, 0, dp(1)); setLayerInset(2, 0, 0, 0, dp(2.5f)) }
        val resRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        for (i in 0 until 5) {
            val t = tv("", 14.5f, CREAM, true).apply {
                maxLines = 1; gravity = Gravity.CENTER_VERTICAL
                background = box(0x66000000, 0x55d9a84a, 12f, 1f); setPadding(dp(8), dp(3), dp(10), dp(3))
            }
            resTv[i] = t
            resRow.addView(t, LinearLayout.LayoutParams(WC, WC).apply { marginEnd = dp(6) })
        }
        top.addView(resRow, LinearLayout.LayoutParams(0, WC, 1f))
        fun tb(t: String, click: () -> Unit) = btn(t, 14f, woodBtn(6f), 0, 8, true, click = click).apply {
            minWidth = dp(44)
        }
        speedBtn = tb("×1") { G?.let { it.speed = if (it.speed >= 3) 1 else it.speed + 1; syncHudButtons(it) } }
        top.addView(speedBtn, LinearLayout.LayoutParams(WC, dp(28)))
        compassBtn = tb("🧭") { v3?.resetYaw() }
        top.addView(compassBtn, LinearLayout.LayoutParams(WC, dp(28)).apply { marginStart = dp(6) })
        compassBtn.visibility = if (use3d) View.VISIBLE else View.GONE
        top.addView(tb("❚❚") { togglePause() }, LinearLayout.LayoutParams(WC, dp(28)).apply { marginStart = dp(6) })
        hud.addView(top, lp(MP, dp(38), Gravity.TOP))

        // быстрые кнопки справа
        val quick = LinearLayout(this)
        quick.orientation = LinearLayout.VERTICAL
        fun qb(t: String, click: () -> Unit) = btn(t, 13f, woodBtn(), 8, 10, click = click).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0xaa000000.toInt())
        }
        quick.addView(qb("⚔ Армия") { quickArmy() }, LinearLayout.LayoutParams(MP, WC))
        quick.addView(qb("⛏ Без дела") { quickIdle() }, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        boxBtn = qb("▭ Рамка") { G?.let { it.boxMode = !it.boxMode; it.uiDirty = true; syncHudButtons(it) } }
        quick.addView(boxBtn, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        val grow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (k in 0 until 3) {
            val b = btn("${k + 1}", 15f, woodBtn(8f), 6, 0, true) { groupTap(k) }.apply {
                setOnLongClickListener { groupSet(k); true }
            }
            groupBtns[k] = b
            grow.addView(b, LinearLayout.LayoutParams(0, dp(36), 1f).apply { if (k > 0) marginStart = dp(5) })
        }
        quick.addView(grow, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        hud.addView(quick, lp(WC, WC, Gravity.TOP or Gravity.END).apply { topMargin = dp(42); marginEnd = dp(6) })

        // мини-карта
        val mv = MiniView(this, gv!!)
        miniV = mv
        hud.addView(mv, lp(dp(108), dp(108), Gravity.BOTTOM or Gravity.START).apply { leftMargin = dp(4); bottomMargin = dp(4) })

        // нижняя панель
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.HORIZONTAL
        panel.background = panelBg(10f)
        panel.setPadding(dp(8), dp(8), dp(8), dp(8))
        portrait = FrameLayout(this).apply { background = LayerDrawable(arrayOf(
            box(0xff120b05.toInt(), GILT, 6f, 1.5f),
            GradientDrawable().apply { gradientType = GradientDrawable.RADIAL_GRADIENT; colors = intArrayOf(0xff5a4024.toInt(), 0xff1c120a.toInt()); gradientRadius = dp(30).toFloat(); cornerRadius = dp(5).toFloat() },
        )).apply { setLayerInset(1, dp(2), dp(2), dp(2), dp(2)) } }
        portraitImg = ImageView(this)
        portrait.addView(portraitImg, lp(dp(46), dp(46), Gravity.CENTER))
        panel.addView(portrait, LinearLayout.LayoutParams(dp(56), MP).apply { marginEnd = dp(8) })
        info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        panel.addView(info, LinearLayout.LayoutParams(dp(176), MP))
        buildInfo()
        val sc = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        acts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sc.addView(acts, ViewGroup.LayoutParams(WC, MP))
        panel.addView(sc, LinearLayout.LayoutParams(0, MP, 1f).apply { marginStart = dp(6) })
        hud.addView(panel, lp(MP, dp(86), Gravity.BOTTOM).apply { leftMargin = dp(116); rightMargin = dp(4); bottomMargin = dp(4) })

        // подтверждение постройки
        val pl = LinearLayout(this)
        pl.orientation = LinearLayout.HORIZONTAL
        val pbg = { woodBtn(8f) }
        pl.addView(btn("✓ Строить здесь", 15f, pbg(), 10, 16) { G?.confirmPlace() })
        pl.addView(btn("✕ Отмена", 15f, pbg(), 10, 16) { G?.let { it.placing = null; it.uiDirty = true } },
            LinearLayout.LayoutParams(WC, WC).apply { marginStart = dp(10) })
        place = pl
        hud.addView(pl, lp(WC, WC, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(98) })

        toastTv = tv("", 14f).apply {
            background = box(0xd8201409.toInt(), GILT, 16f, 1f); setPadding(dp(16), dp(7), dp(16), dp(7)); alpha = 0f; maxLines = 1
            setTextColor(GOLD_L); elevation = dp(4).toFloat()
        }
        hud.addView(toastTv, lp(WC, WC, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(44) })
        // цель миссии кампании: строка под ресурсами
        objTv = tv("", 12.5f).apply {
            background = box(0xc8201409.toInt(), GILT, 10f, 1f); setPadding(dp(10), dp(4), dp(10), dp(4)); maxLines = 1
            setTextColor(GOLD_L); visibility = View.GONE
        }
        hud.addView(objTv, lp(WC, WC, Gravity.TOP or Gravity.START).apply { topMargin = dp(44); marginStart = dp(8) })
    }

    private fun syncHudButtons(g: Game) {
        if (boxOn != g.boxMode) { boxOn = g.boxMode; boxBtn.background = woodBtn(8f, g.boxMode) }
        boxBtn.setTextColor(if (g.boxMode) 0xff1d140b.toInt() else CREAM)
        place.visibility = if (g.placing != null) View.VISIBLE else View.GONE
        speedBtn.text = "×" + g.speed
    }

    private var boxOn = false

    private fun updateTop(g: Game) {
        val R = g.res[0]
        for (r in 0..3) resTv[r]!!.text = RICON[r] + " " + floor(R[r]).toInt()
        resTv[4]!!.text = "👥 " + g.pop[0] + "/" + g.cap[0]
        if (g.mission >= 0 && !demo) { objTv.text = "⚑ " + Campaign.status(g); objTv.visibility = View.VISIBLE } else objTv.visibility = View.GONE
        resTv[4]!!.setTextColor(if (g.pop[0] >= g.cap[0]) 0xffff8a7a.toInt() else CREAM)
        for (r in 0..3) resTv[r]!!.setTextColor(if (R[r] < 20) 0xffffb08a.toInt() else CREAM)
    }

    // полоска (здоровье, прогресс)
    private class Bar(ctx: Context, var frac: Float, val bg: Int, val fg: Int) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val r = height / 2f
            p.color = bg; c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
            val w = width * frac.coerceIn(0f, 1f)
            if (w > 0) {
                c.save(); c.clipRect(0f, 0f, w, height.toFloat())
                p.color = fg; c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
                p.color = 0x40ffffff; c.drawRect(0f, 0f, width.toFloat(), height * 0.4f, p)
                c.restore()
            }
        }
    }

    private lateinit var portrait: FrameLayout
    private lateinit var portraitImg: ImageView
    private var portraitBmp: Bitmap? = null
    private lateinit var iName: TextView
    private lateinit var iBar1: Bar
    private lateinit var iL1: TextView
    private lateinit var iBar2: Bar
    private lateinit var iL2: TextView
    private lateinit var iL3: TextView

    private fun buildInfo() {
        iName = tv("", 15f, GOLD_L, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        iBar1 = Bar(this, 1f, 0xff2a120c.toInt(), 0xff62c43a.toInt())
        iBar2 = Bar(this, 0f, 0xff2a1a0c.toInt(), 0xffe8b84a.toInt())
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
    private fun setInfo(icon: Bitmap?, name: String, hp: Double, l1: CharSequence?, q: Double, l2: CharSequence? = null, l3: CharSequence? = null) {
        if (icon !== portraitBmp) {
            portraitBmp = icon
            portraitImg.setImageBitmap(icon)
            portrait.visibility = if (icon != null) View.VISIBLE else View.GONE
        }
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
            if (e is Bld) { sb.append(if (e.built) 'b' else 'u'); if (e.hp < e.maxhp - 1) sb.append('d'); for (q in e.queue) sb.append(q) }
            sb.append(',')
        }
        sb.append('|'); for (r in 0..3) sb.append(floor(g.res[0][r] / 10).toInt()).append(',')
        val sig = sb.toString()

        val pl = g.placing
        if (pl != null) {
            if (pl.type == B_WALL) {
                val n = pl.oks.count { it }
                setInfo(spr!!.bldIcons[pl.type], BLD[pl.type].name, -1.0, "Проведите пальцем по карте: стена тянется по прямой", -1.0,
                    if (n > 0) "Кусков: $n · цена ${costStr(IntArray(4) { BLD[B_WALL].cost[it] * n })}" else "Сюда нельзя: нужна свободная земля")
            } else setInfo(spr!!.bldIcons[pl.type], BLD[pl.type].name, -1.0, BLD[pl.type].desc, -1.0, "Нажмите на карту, чтобы выбрать место")
        } else if (sel.isEmpty()) {
            setInfo(null, "Ничего не выбрано", -1.0, "Тап по своему отряду или зданию. Удерживайте палец, чтобы выделить рамкой.", -1.0)
        } else if (sel.size == 1) {
            val e = sel[0]
            if (e is Unt) {
                val d = UNIT[e.type]
                val t = e.task
                val st = if (t != null && t.type == TASK_BUILD) "Строит"
                else if (t != null && t.type == TASK_REPAIR) "Чинит: " + BLD[t.b!!.type].name.lowercase()
                else if (t != null) "Добывает: " + RNAME[t.res].lowercase() + (if (e.carry > 0) " (${e.carry})" else "")
                else if (e.target != null) "В бою" else if (e.goal != null) "В пути" else "Ожидает"
                setInfo(spr!!.unitIcons[e.type], d.name + if (e.team != 0) " (враг)" else "", e.hp / e.maxhp, "${ceil(e.hp).toInt()}/${e.maxhp.toInt()} · атака ${d.atk.toInt()}", -1.0, if (e.team != 0) d.desc else if (e.type == U_HERO && st == "Ожидает") "Свои рядом бьют сильнее" else st)
            } else if (e is Bld) {
                val d = BLD[e.type]
                val nm = d.name + if (e.team != 0) " (враг)" else ""
                val hpT = "${ceil(e.hp).toInt()}/${e.maxhp.toInt()}"
                val ic = spr!!.bldIcons[e.type]
                if (!e.built) setInfo(ic, nm, e.hp / e.maxhp, hpT, -1.0, "Строится: ${floor(e.progress / d.time * 100).toInt()}%")
                else if (e.queue.isNotEmpty()) setInfo(ic, nm, e.hp / e.maxhp, hpT, e.qt / g.qTime(e, e.queue[0]), if (e.popBlocked) "Нужны дома!"
                    else if (e.queue[0] >= Q_TECH) "Изучается: " + TECH[e.queue[0] - Q_TECH].name else "Обучение: " + UNIT[e.queue[0]].name)
                else if (d.research.isNotEmpty() && e.team == 0) setInfo(ic, nm, e.hp / e.maxhp, hpT, -1.0, "Изучено: " + d.research.joinToString(" · ") { t -> TECH[t].icon + " ${g.tech[0][t]}/${TECH[t].levels}" })
                else if (e.type == B_STORE && e.team == 0) setInfo(ic, nm, e.hp / e.maxhp, hpT, -1.0, "Торговля: купить или продать по 100")
                else setInfo(ic, nm, e.hp / e.maxhp, hpT, -1.0, d.desc)
            } else if (e is Mine) {
                setInfo(null, if (e.res == GOLD) "Золотая жила" else "Каменоломня", -1.0, "Осталось: ${e.amount}", -1.0, "Отправьте сюда крестьян")
            }
        } else {
            val cnt = LinkedHashMap<Int, Int>()
            for (e in sel) if (e is Unt) cnt[e.type] = (cnt[e.type] ?: 0) + 1
            val top = cnt.maxByOrNull { it.value }?.key
            setInfo(if (top != null) spr!!.unitIcons[top] else null, "Отряд: ${sel.size}", -1.0, cnt.entries.joinToString("   ") { UNIT[it.key].name + " ×" + it.value }, -1.0, if (g.amove) "Натиск: тап по карте" else null)
        }

        if (sig == uiSig && !g.uiDirty) {
            val b = sel.firstOrNull()
            if (b is Bld && b.queue.isNotEmpty()) qBar?.scaleX = (b.qt / g.qTime(b, b.queue[0])).toFloat()
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
            // выбор: все такие же на карте; в смешанном отряде — оставить только один род войск
            val types = own.map { (it as Unt).type }.distinct()
            if (types.size == 1) txtBtn("◎ Все\n" + plural(types[0])) {
                g.sel = ArrayList(g.units.filter { it.team == 0 && !it.dead && it.type == types[0] })
                toast("Выбрано: ${g.sel.size}")
            } else for (t in types.sortedBy { FORM_ORDER[it] }) {
                val n = own.count { (it as Unt).type == t }
                pickBtn(spr!!.unitIcons[t], "Только", n) { g.sel = ArrayList(own.filter { (it as Unt).type == t }) }
            }
            txtBtn("✕ Снять") { g.sel = ArrayList(); g.amove = false }
        } else if (own.size == 1 && own[0] is Bld) {
            val b = own[0] as Bld
            val d = BLD[b.type]
            if (b.built && d.trains.isNotEmpty()) {
                for (t in d.trains) {
                    val q = b.queue.count { it == t }
                    actBtn(spr!!.unitIcons[t], UNIT[t].name, UNIT[t].cost, q) {
                        if (t == U_HERO && g.hasHero(0)) toast("Генерал у вас уже есть: герой один на армию")
                        else if (!g.enqueue(b, t)) toast(if (b.queue.size >= 6) "Очередь заполнена" else "Не хватает: " + g.missing(UNIT[t].cost))
                        else sfx(S_CLICK, Double.NaN, Double.NaN)
                    }
                }
            }
            if (b.built && d.research.isNotEmpty()) {
                for (t in d.research) {
                    val next = g.techNext(0, t)
                    val done = next >= TECH[t].levels
                    actBtn(techIcon(t), TECH[t].short + if (TECH[t].levels > 1 && !done) " ${next + 1}" else "", if (done) IntArray(4) else g.techCost(0, t), 0) {
                        if (done) toast(TECH[t].name + ": изучено полностью")
                        else if (!g.enqueueTech(b, t)) toast(if (b.queue.size >= 6) "Очередь заполнена" else "Не хватает: " + g.missing(g.techCost(0, t)))
                        else { sfx(S_CLICK, Double.NaN, Double.NaN); toast(TECH[t].desc) }
                    }
                }
            }
            if (b.built && b.queue.isNotEmpty()) b.queue.forEachIndexed { k, t ->
                qItem(if (t >= Q_TECH) techIcon(t - Q_TECH) else spr!!.unitIcons[t], if (k == 0) b.qt / g.qTime(b, t) else -1.0) {
                    if (k < b.queue.size) { val tt = b.queue.removeAt(k); g.refund(b, tt); if (k == 0) b.qt = 0.0 }
                }
            }
            if (b.built && b.type == B_STORE) {
                for (r in 0..2) {
                    tradeBtn("Купить\n100 ${RICON[r]}\n−${g.buyPrice(r)} 💰", g.res[0][GOLD] >= g.buyPrice(r)) {
                        if (g.trade(0, r, true)) sfx(S_CLICK, Double.NaN, Double.NaN) else toast("Не хватает золота")
                    }
                    tradeBtn("Продать\n100 ${RICON[r]}\n+${g.sellPrice(r)} 💰", g.res[0][r] >= 100) {
                        if (g.trade(0, r, false)) sfx(S_CLICK, Double.NaN, Double.NaN) else toast("Нужно 100: " + RNAME[r].lowercase())
                    }
                }
            }
            if (b.built && b.hp < b.maxhp - 1) txtBtn("🔧 Чинить") {
                val peas = g.units.filter { it.team == 0 && !it.dead && it.type == U_PEASANT }
                    .sortedBy { (if (it.task == null) 0.0 else 1e6) + hyp(it.x - b.x, it.y - b.y) }.take(3)
                if (peas.isEmpty()) toast("Нет крестьян") else { for (u in peas) g.orderRepair(u, b); toast("Чинят: ${peas.size}") }
            }
            txtBtn("🔨 Снести") {
                val now = SystemClock.uptimeMillis()
                if (demolishId == b.id && now - demolishT < 2500) { g.demolish(b); demolishId = 0 }
                else { demolishId = b.id; demolishT = now; toast("Нажмите «Снести» ещё раз для подтверждения") }
            }
            txtBtn("✕ Снять") { g.sel = ArrayList(); g.amove = false }
        } else if (sel.isNotEmpty()) txtBtn("✕ Снять") { g.sel = ArrayList(); g.amove = false }
    }

    private fun actLp() = LinearLayout.LayoutParams(dp(78), MP).apply { marginEnd = dp(5) }

    private var demolishId = 0
    private var demolishT = 0L

    private fun plural(t: Int) = when (t) {
        U_PEASANT -> "крестьяне"; U_PIKE -> "пикинёры"; U_MUSKET -> "мушкетёры"; U_CAV -> "рейтары"; U_DRAGON -> "драконы"
        U_GREN -> "гренадеры"; U_DRAGOON -> "драгуны"; U_MORTAR -> "мортиры"; U_HERO -> "герои"; U_SIEGE -> "башни"; else -> "пушки"
    }

    private fun tradeBtn(label: String, ok: Boolean, f: () -> Unit) {
        acts.addView(btn(label, 11.5f, woodBtn(7f), 2, 2) { clickAct(f) }.apply {
            setLineSpacing(0f, 1.05f); alpha = if (ok) 1f else 0.45f
        }, LinearLayout.LayoutParams(dp(70), MP).apply { marginEnd = dp(5) })
    }

    /** кнопка-портрет с числом: оставить в выделении только этот род войск */
    private fun pickBtn(icon: Bitmap?, label: String, n: Int, f: () -> Unit) {
        val fl = FrameLayout(this)
        fl.background = woodBtn(7f)
        fl.isClickable = true
        fl.setOnClickListener { clickAct(f) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(2), dp(3), dp(2), dp(2)) }
        col.addView(ImageView(this).apply { setImageBitmap(icon) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        col.addView(tv(label, 11f, bold = true).apply { gravity = Gravity.CENTER })
        fl.addView(col, lp(MP, MP))
        fl.addView(tv(n.toString(), 11f, 0xff1d140b.toInt(), true).apply {
            background = box(0xffe8b84a.toInt(), 0, 8f, 0f); setPadding(dp(5), dp(1), dp(5), dp(1))
        }, lp(WC, WC, Gravity.TOP or Gravity.END).apply { topMargin = dp(2); marginEnd = dp(3) })
        acts.addView(fl, LinearLayout.LayoutParams(dp(62), MP).apply { marginEnd = dp(5) })
    }

    private fun clickAct(f: () -> Unit) { f(); G?.uiDirty = true }

    private fun txtBtn(label: String, f: () -> Unit) {
        acts.addView(btn(label, 13.5f, woodBtn(7f), 2, 2) { clickAct(f) }.apply {
            setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0xaa000000.toInt())
        }, actLp())
    }

    private fun actBtn(icon: Bitmap?, name: String, cost: IntArray, badge: Int, f: () -> Unit) {
        val fl = FrameLayout(this)
        fl.background = woodBtn(7f)
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

    private val techIcons = arrayOfNulls<Bitmap>(TECH.size)
    /** значок улучшения: эмодзи на тёмном круге с золотым кантом */
    private fun techIcon(t: Int): Bitmap = techIcons[t] ?: Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).also { bm ->
        val c = android.graphics.Canvas(bm)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = 0xff2a1c10.toInt(); c.drawCircle(36f, 36f, 33f, p)
        p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xffe8b84a.toInt(); c.drawCircle(36f, 36f, 33f, p)
        p.style = android.graphics.Paint.Style.FILL; p.textSize = 38f; p.textAlign = android.graphics.Paint.Align.CENTER
        c.drawText(TECH[t].icon, 36f, 36f - (p.descent() + p.ascent()) / 2, p)
        techIcons[t] = bm
    }

    private fun qItem(icon: Bitmap?, frac: Double, f: () -> Unit) {
        val fl = FrameLayout(this)
        fl.background = box(0xff1c120a.toInt(), GILT, 6f, 1f)
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

    // ------------------------------------------------------------ отряды 1·2·3
    private val groups = Array(3) { ArrayList<Ent>() }
    private val groupBtns = arrayOfNulls<TextView>(3)
    private var groupLastK = -1
    private var groupLastT = 0L

    private fun groupSet(k: Int) {
        val g = G ?: return
        val own = g.sel.filter { it.team == 0 && !it.dead && (it is Unt || it is Bld) }
        if (own.isEmpty()) { toast("Сначала выделите войска"); return }
        groups[k].clear(); groups[k].addAll(own)
        toast("Отряд ${k + 1} запомнен: ${own.size}")
        sfx(S_CLICK, Double.NaN, Double.NaN)
        syncGroups()
    }

    private fun groupTap(k: Int) {
        val g = G ?: return
        groups[k].removeAll { it.dead }
        if (groups[k].isEmpty()) { groupSet(k); return }
        val now = SystemClock.uptimeMillis()
        if (groupLastK == k && now - groupLastT < 450) {
            val list = groups[k]
            g.centerCam(list.sumOf { it.x } / list.size, list.sumOf { it.y } / list.size)
        }
        groupLastK = k; groupLastT = now
        g.sel = ArrayList(groups[k]); g.amove = false; g.uiDirty = true
        sfx(S_CLICK, Double.NaN, Double.NaN)
    }

    private fun syncGroups() {
        for (k in 0 until 3) {
            groups[k].removeAll { it.dead }
            val b = groupBtns[k] ?: continue
            val t = if (groups[k].isEmpty()) "${k + 1}" else "${k + 1}·${groups[k].size}"
            if (b.text.toString() != t) b.text = t
            b.alpha = if (groups[k].isEmpty()) 0.6f else 1f
        }
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
        launch(g)
        hud.postDelayed({ if (G === g) toast("Выделите крестьян и постройте ферму и дома") }, 800)
    }

    /** сколько миссий кампании пройдено (следующая по счёту открыта) */
    private fun campDone() = prefs.getInt("camp", 0).coerceIn(0, Campaign.M.size)

    private fun showCampaign() {
        if (gv == null) return
        val done = campDone()
        val items = Array(Campaign.M.size) { i -> (if (i < done) "✓ " else if (i == done) "▶ " else "🔒 ") + (i + 1) + ". " + Campaign.M[i].title }
        android.app.AlertDialog.Builder(this).setTitle("Кампания «Знамёна над рекой»")
            .setItems(items) { _, i ->
                if (i > done) toast2("Сначала пройдите миссию " + (done + 1))
                else briefing(i)
            }
            .setNegativeButton("Назад", null).show()
    }

    private fun briefing(i: Int) {
        val m = Campaign.M[i]
        android.app.AlertDialog.Builder(this).setTitle((i + 1).toString() + ". " + m.title)
            .setMessage(m.story + "\n\nЗадача: " + m.goal)
            .setPositiveButton("В бой") { _, _ -> startMission(i) }
            .setNegativeButton("Назад") { _, _ -> showCampaign() }.show()
    }

    private fun toast2(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    private fun startMission(i: Int) {
        val v = gv ?: return
        val m = Campaign.M[i]
        val g = Game(m.size, m.diff, false, this, v.vwDp, v.vhDp, seed0 = m.seed)
        Campaign.setup(g, i)
        launch(g)
        hud.postDelayed({ if (G === g) toast(m.goal) }, 800)
    }

    private fun launch(g: Game) {
        val v = gv ?: return
        demo = false; autoCam = false
        groups.forEach { it.clear() }
        v.game = g
        menu.visibility = View.GONE
        hud.visibility = View.VISIBLE
        uiSig = ""; uiT = 0.0; miniT = 0.0
        syncHudButtons(g)
        startLoop()
    }

    private fun togglePause() {
        val g = G ?: return
        if (g.over) return
        g.paused = !g.paused
        if (g.paused) saveBtn.text = "💾 Сохранить игру"
        pauseM.visibility = if (g.paused) View.VISIBLE else View.GONE
    }

    private fun toMenu() {
        gv?.game = null
        pauseM.visibility = View.GONE; endM.visibility = View.GONE; hud.visibility = View.GONE; menu.visibility = View.VISIBLE
        refreshContinue()
        startDemo()
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
        val w = v3
        if (use3d && w != null) v.game?.let { w.frame(it) } else v.invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun onFrame(dtReal: Double) {
        val g = G ?: return
        if (demo) { demoCam(g, dtReal); return }
        uiT -= dtReal
        if (uiT <= 0 || g.uiDirty) { uiT = 0.25; updateTop(g); updatePanel(g); syncHudButtons(g); syncGroups() }
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

    private var camTX = Double.NaN
    private var camTY = 0.0

    /** камера заставки плавно следит за самым интересным: бой, армия, город */
    private fun demoCam(g: Game, dt: Double) {
        autoCamT -= dt
        if (autoCamT <= 0 || camTX.isNaN()) {
            autoCamT = 6.0
            val fight = g.units.filter { it.type != U_PEASANT && it.target != null }
            val army = g.units.filter { it.type != U_PEASANT }
            val list = if (fight.isNotEmpty()) fight else if (army.isNotEmpty()) army else g.units
            if (list.isNotEmpty()) {
                val pick = list[(rnd() * list.size).toInt().coerceIn(0, list.size - 1)]
                val near = list.filter { hyp(it.x - pick.x, it.y - pick.y) < 260 }
                camTX = near.sumOf { it.x } / near.size; camTY = near.sumOf { it.y } / near.size
            }
        }
        if (!camTX.isNaN()) {
            val cx = g.camX + g.vw / g.camZ / 2
            val cy = g.camY + g.vh / g.camZ / 2
            val k = min(1.0, dt * 0.6)
            g.centerCam(cx + (camTX - cx) * k, cy + (camTY - cy) * k)
        }
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
        if (demo) return
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

    override fun chunksDirty(tx: Int, ty: Int, w: Int, h: Int) { gv?.chunksDirty(tx, ty, w, h); v3?.chunksDirty(tx, ty, w, h) }

    /** переключить 3D / 2D: GameView в 3D остаётся невидимым (считает размеры, туман и мини-карту) */
    private fun set3d(on: Boolean, save: Boolean = true) {
        val w = v3
        use3d = on && w != null
        if (save) prefs.edit().putBoolean("3d", on).apply()
        gv?.visibility = if (use3d) View.INVISIBLE else View.VISIBLE
        w?.visibility = if (use3d) View.VISIBLE else View.GONE
        if (::compassBtn.isInitialized) compassBtn.visibility = if (use3d) View.VISIBLE else View.GONE
        if (::gfxRow.isInitialized) gfxRow.visibility = if (v3 != null) View.VISIBLE else View.GONE
        gv?.invalidate()
    }
    private lateinit var compassBtn: TextView
    private lateinit var gfxRow: View

    override fun ended(win: Boolean) {
        val g = G ?: return
        if (demo) { root.postDelayed({ if (demo && G === g) startDemo() }, 4000); return }
        endTitle.text = if (g.mission >= 0) (if (win) "Миссия выполнена!" else "Миссия провалена") else if (win) "Победа!" else "Поражение"
        endStats.removeAllViews()
        endNext.visibility = View.GONE
        if (g.mission >= 0) {
            val m = g.mission
            if (win && m + 1 > campDone()) prefs.edit().putInt("camp", m + 1).apply()
            val last = m + 1 >= Campaign.M.size
            if (win && last) endStats.addView(tv("Кампания пройдена. Знамёна над рекой — ваши!", 14f, GOLD_L).apply { setPadding(0, dp(2), 0, dp(4)) })
            if (!(win && last)) {
                val next = if (win) m + 1 else m
                endNext.text = if (win) "Следующая миссия ▶" else "↻ Ещё раз"
                endNext.setOnClickListener { endM.visibility = View.GONE; startMission(next) }
                endNext.visibility = View.VISIBLE
            }
        }
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
