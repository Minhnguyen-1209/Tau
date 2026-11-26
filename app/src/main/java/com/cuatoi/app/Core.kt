package com.cuatoi.app

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.app.KeyguardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/* ---------- Tiện ích thời gian ---------- */
object T {
    private fun cal(ms: Long): Calendar {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return c
    }

    fun dayStart(ms: Long): Long {
        val c = cal(ms)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun addDays(ms: Long, d: Int): Long {
        val c = cal(ms)
        c.add(Calendar.DAY_OF_YEAR, d)
        return c.timeInMillis
    }

    fun key(ms: Long): String {
        val c = cal(ms)
        return String.format(Locale.US, "%04d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun hm(ms: Long): String {
        val c = cal(ms)
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun hms(ms: Long): String {
        val c = cal(ms)
        return String.format(Locale.US, "%02d:%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
    }

    fun mm(minOfDay: Int): String = String.format(Locale.US, "%02d:%02d", minOfDay / 60, minOfDay % 60)

    fun dow(ms: Long): String =
        arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")[cal(ms).get(Calendar.DAY_OF_WEEK) - 1]

    fun dom(ms: Long): String = cal(ms).get(Calendar.DAY_OF_MONTH).toString()

    fun fmt(ms: Long): String {
        val m = ms / 60000
        return if (m >= 60) "${m / 60}h ${m % 60}p" else if (m == 0L && ms >= 1000) "<1p" else "${m}p"
    }

    fun delta(cur: Long, prev: Long): String {
        if (prev <= 0L) return "mới"
        val d = (cur - prev) * 100 / prev
        return if (d >= 0) "+$d%" else "$d%"
    }
}

/* ---------- Lưu cài đặt ---------- */
object Store {
    fun p(c: Context): SharedPreferences = c.getSharedPreferences("tau", Context.MODE_PRIVATE)

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

    fun setLast(c: Context, pkg: String) {
        p(c).edit().putString("last_pkg", pkg).putLong("last_t", System.currentTimeMillis()).apply()
    }

    fun setErr(c: Context, msg: String) {
        p(c).edit().putString("last_err", msg).apply()
    }

    fun saveDay(c: Context, key: String, m: Map<String, Long>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        p(c).edit().putString("day_$key", o.toString()).apply()
    }

    fun loadDay(c: Context, key: String): Map<String, Long> {
        val s = p(c).getString("day_$key", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = HashMap<String, Long>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = o.getLong(k)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/* ---------- Đọc thời gian dùng app ---------- */
class Session(val pkg: String, val start: Long, var end: Long)

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

    /** Ghép các sự kiện của hệ thống thành các phiên dùng app. */
    fun sessions(c: Context, from: Long, to: Long): List<Session> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = usm.queryEvents(from, to) ?: return emptyList()
        val out = ArrayList<Session>()
        val e = UsageEvents.Event()
        var cur: String? = null
        var st = 0L

        fun close(t: Long) {
            val p = cur
            if (p != null && t > st) out.add(Session(p, st, t))
            cur = null
        }

        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                1 -> {
                    val p = e.packageName
                    if (cur != p) {
                        close(e.timeStamp)
                        cur = p
                        st = e.timeStamp
                    }
                }
                2 -> if (cur == e.packageName) close(e.timeStamp)
                16, 17, 26 -> close(e.timeStamp)
            }
        }
        close(to)

        val merged = ArrayList<Session>()
        for (s in out) {
            val l = if (merged.isEmpty()) null else merged[merged.size - 1]
            if (l != null && l.pkg == s.pkg && s.start - l.end <= 3000) l.end = s.end else merged.add(s)
        }
        return merged.filter { it.end - it.start >= 1000 }
    }

    fun totals(l: List<Session>, from: Long, to: Long, ok: Set<String>): HashMap<String, Long> {
        val out = HashMap<String, Long>()
        for (s in l) {
            if (!ok.contains(s.pkg)) continue
            val a = Math.max(s.start, from)
            val b = Math.min(s.end, to)
            if (b > a) out[s.pkg] = (out[s.pkg] ?: 0L) + (b - a)
        }
        return out
    }

    fun hourly(l: List<Session>, dayStart: Long, ok: Set<String>): DoubleArray {
        val out = DoubleArray(24)
        for (s in l) {
            if (!ok.contains(s.pkg)) continue
            for (h in 0 until 24) {
                val a = Math.max(s.start, dayStart + h * 3600000L)
                val b = Math.min(s.end, dayStart + (h + 1) * 3600000L)
                if (b > a) out[h] += (b - a) / 60000.0
            }
        }
        return out
    }

    fun todayMs(c: Context, pkg: String): Long {
        val now = System.currentTimeMillis()
        var t = 0L
        for (s in sessions(c, T.dayStart(now), now)) if (s.pkg == pkg) t += s.end - s.start
        return t
    }

    /** Lưu số liệu từng ngày để so sánh tuần/tháng về sau. */
    fun syncDays(c: Context, ok: Set<String>, days: Int) {
        val now = System.currentTimeMillis()
        val t0 = T.dayStart(now)
        for (i in 0 until days) {
            val ds = T.addDays(t0, -i)
            val to = Math.min(T.addDays(ds, 1), now)
            val m = totals(sessions(c, ds, to), ds, to, ok)
            if (m.isEmpty()) continue
            val saved = Store.loadDay(c, T.key(ds))
            if (m.values.sum() >= saved.values.sum()) Store.saveDay(c, T.key(ds), m)
        }
    }

    fun dayTotal(c: Context, ds: Long): Long = Store.loadDay(c, T.key(ds)).values.sum()

    fun range(c: Context, fromDay: Long, days: Int): HashMap<String, Long> {
        val out = HashMap<String, Long>()
        for (i in 0 until days) {
            val m = Store.loadDay(c, T.key(T.addDays(fromDay, i)))
            for ((k, v) in m) out[k] = (out[k] ?: 0L) + v
        }
        return out
    }

    fun daysWithData(c: Context, fromDay: Long, days: Int): Int {
        var n = 0
        for (i in 0 until days) if (Store.loadDay(c, T.key(T.addDays(fromDay, i))).isNotEmpty()) n++
        return n
    }
}

/* ---------- Thông báo ---------- */
object Notif {
    private const val CH = "tau_main"

    fun show(c: Context, id: Int, title: String, text: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Nhắc nhở", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_tau_fg)
            .setContentTitle(title)
            .setContentText(if (text.isEmpty()) "Đến giờ rồi" else text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}

/* ---------- Nhắc sức khoẻ ---------- */
class Hr(val id: Int, val name: String, var msg: String, var on: Boolean, var interval: Int, var start: Int, var end: Int)

object Health {
    private fun defaults(): List<Hr> = listOf(
        Hr(0, "Uống nước", "Uống một ly nước nhé.", false, 90, 7 * 60, 22 * 60),
        Hr(1, "Nghỉ mắt", "Nhìn xa 20 giây và chớp mắt vài lần.", false, 40, 8 * 60, 22 * 60),
        Hr(2, "Đứng dậy vận động", "Đứng dậy, đi lại và vươn vai.", false, 60, 8 * 60, 21 * 60),
        Hr(3, "Đi ngủ", "Đến giờ chuẩn bị ngủ, cất điện thoại thôi.", false, 1440, 22 * 60 + 30, 22 * 60 + 30)
    )

    fun load(c: Context): List<Hr> {
        val d = defaults()
        val s = Store.p(c).getString("health", null) ?: return d
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val r = d.firstOrNull { it.id == o.getInt("id") } ?: continue
                r.on = o.getBoolean("on")
                r.interval = o.getInt("iv")
                r.start = o.getInt("s")
                r.end = o.getInt("e")
                r.msg = o.getString("m")
            }
        } catch (e: Exception) {
        }
        return d
    }

    fun save(c: Context, list: List<Hr>) {
        val a = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("on", r.on)
            o.put("iv", r.interval)
            o.put("s", r.start)
            o.put("e", r.end)
            o.put("m", r.msg)
            a.put(o)
        }
        Store.p(c).edit().putString("health", a.toString()).apply()
    }

    fun next(r: Hr, now: Long): Long {
        val day = T.dayStart(now)
        if (r.interval >= 1440) {
            val t = day + r.start * 60000L
            return if (t > now + 1000) t else T.addDays(day, 1) + r.start * 60000L
        }
        val cand = now + r.interval * 60000L
        val ds = T.dayStart(cand)
        val m = ((cand - ds) / 60000L).toInt()
        if (m >= r.start && m <= r.end) return cand
        return if (m < r.start) ds + r.start * 60000L else T.addDays(ds, 1) + r.start * 60000L
    }

    private fun pi(c: Context, id: Int): PendingIntent {
        val i = Intent(c, HealthReceiver::class.java)
        i.putExtra("id", id)
        return PendingIntent.getBroadcast(c, 100 + id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(c: Context, r: Hr) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!r.on) {
            am.cancel(pi(c, r.id))
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(r, System.currentTimeMillis()), pi(c, r.id))
    }

    fun scheduleAll(c: Context) {
        for (r in load(c)) schedule(c, r)
    }
}

class HealthReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val r = Health.load(c).firstOrNull { it.id == id } ?: return
        if (!r.on) return
        Notif.show(c, 200 + id, r.name, r.msg)
        Health.schedule(c, r)
    }
}

