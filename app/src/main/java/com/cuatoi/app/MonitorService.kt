package com.cuatoi.app

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bộ theo dõi chạy nền (dịch vụ nền có thông báo), không phụ thuộc dịch vụ trợ năng.
 * Biết app đang mở nhờ dữ liệu sử dụng, vẽ ô thời gian và màn hình chặn bằng quyền
 * "hiển thị trên app khác", tắt tiếng khi đang chặn.
 */
class MonitorService : Service() {
    companion object {
        @Volatile
        var inst: MonitorService? = null

        fun start(c: Context) {
            try {
                val i = Intent(c, MonitorService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (t: Throwable) {
                Store.setErr(c, "Không khởi động được bộ theo dõi: $t")
            }
        }

        fun fmtSec(n: Int): String =
            if (n >= 60 && n % 60 == 0) "${n / 60} phút" else "$n giây"
    }

    private val h = Handler(Looper.getMainLooper())
    private var running = false

    // App đang ở màn hình
    private var fg = ""
    private var emptySince = 0L
    private var current = ""
    private var evFrom = 0L
    private var home: String? = null
    private var imes: Set<String> = emptySet()
    private var sysAt = 0L
    private val launchable = HashMap<String, Boolean>()
    private var lastBeat = 0L
    private var lastState = ""
    private var demoUntil = 0L

    // Lớp phủ
    private var pill: TextView? = null
    private var pillPos = -1
    private var blockRoot: View? = null
    private var blockPkg = ""
    private var cdView: TextView? = null
    private var lastBounce = 0L
    private val gates = HashMap<String, Gate>()

    // Số liệu của app đang mở, làm mới mỗi 5 giây
    private var stPkg = ""
    private var stAt = 0L
    private var stUsed = 0L
    private var stLim = 0
    private var stEnt = false
    private var stEntUsed = 0L
    private var stEntLim = 0

    // Âm thanh
    private var muted = false
    private var focusReq: AudioFocusRequest? = null

    private val loop = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (t: Throwable) {
                Store.setErr(this@MonitorService, t.toString())
                pillState("lỗi: ${t.javaClass.simpleName} ${t.message}")
            }
            if (running) h.postDelayed(this, if (blockRoot != null) 250L else 500L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        inst = this
        Store.logSvc(this, "bộ theo dõi khởi động (pid ${Process.myPid()})")
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("tau_keep", "Bộ theo dõi Tau", NotificationManager.IMPORTANCE_MIN))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(this, "tau_keep")
                .setSmallIcon(R.drawable.ic_tau_fg)
                .setContentTitle("Tau đang chạy")
                .setContentText("Theo dõi thời gian dùng app")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
            startForeground(77, n)
        } catch (t: Throwable) {
            Store.logSvc(this, "giữ chạy nền: lỗi ${t.javaClass.simpleName}")
        }
        try {
            val rx = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    try {
                        onBattery(i)
                    } catch (t: Throwable) {
                    }
                }
            }
            registerReceiver(rx, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batRx = rx
        } catch (t: Throwable) {
            Store.setErr(this, "pin: $t")
        }
        // Nếu lần trước bị tắt giữa lúc đang tắt tiếng thì trả âm lượng lại
        if (Store.p(this).getBoolean("we_muted", false)) {
            muted = true
            unmute()
        }
        try {
            seed(System.currentTimeMillis())
        } catch (t: Throwable) {
            Store.setErr(this, "seed: $t")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            h.removeCallbacks(loop)
            h.post(loop)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Store.logSvc(this, "Tau bị vuốt khỏi đa nhiệm")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        inst = null
        Store.logSvc(this, "bộ theo dõi bị huỷ")
        h.removeCallbacks(loop)
        unmute()
        removePill()
        hideBlock()
        try {
            batRx?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    /** Hiện thử ô thời gian khoảng 8 giây, bất kể đang ở app nào. */
    fun demo() {
        demoUntil = System.currentTimeMillis() + 8000L
        h.post { loop.run() }
    }

    /* ---------- Biết app nào đang mở ---------- */
    // Theo dõi từng màn hình (activity) đang mở. Một app như TikTok chuyển qua lại nhiều màn hình,
    // nếu chỉ theo dõi theo tên app thì sự kiện "tạm dừng" của màn hình cũ làm app tưởng đã thoát.
    private val act = LinkedHashSet<String>()

    private fun handle(type: Int, pkg: String, cls: String) {
        val k = "$pkg/$cls"
        when (type) {
            1 -> {
                act.remove(k)
                act.add(k)
            }
            2, 23, 24 -> act.remove(k)
            16, 17, 26 -> act.clear()
        }
        fg = act.lastOrNull()?.substringBefore('/') ?: ""
    }

    private fun usm(): UsageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private fun seed(now: Long) {
        if (!Usage.hasAccess(this)) return
        val ev = usm().queryEvents(now - 30 * 60000L, now) ?: return
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            handle(e.eventType, e.packageName, e.className ?: "")
        }
        evFrom = now - 1000L
    }

    private fun poll(now: Long) {
        if (!Usage.hasAccess(this)) return
        if (evFrom == 0L) evFrom = now - 3000L
        val ev = usm().queryEvents(evFrom, now) ?: return
        evFrom = now - 1000L
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            handle(e.eventType, e.packageName, e.className ?: "")
        }
    }

