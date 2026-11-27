package com.lumetime.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lumetime.app.R
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.TimeBreakdown
import com.lumetime.app.model.YearProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure text layout values for shareable Glass Poster cards (unit-tested independently).
 */
data class PosterCopy(
    val badge: String,
    val title: String,
    val heroNumber: String,
    val heroUnit: String,
    val subline: String,
    val footerMeta: String
)

/**
 * Renders crisp 1080x1350 Liquid Glass Poster PNGs onto a native Canvas and shares via FileProvider.
 */
object ShareCardGenerator {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1350

    fun buildCountdownCopy(
        item: CountdownItem,
        breakdown: TimeBreakdown,
        formattedDate: String
    ): PosterCopy {
        val badge = "${item.category.label.uppercase(Locale.US)} • " +
            if (breakdown.isCountUp) "COUNT-UP" else "COUNTDOWN"
        val heroNumber: String
        val heroUnit: String
        val subline: String

        when {
            breakdown.isExpired -> {
                heroNumber = "00d"
                heroUnit = "target reached"
                subline = "Completed"
            }
            breakdown.isCountUp -> {
                heroNumber = "+${breakdown.days}d"
                heroUnit = String.format(Locale.US, "%02dh %02dm", breakdown.hours, breakdown.minutes)
                subline = String.format(Locale.US, "+%,d total hours elapsed", breakdown.totalHours)
            }
            else -> {
                heroNumber = "${breakdown.days}d"
                heroUnit = String.format(Locale.US, "%02dh %02dm", breakdown.hours, breakdown.minutes)
                subline = String.format(
                    Locale.US,
                    "%,d hours left • %.0f%% elapsed",
                    breakdown.totalHours,
                    breakdown.progress * 100f
                )
            }
        }

        return PosterCopy(
            badge = badge,
            title = item.title,
            heroNumber = heroNumber,
            heroUnit = heroUnit,
            subline = subline,
            footerMeta = formattedDate
        )
    }

    fun buildYearCopy(progress: YearProgress): PosterCopy {
        return PosterCopy(
            badge = "${progress.year} • YEAR PULSE",
            title = "Day ${progress.todayDayOfYear} of ${progress.totalDays}",
            heroNumber = "${progress.daysLeft}",
            heroUnit = if (progress.daysLeft == 1L) "day left" else "days left",
            subline = String.format(
                Locale.US,
                "%,d hours left • %.1f%% completed",
                progress.hoursLeft,
                progress.percentCompleted
            ),
            footerMeta = "${progress.weeksLeft} weeks remaining in ${progress.year}"
        )
    }

