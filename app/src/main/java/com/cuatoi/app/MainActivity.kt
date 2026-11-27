package com.cuatoi.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class AppInfo(val pkg: String, val label: String, val icon: Drawable)

/* Nền cam với chữ τ (hằng số thời gian) */
class TauBg(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        cv.drawColor(Color.parseColor("#F26B1D"))
        p.color = Color.WHITE
        p.alpha = 46
        p.textAlign = Paint.Align.CENTER
        p.textSize = Math.min(width, height) * 1.25f
        cv.drawText("τ", width / 2f, height * 0.78f, p)
    }
}

/* Biểu đồ cột đơn giản */
class BarChart(c: Context) : View(c) {
    var vals = DoubleArray(0)
    var labels: Array<String> = emptyArray()
    var hi = -1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (150 * resources.displayMetrics.density).toInt())
    }

    override fun onDraw(cv: Canvas) {
        if (vals.isEmpty()) return
        val d = resources.displayMetrics.density
        val top = 8 * d
        val bot = 20 * d
        val ch = height - top - bot
        var mx = 1.0
        for (v in vals) if (v > mx) mx = v
        val n = vals.size
        val gw = width.toFloat() / n
        val bw = gw * 0.62f
        p.textSize = 10 * d
        p.textAlign = Paint.Align.CENTER
        for (i in 0 until n) {
            val bh = (vals[i] / mx * ch).toFloat()
            val x = i * gw + (gw - bw) / 2
            p.color = if (i == hi) Color.parseColor("#C2410C") else Color.parseColor("#F5A26B")
            cv.drawRoundRect(x, top + ch - bh, x + bw, top + ch, 3 * d, 3 * d, p)
            if (i < labels.size && labels[i].isNotEmpty()) {
                p.color = Color.parseColor("#6B7280")
                cv.drawText(labels[i], i * gw + gw / 2, height - 5 * d, p)
            }
        }
    }
}

class MainActivity : Activity() {
    private val orange = Color.parseColor("#F26B1D")
    private val deep = Color.parseColor("#C2410C")
    private val ink = Color.parseColor("#1F2937")
    private val mut = Color.parseColor("#6B7280")