/* ---------- Nhắc việc từ mục Ghi chú ---------- */
object Reminders {
    private fun pi(c: Context, id: String, t: String, b: String): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java)
        i.putExtra("id", id)
        i.putExtra("t", t)
        i.putExtra("b", b)
        return PendingIntent.getBroadcast(
            c, 1000 + (id.hashCode() and 0xFFFFF), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun am(c: Context): AlarmManager = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun sync(c: Context, json: String) {
        val old = Store.p(c).getString("rems", "[]") ?: "[]"
        try {
            val a = JSONArray(old)
            for (i in 0 until a.length()) am(c).cancel(pi(c, a.getJSONObject(i).getString("id"), "", ""))
        } catch (e: Exception) {
        }
        val keep = JSONArray()
        try {
            val a = JSONArray(json)
            val now = System.currentTimeMillis()
            val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val w = o.getLong("w")
                if (w <= now) continue
                val id = o.getString("id")
                am(c).setAlarmClock(
                    AlarmManager.AlarmClockInfo(w, show),
                    pi(c, id, o.optString("t", "Nhắc việc"), o.optString("b", ""))
                )
                keep.put(o)
            }
        } catch (e: Exception) {
        }
        Store.p(c).edit().putString("rems", keep.toString()).apply()
    }

    fun restore(c: Context) {
        sync(c, Store.p(c).getString("rems", "[]") ?: "[]")
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        Notif.show(c, 3000 + (id.hashCode() and 0xFFFF), i.getStringExtra("t") ?: "Nhắc việc", i.getStringExtra("b") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Health.scheduleAll(c)
        Reminders.restore(c)
    }
}

