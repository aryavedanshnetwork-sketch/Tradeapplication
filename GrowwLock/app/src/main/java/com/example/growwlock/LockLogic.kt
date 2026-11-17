package com.example.growwlock

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** Same rules as groww_limit.py: 1 BUY locks you until 9:15 AM IST on the next trading day. */
object LockLogic {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val OPEN: LocalTime = LocalTime.of(9, 15)
    private val CLOSE: LocalTime = LocalTime.of(15, 30)

    // Orders in these states do NOT use up your daily buy.
    val IGNORED = setOf("REJECTED", "CANCELLED", "CANCELED", "FAILED")

    fun isTradingDay(d: LocalDate, holidays: Set<LocalDate>): Boolean =
        d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && !holidays.contains(d)

    fun nextTradingDay(d: LocalDate, holidays: Set<LocalDate>): LocalDate {
        var x = d.plusDays(1)
        while (!isTradingDay(x, holidays)) x = x.plusDays(1)
        return x
    }

    /** Before 3:30 PM on a trading day -> that day. After close, weekend or holiday (AMO) -> next trading day. */
    fun tradingDayOf(created: ZonedDateTime, holidays: Set<LocalDate>): LocalDate {
        val d = created.toLocalDate()
        return if (isTradingDay(d, holidays) && created.toLocalTime().isBefore(CLOSE)) d
        else nextTradingDay(d, holidays)
    }

    fun unlockTime(created: ZonedDateTime, holidays: Set<LocalDate>): ZonedDateTime =
        ZonedDateTime.of(nextTradingDay(tradingDayOf(created, holidays), holidays), OPEN, IST)

    fun parseTs(s: String, tsIsIst: Boolean): ZonedDateTime {
        val odt: OffsetDateTime? = try {
            OffsetDateTime.parse(s)
        } catch (e: DateTimeParseException) {
            null
        }
        if (odt == null) return LocalDateTime.parse(s).atZone(IST)
        return if (tsIsIst) odt.toLocalDateTime().atZone(IST) else odt.atZoneSameInstant(IST)
    }

    fun parseHolidays(text: String): Set<LocalDate> {
        val out = HashSet<LocalDate>()
        for (raw in text.lines()) {
            val line = raw.substringBefore("#").trim()
            if (line.isEmpty()) continue
            try {
                out.add(LocalDate.parse(line))
            } catch (e: DateTimeParseException) {
                // ignore lines that are not a valid date
            }
        }
        return out
    }

    /** Returns the time you are locked until (null if not locked) and the order ids behind it. */
    fun compute(
        orders: List<JSONObject>,
        now: ZonedDateTime,
        holidays: Set<LocalDate>,
        tsIsIst: Boolean
    ): Pair<ZonedDateTime?, List<String>> {
        var lockedUntil: ZonedDateTime? = null
        val ids = ArrayList<String>()
        for (o in orders) {
            if (!o.optString("transaction_type").equals("BUY", ignoreCase = true)) continue
            if (IGNORED.contains(o.optString("order_status").uppercase())) continue
            val ts = o.optString("created_at")
            if (ts.isEmpty()) continue
            val unlock = try {
                unlockTime(parseTs(ts, tsIsIst), holidays)
            } catch (e: Exception) {
                continue
            }
            if (unlock.isAfter(now)) {
                ids.add(o.optString("groww_order_id"))
                val cur = lockedUntil
                if (cur == null || unlock.isAfter(cur)) lockedUntil = unlock
            }
        }
        return Pair(lockedUntil, ids)
    }
}
