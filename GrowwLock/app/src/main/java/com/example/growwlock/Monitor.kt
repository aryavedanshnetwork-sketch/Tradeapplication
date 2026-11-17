package com.example.growwlock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.ZonedDateTime

object Prefs {
    const val DEFAULT_GROWW_PKG = "com.nextbillion.groww"

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("lock", Context.MODE_PRIVATE)

    fun lockedUntilMs(ctx: Context): Long = get(ctx).getLong("locked_until", 0L)

    fun isLocked(ctx: Context): Boolean = lockedUntilMs(ctx) > System.currentTimeMillis()

    fun growwPackage(ctx: Context): String {
        val p = get(ctx).getString("groww_pkg", DEFAULT_GROWW_PKG) ?: DEFAULT_GROWW_PKG
        return p.trim().ifEmpty { DEFAULT_GROWW_PKG }
    }
}

/** Shared between the blocker service and the screens (same process). */
object AppState {
    @Volatile
    var growwInFront: Boolean = false
}

object Monitor {
    private var token: String? = null

    /** Reads orders once, updates the saved lock, returns a short message to show the user. */
    @Synchronized
    fun checkOnce(ctx: Context): String {
        val p = Prefs.get(ctx)
        val key = p.getString("api_key", "")?.trim() ?: ""
        val secret = p.getString("api_secret", "")?.trim() ?: ""
        if (key.isEmpty() || secret.isEmpty()) return "Enter your API key and secret, then press Save."

        val nowTime = ZonedDateTime.now(LockLogic.IST)
        val orders: List<JSONObject>
        try {
            var t = token ?: Groww.token(key, secret)
            token = t
            orders = try {
                Groww.allOrders(t)
            } catch (e: ApiException) {
                if (e.code == 401 || e.code == 403) {
                    t = Groww.token(key, secret) // token expires daily at 6 AM: log in again
                    token = t
                    Groww.allOrders(t)
                } else {
                    throw e
                }
            }
        } catch (e: Exception) {
            token = null
            val msg = "Error: ${e.message}"
            p.edit().putLong("last_check", System.currentTimeMillis()).putString("last_msg", msg).apply()
            return msg
        }

        val holidays = LockLogic.parseHolidays(p.getString("holidays", "") ?: "")
        val tsIsIst = p.getBoolean("ts_is_ist", false)
        val (computed, ids) = LockLogic.compute(orders, nowTime, holidays, tsIsIst)

        // A lock never gets shorter: buy then cancel keeps you locked.
        val nowMs = System.currentTimeMillis()
        val computedMs = computed?.toInstant()?.toEpochMilli() ?: 0L
        val prevMs = Prefs.lockedUntilMs(ctx).let { if (it > nowMs) it else 0L }
        val finalMs = maxOf(computedMs, prevMs)

        val statuses = orders.map { it.optString("order_status") }.toSortedSet()
        val msg = "Read ${orders.size} orders. Statuses seen: $statuses. Buy orders counting: ${ids.size}."
        p.edit()
            .putLong("locked_until", finalMs)
            .putLong("last_check", nowMs)
            .putString("last_msg", msg)
            .apply()
        return msg
    }
}