    private var apps: List<AppInfo> = emptyList()
    private var appMap: Map<String, AppInfo> = emptyMap()
    private lateinit var sTime: ScrollView
    private lateinit var sFocus: ScrollView
    private lateinit var sHealth: ScrollView
    private lateinit var lTime: LinearLayout
    private lateinit var lFocus: LinearLayout
    private lateinit var lHealth: LinearLayout
    private lateinit var web: WebView
    private lateinit var navItems: Array<TextView>
    private var tab = 0
    private var timeMode = 0
    private var gen = 0
    private var focusLabel: TextView? = null
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateFocusLabel()
            h.postDelayed(this, 1000)
        }
    }

    /* ---------- Cầu nối cho phần web (ghi chú nhắc việc) ---------- */
    inner class Bridge {
        @JavascriptInterface
        fun syncReminders(json: String) {
            Reminders.sync(applicationContext, json)
        }
    }

    /* ---------- Hàm dựng giao diện ---------- */
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tv(t: String, size: Float = 14f, bold: Boolean = false, color: Int = ink): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun head(t: String): TextView {
        val v = tv(t, 24f, true, Color.WHITE)
        v.setPadding(0, dp(8), 0, dp(12))
        return v
    }

    private fun card(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(Color.WHITE)
        g.cornerRadius = dp(16).toFloat()
        l.background = g
        l.setPadding(dp(14), dp(12), dp(14), dp(12))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(12)
        l.layoutParams = lp
        return l
    }

    private fun btn(t: String, f: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.stateListAnimator = null
        val g = GradientDrawable()
        g.setColor(deep)
        g.cornerRadius = dp(10).toFloat()
        b.background = g
        b.setOnClickListener { f() }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.topMargin = dp(8)
        lp.rightMargin = dp(6)
        b.layoutParams = lp
        return b
    }

    private fun seg(t: String, sel: Boolean, f: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.stateListAnimator = null
        val g = GradientDrawable()
        g.cornerRadius = dp(20).toFloat()
        g.setColor(if (sel) Color.WHITE else Color.parseColor("#33FFFFFF"))
        b.background = g
        b.setTextColor(if (sel) deep else Color.WHITE)
        b.setOnClickListener { f() }
        return b
    }

    private fun scrollPage(): Pair<ScrollView, LinearLayout> {
        val s = ScrollView(this)
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(12), dp(16), dp(24))
        s.addView(l)
        return Pair(s, l)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWeb(): WebView {
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.addJavascriptInterface(Bridge(), "TauApp")
        w.webViewClient = WebViewClient()
        w.setBackgroundColor(Color.parseColor("#FFF4EA"))
        val html = assets.open("cuatoi.html").bufferedReader().use { it.readText() }
        w.loadDataWithBaseURL("https://tau.app/", html, "text/html", "utf-8", null)
        return w
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = orange
        window.navigationBarColor = Color.WHITE
        loadApps()

        val rootF = FrameLayout(this)
        rootF.addView(TauBg(this), FrameLayout.LayoutParams(-1, -1))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        rootF.addView(col, FrameLayout.LayoutParams(-1, -1))

        val content = FrameLayout(this)
        col.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val p1 = scrollPage()
        sTime = p1.first
        lTime = p1.second
        val p2 = scrollPage()
        sFocus = p2.first
        lFocus = p2.second
        val p3 = scrollPage()
        sHealth = p3.first
        lHealth = p3.second
        web = makeWeb()
        content.addView(sTime, FrameLayout.LayoutParams(-1, -1))
        content.addView(sFocus, FrameLayout.LayoutParams(-1, -1))
        content.addView(sHealth, FrameLayout.LayoutParams(-1, -1))
        content.addView(web, FrameLayout.LayoutParams(-1, -1))

        val nav = LinearLayout(this)
        nav.setBackgroundColor(Color.WHITE)
        val names = arrayOf("Thời gian", "Tập trung", "Sức khoẻ", "Tiện ích")
        navItems = Array(4) { i ->
            val t = TextView(this)
            t.text = names[i]
            t.gravity = Gravity.CENTER
            t.textSize = 13f
            t.setPadding(0, dp(14), 0, dp(14))
            t.setOnClickListener { showTab(i) }
            nav.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            t
        }
        col.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(rootF)
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        refresh()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (tab == 3 && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    private fun showTab(i: Int) {
        tab = i
        sTime.visibility = if (i == 0) View.VISIBLE else View.GONE
        sFocus.visibility = if (i == 1) View.VISIBLE else View.GONE
        sHealth.visibility = if (i == 2) View.VISIBLE else View.GONE
        web.visibility = if (i == 3) View.VISIBLE else View.GONE
        for (k in 0 until 4) {
            navItems[k].setTextColor(if (k == i) deep else mut)
            navItems[k].typeface = if (k == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        refresh()
    }

    private fun refresh() {
        when (tab) {
            0 -> buildTime()
            1 -> buildFocus()
            2 -> buildHealth()
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        val seen = HashSet<String>()
        val list = ArrayList<AppInfo>()
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            val p = ri.activityInfo.packageName
            if (p == packageName || p == home || !seen.add(p)) continue
            list.add(AppInfo(p, ri.loadLabel(pm).toString(), ri.loadIcon(pm)))
        }
        apps = list.sortedBy { it.label.lowercase() }
        appMap = apps.associateBy { it.pkg }
    }

    private fun serviceOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains(packageName) && s.contains("BlockerService")
    }

    private fun needNotifPerm(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun askNotif() {
        if (needNotifPerm()) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    private fun appRow(a: AppInfo, extra: String, check: Boolean): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(6), 0, dp(6))
        val ic = ImageView(this)
        ic.setImageDrawable(a.icon)
        r.addView(ic, LinearLayout.LayoutParams(dp(36), dp(36)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(10), 0, dp(6), 0)
        col.addView(tv(a.label, 15f, true))
        val parts = ArrayList<String>()
        if (extra.isNotEmpty()) parts.add(extra)
        val lim = Store.limitMin(this, a.pkg)
        if (lim > 0) parts.add("giới hạn ${lim}p")
        if (Ent.isEnt(this, a.pkg)) parts.add("giải trí")
        col.addView(tv(parts.joinToString(" · "), 12f, false, mut))
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (check) {
            val cb = CheckBox(this)
            cb.isChecked = Store.focusApps(this).contains(a.pkg)
            cb.setOnCheckedChangeListener { _, v -> Store.setFocusApp(this, a.pkg, v) }
            r.addView(cb)
        }
        col.setOnClickListener { edit(a) }
        ic.setOnClickListener { edit(a) }
        return r
    }

    private fun edit(a: AppInfo) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.hint = "Giới hạn mỗi ngày (phút), để trống = không giới hạn"
        val cur = Store.limitMin(this, a.pkg)
        if (cur > 0) et.setText(cur.toString())
        val cb = CheckBox(this)
        cb.text = "Chặn khi tập trung"
        cb.isChecked = Store.focusApps(this).contains(a.pkg)
        box.addView(et)
        box.addView(cb)
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                Store.setLimit(this, a.pkg, et.text.toString().toIntOrNull() ?: 0)
                Store.setFocusApp(this, a.pkg, cb.isChecked)
                refresh()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    /* ---------- Tab 1: Thời gian ---------- */
    private fun buildTime() {
        val ll = lTime
        ll.removeAllViews()
        ll.addView(head("Thời gian sử dụng"))
        if (!Usage.hasAccess(this)) {
            val c = card()
            c.addView(tv("Cần cấp quyền xem thời gian dùng app để thống kê."))
            c.addView(btn("Cấp quyền") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
            ll.addView(c)
            return
        }
        val row = LinearLayout(this)
        val names = arrayOf("Hôm nay", "7 ngày", "30 ngày")
        for (i in 0..2) {
            val b = seg(names[i], timeMode == i) {
                timeMode = i
                buildTime()
            }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.rightMargin = dp(6)
            row.addView(b, lp)
        }
        ll.addView(row)

        val loading = card()
        loading.addView(tv("Đang tính toán...", 14f, false, mut))
        ll.addView(loading)

        gen++
        val g = gen
        val ok = appMap.keys.toSet()
        val mode = timeMode
        Thread {
            val now = System.currentTimeMillis()
            val t0 = T.dayStart(now)
            var sess: List<Session> = emptyList()
            try {
                Usage.syncDays(this, ok, 8)
                if (mode == 0) sess = Usage.sessions(this, t0, now)
            } catch (e: Exception) {
                Store.setErr(this, e.toString())
            }
            runOnUiThread {
                if (g == gen && tab == 0) {
                    ll.removeView(loading)
                    if (mode == 0) renderToday(ll, sess, ok, t0, now) else renderRange(ll, if (mode == 1) 7 else 30, t0)
                }
            }
        }.start()
    }

    private fun renderToday(ll: LinearLayout, sess: List<Session>, ok: Set<String>, t0: Long, now: Long) {
        val tot = Usage.totals(sess, t0, now, ok)
        val sum = tot.values.sum()
        val c1 = card()
        c1.addView(tv("Hôm nay", 13f, false, mut))
        c1.addView(tv(T.fmt(sum), 32f, true))
        val yt = Usage.dayTotal(this, T.addDays(t0, -1))
        if (yt > 0) c1.addView(tv("Hôm qua cả ngày: ${T.fmt(yt)}", 13f, false, mut))
        ll.addView(c1)

        val hc = card()
        hc.addView(tv("Theo giờ trong ngày (phút)", 14f, true))
        val ch = BarChart(this)
        ch.vals = Usage.hourly(sess, t0, ok)
        ch.labels = Array(24) { if (it % 6 == 0) "${it}h" else "" }
        ch.hi = ((now - t0) / 3600000L).toInt()
        hc.addView(ch)
        ll.addView(hc)

        val tc = card()
        tc.addView(tv("Dòng thời gian", 14f, true))
        val shown = sess.filter { ok.contains(it.pkg) && it.end - it.start >= 20000 }.reversed().take(40)
        if (shown.isEmpty()) tc.addView(tv("Chưa có phiên dùng nào đủ dài.", 13f, false, mut))
        for (s in shown) {
            val a = appMap[s.pkg] ?: continue
            tc.addView(sessionRow(a, s))
        }
        ll.addView(tc)

        val ac = card()
        ac.addView(tv("Theo app (chạm để đặt giới hạn)", 14f, true))
        val top = tot.entries.sortedByDescending { it.value }.take(25)
        if (top.isEmpty()) ac.addView(tv("Chưa có số liệu hôm nay.", 13f, false, mut))
        for (e in top) {
            val a = appMap[e.key] ?: continue
            ac.addView(appRow(a, "Hôm nay: ${T.fmt(e.value)}", false))
        }
        ll.addView(ac)
    }

    private fun sessionRow(a: AppInfo, s: Session): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(5), 0, dp(5))
        val tm = tv(T.hm(s.start) + "–" + T.hm(s.end), 12f, false, mut)
        r.addView(tm, LinearLayout.LayoutParams(dp(92), -2))
        val ic = ImageView(this)
        ic.setImageDrawable(a.icon)
        r.addView(ic, LinearLayout.LayoutParams(dp(24), dp(24)))
        val nm = tv(a.label, 14f)
        nm.setPadding(dp(8), 0, dp(6), 0)
        r.addView(nm, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(tv(T.fmt(s.end - s.start), 13f, true))
        return r
    }

    private fun renderRange(ll: LinearLayout, days: Int, t0: Long) {
        val curFrom = T.addDays(t0, -(days - 1))
        val prevFrom = T.addDays(t0, -(2 * days - 1))
        val cur = Usage.range(this, curFrom, days)
        val prev = Usage.range(this, prevFrom, days)
        val curSum = cur.values.sum()
        val prevSum = prev.values.sum()
        val haveCur = Math.max(Usage.daysWithData(this, curFrom, days), 1)
        val havePrev = Usage.daysWithData(this, prevFrom, days)
        val curAvg = curSum / haveCur

        val c1 = card()
        c1.addView(tv("$days ngày qua", 13f, false, mut))
        c1.addView(tv(T.fmt(curSum), 32f, true))
        c1.addView(tv("Trung bình mỗi ngày: ${T.fmt(curAvg)}", 13f, false, ink))
        if (havePrev > 0) {
            val prevAvg = prevSum / havePrev
            c1.addView(tv("$days ngày trước đó: ${T.fmt(prevAvg)} mỗi ngày (${T.delta(curAvg, prevAvg)})", 13f, false, ink))
        } else {
            c1.addView(tv("Chưa có dữ liệu của $days ngày trước đó.", 13f, false, mut))
        }
        if (havePrev < days) {
            c1.addView(tv("Kỳ trước mới có $havePrev/$days ngày dữ liệu vì app chỉ ghi lại từ lúc bạn cài, nên so sánh dùng mức trung bình mỗi ngày.", 12f, false, mut))
        }
        ll.addView(c1)

        val cc = card()
        cc.addView(tv("Từng ngày (phút)", 14f, true))
        val ch = BarChart(this)
        val vals = DoubleArray(days)
        val labs = Array(days) { "" }
        for (i in 0 until days) {
            val ds = T.addDays(curFrom, i)
            vals[i] = Usage.dayTotal(this, ds) / 60000.0
            if (days <= 7) labs[i] = T.dow(ds) else if (i % 5 == 0) labs[i] = T.dom(ds)
        }
        ch.vals = vals
        ch.labels = labs
        ch.hi = days - 1
        cc.addView(ch)
        ll.addView(cc)

        val ac = card()
        ac.addView(tv("App dùng nhiều nhất", 14f, true))
        val top = cur.entries.sortedByDescending { it.value }.take(10)
        if (top.isEmpty()) ac.addView(tv("Chưa có số liệu.", 13f, false, mut))
        for (e in top) {
            val a = appMap[e.key] ?: continue
            val pv = prev[e.key] ?: 0L
            val cmp = if (havePrev > 0 && pv > 0) " (${T.delta(e.value / haveCur, pv / havePrev)} so với kỳ trước)" else ""
            ac.addView(appRow(a, "${T.fmt(e.value)}$cmp", false))
        }
        ll.addView(ac)
    }

    /* ---------- Tab 2: Tập trung ---------- */
    private fun updateFocusLabel() {
        val l = focusLabel ?: return
        val left = Store.focusEnd(this) - System.currentTimeMillis()
        if (left > 0) {
            val sec = (left / 1000) % 60
            val st = if (sec < 10) "0$sec" else "$sec"
            l.text = "Đang tập trung · còn ${left / 60000}:$st"
        } else {
            l.text = "Chưa bật chế độ tập trung"
        }
    }

    private fun startFocus(minutes: Int) {
        if (Store.focusApps(this).isEmpty()) {
            Toast.makeText(this, "Hãy tick ít nhất một app cần chặn ở danh sách bên dưới", Toast.LENGTH_LONG).show()
            return
        }
        Store.setFocusEnd(this, System.currentTimeMillis() + minutes * 60000L)
        updateFocusLabel()
    }

    private fun buildFocus() {
        val ll = lFocus
        ll.removeAllViews()
        ll.addView(head("Tập trung & giới hạn"))

        val st = card()
        val on = serviceOn()
        st.addView(
            tv(
                if (on) "Dịch vụ chặn app: đang bật" else "Dịch vụ chặn app: CHƯA bật",
                15f, true, if (on) Color.parseColor("#15803D") else Color.RED
            )
        )
        val lastPkg = Store.p(this).getString("last_pkg", null)
        val lastT = Store.p(this).getLong("last_t", 0L)
        st.addView(
            tv(
                if (lastPkg == null) "Chưa nhận được sự kiện nào từ hệ thống. Hãy mở một app khác rồi quay lại đây."
                else "Sự kiện gần nhất: ${T.hms(lastT)} · $lastPkg",
                12f, false, mut
            )
        )
        val err = Store.p(this).getString("last_err", null)
        if (err != null) st.addView(tv("Lỗi gần nhất: $err", 12f, false, Color.RED))
        st.addView(tv("Ô thời gian: " + (Store.p(this).getString("pill_state", null) ?: "chưa có thông tin"), 12f, false, mut))
        if (!Settings.canDrawOverlays(this)) {
            st.addView(tv("Nếu ô thời gian hoặc màn hình chặn không hiện, hãy cấp thêm quyền hiển thị trên app khác.", 12f, false, mut))
            st.addView(btn("Cấp quyền hiển thị trên app khác") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
        } else {
            st.addView(tv("Quyền hiển thị trên app khác: đã cấp", 12f, false, mut))
        }
        st.addView(btn("Hiện thử ô thời gian") {
            val svc = BlockerService.inst
            if (svc == null) {
                Toast.makeText(this, "Dịch vụ chặn app chưa chạy. Hãy bật ở cài đặt trợ năng.", Toast.LENGTH_LONG).show()
            } else {
                svc.demo()
                Toast.makeText(this, "Nhìn ô cam ở góc màn hình trong vài giây", Toast.LENGTH_LONG).show()
            }
        })
        if (!on) st.addView(btn("Mở cài đặt trợ năng") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        if (!Usage.hasAccess(this)) st.addView(btn("Cấp quyền xem thời gian dùng") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        ll.addView(st)

        val oc = card()
        val osw = Switch(this)
        osw.text = "Hiện đồng hồ nhỏ khi dùng app"
        osw.textSize = 15f
        osw.isChecked = Store.p(this).getBoolean("overlay_on", true)
        osw.setOnCheckedChangeListener { _, v -> Store.p(this).edit().putBoolean("overlay_on", v).apply() }
        oc.addView(osw)
        oc.addView(tv("Hiện số phút bạn đã dùng app đó hôm nay ở góc màn hình, kèm giới hạn nếu có.", 12f, false, mut))
        val posNames = arrayOf("Trên phải", "Trên trái", "Dưới phải", "Dưới trái")
        val pos = Store.p(this).getInt("overlay_pos", 0)
        oc.addView(btn("Vị trí: ${posNames[pos]}") {
            Store.p(this).edit().putInt("overlay_pos", (pos + 1) % 4).apply()
            buildFocus()
        })
        ll.addView(oc)

        val ec = card()
        ec.addView(tv("Hẹn giờ giải trí", 16f, true))
        ec.addView(tv("Quỹ thời gian chung mỗi ngày cho các app trò chơi và giải trí. Dùng hết quỹ là các app này bị chặn.", 12f, false, mut))
        val elim = Store.entLimit(this)
        val el = tv("", 14f)
        el.setPadding(0, dp(8), 0, dp(4))
        ec.addView(el)
        val ebar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        ebar.max = 100
        ebar.progressTintList = ColorStateList.valueOf(orange)
        ec.addView(ebar, LinearLayout.LayoutParams(-1, dp(10)))
        if (elim <= 0) {
            el.text = "Chưa đặt giới hạn · ${Ent.set(this).size} app đang tính là giải trí"
        } else if (!Usage.hasAccess(this)) {
            el.text = "Cần cấp quyền xem thời gian dùng ở tab Thời gian."
        } else {
            el.text = "Đang tính..."
            Thread {
                val now = System.currentTimeMillis()
                val set = Ent.set(this)
                var tot = 0L
                for (s in Usage.sessions(this, T.dayStart(now), now)) if (set.contains(s.pkg)) tot += s.end - s.start
                runOnUiThread {
                    el.text = "Hôm nay: ${T.fmt(tot)} / ${elim}p · ${set.size} app"
                    ebar.progress = Math.min(100L, tot / 60000L * 100L / elim).toInt()
                }
            }.start()
        }
        ec.addView(btn(if (elim > 0) "Giới hạn: $elim phút mỗi ngày" else "Đặt giới hạn mỗi ngày") { editEnt() })
        ec.addView(btn("Chọn app giải trí") { pickEnt() })
        ll.addView(ec)

        val tc = card()
        tc.addView(tv("Chế độ tập trung", 16f, true))
        val fl = tv("", 15f, false, mut)
        focusLabel = fl
        tc.addView(fl)
        val row = LinearLayout(this)
        for (m in intArrayOf(15, 25, 45, 60)) {
            row.addView(btn("${m}p") { startFocus(m) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        row.addView(btn("Dừng") {
            Store.setFocusEnd(this, 0L)
            updateFocusLabel()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        tc.addView(row)
        tc.addView(tv("Chỉ chặn các app được tick bên dưới. Khi bị chặn: màn hình chặn đếm ngược 15 giây, sau đó mở 1 phút, rồi khoá lại.", 12f, false, mut))
        ll.addView(tc)
        updateFocusLabel()

        val lc = card()
        lc.addView(tv("App bị chặn khi tập trung", 16f, true))
        lc.addView(tv("Tick để chặn. Chạm vào tên app để đặt giới hạn phút mỗi ngày.", 12f, false, mut))
        for (a in apps) lc.addView(appRow(a, "", true))
        ll.addView(lc)
    }

    private fun editEnt() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.hint = "Số phút mỗi ngày, để trống = tắt"
        val cur = Store.entLimit(this)
        if (cur > 0) et.setText(cur.toString())
        box.addView(et)
        AlertDialog.Builder(this)
            .setTitle("Hẹn giờ giải trí")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                Store.setEntLimit(this, et.text.toString().toIntOrNull() ?: 0)
                buildFocus()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun pickEnt() {
        val names = Array(apps.size) { apps[it].label }
        val checked = BooleanArray(apps.size) { Ent.isEnt(this, apps[it].pkg) }
        AlertDialog.Builder(this)
            .setTitle("App tính là giải trí")
            .setMultiChoiceItems(names, checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Lưu") { _, _ ->
                Ent.saveChoice(this, apps.map { it.pkg }, checked)
                buildFocus()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    /* ---------- Tab 3: Sức khoẻ ---------- */
    private fun buildHealth() {
        val ll = lHealth
        ll.removeAllViews()
        ll.addView(head("Nhắc sức khoẻ"))
        val top = card()
        top.addView(tv("Thông báo hiện trong khung giờ bạn chọn. Trên Redmi, hãy bật Tự khởi động và đặt Tiết kiệm pin là Không hạn chế để nhắc đúng giờ.", 12f, false, mut))
        if (needNotifPerm()) top.addView(btn("Cho phép thông báo") { askNotif() })
        top.addView(btn("Gửi thử một thông báo") {
            askNotif()
            Notif.show(this, 1, "Tau", "Thông báo hoạt động bình thường.")
        })
        ll.addView(top)

        val list = Health.load(this)
        for (r in list) {
            val c = card()
            val sw = Switch(this)
            sw.text = r.name
            sw.textSize = 16f
            sw.isChecked = r.on
            sw.setOnCheckedChangeListener { _, v ->
                r.on = v
                Health.save(this, list)
                Health.schedule(this, r)
                if (v) askNotif()
            }
            c.addView(sw)
            val sum = if (r.interval >= 1440) "Mỗi ngày lúc ${T.mm(r.start)}" else "Mỗi ${r.interval} phút, từ ${T.mm(r.start)} đến ${T.mm(r.end)}"
            c.addView(tv(sum, 13f, false, mut))
            c.addView(tv(r.msg, 13f))
            c.addView(btn("Chỉnh") { editHealth(r, list) })
            ll.addView(c)
        }
    }

    private fun editHealth(r: Hr, list: List<Hr>) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val daily = r.interval >= 1440
        var st = r.start
        var en = r.end

        val iv = EditText(this)
        iv.inputType = InputType.TYPE_CLASS_NUMBER
        iv.hint = "Lặp lại mỗi (phút)"
        iv.setText(r.interval.toString())
        if (!daily) box.addView(iv)

        val b1 = Button(this)
        b1.isAllCaps = false
        b1.text = (if (daily) "Giờ nhắc: " else "Từ: ") + T.mm(st)
        b1.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm ->
                st = hh * 60 + mm
                b1.text = (if (daily) "Giờ nhắc: " else "Từ: ") + T.mm(st)
            }, st / 60, st % 60, true).show()
        }
        box.addView(b1)

        val b2 = Button(this)
        b2.isAllCaps = false
        b2.text = "Đến: " + T.mm(en)
        b2.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm ->
                en = hh * 60 + mm
                b2.text = "Đến: " + T.mm(en)
            }, en / 60, en % 60, true).show()
        }
        if (!daily) box.addView(b2)

        val msg = EditText(this)
        msg.hint = "Nội dung nhắc"
        msg.setText(r.msg)
        box.addView(msg)

        AlertDialog.Builder(this)
            .setTitle(r.name)
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                if (!daily) r.interval = Math.max(5, iv.text.toString().toIntOrNull() ?: r.interval)
                r.start = st
                r.end = if (daily) st else en
                if (msg.text.toString().isNotBlank()) r.msg = msg.text.toString()
                Health.save(this, list)
                Health.schedule(this, r)
                buildHealth()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}
