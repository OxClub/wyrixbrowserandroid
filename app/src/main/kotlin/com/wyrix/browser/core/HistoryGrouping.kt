package com.wyrix.browser.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Groups items by local calendar day, newest day first, preserving item order within a day. */
fun <T> groupByDay(items: List<T>, zone: ZoneId = ZoneId.systemDefault(), timestamp: (T) -> Long): List<Pair<LocalDate, List<T>>> {
    val out = LinkedHashMap<LocalDate, MutableList<T>>()
    for (item in items.sortedByDescending(timestamp)) {
        val day = Instant.ofEpochMilli(timestamp(item)).atZone(zone).toLocalDate()
        out.getOrPut(day) { ArrayList() }.add(item)
    }
    return out.map { it.key to it.value }
}
