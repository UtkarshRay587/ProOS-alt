package com.pro444yt.proos

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.GestureDetector
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class LauncherActivity : FragmentActivity() {

    private val accents = intArrayOf(
        Color.parseColor("#FF2E4C"), // Crimson Glow
        Color.parseColor("#00E5FF"), // Electric Cyan Glow
        Color.parseColor("#FF7A1A"), // Solar Amber Glow
        Color.parseColor("#9D4EDD")  // Cyber Violet Glow
    )

    private lateinit var prefs: SharedPreferences
    private var accentColor = accents[0]
    private var mono = false // Full vibrant icons by default so it doesn't look black & white

    private lateinit var root: FrameLayout
    private lateinit var ambientGlowLayer: View
    private lateinit var home: LinearLayout
    private lateinit var clockView: DotMatrixView
    private lateinit var dateText: TextView
    private lateinit var alarmText: TextView
    private lateinit var ring: DotRingView
    private lateinit var ringText: DotMatrixView
    private lateinit var dayView: DotMatrixView
    private lateinit var weekdayText: TextView
    private lateinit var gridBox: LinearLayout
    private lateinit var dockBox: LinearLayout

    // App Drawer + Right-Edge A-Z Slider
    private lateinit var drawer: LinearLayout
    private lateinit var searchEdit: EditText
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DrawerAdapter
    private lateinit var alphaBar: LinearLayout

    // Custom ProOS Vault PIN Overlay (Independent of tablet lock screen PIN)
    private lateinit var pinOverlay: LinearLayout
    private lateinit var pinTitleText: TextView
    private lateinit var pinSubtitleText: TextView
    private lateinit var pinDotsView: DotMatrixView
    private var enteredPin = StringBuilder()
    private var tempNewPin: String? = null
    private var pinMode = PinMode.UNLOCK_VAULT
    private var pendingLockedPackage: String? = null

    // Secret Hidden Space Overlay (Zero external clues)
    private lateinit var hiddenSpace: LinearLayout
    private lateinit var hiddenRecycler: RecyclerView
    private lateinit var hiddenAdapter: HiddenAdapter
    private lateinit var hiddenModeBadge: TextView
    private lateinit var faceToggleBtn: TextView

    // Inside-Vault App Security Manager Overlay (Pick apps to Hide or Lock privately)
    private lateinit var managerOverlay: LinearLayout
    private lateinit var managerSearchEdit: EditText
    private lateinit var managerRecycler: RecyclerView
    private lateinit var managerAdapter: SecurityManagerAdapter

    private lateinit var homeGestures: GestureDetector
    private lateinit var drawerGestures: GestureDetector

    private var apps: List<AppEntry> = emptyList()
    private var drawerOpen = false
    private var hiddenOpen = false
    private var pinOpen = false
    private var managerOpen = false
    private var receiversOn = false
    private var clockTapCount = 0
    private var lastClockTapTime = 0L

    private enum class PinMode {
        CREATE_PIN,
        CONFIRM_PIN,
        UNLOCK_VAULT,
        UNLOCK_APP
    }

    private val monoFilter by lazy {
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_BATTERY_CHANGED) updateBattery() else updateTime()
        }
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            loadAppsAsync()
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OSController.init(this)
        prefs = getSharedPreferences("pros", MODE_PRIVATE)
        accentColor = accents[prefs.getInt("accent", 0).coerceIn(0, accents.size - 1)]
        mono = prefs.getBoolean("mono", false)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        ctl.isAppearanceLightStatusBars = false
        ctl.isAppearanceLightNavigationBars = false

        buildUi()
        buildGestures()
        loadAppsAsync()
        handleLockIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(this, tickReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        val pf = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, pf, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiversOn = true
        updateTime()
        updateBattery()
        updateRootHideBadge()
    }

    override fun onStop() {
        super.onStop()
        if (receiversOn) {
            try {
                unregisterReceiver(tickReceiver)
                unregisterReceiver(pkgReceiver)
            } catch (_: Throwable) {
            }
            receiversOn = false
        }
        // Auto-lock vault if user leaves launcher
        if (managerOpen) closeSecurityManager()
        if (hiddenOpen) closeHiddenSpace()
        if (pinOpen && pinMode != PinMode.UNLOCK_APP) closePinPad()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (managerOpen) closeSecurityManager()
            if (hiddenOpen) closeHiddenSpace()
            if (pinOpen) closePinPad()
            if (drawerOpen) closeDrawer()
        }
        handleLockIntent(intent)
    }

    private fun handleLockIntent(intent: Intent?) {
        val lockedPkg = intent?.getStringExtra("LOCKED_PACKAGE") ?: return
        requestAppUnlock(lockedPkg)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            managerOpen -> closeSecurityManager()
            pinOpen -> closePinPad()
            hiddenOpen -> closeHiddenSpace()
            drawerOpen -> closeDrawer()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!hiddenOpen && !pinOpen && !managerOpen) {
            if (drawerOpen) drawerGestures.onTouchEvent(ev) else homeGestures.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    // ------------------------------------------------------------------ UI

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun monoText(t: String, sp: Float, alpha: Float = 0.78f): TextView = TextView(this).apply {
        text = t
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = sp
        letterSpacing = 0.1f
        maxLines = 1
        setTextColor(Color.argb((alpha * 255).toInt(), 255, 255, 255))
    }

    private fun isTabletWide(): Boolean = resources.configuration.smallestScreenWidthDp >= 600

    private fun buildUi() {
        root = FrameLayout(this)

        // Subtle atmospheric ambient accent glow over the wallpaper so it never feels flat/monochrome
        ambientGlowLayer = View(this).apply {
            val r = Color.red(accentColor)
            val g = Color.green(accentColor)
            val b = Color.blue(accentColor)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(62, r, g, b),
                    Color.argb(44, 10, 12, 18),
                    Color.argb(72, r, g, b)
                )
            )
        }
        root.addView(ambientGlowLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 1. NORMAL HOME SCREEN LAYER (Zero clues about vault or locks)
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isLongClickable = true
            setOnLongClickListener {
                showHomeMenu(this)
                true
            }
        }
        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))

        // Clock Glass Card with luminous accent edge glow (5 quick taps also opens secret PIN pad)
        val clockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.make(this@LauncherActivity, 28f, glowAccent = accentColor)
            setPadding(dpi(18), dpi(16), dpi(18), dpi(14))
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastClockTapTime < 650L) clockTapCount++ else clockTapCount = 1
                lastClockTapTime = now
                if (clockTapCount >= 5) {
                    clockTapCount = 0
                    requestOpenVault()
                }
            }
        }
        clockView = DotMatrixView(this).apply {
            pitch = dp(if (isTabletWide()) 10.5f else 9f)
            accentColor = this@LauncherActivity.accentColor
        }
        clockCard.addView(clockView, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val meta = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dateText = monoText("", 10.5f)
        alarmText = monoText("", 10.5f)
        meta.addView(dateText, lp(0, WRAP, 1f))
        meta.addView(alarmText, lp(WRAP, WRAP))
        clockCard.addView(meta, lp(MATCH, WRAP).apply { topMargin = dpi(12) })
        home.addView(clockCard, lp(MATCH, WRAP))

        // Row 2: Glowing Battery Ring Tile + Glowing Today Tile
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val batteryCard = FrameLayout(this).apply {
            background = Glass.make(this@LauncherActivity, 28f, glowAccent = accentColor)
        }
        batteryCard.addView(
            monoText("BATTERY", 10f, 0.72f),
            FrameLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dpi(16), dpi(16), 0, 0) }
        )
        ring = DotRingView(this).apply { accentColor = this@LauncherActivity.accentColor }
        batteryCard.addView(
            ring,
            FrameLayout.LayoutParams(dpi(100), dpi(100), Gravity.CENTER).apply { topMargin = dpi(12) }
        )
        ringText = DotMatrixView(this).apply { pitch = dp(5f) }
        batteryCard.addView(
            ringText,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER).apply { topMargin = dpi(12) }
        )
        row2.addView(batteryCard, lp(0, dpi(150), 1f).apply { rightMargin = dpi(6) })

        val dayCard = FrameLayout(this).apply {
            background = Glass.make(this@LauncherActivity, 28f, glowAccent = accentColor)
        }
        dayCard.addView(
            monoText("TODAY", 10f, 0.72f),
            FrameLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dpi(16), dpi(16), 0, 0) }
        )
        dayView = DotMatrixView(this).apply {
            pitch = dp(10f)
            accentColor = this@LauncherActivity.accentColor
        }
        dayCard.addView(
            dayView,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER).apply { topMargin = dpi(4) }
        )
        weekdayText = monoText("", 10.5f)
        dayCard.addView(
            weekdayText,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply {
                setMargins(dpi(16), 0, 0, dpi(14))
            }
        )
        row2.addView(dayCard, lp(0, dpi(150), 1f).apply { leftMargin = dpi(6) })
        home.addView(row2, lp(MATCH, WRAP).apply { topMargin = dpi(12) })

        // Pinned Home App Grid
        gridBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        home.addView(gridBox, lp(MATCH, WRAP).apply { topMargin = dpi(8) })

        home.addView(Space(this), lp(MATCH, 0, 1f))

        // Clean Search Pill (Zero lock icons or secret hints)
        val search = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Glass.make(this@LauncherActivity, 20f, glowAccent = accentColor)
            setPadding(dpi(16), 0, dpi(16), 0)
            setOnClickListener { openDrawer(true) }
        }
        search.addView(
            ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_search)
                setColorFilter(accentColor)
            },
            lp(dpi(20), dpi(20))
        )
        search.addView(
            TextView(this).apply {
                text = "Search apps"
                textSize = 14f
                setTextColor(Color.argb(185, 255, 255, 255))
            },
            lp(0, WRAP, 1f).apply { leftMargin = dpi(10) }
        )
        home.addView(search, lp(MATCH, dpi(50)))

        // Bottom Glowing Glass Dock
        dockBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = Glass.make(this@LauncherActivity, 30f, glowAccent = accentColor)
            setPadding(dpi(8), dpi(10), dpi(8), dpi(10))
        }
        home.addView(dockBox, lp(MATCH, WRAP).apply { topMargin = dpi(12) })

        // ---- 2. SLIDING APP DRAWER + RIGHT-EDGE A-Z SLIDER BAR (Zero clues in hint)
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(185, 8, 10, 15))
            visibility = View.GONE
            isClickable = true
        }
        searchEdit = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(Color.argb(150, 255, 255, 255))
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = Glass.make(this@LauncherActivity, 20f, glowAccent = accentColor)
            setPadding(dpi(16), 0, dpi(16), 0)
            setOnEditorActionListener { _, _, _ ->
                adapter.first()?.let { launch(it) }
                true
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    adapter.filter(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        drawer.addView(searchEdit, lp(MATCH, dpi(50)))

        val drawerBody = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val cols = if (isTabletWide()) 6 else 4
        recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@LauncherActivity, cols)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        adapter = DrawerAdapter()
        recycler.adapter = adapter
        drawerBody.addView(recycler, lp(0, MATCH, 1f))

        alphaBar = buildAlphabetSlider()
        drawerBody.addView(
            alphaBar,
            lp(dpi(24), WRAP).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = dpi(4)
            }
        )
        drawer.addView(drawerBody, lp(MATCH, 0, 1f))
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 3. SECRET HIDDEN SPACE OVERLAY (Only reachable after custom Vault PIN)
        hiddenSpace = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(246, 8, 9, 14))
            visibility = View.GONE
            isClickable = true
        }
        val hiddenHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Glass.make(this@LauncherActivity, 22f, glowAccent = accentColor)
            setPadding(dpi(16), dpi(12), dpi(16), dpi(12))
        }
        val hiddenTitleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hiddenTitleCol.addView(monoText("PRIVATE VAULT", 13.5f, 0.96f))
        hiddenModeBadge = monoText("STEALTH MODE", 9.5f, 0.85f).apply {
            setTextColor(accentColor)
        }
        hiddenTitleCol.addView(hiddenModeBadge, lp(WRAP, WRAP).apply { topMargin = dpi(2) })
        hiddenHeader.addView(hiddenTitleCol, lp(0, WRAP, 1f))

        val closeHiddenBtn = monoText("LOCK", 10.5f, 0.95f).apply {
            setPadding(dpi(14), dpi(8), dpi(14), dpi(8))
            background = Glass.make(this@LauncherActivity, 14f, glowAccent = accentColor)
            setOnClickListener { closeHiddenSpace() }
        }
        hiddenHeader.addView(closeHiddenBtn, lp(WRAP, WRAP))
        hiddenSpace.addView(hiddenHeader, lp(MATCH, WRAP))

        // Control Bar inside Vault: Manage Apps (Hide & Lock), Change Vault PIN, Face ID Toggle, Accessibility Shield
        val vaultControlsRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val manageAppsBtn = monoText("+ HIDE / LOCK APPS", 10f, 0.95f).apply {
            gravity = Gravity.CENTER
            setPadding(dpi(10), dpi(10), dpi(10), dpi(10))
            background = Glass.make(this@LauncherActivity, 16f, glowAccent = accentColor)
            setOnClickListener { openSecurityManager() }
        }
        val changePinBtn = monoText("SET NEW PIN", 10f, 0.9f).apply {
            gravity = Gravity.CENTER
            setPadding(dpi(10), dpi(10), dpi(10), dpi(10))
            background = Glass.make(this@LauncherActivity, 16f)
            setOnClickListener { startChangePinFlow() }
        }
        vaultControlsRow1.addView(manageAppsBtn, lp(0, WRAP, 1.3f).apply { rightMargin = dpi(6) })
        vaultControlsRow1.addView(changePinBtn, lp(0, WRAP, 1f))
        hiddenSpace.addView(vaultControlsRow1, lp(MATCH, WRAP).apply { topMargin = dpi(10) })

        val vaultControlsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        faceToggleBtn = monoText("FACE ID: OFF", 10f, 0.9f).apply {
            gravity = Gravity.CENTER
            setPadding(dpi(10), dpi(9), dpi(10), dpi(9))
            background = Glass.make(this@LauncherActivity, 16f)
            setOnClickListener {
                val next = !OSController.isFaceUnlockEnabled(this@LauncherActivity)
                OSController.setFaceUnlockEnabled(this@LauncherActivity, next)
                updateFaceBadge()
                Toast.makeText(
                    this@LauncherActivity,
                    if (next) "Face ID enabled alongside Vault PIN" else "Vault PIN only (Face ID off)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        val shieldServiceBtn = monoText("APP LOCK SERVICE", 10f, 0.9f).apply {
            gravity = Gravity.CENTER
            setPadding(dpi(10), dpi(9), dpi(10), dpi(9))
            background = Glass.make(this@LauncherActivity, 16f)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this@LauncherActivity, "Enable Pro OS under Accessibility", Toast.LENGTH_LONG).show()
            }
        }
        vaultControlsRow2.addView(faceToggleBtn, lp(0, WRAP, 1f).apply { rightMargin = dpi(6) })
        vaultControlsRow2.addView(shieldServiceBtn, lp(0, WRAP, 1f))
        hiddenSpace.addView(vaultControlsRow2, lp(MATCH, WRAP).apply { topMargin = dpi(8) })

        hiddenSpace.addView(
            monoText("HIDDEN APPS  •  TAP TO OPEN  •  LONG-PRESS TO UNHIDE", 9.5f, 0.6f),
            lp(MATCH, WRAP).apply { topMargin = dpi(12); bottomMargin = dpi(6) }
        )

        hiddenRecycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@LauncherActivity, cols)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        hiddenAdapter = HiddenAdapter()
        hiddenRecycler.adapter = hiddenAdapter
        hiddenSpace.addView(hiddenRecycler, lp(MATCH, 0, 1f))
        root.addView(hiddenSpace, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 4. INSIDE-VAULT SECURITY MANAGER OVERLAY (Toggle Hide & Lock per app)
        managerOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(250, 8, 9, 14))
            visibility = View.GONE
            isClickable = true
        }
        val mgrHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Glass.make(this@LauncherActivity, 20f, glowAccent = accentColor)
            setPadding(dpi(16), dpi(12), dpi(16), dpi(12))
        }
        mgrHeader.addView(monoText("MANAGE HIDE & APP LOCK", 12.5f, 0.95f), lp(0, WRAP, 1f))
        val mgrDoneBtn = monoText("DONE", 10.5f, 0.95f).apply {
            setPadding(dpi(14), dpi(8), dpi(14), dpi(8))
            background = Glass.make(this@LauncherActivity, 14f, glowAccent = accentColor)
            setOnClickListener { closeSecurityManager() }
        }
        mgrHeader.addView(mgrDoneBtn, lp(WRAP, WRAP))
        managerOverlay.addView(mgrHeader, lp(MATCH, WRAP))

        managerSearchEdit = EditText(this).apply {
            hint = "Filter installed apps..."
            setHintTextColor(Color.argb(140, 255, 255, 255))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine()
            background = Glass.make(this@LauncherActivity, 18f)
            setPadding(dpi(14), 0, dpi(14), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    managerAdapter.filter(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        managerOverlay.addView(managerSearchEdit, lp(MATCH, dpi(46)).apply { topMargin = dpi(10) })

        managerRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@LauncherActivity)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        managerAdapter = SecurityManagerAdapter()
        managerRecycler.adapter = managerAdapter
        managerOverlay.addView(managerRecycler, lp(MATCH, 0, 1f).apply { topMargin = dpi(8) })
        root.addView(managerOverlay, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 5. CUSTOM PROOS DOT-MATRIX PIN KEYPAD OVERLAY (Separate from tablet lock screen)
        pinOverlay = buildPinPadOverlay()
        root.addView(pinOverlay, FrameLayout.LayoutParams(MATCH, MATCH))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            home.setPadding(dpi(16), bars.top + dpi(12), dpi(16), bars.bottom + dpi(14))
            drawer.setPadding(dpi(16), bars.top + dpi(12), dpi(8), 0)
            hiddenSpace.setPadding(dpi(16), bars.top + dpi(12), dpi(16), bars.bottom + dpi(14))
            managerOverlay.setPadding(dpi(16), bars.top + dpi(12), dpi(16), bars.bottom + dpi(14))
            pinOverlay.setPadding(dpi(20), bars.top + dpi(20), dpi(20), bars.bottom + dpi(20))
            recycler.setPadding(0, dpi(14), 0, max(bars.bottom, ime.bottom) + dpi(14))
            insets
        }

        setContentView(root)
    }

    private fun buildPinPadOverlay(): LinearLayout {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(248, 7, 8, 12))
            visibility = View.GONE
            isClickable = true
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Glass.make(this@LauncherActivity, 28f, glowAccent = accentColor)
            setPadding(dpi(22), dpi(24), dpi(22), dpi(22))
        }

        pinTitleText = monoText("PRO OS // VAULT", 14f, 0.96f)
        card.addView(pinTitleText, lp(WRAP, WRAP))

        pinSubtitleText = monoText("ENTER CUSTOM VAULT PIN", 10f, 0.72f).apply {
            setTextColor(accentColor)
        }
        card.addView(pinSubtitleText, lp(WRAP, WRAP).apply { topMargin = dpi(6) })

        pinDotsView = DotMatrixView(this).apply {
            pitch = dp(8f)
            accentColor = this@LauncherActivity.accentColor
            text = "----"
        }
        card.addView(pinDotsView, lp(WRAP, WRAP).apply { topMargin = dpi(22); bottomMargin = dpi(22) })

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("CLR", "0", "OK")
        )
        rows.forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { key ->
                val isAction = key == "CLR" || key == "OK"
                val btn = TextView(this).apply {
                    text = key
                    textSize = if (isAction) 12.5f else 20f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(if (key == "OK") accentColor else Color.WHITE)
                    background = Glass.make(
                        this@LauncherActivity,
                        20f,
                        glowAccent = if (key == "OK") accentColor else null
                    )
                    setOnClickListener { onPinKeyPressed(key) }
                }
                row.addView(
                    btn,
                    lp(dpi(76), dpi(60)).apply {
                        setMargins(dpi(6), dpi(6), dpi(6), dpi(6))
                    }
                )
            }
            card.addView(row, lp(WRAP, WRAP))
        }

        val cancelBtn = monoText("CANCEL", 10.5f, 0.65f).apply {
            setPadding(dpi(16), dpi(12), dpi(16), dpi(6))
            setOnClickListener { closePinPad() }
        }
        card.addView(cancelBtn, lp(WRAP, WRAP).apply { topMargin = dpi(10) })

        val cardWidth = if (isTabletWide()) dpi(360) else MATCH
        overlay.addView(card, lp(cardWidth, WRAP))
        return overlay
    }

    private fun openPinPad(mode: PinMode, title: String, subtitle: String, lockedPkg: String? = null) {
        pinMode = mode
        pendingLockedPackage = lockedPkg
        enteredPin.clear()
        pinTitleText.text = title
        pinSubtitleText.text = subtitle
        pinDotsView.text = "----"
        pinOverlay.visibility = View.VISIBLE
        pinOverlay.alpha = 0f
        pinOverlay.animate().alpha(1f).setDuration(180).start()
        pinOpen = true
    }

    private fun closePinPad() {
        if (!pinOpen) return
        pinOpen = false
        enteredPin.clear()
        tempNewPin = null
        pinOverlay.animate().alpha(0f).setDuration(150)
            .withEndAction { if (!pinOpen) pinOverlay.visibility = View.GONE }
            .start()
    }

    private fun onPinKeyPressed(key: String) {
        when (key) {
            "CLR" -> {
                if (enteredPin.isNotEmpty()) {
                    enteredPin.deleteCharAt(enteredPin.length - 1)
                }
            }
            "OK" -> {
                val code = enteredPin.toString()
                if (code.length < 4) {
                    Toast.makeText(this, "Enter at least 4 digits", Toast.LENGTH_SHORT).show()
                    return
                }
                handlePinSubmit(code)
                return
            }
            else -> {
                if (enteredPin.length < 6) {
                    enteredPin.append(key)
                }
            }
        }
        pinDotsView.text = if (enteredPin.isEmpty()) "----" else "*".repeat(enteredPin.length)
        // Auto-submit on 4 digits when unlocking (or 6 digits max)
        if ((pinMode == PinMode.UNLOCK_VAULT || pinMode == PinMode.UNLOCK_APP) &&
            enteredPin.length >= 4 && OSController.verifyCustomPin(this, enteredPin.toString())
        ) {
            handlePinSubmit(enteredPin.toString())
        }
    }

    private fun handlePinSubmit(code: String) {
        when (pinMode) {
            PinMode.CREATE_PIN -> {
                tempNewPin = code
                enteredPin.clear()
                pinDotsView.text = "----"
                pinMode = PinMode.CONFIRM_PIN
                pinTitleText.text = "CONFIRM VAULT PIN"
                pinSubtitleText.text = "RE-ENTER NEW PIN TO SAVE"
            }
            PinMode.CONFIRM_PIN -> {
                if (code == tempNewPin) {
                    OSController.setCustomPin(this, code)
                    Toast.makeText(this, "Custom Vault PIN saved!", Toast.LENGTH_SHORT).show()
                    closePinPad()
                    openHiddenSpace()
                } else {
                    Toast.makeText(this, "PINs did not match. Try again.", Toast.LENGTH_SHORT).show()
                    tempNewPin = null
                    enteredPin.clear()
                    pinDotsView.text = "----"
                    pinMode = PinMode.CREATE_PIN
                    pinTitleText.text = "CREATE VAULT PIN"
                    pinSubtitleText.text = "SET SEPARATE 4-6 DIGIT PIN"
                }
            }
            PinMode.UNLOCK_VAULT -> {
                if (OSController.verifyCustomPin(this, code)) {
                    closePinPad()
                    openHiddenSpace()
                } else {
                    enteredPin.clear()
                    pinDotsView.text = "----"
                    pinSubtitleText.text = "WRONG PIN  •  TRY AGAIN"
                }
            }
            PinMode.UNLOCK_APP -> {
                if (OSController.verifyCustomPin(this, code)) {
                    val pkg = pendingLockedPackage
                    closePinPad()
                    if (pkg != null) {
                        AppLockService.unlockedSessionPackage = pkg
                        launchPackageByName(pkg)
                    }
                } else {
                    enteredPin.clear()
                    pinDotsView.text = "----"
                    pinSubtitleText.text = "WRONG PIN  •  TRY AGAIN"
                }
            }
        }
    }

    private fun startChangePinFlow() {
        openPinPad(
            mode = PinMode.CREATE_PIN,
            title = "NEW VAULT PIN",
            subtitle = "SEPARATE FROM TABLET PIN (4-6 DIGITS)"
        )
    }

    private fun buildAlphabetSlider(): LinearLayout {
        val letters = listOf("#") + ('A'..'Z').map { it.toString() }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Glass.make(this@LauncherActivity, 12f, glowAccent = accentColor)
            setPadding(dpi(2), dpi(6), dpi(2), dpi(6))
        }
        letters.forEach { ch ->
            val tv = TextView(this).apply {
                text = ch
                textSize = 9f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.argb(210, 255, 255, 255))
            }
            bar.addView(tv, lp(MATCH, dpi(14)))
        }
        bar.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val ratio = (event.y / v.height.coerceAtLeast(1)).coerceIn(0f, 0.999f)
                val targetLetter = letters[(ratio * letters.size).toInt()]
                val pos = adapter.indexForLetter(targetLetter)
                if (pos >= 0) {
                    (recycler.layoutManager as GridLayoutManager).scrollToPositionWithOffset(pos, 0)
                }
            }
            true
        }
        return bar
    }

    private fun buildGestures() {
        homeGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -900f && abs(vy) > abs(vx)) {
                    openDrawer(false)
                    return true
                }
                return false
            }
        })
        drawerGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                // Silent swipe right inside drawer -> Custom Vault PIN Pad
                if (vx > 1100f && abs(vx) > abs(vy) * 1.3f) {
                    requestOpenVault()
                    return true
                }
                if (vy > 1500f && abs(vy) > abs(vx) && !recycler.canScrollVertically(-1)) {
                    closeDrawer()
                    return true
                }
                return false
            }
        })
    }

    // ------------------------------------------------------------------ drawer & vault

    private fun openDrawer(focusSearch: Boolean) {
        if (drawerOpen) return
        drawerOpen = true
        searchEdit.setText("")
        adapter.filter("")
        drawer.visibility = View.VISIBLE
        drawer.alpha = 0f
        drawer.translationY = dp(80f)
        drawer.animate().alpha(1f).translationY(0f).setDuration(240).start()
        home.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(240).start()
        setBlur(true)
        if (focusSearch) {
            searchEdit.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEdit, 0)
        }
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEdit.windowToken, 0)
        searchEdit.clearFocus()
        drawer.animate().alpha(0f).translationY(dp(80f)).setDuration(200)
            .withEndAction { if (!drawerOpen) drawer.visibility = View.GONE }
            .start()
        home.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
        setBlur(false)
    }

    private fun requestOpenVault() {
        if (!OSController.hasCustomPin(this)) {
            openPinPad(
                mode = PinMode.CREATE_PIN,
                title = "SETUP VAULT PIN",
                subtitle = "CREATE SEPARATE 4-6 DIGIT PIN"
            )
            return
        }
        if (OSController.isFaceUnlockEnabled(this)) {
            OSController.triggerBiometricOnly(
                activity = this,
                title = "PRO OS",
                subtitle = "Verify Face ID",
                onSuccess = { openHiddenSpace() },
                onFallbackPin = {
                    openPinPad(PinMode.UNLOCK_VAULT, "PRO OS // VAULT", "ENTER CUSTOM VAULT PIN")
                }
            )
        } else {
            openPinPad(PinMode.UNLOCK_VAULT, "PRO OS // VAULT", "ENTER CUSTOM VAULT PIN")
        }
    }

    private fun requestAppUnlock(pkg: String) {
        if (!OSController.hasCustomPin(this)) {
            openPinPad(
                mode = PinMode.CREATE_PIN,
                title = "SETUP VAULT PIN",
                subtitle = "CREATE SEPARATE 4-6 DIGIT PIN"
            )
            return
        }
        val appLabel = apps.firstOrNull { it.pkg == pkg }?.label?.uppercase() ?: "APP LOCKED"
        if (OSController.isFaceUnlockEnabled(this)) {
            OSController.triggerBiometricOnly(
                activity = this,
                title = appLabel,
                subtitle = "Verify Face ID",
                onSuccess = {
                    AppLockService.unlockedSessionPackage = pkg
                    launchPackageByName(pkg)
                },
                onFallbackPin = {
                    openPinPad(PinMode.UNLOCK_APP, appLabel, "ENTER CUSTOM VAULT PIN", pkg)
                }
            )
        } else {
            openPinPad(PinMode.UNLOCK_APP, appLabel, "ENTER CUSTOM VAULT PIN", pkg)
        }
    }

    private fun openHiddenSpace() {
        if (hiddenOpen) return
        hiddenOpen = true
        updateRootHideBadge()
        updateFaceBadge()
        refreshHiddenList()
        hiddenSpace.visibility = View.VISIBLE
        hiddenSpace.alpha = 0f
        hiddenSpace.translationX = -dp(90f)
        hiddenSpace.animate().alpha(1f).translationX(0f).setDuration(230).start()
    }

    private fun closeHiddenSpace() {
        if (!hiddenOpen) return
        hiddenOpen = false
        hiddenSpace.animate().alpha(0f).translationX(-dp(90f)).setDuration(190)
            .withEndAction { if (!hiddenOpen) hiddenSpace.visibility = View.GONE }
            .start()
    }

    private fun openSecurityManager() {
        managerOpen = true
        managerSearchEdit.setText("")
        managerAdapter.setData(apps)
        managerOverlay.visibility = View.VISIBLE
        managerOverlay.alpha = 0f
        managerOverlay.animate().alpha(1f).setDuration(180).start()
    }

    private fun closeSecurityManager() {
        if (!managerOpen) return
        managerOpen = false
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(managerSearchEdit.windowToken, 0)
        managerOverlay.animate().alpha(0f).setDuration(160)
            .withEndAction { if (!managerOpen) managerOverlay.visibility = View.GONE }
            .start()
        refreshHiddenList()
        adapter.setData(apps.filter { it.pkg !in OSController.hiddenPackages })
        refreshHome()
    }

    private fun updateFaceBadge() {
        if (!::faceToggleBtn.isInitialized) return
        val on = OSController.isFaceUnlockEnabled(this)
        faceToggleBtn.text = if (on) "FACE ID: ON" else "FACE ID: OFF"
        faceToggleBtn.setTextColor(if (on) Color.parseColor("#00E676") else Color.WHITE)
    }

    private fun updateRootHideBadge() {
        if (!::hiddenModeBadge.isInitialized) return
        if (OSController.isDeviceOwner(this)) {
            hiddenModeBadge.text = "SYSTEM ROOT-HIDE: ACTIVE"
            hiddenModeBadge.setTextColor(Color.parseColor("#00E676"))
        } else {
            hiddenModeBadge.text = "STEALTH LAUNCHER HIDE ACTIVE"
            hiddenModeBadge.setTextColor(accentColor)
        }
    }

    private fun refreshHiddenList() {
        val pm = packageManager
        val list = OSController.hiddenPackages.mapNotNull { pkg ->
            try {
                val flags = if (Build.VERSION.SDK_INT >= 33) {
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
                } else null
                val ai = if (flags != null) pm.getApplicationInfo(pkg, flags)
                else @Suppress("DEPRECATION") pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                AppEntry(pm.getApplicationLabel(ai).toString(), pkg, "", pm.getApplicationIcon(ai))
            } catch (_: Throwable) {
                AppEntry(pkg.substringAfterLast("."), pkg, "", packageManager.defaultActivityIcon)
            }
        }
        hiddenAdapter.setData(list)
    }

    private fun setBlur(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.setBackgroundBlurRadius(if (on) 80 else 0)
            } catch (_: Throwable) {
            }
        }
    }

    // ------------------------------------------------------------------ icons and cells (Stealth: no lock badges)

    private fun applyIcon(iv: ImageView, e: AppEntry) {
        val d = e.icon.constantState?.newDrawable()?.mutate() ?: e.icon
        iv.setImageDrawable(d)
        iv.colorFilter = if (mono) monoFilter else null
    }

    private fun makeTile(): Pair<FrameLayout, ImageView> {
        val tile = FrameLayout(this).apply {
            background = Glass.make(this@LauncherActivity, 21f, fill = 0x2E161922, glowAccent = accentColor)
        }
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        tile.addView(iv, FrameLayout.LayoutParams(dpi(42), dpi(42), Gravity.CENTER))
        return Pair(tile, iv)
    }

    private fun makeLabel(): TextView = TextView(this).apply {
        textSize = 11f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setShadowLayer(4f, 0f, 1f, Color.argb(140, 0, 0, 0))
    }

    private fun makeCell(e: AppEntry, showLabel: Boolean, fromHome: Boolean): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            setOnClickListener { launch(e) }
            setOnLongClickListener {
                showAppMenu(it, e, fromHome)
                true
            }
        }
        val (tile, iv) = makeTile()
        applyIcon(iv, e)
        cell.addView(tile, lp(dpi(58), dpi(58)))
        if (showLabel) {
            val label = makeLabel().apply { text = e.label }
            cell.addView(label, lp(MATCH, WRAP).apply { topMargin = dpi(6) })
        }
        return cell
    }

    private fun refreshHome() {
        gridBox.removeAllViews()
        dockBox.removeAllViews()
        val pins = pinEntries("home").filter { it.pkg !in OSController.hiddenPackages }
        val cols = if (isTabletWide()) 6 else 4
        val totalSlots = if (isTabletWide()) 12 else 8
        for (start in 0 until totalSlots step cols) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in start until start + cols) {
                val e = pins.getOrNull(i)
                val cell: View = if (e != null) makeCell(e, true, true) else Space(this)
                row.addView(cell, lp(0, WRAP, 1f))
            }
            gridBox.addView(row, lp(MATCH, WRAP).apply { topMargin = dpi(12) })
        }
        pinEntries("dock")
            .filter { it.pkg !in OSController.hiddenPackages }
            .forEach { dockBox.addView(makeCell(it, false, false), lp(0, WRAP, 1f)) }
    }

    // ------------------------------------------------------------------ apps

    private fun loadAppsAsync() {
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    AppEntry(
                        it.loadLabel(pm).toString(),
                        it.activityInfo.packageName,
                        it.activityInfo.name,
                        it.loadIcon(pm)
                    )
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                apps = list
                adapter.setData(list.filter { it.pkg !in OSController.hiddenPackages })
                refreshHome()
            }
        }.start()
    }

    private fun launch(e: AppEntry) {
        if (OSController.lockedPackages.contains(e.pkg)) {
            requestAppUnlock(e.pkg)
        } else {
            startAppDirectly(e)
        }
    }

    private fun launchPackageByName(pkg: String) {
        val entry = apps.firstOrNull { it.pkg == pkg }
        if (entry != null) {
            startAppDirectly(entry)
            return
        }
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            if (drawerOpen) closeDrawer()
        }
    }

    private fun startAppDirectly(e: AppEntry) {
        val i = if (e.cls.isNotEmpty()) {
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        } else {
            packageManager.getLaunchIntentForPackage(e.pkg)
        }
        try {
            if (i != null) {
                startActivity(i)
                if (drawerOpen) closeDrawer()
            } else {
                Toast.makeText(this, "Can't open " + e.label, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Throwable) {
            Toast.makeText(this, "Can't open " + e.label, Toast.LENGTH_SHORT).show()
        }
    }

    // Public app long-press menu: ONLY normal launcher actions (no Hide or Lock exposed publicly!)
    private fun showAppMenu(anchor: View, e: AppEntry, fromHome: Boolean) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, if (fromHome) "Remove from home" else "Pin to home")
        m.menu.add(0, 2, 1, "App info")
        m.menu.add(0, 3, 2, "Uninstall")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> if (fromHome) unpin(e) else pin(e)
                2 -> startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                3 -> startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            true
        }
        m.show()
    }

    // Public home long-press menu: ONLY normal visual settings (no Hidden Space or App Lock exposed!)
    private fun showHomeMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "Change wallpaper")
        m.menu.add(0, 2, 1, "Icon style: " + (if (mono) "Monochrome" else "Full Colour"))
        m.menu.add(0, 3, 2, "Next glow accent colour")
        m.menu.add(0, 4, 3, "Default home app settings")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
                } catch (_: Throwable) {
                }
                2 -> {
                    mono = !mono
                    prefs.edit().putBoolean("mono", mono).apply()
                    refreshHome()
                    adapter.notifyDataSetChanged()
                }
                3 -> {
                    val next = (prefs.getInt("accent", 0) + 1) % accents.size
                    prefs.edit().putInt("accent", next).apply()
                    recreate()
                }
                4 -> try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (_: Throwable) {
                }
            }
            true
        }
        m.show()
    }

    // ------------------------------------------------------------------ pins

    private fun pkgFor(intent: Intent): String? =
        packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != "android" && it != packageName }

    private fun selector(category: String): Intent =
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

    private fun defaultPins(name: String): MutableList<String> {
        val intents: List<Intent> = if (name == "dock") {
            listOf(
                Intent(Intent.ACTION_DIAL),
                selector(Intent.CATEGORY_APP_MESSAGING),
                selector(Intent.CATEGORY_APP_BROWSER),
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            )
        } else {
            listOf(
                Intent(Settings.ACTION_SETTINGS),
                selector(Intent.CATEGORY_APP_GALLERY),
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                selector(Intent.CATEGORY_APP_CALCULATOR),
                selector(Intent.CATEGORY_APP_CALENDAR),
                selector(Intent.CATEGORY_APP_MUSIC),
                selector(Intent.CATEGORY_APP_MAPS),
                selector(Intent.CATEGORY_APP_EMAIL)
            )
        }
        val out = mutableListOf<String>()
        intents.forEach { i ->
            val p = pkgFor(i)
            val e = apps.firstOrNull { it.pkg == p }
            if (e != null && e.key !in out) out.add(e.key)
        }
        val maxHome = if (isTabletWide()) 12 else 8
        if (name == "home") {
            val dock = prefs.getString("dock", "").orEmpty().split("\n")
            apps.forEach { if (out.size < maxHome && it.key !in out && it.key !in dock) out.add(it.key) }
        }
        if (apps.isNotEmpty()) savePins(name, out)
        return out
    }

    private fun pinList(name: String): MutableList<String> {
        val s = prefs.getString(name, null)
        return if (s == null) defaultPins(name)
        else s.split("\n").filter { it.isNotEmpty() }.toMutableList()
    }

    private fun savePins(name: String, list: List<String>) {
        prefs.edit().putString(name, list.joinToString("\n")).apply()
    }

    private fun pinEntries(name: String): List<AppEntry> {
        if (name == "home" && prefs.getString("dock", null) == null) pinList("dock")
        return pinList(name).mapNotNull { k -> apps.firstOrNull { it.key == k } }
    }

    private fun pin(e: AppEntry) {
        val list = pinList("home")
        val maxHome = if (isTabletWide()) 12 else 8
        if (e.key in list) return
        if (list.size >= maxHome) {
            Toast.makeText(this, "Home is full. Remove an app first.", Toast.LENGTH_SHORT).show()
            return
        }
        list.add(e.key)
        savePins("home", list)
        refreshHome()
        Toast.makeText(this, e.label + " added to home", Toast.LENGTH_SHORT).show()
    }

    private fun unpin(e: AppEntry) {
        val list = pinList("home")
        list.remove(e.key)
        savePins("home", list)
        refreshHome()
    }

    // ------------------------------------------------------------------ clock and battery

    private fun updateTime() {
        val now = Calendar.getInstance()
        val h = if (DateFormat.is24HourFormat(this)) {
            now.get(Calendar.HOUR_OF_DAY)
        } else {
            val x = now.get(Calendar.HOUR)
            if (x == 0) 12 else x
        }
        clockView.text = String.format(Locale.US, "%02d:%02d", h, now.get(Calendar.MINUTE))
        dateText.text = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now.time).uppercase()
        weekdayText.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(now.time).uppercase()
        dayView.text = now.get(Calendar.DAY_OF_MONTH).toString()

        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock
        alarmText.text = if (next != null) {
            val f = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
            "ALARM " + SimpleDateFormat(f, Locale.getDefault()).format(next.triggerTime).uppercase()
        } else {
            "NO ALARM"
        }
    }

    private fun updateBattery() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val pct = (level * 100f / scale).roundToInt()
        ring.percent = pct
        ringText.text = pct.toString()
    }

    // ------------------------------------------------------------------ drawer adapter

    inner class DrawerAdapter : RecyclerView.Adapter<DrawerAdapter.VH>() {
        private var all: List<AppEntry> = emptyList()
        private var shown: List<AppEntry> = emptyList()

        inner class VH(val cell: LinearLayout, val icon: ImageView, val label: TextView) :
            RecyclerView.ViewHolder(cell)

        fun setData(list: List<AppEntry>) {
            all = list
            filter(searchEdit.text?.toString() ?: "")
        }

        fun filter(q: String) {
            val t = q.trim().lowercase()
            shown = if (t.isEmpty()) all else all.filter { it.label.lowercase().contains(t) }
            notifyDataSetChanged()
        }

        fun indexForLetter(letter: String): Int {
            if (letter == "#") return if (shown.isNotEmpty()) 0 else -1
            return shown.indexOfFirst { it.label.uppercase().startsWith(letter) }
        }

        fun first(): AppEntry? = shown.firstOrNull()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val cell = LinearLayout(this@LauncherActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dpi(8), 0, dpi(8))
                isClickable = true
                layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            }
            val (tile, iv) = makeTile()
            val label = makeLabel()
            cell.addView(tile, lp(dpi(58), dpi(58)))
            cell.addView(label, lp(MATCH, WRAP).apply { topMargin = dpi(6) })
            return VH(cell, iv, label)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = shown[position]
            applyIcon(holder.icon, e)
            holder.label.text = e.label
            holder.cell.setOnClickListener { launch(e) }
            holder.cell.setOnLongClickListener {
                showAppMenu(it, e, false)
                true
            }
        }

        override fun getItemCount(): Int = shown.size
    }

    // ------------------------------------------------------------------ hidden space adapter

    inner class HiddenAdapter : RecyclerView.Adapter<HiddenAdapter.VH>() {
        private var items: List<AppEntry> = emptyList()

        inner class VH(val cell: LinearLayout, val icon: ImageView, val label: TextView) :
            RecyclerView.ViewHolder(cell)

        fun setData(list: List<AppEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val cell = LinearLayout(this@LauncherActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dpi(10), 0, dpi(10))
                isClickable = true
                layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            }
            val (tile, iv) = makeTile()
            val label = makeLabel()
            cell.addView(tile, lp(dpi(58), dpi(58)))
            cell.addView(label, lp(MATCH, WRAP).apply { topMargin = dpi(6) })
            return VH(cell, iv, label)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            applyIcon(holder.icon, e)
            holder.label.text = e.label
            holder.cell.setOnClickListener {
                OSController.setAppSystemHidden(this@LauncherActivity, e.pkg, false)
                loadAppsAsync()
                closeHiddenSpace()
                startAppDirectly(e)
            }
            holder.cell.setOnLongClickListener {
                OSController.setAppSystemHidden(this@LauncherActivity, e.pkg, false)
                refreshHiddenList()
                loadAppsAsync()
                Toast.makeText(this@LauncherActivity, "${e.label} unhidden", Toast.LENGTH_SHORT).show()
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }

    // ------------------------------------------------------------------ inside-vault security manager adapter

    inner class SecurityManagerAdapter : RecyclerView.Adapter<SecurityManagerAdapter.VH>() {
        private var all: List<AppEntry> = emptyList()
        private var shown: List<AppEntry> = emptyList()

        inner class VH(
            val row: LinearLayout,
            val icon: ImageView,
            val name: TextView,
            val lockBtn: TextView,
            val hideBtn: TextView
        ) : RecyclerView.ViewHolder(row)

        fun setData(list: List<AppEntry>) {
            all = list
            filter(managerSearchEdit.text?.toString() ?: "")
        }

        fun filter(q: String) {
            val t = q.trim().lowercase()
            shown = if (t.isEmpty()) all else all.filter { it.label.lowercase().contains(t) }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(this@LauncherActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Glass.make(this@LauncherActivity, 16f)
                setPadding(dpi(12), dpi(10), dpi(12), dpi(10))
                layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply {
                    bottomMargin = dpi(8)
                }
            }
            val icon = ImageView(this@LauncherActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            row.addView(icon, lp(dpi(36), dpi(36)))

            val name = TextView(this@LauncherActivity).apply {
                textSize = 13.5f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(name, lp(0, WRAP, 1f).apply { leftMargin = dpi(10); rightMargin = dpi(8) })

            val lockBtn = monoText("LOCK", 9.5f, 0.9f).apply {
                setPadding(dpi(10), dpi(7), dpi(10), dpi(7))
            }
            val hideBtn = monoText("HIDE", 9.5f, 0.9f).apply {
                setPadding(dpi(10), dpi(7), dpi(10), dpi(7))
            }
            row.addView(lockBtn, lp(WRAP, WRAP).apply { rightMargin = dpi(6) })
            row.addView(hideBtn, lp(WRAP, WRAP))
            return VH(row, icon, name, lockBtn, hideBtn)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = shown[position]
            val d = e.icon.constantState?.newDrawable()?.mutate() ?: e.icon
            holder.icon.setImageDrawable(d)
            holder.name.text = e.label

            val isLocked = OSController.lockedPackages.contains(e.pkg)
            val isHidden = OSController.hiddenPackages.contains(e.pkg)

            holder.lockBtn.text = if (isLocked) "LOCKED" else "LOCK"
            holder.lockBtn.setTextColor(if (isLocked) accentColor else Color.WHITE)
            holder.lockBtn.background = Glass.make(
                this@LauncherActivity,
                12f,
                glowAccent = if (isLocked) accentColor else null
            )

            holder.hideBtn.text = if (isHidden) "HIDDEN" else "HIDE"
            holder.hideBtn.setTextColor(if (isHidden) Color.parseColor("#00E676") else Color.WHITE)
            holder.hideBtn.background = Glass.make(
                this@LauncherActivity,
                12f,
                glowAccent = if (isHidden) Color.parseColor("#00E676") else null
            )

            holder.lockBtn.setOnClickListener {
                OSController.toggleLock(this@LauncherActivity, e.pkg)
                notifyItemChanged(position)
            }
            holder.hideBtn.setOnClickListener {
                OSController.setAppSystemHidden(this@LauncherActivity, e.pkg, !isHidden)
                notifyItemChanged(position)
            }
        }

        override fun getItemCount(): Int = shown.size
    }
}
