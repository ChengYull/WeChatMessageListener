package com.example.wechatstats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.wechatstats.data.ChartPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class StatsChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<ChartPoint> = emptyList()
    private var dayStart: Long = 0L
    private var dayEnd: Long = 0L
    private var maxCount: Int = 0
    private var xLabelMode: Int = XLABEL_HOUR

    // 触摸状态
    private var touchedIndex: Int = -1
    private val touchSlop = 30f // dp

    // 画笔
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDDDDDD.toInt()
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF999999.toInt()
        strokeWidth = 1f
    }
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5722.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isDither = true
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FF5722.toInt()
        style = Paint.Style.FILL
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = 28f // sp -> px 在 onDraw 中按 density 换算
    }
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5722.toInt()
        style = Paint.Style.FILL
    }
    private val tooltipTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5722.toInt()
        strokeWidth = 2f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5722.toInt()
        style = Paint.Style.FILL
    }

    // 缓存
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
    private val labelBounds = Rect()
    private var density: Float = 1f
    private var paddingLeft = 0f
    private var paddingRight = 0f
    private var paddingTop = 0f
    private var paddingBottom = 0f
    private var chartLeft = 0f
    private var chartRight = 0f
    private var chartTop = 0f
    private var chartBottom = 0f
    private var chartWidth = 0f
    private var chartHeight = 0f

    fun setData(points: List<ChartPoint>, start: Long, end: Long, labelMode: Int = XLABEL_HOUR) {
        data = points
        dayStart = start
        dayEnd = end
        maxCount = points.maxOfOrNull { it.count } ?: 0
        touchedIndex = -1
        xLabelMode = labelMode
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        density = resources.displayMetrics.density
        val labelSize = 12f * density
        labelPaint.textSize = labelSize
        tooltipTextPaint.textSize = labelSize

        paddingLeft = 44f * density
        paddingRight = 16f * density
        paddingTop = 16f * density
        paddingBottom = 32f * density

        chartLeft = paddingLeft
        chartRight = w.toFloat() - paddingRight
        chartTop = paddingTop
        chartBottom = h.toFloat() - paddingBottom
        chartWidth = chartRight - chartLeft
        chartHeight = chartBottom - chartTop
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chartWidth <= 0 || chartHeight <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()

        // 无数据时显示提示
        if (data.isEmpty()) {
            val msg = if (maxCount == 0 && dayStart > 0) "暂无数据" else ""
            if (msg.isNotEmpty()) {
                labelPaint.color = 0xFF999999.toInt()
                val textWidth = labelPaint.measureText(msg)
                canvas.drawText(msg, (w - textWidth) / 2f, h / 2f, labelPaint)
                labelPaint.color = 0xFF666666.toInt()
            }
            return
        }

        // ── 网格 & Y 轴 ──
        val gridLines = 4
        val yMax = if (maxCount <= 1) 1 else maxCount
        for (i in 0..gridLines) {
            val y = chartBottom - chartHeight * i / gridLines
            // 水平虚线
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            // Y 轴标签
            val label = ((yMax * i / gridLines).coerceAtLeast(1)).toString()
            labelPaint.getTextBounds(label, 0, label.length, labelBounds)
            canvas.drawText(label, chartLeft - labelBounds.width() - 4f * density,
                y + labelBounds.height() / 2f, labelPaint)
        }

        // ── X 轴标签 ──
        val duration = dayEnd - dayStart
        if (duration > 0) {
            if (xLabelMode == XLABEL_HOUR) {
                // 6 小时间隔（日折线图）
                for (hour in 0..24 step 6) {
                    val fraction = hour / 24f
                    val x = chartLeft + chartWidth * fraction
                    val timeStr = String.format("%02d:00", hour)
                    val tw = labelPaint.measureText(timeStr)
                    canvas.drawText(timeStr, x - tw / 2f, h - 8f * density, labelPaint)
                    canvas.drawLine(x, chartBottom, x, chartBottom + 6f * density, axisPaint)
                }
            } else {
                // 日期间隔（月折线图）
                val dayCount = (duration / 86400000).toInt().coerceAtLeast(1)
                val interval = when {
                    dayCount <= 7 -> 1
                    dayCount <= 14 -> 2
                    dayCount <= 31 -> 5
                    else -> 7
                }
                for (day in 0..dayCount step interval) {
                    val fraction = day.toFloat() / dayCount
                    val x = chartLeft + chartWidth * fraction
                    val date = Date(dayStart + day * 86400000L)
                    val dateStr = dateFmt.format(date)
                    val tw = labelPaint.measureText(dateStr)
                    canvas.drawText(dateStr, x - tw / 2f, h - 8f * density, labelPaint)
                    canvas.drawLine(x, chartBottom, x, chartBottom + 6f * density, axisPaint)
                }
            }
        }

        // ── 曲线 & 填充 ──
        val durationMs = (dayEnd - dayStart).toFloat()
        val points = data.map { p ->
            val x = chartLeft + ((p.bucketStartMillis - dayStart) / durationMs) * chartWidth
            val y = chartBottom - (p.count.toFloat() / yMax) * chartHeight
            x to y
        }

        if (points.size >= 2) {
            val linePath = Path()
            val fillPath = Path()
            linePath.moveTo(points[0].first, points[0].second)
            fillPath.moveTo(points[0].first, chartBottom)
            fillPath.lineTo(points[0].first, points[0].second)

            for (i in 1 until points.size) {
                // 使用直线连接（每 5 分钟一个点，折线足够平滑）
                linePath.lineTo(points[i].first, points[i].second)
                fillPath.lineTo(points[i].first, points[i].second)
            }
            fillPath.lineTo(points.last().first, chartBottom)
            fillPath.close()

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(linePath, curvePaint)

            // 画数据点小圆
            for (p in points) {
                canvas.drawCircle(p.first, p.second, 3f * density, dotPaint)
            }
        } else if (points.size == 1) {
            // 只有一个数据点，画一个点加水平线
            val x = points[0].first
            val y = points[0].second
            canvas.drawLine(chartLeft, y, chartRight, y, curvePaint)
            canvas.drawCircle(x, y, 3f * density, dotPaint)
        }

        // ── 触摸指示线 ──
        if (touchedIndex in data.indices) {
            val pt = points[touchedIndex]
            val point = data[touchedIndex]

            // 竖线
            canvas.drawLine(pt.first, chartTop, pt.first, chartBottom, indicatorPaint)
            // 圆点
            canvas.drawCircle(pt.first, pt.second, 6f * density, dotPaint)

            // 气泡
            val tooltipTime = if (xLabelMode == XLABEL_HOUR) {
                timeFmt.format(Date(point.bucketStartMillis))
            } else {
                dateFmt.format(Date(point.bucketStartMillis))
            }
            val tooltipText = "$tooltipTime  ${point.count} 条"
            val tooltipWidth = tooltipTextPaint.measureText(tooltipText) + 16f * density
            val tooltipHeight = 28f * density
            val tooltipX: Float
            val tooltipY = paddingTop / 2f

            tooltipX = if (pt.first + tooltipWidth / 2f > chartRight) {
                chartRight - tooltipWidth
            } else if (pt.first - tooltipWidth / 2f < chartLeft) {
                chartLeft
            } else {
                pt.first - tooltipWidth / 2f
            }

            val bgRect = RectF(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight)
            canvas.drawRoundRect(bgRect, 6f * density, 6f * density, tooltipBgPaint)
            canvas.drawText(tooltipText,
                bgRect.centerX() - tooltipTextPaint.measureText(tooltipText) / 2f,
                bgRect.centerY() + tooltipTextPaint.textSize / 2f - 2f * density,
                tooltipTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val touchX = event.x
                val durationMs = (dayEnd - dayStart).toFloat()
                if (durationMs <= 0 || data.isEmpty()) return false

                // 找到最近的数据点
                val touchBucket = dayStart + ((touchX - chartLeft) / chartWidth * durationMs).toLong()
                var bestIdx = 0
                var bestDist = Long.MAX_VALUE
                for ((i, p) in data.withIndex()) {
                    val dist = abs(p.bucketStartMillis - touchBucket)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestIdx = i
                    }
                }
                // 转换为像素距离验证是否在 touchSlop 内
                val bestX = chartLeft + ((data[bestIdx].bucketStartMillis - dayStart) / durationMs) * chartWidth
                if (abs(touchX - bestX) > touchSlop * density) {
                    if (touchedIndex >= 0) {
                        touchedIndex = -1
                        invalidate()
                    }
                    return false
                }
                if (bestIdx != touchedIndex) {
                    touchedIndex = bestIdx
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (touchedIndex >= 0) {
                    touchedIndex = -1
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        const val XLABEL_HOUR = 0
        const val XLABEL_DATE = 1
    }
}
