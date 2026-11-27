package com.cuatoi.app

/**
 * Chu kỳ khoá của một app đang bị hạn chế (hết giới hạn hoặc đang tập trung):
 * đếm ngược [countdownMs] trên màn hình chặn, mở khoá đúng [unlockMs], rồi khoá lại,
 * và lặp lại cho đến khi app không còn bị hạn chế.
 *
 * Lớp này không dùng Android để có thể chạy thử độc lập.
 */
class Gate(var countdownMs: Long = 15000L, var unlockMs: Long = 60000L) {
    enum class Phase { FREE, COUNTDOWN, UNLOCKED }

    class State(val phase: Phase, val leftMs: Long)

    private var countdownEnd = 0L
    private var unlockUntil = 0L

    /** Người dùng rời app bị chặn: huỷ lượt đếm ngược đang chạy. Thời gian mở khoá vẫn tính tiếp. */
    fun leave() {
        countdownEnd = 0L
    }

    /**
     * Gọi liên tục (mỗi 250 ms đến 1 s) khi app đang ở màn hình.
     * [restricted]: app đang bị hạn chế. [screenOn]: màn hình đang sáng và đã mở khoá.
     */
    fun step(now: Long, restricted: Boolean, screenOn: Boolean): State {
        if (!restricted) {
            countdownEnd = 0L
            unlockUntil = 0L
            return State(Phase.FREE, 0L)
        }
        if (now < unlockUntil) return State(Phase.UNLOCKED, unlockUntil - now)
        // Màn hình tắt thì không tính giờ chờ, tránh việc tắt màn hình 15 giây để lách.
        if (countdownEnd == 0L || !screenOn) countdownEnd = now + countdownMs
        if (now >= countdownEnd) {
            countdownEnd = 0L
            unlockUntil = now + unlockMs
            return State(Phase.UNLOCKED, unlockMs)
        }
        return State(Phase.COUNTDOWN, countdownEnd - now)
    }
}
