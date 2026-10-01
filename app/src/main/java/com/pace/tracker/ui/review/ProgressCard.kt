package com.pace.tracker.ui.review

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.pace.tracker.ui.components.shortDate
import java.io.File
import java.util.Locale
import kotlin.math.abs

/** Everything shown on the shareable weekly card. Null = not logged that week. */
data class ProgressCardData(
    val weekIndex: Int,
    val startDay: Long,
    val endDay: Long,
    val inProgress: Boolean,
    val weightChange: Double?,
    val avgWeight: Double?,
    val lostSoFar: Double?,
    val toLose: Double,
    val status: String?,
    val avgKcal: Int?,
    val targetKcal: Int,
    val avgProtein: Int?,
    val proteinGoal: Int,
    val totalSteps: Int,
    val avgSteps: Int?,
    val workouts: Int,
    val streak: Int,
    val dayNumber: Int,
    val durationDays: Int,
)

object ProgressCard {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private val BG = Color.parseColor("#0D1114")
    private val SURFACE = Color.parseColor("#161B20")
    private val TRACK = Color.parseColor("#28313A")
    private val FG = Color.parseColor("#E6EBEF")
    private val MUTED = Color.parseColor("#8B96A1")
    private val TEAL = Color.parseColor("#4FD1A5")
    private val AMBER = Color.parseColor("#FFB84D")
    private val BLUE = Color.parseColor("#7AB8FF")
    private val PURPLE = Color.parseColor("#C792EA")
    private val PINK = Color.parseColor("#FF8FB1")

    private fun paint(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun n(v: Int) = String.format(Locale.US, "%,d", v)

    fun render(d: ProgressCardData): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        val pad = 72f

        // Header
        c.drawText("PACE  ·  WEEK ${d.weekIndex}" + if (d.inProgress) " SO FAR" else "", pad, 130f,
            paint(TEAL, 40f, bold = true).apply { letterSpacing = 0.12f })
        c.drawText("${d.startDay.shortDate()} – ${d.endDay.shortDate()}", pad, 190f, paint(MUTED, 38f))
        d.status?.let { c.drawText(it, WIDTH - pad, 130f, paint(statusColor(it), 40f, bold = true, align = Paint.Align.RIGHT)) }

        // Hero: weekly weight change
        val change = d.weightChange
        val hero = when {
            change == null -> "—"
            change <= 0 -> String.format(Locale.US, "−%.1f kg", abs(change))
            else -> String.format(Locale.US, "+%.1f kg", change)
        }
        c.drawText(hero, pad, 390f, paint(if (change == null || change <= 0) TEAL else AMBER, 190f, bold = true))
        c.drawText(
            "change in weekly average weight" + (d.avgWeight?.let { String.format(Locale.US, " · now %.1f kg", it) } ?: ""),
            pad, 450f, paint(MUTED, 36f),
        )

        // Progress to goal
        val lost = (d.lostSoFar ?: 0.0).coerceAtLeast(0.0)
        val pct = if (d.toLose > 0) (lost / d.toLose).coerceIn(0.0, 1.0) else 0.0
        c.drawText(
            String.format(Locale.US, "%.1f of %.1f kg lost", lost, d.toLose), pad, 560f, paint(FG, 40f, bold = true),
        )
        c.drawText("${(pct * 100).toInt()}%", WIDTH - pad, 560f, paint(TEAL, 40f, bold = true, align = Paint.Align.RIGHT))
        val bar = RectF(pad, 590f, WIDTH - pad, 622f)
        c.drawRoundRect(bar, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TRACK })
        if (pct > 0) {
            c.drawRoundRect(RectF(bar.left, bar.top, bar.left + (bar.width() * pct).toFloat().coerceAtLeast(32f), bar.bottom),
                16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEAL })
        }

        // 2 × 2 stat tiles
        val gap = 32f
        val tileW = (WIDTH - pad * 2 - gap) / 2
        val tileH = 270f
        val top = 690f
        tile(c, pad, top, tileW, tileH, "Avg calories", d.avgKcal?.let(::n) ?: "—", "target ${n(d.targetKcal)} kcal", AMBER)
        tile(c, pad + tileW + gap, top, tileW, tileH, "Avg protein", d.avgProtein?.let { "$it g" } ?: "—", "goal ${d.proteinGoal} g", PURPLE)
        tile(c, pad, top + tileH + gap, tileW, tileH, "Steps", n(d.totalSteps), d.avgSteps?.let { "avg ${n(it)} / day" } ?: "no steps logged", BLUE)
        tile(c, pad + tileW + gap, top + tileH + gap, tileW, tileH, "Streak", "${d.streak} days",
            "${d.workouts} workout" + (if (d.workouts == 1) "" else "s") + " this week", PINK)

        // Footer
        c.drawText("Day ${d.dayNumber} of ${d.durationDays}", pad, HEIGHT - 80f, paint(FG, 38f, bold = true))
        c.drawText("Pace · adaptive fat-loss tracker", WIDTH - pad, HEIGHT - 80f, paint(MUTED, 32f, align = Paint.Align.RIGHT))
        return bmp
    }

    private fun tile(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, value: String, sub: String, accent: Int) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE })
        c.drawCircle(x + 48f, y + 62f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        c.drawText(label, x + 72f, y + 75f, paint(MUTED, 34f))
        c.drawText(value, x + 36f, y + 175f, paint(FG, 80f, bold = true))
        c.drawText(sub, x + 36f, y + 230f, paint(MUTED, 32f))
    }

    private fun statusColor(status: String) = when {
        status.startsWith("Ahead") || status.startsWith("Goal") -> TEAL
        status.startsWith("On pace") -> BLUE
        status.startsWith("Losing too fast") -> PINK
        status.startsWith("Behind") || status.startsWith("Stalled") -> AMBER
        else -> MUTED
    }

    private fun fileName(d: ProgressCardData) = "pace-week-${d.weekIndex}.png"

    /** Opens the share sheet with the card as a PNG. */
    fun share(context: Context, d: ProgressCardData, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName(d))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Week ${d.weekIndex} with Pace")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share progress card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Saves the card to Pictures/Pace (no storage permission needed on Android 10+). */
    fun saveToGallery(context: Context, d: ProgressCardData, bitmap: Bitmap): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName(d))
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Pace")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
    }
}
