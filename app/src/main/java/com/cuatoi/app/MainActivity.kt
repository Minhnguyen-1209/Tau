package com.cuatoi.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/* ---------- Lưu cài đặt ---------- */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("cuatoi", Context.MODE_PRIVATE)

    fun limitMin(c: Context, pkg: String): Int = p(c).getInt("lim_$pkg", 0)

    fun setLimit(c: Context, pkg: String, m: Int) {
        val e = p(c).edit()
        if (m <= 0) e.remove("lim_$pkg") else e.putInt("lim_$pkg", m)
        e.apply()
    }

    fun focusApps(c: Context): Set<String> =
        p(c).getStringSet("focus_apps", emptySet()) ?: emptySet()

    fun setFocusApp(c: Context, pkg: String, on: Boolean) {
        val s = focusApps(c).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        p(c).edit().putStringSet("focus_apps", s).apply()
    }

    fun focusEnd(c: Context): Long = p(c).getLong("focus_end", 0L)

    fun setFocusEnd(c: Context, t: Long) {
        p(c).edit().putLong("focus_end", t).apply()
    }
}

/* ---------- Đọc thời gian dùng app ---------- */
object Usage {
    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun today(c: Context): Map<String, Long> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val m = usm.queryAndAggregateUsageStats(startOfDay(), System.currentTimeMillis())
        val out = HashMap<String, Long>()
        if (m != null) {
            for ((k, v) in m) out[k] = v.totalTimeInForeground
        }
        return out
    }
}

fun fmt(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60}h ${m % 60}p" else "${m}p"
}

/* ---------- Dịch vụ chặn app ---------- */
class BlockerService : AccessibilityService() {
    private var current = ""
    private val h = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            check(current)
            h.postDelayed(this, 15000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        h.post(loop)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        current = pkg
        check(pkg)
    }

    private fun check(pkg: String) {
        if (pkg.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now < Store.focusEnd(this) && Store.focusApps(this).contains(pkg)) {
            block("Đang tập trung, chưa mở app này được")
            return
        }
        val lim = Store.limitMin(this, pkg)
        if (lim > 0 && Usage.hasAccess(this)) {
            val used = (Usage.today(this)[pkg] ?: 0L) / 60000
            if (used >= lim) block("Đã hết giới hạn $lim phút hôm nay")
        }
    }

    private fun block(msg: String) {
        current = ""
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        h.removeCallbacks(loop)
        super.onDestroy()
    }
}

/* ---------- Màn hình chính ---------- */
class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var focusLabel: TextView? = null
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateFocusLabel()
            h.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tv(t: String, size: Float = 16f, bold: Boolean = false, color: Int = Color.BLACK): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    private fun btn(t: String, f: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { f() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(32), dp(16), dp(32))
        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        build()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
    }

    private fun serviceOn(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains(packageName) && s.contains("BlockerService")
    }

    private fun updateFocusLabel() {
        val l = focusLabel ?: return
        val left = Store.focusEnd(this) - System.currentTimeMillis()
        if (left > 0) {
            val sec = (left / 1000) % 60
            val secText = if (sec < 10) "0$sec" else "$sec"
            l.text = "Đang tập trung · còn ${left / 60000}:$secText"
        } else {
            l.text = "Chưa bật chế độ tập trung"
        }
    }

    private fun startFocus(minutes: Int) {
        if (Store.focusApps(this).isEmpty()) {
            Toast.makeText(this, "Hãy chạm vào app bên dưới và chọn \"Chặn khi tập trung\" trước", Toast.LENGTH_LONG).show()
            return
        }
        Store.setFocusEnd(this, System.currentTimeMillis() + minutes * 60000L)
        updateFocusLabel()
    }

    private fun edit(pkg: String, label: String) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.hint = "Giới hạn mỗi ngày (phút), để trống = không giới hạn"
        val cur = Store.limitMin(this, pkg)
        if (cur > 0) et.setText(cur.toString())

        val cb = CheckBox(this)
        cb.text = "Chặn khi tập trung"
        cb.isChecked = Store.focusApps(this).contains(pkg)

        box.addView(et)
        box.addView(cb)

        AlertDialog.Builder(this)
            .setTitle(label)
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                Store.setLimit(this, pkg, et.text.toString().toIntOrNull() ?: 0)
                Store.setFocusApp(this, pkg, cb.isChecked)
                build()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun build() {
        root.removeAllViews()
        val hasUsage = Usage.hasAccess(this)

        root.addView(tv("Của tôi", 26f, true))

        if (!hasUsage) {
            root.addView(tv("Cần cấp quyền để xem thời gian dùng từng app.", 14f, false, Color.DKGRAY))
            root.addView(btn("Bước 1: Cấp quyền xem thời gian dùng") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        }
        if (!serviceOn()) {
            root.addView(tv("Cần bật dịch vụ trợ năng để chặn app khi hết giờ.", 14f, false, Color.DKGRAY))
            root.addView(btn("Bước 2: Bật dịch vụ chặn app") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }

        /* Tập trung */
        val title = tv("Chế độ tập trung", 18f, true)
        title.setPadding(0, dp(20), 0, dp(4))
        root.addView(title)

        val fl = tv("", 16f)
        focusLabel = fl
        root.addView(fl)
        updateFocusLabel()

        val row = LinearLayout(this)
        val lp = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        for (m in listOf(25, 45, 60)) {
            row.addView(btn("$m phút") { startFocus(m) }, lp())
        }
        row.addView(btn("Dừng") {
            Store.setFocusEnd(this, 0L)
            updateFocusLabel()
        }, lp())
        root.addView(row)

        /* Danh sách app */
        val pm = packageManager
        val usage: Map<String, Long> = if (hasUsage) Usage.today(this) else emptyMap()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val apps = pm.queryIntentActivities(launch, 0)
            .filter { it.activityInfo.packageName != packageName && seen.add(it.activityInfo.packageName) }
            .sortedByDescending { usage[it.activityInfo.packageName] ?: 0L }

        var total = 0L
        for (ri in apps) total += usage[ri.activityInfo.packageName] ?: 0L

        val head = tv("Hôm nay: ${fmt(total)}", 22f, true)
        head.setPadding(0, dp(24), 0, dp(4))
        root.addView(head)
        root.addView(tv("Chạm vào một app để đặt giới hạn hoặc chọn chặn khi tập trung.", 13f, false, Color.DKGRAY))

        for (ri in apps) {
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            val ms = usage[pkg] ?: 0L
            val lim = Store.limitMin(this, pkg)
            val foc = Store.focusApps(this).contains(pkg)

            val item = LinearLayout(this)
            item.orientation = LinearLayout.HORIZONTAL
            item.gravity = Gravity.CENTER_VERTICAL
            item.setPadding(0, dp(8), 0, dp(8))
            item.setOnClickListener { edit(pkg, label) }

            val icon = ImageView(this)
            icon.setImageDrawable(ri.loadIcon(pm))
            item.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(dp(12), 0, 0, 0)
            col.addView(tv(label, 16f, true))

            val info = StringBuilder("Hôm nay: ${fmt(ms)}")
            if (lim > 0) info.append(" · Giới hạn ${lim}p")
            if (foc) info.append(" · Chặn khi tập trung")
            val over = lim > 0 && ms / 60000 >= lim
            col.addView(tv(info.toString(), 13f, false, if (over) Color.RED else Color.DKGRAY))

            item.addView(col)
            root.addView(item)
        }
    }
}
