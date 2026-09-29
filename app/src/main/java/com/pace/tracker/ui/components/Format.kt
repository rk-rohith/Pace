package com.pace.tracker.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val longFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

fun Long.shortDate(): String = LocalDate.ofEpochDay(this).format(shortFmt)
fun Long.longDate(): String = LocalDate.ofEpochDay(this).format(longFmt)
fun Long.dayLabel(): String = LocalDate.ofEpochDay(this).format(dayFmt)

fun Double.kg(): String = String.format(Locale.US, "%.1f kg", this)
fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
fun Double.signedKg(): String = String.format(Locale.US, "%+.1f kg", this)
fun Int.grouped(): String = String.format(Locale.US, "%,d", this)
fun Double.kcal(): String = "${this.roundToInt().grouped()} kcal"
fun Int.kcal(): String = "${this.grouped()} kcal"

/** Parses user decimal input accepting both "." and "," separators. */
fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