/* ---------- Dịch vụ chặn app + ô thời gian nhỏ ---------- */
class BlockerService : AccessibilityService() {
    private var current = ""
    private var lastSaved = ""
    private var home: String? = null
    private var pill: TextView? = null
    private var pillPos = -1
    private val h = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            tickOnce()
            h.postDelayed(this, 5000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        )?.activityInfo?.packageName
        h.post(loop)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        try {
            if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = e.packageName?.toString() ?: return
            if (pkg != lastSaved) {
                lastSaved = pkg
                Store.setLast(this, pkg)
            }
            if (pkg == packageName || pkg == home) {
                current = ""
                hidePill()
                return
            }
            if (packageManager.getLaunchIntentForPackage(pkg) == null) return
            current = pkg
            tickOnce()
        } catch (t: Throwable) {
            Store.setErr(this, t.toString())
        }
    }

    private fun screenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
    }

    private fun tickOnce() {
        try {
            val pkg = current
            if (pkg.isEmpty()) {
                hidePill()
                return
            }
            val now = System.currentTimeMillis()
            if (now < Store.focusEnd(this) && Store.focusApps(this).contains(pkg)) {
                block("Đang tập trung, chưa mở app này được")
                return
            }
            val lim = Store.limitMin(this, pkg)
            val wantPill = Store.p(this).getBoolean("overlay_on", true) && screenOn()
            if (!Usage.hasAccess(this)) {
                hidePill()
                return
            }
            if (lim <= 0 && !wantPill) {
                hidePill()
                return
            }
            val used = Usage.todayMs(this, pkg)
            if (lim > 0 && used / 60000 >= lim) {
                block("Đã hết giới hạn $lim phút hôm nay")
                return
            }
            if (wantPill) showPill(used, lim) else hidePill()
        } catch (t: Throwable) {
            Store.setErr(this, t.toString())
        }
    }

    private fun params(pos: Int): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
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
        lp.y = if (pos >= 2) (72 * d).toInt() else (36 * d).toInt()
        return lp
    }

    private fun showPill(used: Long, lim: Int) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val pos = Store.p(this).getInt("overlay_pos", 0)
        val txt = if (lim > 0) "${T.fmt(used)} / ${lim}p" else T.fmt(used)
        var v = pill
        if (v == null) {
            val d = resources.displayMetrics.density
            val tv = TextView(this)
            tv.setTextColor(Color.WHITE)
            tv.textSize = 12f
            tv.setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
            val g = GradientDrawable()
            g.setColor(Color.parseColor("#B3000000"))
            g.cornerRadius = 14 * d
            tv.background = g
            wm.addView(tv, params(pos))
            pill = tv
            pillPos = pos
            v = tv
        } else if (pillPos != pos) {
            wm.updateViewLayout(v, params(pos))
            pillPos = pos
        }
        v.text = txt
    }

    private fun hidePill() {
        val v = pill ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
        }
        pill = null
    }

    private fun block(msg: String) {
        current = ""
        hidePill()
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        h.removeCallbacks(loop)
        hidePill()
        super.onDestroy()
    }
}