    suspend fun generateCountdownPosterUri(
        context: Context,
        item: CountdownItem,
        palette: ThemeManager.ThemePalette,
        nowMillis: Long = System.currentTimeMillis()
    ): Uri = withContext(Dispatchers.IO) {
        val breakdown = TimeCalculator.calculateBreakdown(item, nowMillis)
        val dateText = SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.getDefault())
            .format(Date(breakdown.effectiveTargetMillis))
        val copy = buildCountdownCopy(item, breakdown, dateText)
        val accent = runCatching { Color.parseColor(item.accentHex) }.getOrDefault(palette.accent)
        val bitmap = renderPosterBitmap(
            context = context,
            palette = palette,
            copy = copy,
            accent = accent,
            progress = if (breakdown.isCountUp) null else breakdown.progress,
            yearProgress = null,
            iconRes = item.resolveIcon().drawableRes,
            note = item.note
        )
        writePosterPng(context, bitmap, "countdown_${item.id.take(8)}.png")
    }

    suspend fun generateYearPosterUri(
        context: Context,
        palette: ThemeManager.ThemePalette,
        nowMillis: Long = System.currentTimeMillis()
    ): Uri = withContext(Dispatchers.IO) {
        val yearProgress = TimeCalculator.computeYearProgress(nowMillis)
        val copy = buildYearCopy(yearProgress)
        val bitmap = renderPosterBitmap(
            context = context,
            palette = palette,
            copy = copy,
            accent = palette.accent,
            progress = yearProgress.progressFraction,
            yearProgress = yearProgress,
            iconRes = R.drawable.ic_sparkle,
            note = null
        )
        writePosterPng(context, bitmap, "year_${yearProgress.year}.png")
    }

    fun launchShareChooser(context: Context, uri: Uri, subject: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.share_chooser_title))
        )
    }

    private fun renderPosterBitmap(
        context: Context,
        palette: ThemeManager.ThemePalette,
        copy: PosterCopy,
        accent: Int,
        progress: Float?,
        yearProgress: YearProgress?,
        iconRes: Int?,
        note: String?
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Ambient background gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                CARD_WIDTH.toFloat(),
                CARD_HEIGHT.toFloat(),
                intArrayOf(palette.bgTop, palette.bgMid, palette.bgBottom),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // 2. Ambient orbs
        val orb1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                220f,
                260f,
                680f,
                ThemeManager.withAlpha(accent, 0x72),
                0x00000000,
                Shader.TileMode.CLAMP
            )
        }
        val orb2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                880f,
                1040f,
                660f,
                ThemeManager.withAlpha(palette.theme.glowSecondary, 0x62),
                0x00000000,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(220f, 260f, 680f, orb1)
        canvas.drawCircle(880f, 1040f, 660f, orb2)

        // 3. Central Specular Liquid Glass Card
        val cardRect = RectF(84f, 120f, CARD_WIDTH - 84f, CARD_HEIGHT - 120f)
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.glassHeroSurface }
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left,
                cardRect.top,
                cardRect.right,
                cardRect.bottom,
                intArrayOf(
                    if (palette.isDark) 0x30FFFFFF else 0x70FFFFFF,
                    if (palette.isDark) 0x0CFFFFFF else 0x20FFFFFF,
                    0x02FFFFFF
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = palette.glassHeroStroke
        }
        canvas.drawRoundRect(cardRect, 58f, 58f, cardFill)
        canvas.drawRoundRect(cardRect, 58f, 58f, sheenPaint)
        canvas.drawRoundRect(cardRect, 58f, 58f, cardStroke)

        // 4. Typography & Crisp Icon Badge
        val boldType = Typeface.create("sans-serif-medium", Typeface.BOLD)
        val regularType = Typeface.create("sans-serif", Typeface.NORMAL)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 32f
            letterSpacing = 0.09f
            typeface = boldType
        }
        canvas.drawText(copy.badge, 152f, 236f, badgePaint)

        // Top-right frosted vector icon badge
        if (iconRes != null) {
            val badgeCx = CARD_WIDTH - 196f
            val badgeCy = 224f
            val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ThemeManager.withAlpha(accent, 0x33)
            }
            val badgeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = ThemeManager.withAlpha(accent, 0x80)
            }
            canvas.drawCircle(badgeCx, badgeCy, 44f, badgeBg)
            canvas.drawCircle(badgeCx, badgeCy, 44f, badgeRim)
            ContextCompat.getDrawable(context, iconRes)?.mutate()?.let { drawable ->
                drawable.setTint(accent)
                val half = 24
                drawable.setBounds(
                    (badgeCx - half).toInt(),
                    (badgeCy - half).toInt(),
                    (badgeCx + half).toInt(),
                    (badgeCy + half).toInt()
                )
                drawable.draw(canvas)
            }
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 62f
            typeface = boldType
        }
        canvas.drawText(ellipsize(copy.title, titlePaint, 670f), 152f, 342f, titlePaint)

        if (!note.isNullOrBlank()) {
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textSecondary
                textSize = 34f
                typeface = regularType
            }
            canvas.drawText(ellipsize(note, notePaint, 760f), 152f, 398f, notePaint)
        }

        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 168f
            typeface = boldType
        }
        val heroY = if (yearProgress != null) 545f else 620f
        canvas.drawText(copy.heroNumber, 152f, heroY, heroPaint)

        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 54f
            typeface = boldType
        }
        canvas.drawText(copy.heroUnit, 156f, heroY + 76f, unitPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = 38f
            typeface = regularType
        }
        canvas.drawText(copy.subline, 156f, heroY + 148f, subPaint)

        // Optional Year Dot Matrix inside Year Poster
        if (yearProgress != null) {
            drawYearDots(canvas, yearProgress, accent, palette.dotFuture, 152f, 750f, 776f, 250f)
        }

        // Progress bar
        if (progress != null) {
            val barY = if (yearProgress != null) 1045f else 945f
            val trackRect = RectF(152f, barY, CARD_WIDTH - 152f, barY + 22f)
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.progressTrack }
            canvas.drawRoundRect(trackRect, 11f, 11f, trackPaint)

            val fillW = (trackRect.width() * progress.coerceIn(0f, 1f)).coerceAtLeast(22f)
            val fillRect = RectF(trackRect.left, trackRect.top, trackRect.left + fillW, trackRect.bottom)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    trackRect.left,
                    0f,
                    trackRect.right,
                    0f,
                    palette.accentEnd,
                    accent,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(fillRect, 11f, 11f, fillPaint)
        }

        // Footer meta + LumeTime watermark
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = 32f
            typeface = regularType
        }
        canvas.drawText(copy.footerMeta, 152f, 1138f, metaPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textTertiary
            textSize = 30f
            typeface = boldType
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("LumeTime", CARD_WIDTH - 152f, 1138f, brandPaint)

        return bitmap
    }

    private fun drawYearDots(
        canvas: Canvas,
        yearProgress: YearProgress,
        accent: Int,
        futureColor: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val cols = 26
        val total = yearProgress.totalDays
        val rows = (total + cols - 1) / cols
        val cellW = width / cols
        val cellH = height / rows
        val radius = minOf(cellW, cellH) * 0.28f
        val passedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val futurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = futureColor }
        val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val todayIdx = (yearProgress.todayDayOfYear - 1).coerceIn(0, total - 1)

        for (i in 0 until total) {
            val cx = left + (i % cols + 0.5f) * cellW
            val cy = top + (i / cols + 0.5f) * cellH
            when {
                i < todayIdx -> canvas.drawCircle(cx, cy, radius, passedPaint)
                i == todayIdx -> canvas.drawCircle(cx, cy, radius * 1.25f, todayPaint)
                else -> canvas.drawCircle(cx, cy, radius * 0.9f, futurePaint)
            }
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun writePosterPng(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
