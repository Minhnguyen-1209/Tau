package com.cuatoi.app

/**
 * Chu kỳ khoá của một app đang bị hạn chế (hết giới hạn, tập trung, giờ ngủ, khung giờ chặn):
 * đếm ngược [countdownMs] trên màn hình chặn, mở khoá đúng [unlockMs] SỬ DỤNG THỰC TẾ, rồi khoá lại,
 * lặp lại cho đến khi app không còn bị hạn chế.
 *
 * Cả hai giờ đều tính theo thời gian app thật sự ở trên màn hình (cộng dồn từng nhịp gọi [step]),
 * không tính theo đồng hồ treo tường: rời app, tắt màn hình hay dịch vụ bị treo thì không bị mất giờ mở khoá.
 *
 * Lớp này không dùng Android để có thể chạy thử độc lập.
 */
class Gate(var countdownMs: Long = 15000L, var unlockMs: Long = 60000L) {
    enum class Phase { FREE, COUNTDOWN, UNLOCKED }

    class State(val phase: Phase, val leftMs: Long)

    private var cdLeft = -1L
    private var unLeft = 0L
    private var last = 0L
    private var freeAcc = 0L
    private var prev = State(Phase.FREE, 0L)

    /** Người dùng rời app bị chặn: huỷ lượt đếm ngược đang chạy. Giờ mở khoá còn lại được giữ nguyên. */
    fun leave() {
        cdLeft = -1L
        last = 0L
    }

    /**
     * Gọi liên tục (mỗi 250 ms đến 1 s) khi app đang ở màn hình.
     * [restricted]: app đang bị hạn chế. [screenOn]: màn hình đang sáng và đã mở khoá.
     */
    fun step(now: Long, restricted: Boolean, screenOn: Boolean): State {
        // Chỉ cộng thời gian khi các lần gọi liền nhau; khoảng trống lớn nghĩa là app đã không ở màn hình
        val dt = if (last > 0L && now - last in 0L..5000L) Math.min(now - last, 2000L) else 0L
        last = now
        if (!restricted) {
            // Chống nhấp nháy: số liệu thoáng sai 1-2 giây không được làm mất trạng thái khoá
            freeAcc += if (dt == 0L) 500L else dt
            if (freeAcc < 3000L && (cdLeft >= 0L || unLeft > 0L)) return prev
            cdLeft = -1L
            unLeft = 0L
            prev = State(Phase.FREE, 0L)
            return prev
        }
        freeAcc = 0L
        if (!screenOn) {
            cdLeft = -1L
            last = 0L
            prev = State(Phase.COUNTDOWN, countdownMs)
            return prev
        }
        if (unLeft > 0L) {
            unLeft -= dt
            if (unLeft > 0L) {
                prev = State(Phase.UNLOCKED, unLeft)
                return prev
            }
            unLeft = 0L
            cdLeft = -1L
        }
        if (cdLeft < 0L) cdLeft = countdownMs else cdLeft -= dt
        if (cdLeft <= 0L) {
            cdLeft = -1L
            unLeft = unlockMs
            prev = State(Phase.UNLOCKED, unlockMs)
            return prev
        }
        prev = State(Phase.COUNTDOWN, cdLeft)
        return prev
    }
}