    private fun refreshSys(now: Long) {
        if (now - sysAt < 60000L && home != null) return
        sysAt = now
        home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        )?.activityInfo?.packageName
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imes = imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) {
        }
    }

    private fun isLaunchable(pkg: String): Boolean =
        launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }

    private fun applyForeground(now: Long) {
        refreshSys(now)
        val pkg = fg
        if (pkg.isEmpty()) {
            // Trống là "chưa rõ", không phải "đã thoát": thoát thật luôn có sự kiện màn hình chính hiện ra.
            // Giữ nguyên app hiện tại tối đa 6 giây (đề phòng thiếu sự kiện) rồi mới coi là đã rời.
            if (emptySince == 0L) emptySince = now
            if (now - emptySince >= 6000L) setCurrent("")
            return
        }
        emptySince = 0L
        if (pkg == packageName || pkg == home) {
            setCurrent("")
            return
        }
        // Bàn phím và cửa sổ hệ thống không có biểu tượng app thì không đổi app hiện tại
        if (imes.contains(pkg)) return
        if (!isLaunchable(pkg)) return
        setCurrent(pkg)
    }

    private fun setCurrent(p: String) {
        if (p == current) return
        gates[current]?.leave()
        current = p
        entPkg = ""
        if (p.isNotEmpty()) Store.setLast(this, p)
        hideBlock()
        unmute()
        if (p.isEmpty()) hidePill()
    }

    private fun gate(pkg: String): Gate = gates.getOrPut(pkg) { Gate() }

    private fun screenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
    }

    private fun pillState(s: String) {
        if (s == lastState) return
        lastState = s
        Store.p(this).edit().putString("pill_state", T.hms(System.currentTimeMillis()) + " · " + s).apply()
    }

    /* ---------- Hạn chế ---------- */
    /** 0 = không hạn chế, 1 = hết thời gian giới hạn, 2 = đang tập trung. */
    private fun reasonFor(pkg: String, now: Long): Int {
        if (now < Store.focusEnd(this) && Store.focusApps(this).contains(pkg)) return 2
        if (Store.bedActive(this, now) && Ent.set(this).contains(pkg)) return 3
        if (Store.inWin(this, pkg, now)) return 4
        if (!Usage.hasAccess(this)) return 0
        if (pkg != stPkg || now - stAt >= 5000L) refreshStats(pkg, now)
        val el = now - stAt
        val hitApp = stLim > 0 && (stUsed + el) / 60000L >= stLim
        val hitEnt = stEnt && (stEntUsed + el) / 60000L >= stEntLim
        return if (hitApp || hitEnt) 1 else 0
    }

    private fun refreshStats(pkg: String, now: Long) {
        stPkg = pkg
        stAt = now
        stLim = Store.limitMin(this, pkg)
        stEntLim = Store.entLimit(this)
        val ent = Ent.set(this)
        stEnt = stEntLim > 0 && ent.contains(pkg)
        var u = 0L
        var eu = 0L
        for (s in Usage.sessions(this, T.dayStart(now), now)) {
            val d = s.end - s.start
            if (s.pkg == pkg) u += d
            if (stEnt && ent.contains(s.pkg)) eu += d
        }
        stUsed = u
        stEntUsed = eu
    }

    private fun pillText(pkg: String, now: Long): String {
        val el = if (pkg == stPkg) now - stAt else 0L
        if (stLim > 0) return "${T.fmt(stUsed + el)} / ${stLim}p"
        if (stEnt) return "Giải trí ${T.fmt(stEntUsed + el)} / ${stEntLim}p"
        return T.fmt(stUsed + el)
    }

    /* ---------- Vòng lặp chính ---------- */
    private fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastBeat >= 2000L) {
            lastBeat = now
            Store.p(this).edit().putLong("mon_beat", now).apply()
        }
        poll(now)
        applyForeground(now)
        checkGoal(now)
        housekeeping(now)
        val fe = Store.focusEnd(this)
        if (fe > 0L && now >= fe) {
            Store.focusFinish(this, fe)
            Store.setFocusEnd(this, 0L)
            Notif.show(this, 90, "Hết giờ tập trung", "Làm tốt lắm! Các app đã được mở lại.")
        }
        val on = screenOn()

        if (now < demoUntil) {
            showPill("Ô thời gian đang hoạt động")
            pillState("đang chạy thử")
            return
        }
        val pkg = current
        if (pkg.isEmpty()) {
            hidePill()
            hideBlock()
            unmute()
            pillState("ẩn: đang ở màn hình chính hoặc trong Tau")
            return
        }
        if (!on) {
            hidePill()
            hideBlock()
            unmute()
            gates[pkg]?.leave()
            pillState("ẩn: màn hình đang tắt hoặc khoá")
            return
        }
        val reason = reasonFor(pkg, now)
        val g = gate(pkg)
        g.countdownMs = Store.waitSec(this) * 1000L
        g.unlockMs = Store.openSec(this) * 1000L
        val st = g.step(now, reason != 0, on)
        val prevPh = lastPh[pkg]
        if (st.phase == Gate.Phase.COUNTDOWN && prevPh != Gate.Phase.COUNTDOWN) Store.blkAdd(this, pkg)
        lastPh[pkg] = st.phase
        warnLong(pkg, now)
        when (st.phase) {
            Gate.Phase.FREE -> {
                hideBlock()
                unmute()
                if (!Store.p(this).getBoolean("overlay_on", true)) {
                    hidePill()
                    pillState("ẩn: đã tắt trong cài đặt")
                } else if (!Usage.hasAccess(this)) {
                    hidePill()
                    pillState("ẩn: chưa cấp quyền xem thời gian dùng")
                } else if (!canDraw()) {
                    pillState("ẩn: chưa cấp quyền hiển thị trên app khác")
                } else {
                    showPill(if (now < flashUntil) flashText else pillText(pkg, now))
                    pillState("đang hiện cho $pkg")
                }
            }
            Gate.Phase.COUNTDOWN -> {
                if (!canDraw()) {
                    // Không có quyền vẽ thì đưa người dùng về màn hình chính
                    pillState("không chặn được: chưa cấp quyền hiển thị trên app khác")
                    if (now - lastBounce > 1500L) {
                        lastBounce = now
                        goHome()
                    }
                } else {
                    showBlock(pkg, reason)
                    mute()
                    cdView?.text = ((st.leftMs + 999L) / 1000L).toString()
                    pillState("đang hiện màn hình chặn cho $pkg")
                }
            }
            Gate.Phase.UNLOCKED -> {
                hideBlock()
                unmute()
                if (canDraw()) showPill("Khoá lại sau ${(st.leftMs + 999L) / 1000L}s")
                pillState("đang mở khoá ${MonitorService.fmtSec(Store.openSec(this))} cho $pkg")
            }
        }
    }

    private var goalAt = 0L
    private var entPkg = ""
    private var nextWarn = 0L
    private var flashText = ""
    private var flashUntil = 0L
    private var lastPh = HashMap<String, Gate.Phase>()
    private var syncAt = 0L
    private var updAt = 0L
    private var batRx: BroadcastReceiver? = null
    private var chgNotified = false
    private var hotAt = 0L

    private fun onBattery(i: Intent) {
        val lv = i.getIntExtra("level", -1)
        val sc = i.getIntExtra("scale", 100)
        val pct = if (lv >= 0 && sc > 0) lv * 100 / sc else -1
        val plugged = i.getIntExtra("plugged", 0) != 0
        val temp = i.getIntExtra("temperature", 0) / 10.0
        if (!plugged) chgNotified = false
        if (!Store.p(this).getBoolean("charge_alert", true)) return
        val now = System.currentTimeMillis()
        if (plugged && pct >= 80 && !chgNotified) {
            chgNotified = true
            Notif.show(this, 92, "Pin đã $pct%", "Rút sạc khoảng 80% giúp pin bền hơn.")
        }
        if (temp >= 42.0 && now - hotAt > 600000L) {
            hotAt = now
            Notif.show(this, 93, "Máy đang nóng ${temp.toInt()}°C", "Tạm ngừng sạc hoặc chơi game, tháo ốp cho máy nguội.")
        }
    }

    /** Mỗi 30 phút lưu số liệu từng ngày và từng khung giờ, Chủ nhật 20h báo cáo tuần. */
    private fun housekeeping(now: Long) {
        if (now - updAt >= 3600000L) {
            updAt = now
            Thread {
                try {
                    Updater.check(applicationContext, false)
                } catch (t: Throwable) {
                }
            }.start()
        }
        if (now - syncAt >= 1800000L && Usage.hasAccess(this)) {
            syncAt = now
            Thread {
                try {
                    Usage.syncDays(applicationContext, Usage.launcherSet(applicationContext), 2)
                } catch (t: Throwable) {
                }
            }.start()
        }
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = now
        if (c.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY && c.get(java.util.Calendar.HOUR_OF_DAY) >= 20) {
            val key = T.key(now)
            if (Store.p(this).getString("week_notified", "") != key) {
                Store.p(this).edit().putString("week_notified", key).apply()
                Notif.show(this, 94, "Báo cáo tuần của bạn", "Mở Tau, mục Bản thân, tab Báo cáo để xem tuần này bạn dùng điện thoại thế nào.")
            }
        }
    }

    /** Mỗi phút kiểm tra tổng thời gian hôm nay so với mục tiêu, báo một lần khi vượt. */
    private fun checkGoal(now: Long) {
        val goal = Store.goalMin(this)
        if (goal <= 0 || now - goalAt < 60000L || !Usage.hasAccess(this)) return
        goalAt = now
        val key = T.key(now)
        if (Store.p(this).getString("goal_notified", "") == key) return
        var tot = 0L
        refreshSys(now)
        for (s in Usage.sessions(this, T.dayStart(now), now)) {
            if (s.pkg == packageName || s.pkg == home || imes.contains(s.pkg) || !isLaunchable(s.pkg)) continue
            tot += s.end - s.start
        }
        if (tot >= goal * 60000L) {
            Store.p(this).edit().putString("goal_notified", key).apply()
            Notif.show(this, 91, "Đã đủ mục tiêu hôm nay", "Bạn đã dùng điện thoại ${T.fmt(tot)}, vượt mục tiêu $goal phút.")
        }
    }

    /** Cảnh báo khi dùng liên tục một app giải trí quá số phút đã đặt, lặp lại sau mỗi chừng đó phút. */
    private fun warnLong(pkg: String, now: Long) {
        val wm = Store.warnMin(this)
        if (wm <= 0 || !Ent.set(this).contains(pkg)) {
            entPkg = ""
            return
        }
        if (entPkg != pkg) {
            entPkg = pkg
            nextWarn = now + wm * 60000L
            return
        }
        if (now >= nextWarn) {
            val total = (now - (nextWarn - wm * 60000L)) / 60000L
            nextWarn += wm * 60000L
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
            Notif.show(this, 95, "Dùng $label khá lâu rồi", "Bạn đã dùng liên tục khoảng $total phút. Hãy nghỉ tay, uống nước hoặc đứng dậy một chút.")
            flashText = "⚠ $total phút liên tục"
            flashUntil = now + 10000L
        }
    }

    private fun goHome() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            Store.setErr(this, "Không về được màn hình chính: $t")
        }
    }

    /* ---------- Âm thanh ---------- */
    private fun am(): AudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun mute() {
        try {
            val a = am()
            if (!muted) {
                if (!a.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                    muted = true
                    Store.p(this).edit().putBoolean("we_muted", true).apply()
                }
                if (Build.VERSION.SDK_INT >= 26 && focusReq == null) {
                    val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setOnAudioFocusChangeListener { }
                        .build()
                    a.requestAudioFocus(r)
                    focusReq = r
                }
            } else if (!a.isStreamMute(AudioManager.STREAM_MUSIC)) {
                // Người dùng bấm tăng âm lượng trong lúc khoá: tắt lại
                a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            }
        } catch (t: Throwable) {
            Store.setErr(this, "Tắt tiếng lỗi: $t")
        }
    }

    private fun unmute() {
        if (!muted && focusReq == null) return
        try {
            val a = am()
            if (muted) a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            val r = focusReq
            if (r != null && Build.VERSION.SDK_INT >= 26) a.abandonAudioFocusRequest(r)
        } catch (t: Throwable) {
            Store.setErr(this, "Bật lại tiếng lỗi: $t")
        }
        muted = false
        focusReq = null
        Store.p(this).edit().putBoolean("we_muted", false).apply()
    }

    /* ---------- Lớp phủ ---------- */
    private fun canDraw(): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun wm(): WindowManager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun blockParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return lp
    }

    private fun buildBlock(pkg: String, reason: Int): View {
        val d = resources.displayMetrics.density
        fun px(v: Int): Int = (v * d).toInt()
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        val root = FrameLayout(this)
        root.isClickable = true // nuốt mọi chạm, không cho thao tác app bên dưới
        root.addView(TauBg(this), FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(px(28), px(56), px(28), px(56))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        fun text(t: String, size: Float, bold: Boolean, alpha: Float): TextView {
            val v = TextView(this)
            v.text = t
            v.textSize = size
            v.setTextColor(Color.WHITE)
            v.alpha = alpha
            v.gravity = Gravity.CENTER
            if (bold) v.typeface = Typeface.DEFAULT_BOLD
            return v
        }

        col.addView(
            text(
                when (reason) {
                    2 -> "Bạn đang trong chế độ tập trung"
                    3 -> "Đã đến giờ đi ngủ, hãy cất điện thoại"
                    4 -> "Ứng dụng này đang trong khung giờ bị chặn"
                    else -> "Bạn đã sử dụng hết thời gian giới hạn"
                },
                24f, true, 1f
            )
        )
        val name = text(label, 16f, false, 0.85f)
        name.setPadding(0, px(8), 0, 0)
        col.addView(name)
        val cd = text(Store.waitSec(this).toString(), 96f, true, 1f)
        cd.setPadding(0, px(24), 0, px(24))
        col.addView(cd)
        cdView = cd
        col.addView(text("Hết đếm ngược, bạn được dùng app đúng ${fmtSec(Store.openSec(this))} rồi app tự khoá lại.", 14f, false, 0.9f))

        val b = Button(this)
        b.text = "Về màn hình chính"
        b.isAllCaps = false
        b.setTextColor(Color.parseColor("#C2410C"))
        val g = GradientDrawable()
        g.setColor(Color.WHITE)
        g.cornerRadius = px(24).toFloat()
        b.background = g
        b.setOnClickListener { goHome() }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.topMargin = px(28)
        col.addView(b, lp)
        return root
    }

    private fun showBlock(pkg: String, reason: Int) {
        val cur = blockRoot
        if (cur != null && blockPkg == pkg && cur.isAttachedToWindow) return
        hideBlock()
        hidePill()
        try {
            val v = buildBlock(pkg, reason)
            wm().addView(v, blockParams())
            blockRoot = v
            blockPkg = pkg
        } catch (t: Throwable) {
            Store.setErr(this, "Không hiện được màn hình chặn: $t")
            cdView = null
            goHome()
        }
    }

    private fun hideBlock() {
        val v = blockRoot ?: return
        try {
            wm().removeView(v)
        } catch (e: Exception) {
        }
        blockRoot = null
        blockPkg = ""
        cdView = null
    }

    private fun pillParams(pos: Int): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        val d = resources.displayMetrics.density
        lp.gravity = when (pos) {
            1 -> Gravity.TOP or Gravity.START
            2 -> Gravity.BOTTOM or Gravity.END
            3 -> Gravity.BOTTOM or Gravity.START
            else -> Gravity.TOP or Gravity.END
        }
        lp.x = (8 * d).toInt()
        lp.y = if (pos >= 2) (72 * d).toInt() else (52 * d).toInt()
        return lp
    }

    /** Ô thời gian được giữ cố định trên màn hình, chỉ đổi chữ và ẩn hiện, không vẽ lại liên tục. */
    private fun showPill(txt: String) {
        if (!canDraw()) return
        val pos = Store.p(this).getInt("overlay_pos", 0)
        var v = pill
        if (v != null && !v.isAttachedToWindow) {
            // Hệ thống đã gỡ cửa sổ mà mình không hay: tạo lại
            pill = null
            v = null
        }
        if (v == null) {
            val d = resources.displayMetrics.density
            val tv = TextView(this)
            tv.setTextColor(Color.WHITE)
            tv.textSize = 14f
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
            val g = GradientDrawable()
            g.setColor(Color.parseColor("#F26B1D"))
            g.setStroke((2 * d).toInt(), Color.WHITE)
            g.cornerRadius = 18 * d
            tv.background = g
            try {
                wm().addView(tv, pillParams(pos))
            } catch (t: Throwable) {
                Store.setErr(this, "Không vẽ được ô thời gian: $t")
                return
            }
            pill = tv
            pillPos = pos
            v = tv
        } else if (pillPos != pos) {
            try {
                wm().updateViewLayout(v, pillParams(pos))
            } catch (e: Exception) {
            }
            pillPos = pos
        }
        if (v.text.toString() != txt) v.text = txt
        if (v.visibility != View.VISIBLE) v.visibility = View.VISIBLE
    }

    private fun hidePill() {
        val v = pill ?: return
        if (v.visibility != View.GONE) v.visibility = View.GONE
    }

    private fun removePill() {
        val v = pill ?: return
        try {
            wm().removeView(v)
        } catch (e: Exception) {
        }
        pill = null
    }
}
