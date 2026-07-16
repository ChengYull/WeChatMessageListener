package com.example.wechatstats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.wechatstats.data.ChartPoint
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarHeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: Map<Long, Int> = emptyMap() // dayStartMillis -> count
    private var yearMonth: YearMonth = YearMonth.now()

    private var density: Float = 1f
    private var cellSize = 0f
    private var cellGap = 0f
    private var cornerRadius = 0f
    private var headerHeight = 0f
    private var labelWidth = 0f
    private var dayLabelWidth = 0f
    private var contentWidth = 0f
    private var contentLeft = 0f

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cellStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0xFFE0E0E0.toInt()
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val emptyCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    // 年月切换监听
    var onMonthChange: ((YearMonth) -> Unit)? = null

    fun setData(points: List<ChartPoint>, yearMonth: YearMonth) {
        this.yearMonth = yearMonth
        data = points.associate { it.bucketStartMillis to it.count }
        requestLayout()
        invalidate()
    }

    fun getCurrentMonth(): YearMonth = yearMonth

    fun previousMonth() {
        yearMonth = yearMonth.minusMonths(1)
        onMonthChange?.invoke(yearMonth)
    }

    fun nextMonth() {
        yearMonth = yearMonth.plusMonths(1)
        onMonthChange?.invoke(yearMonth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        density = resources.displayMetrics.density
        cellSize = 36f * density
        cellGap = 4f * density
        cornerRadius = 4f * density
        headerHeight = 24f * density
        labelWidth = 32f * density
        dayLabelWidth = 20f * density

        val labelSize = 11f * density
        val countSize = 9f * density

        textPaint.apply {
            color = 0xFF333333.toInt()
            textSize = labelSize
            textAlign = Paint.Align.CENTER
        }
        countPaint.apply {
            color = 0xFF666666.toInt()
            textSize = countSize
            textAlign = Paint.Align.CENTER
        }
        headerPaint.apply {
            color = 0xFF999999.toInt()
            textSize = labelSize
            textAlign = Paint.Align.CENTER
        }

        contentLeft = labelWidth + dayLabelWidth + cellGap
        contentWidth = w.toFloat() - contentLeft - cellGap
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val firstDayOfWeek = getDayOfWeek(yearMonth.atDay(1))
        val daysInMonth = yearMonth.lengthOfMonth()
        val totalRows = ((firstDayOfWeek + daysInMonth + 6) / 7).coerceAtLeast(1)
        val totalHeight = (headerHeight + totalRows * (cellSize + cellGap) + cellGap + 24f * density).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            totalHeight.coerceAtLeast(200)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val cellTotal = cellSize + cellGap

        // 月份标题
        headerPaint.color = 0xFF333333.toInt()
        headerPaint.textSize = 14f * density
        headerPaint.textAlign = Paint.Align.LEFT
        val title = "${yearMonth.year}年${yearMonth.monthValue}月"
        canvas.drawText(title, contentLeft, headerHeight, headerPaint)

        // 左右箭头
        headerPaint.textSize = 16f * density
        headerPaint.color = 0xFFFF5722.toInt()
        val arrowY = headerHeight - 2f * density
        canvas.drawText("<", 8f * density, arrowY, headerPaint)
        canvas.drawText(">", w - 16f * density, arrowY, headerPaint)

        // 星期表头（日 一 二 三 四 五 六）
        headerPaint.textSize = 11f * density
        headerPaint.color = 0xFF999999.toInt()
        headerPaint.textAlign = Paint.Align.CENTER
        val headerY = headerHeight + 16f * density
        for (i in 0..6) {
            val x = contentLeft + i * cellTotal + cellSize / 2f
            canvas.drawText(WEEKDAY_LABELS[i], x, headerY, headerPaint)
        }

        // 计算网格起始 Y
        val gridTop = headerY + 8f * density
        val dayCounts = data
        val firstDay = yearMonth.atDay(1)
        val firstDayOfWeek = getDayOfWeek(firstDay) // 0=周日
        val daysInMonth = yearMonth.lengthOfMonth()
        val today = LocalDate.now()

        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            val row = (firstDayOfWeek + day - 1) / 7
            val col = (firstDayOfWeek + day - 1) % 7
            val cx = contentLeft + col * cellTotal
            val cy = gridTop + row * cellTotal

            val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val count = dayCounts[dayStart] ?: 0

            val cellRect = RectF(cx, cy, cx + cellSize, cy + cellSize)

            // 计算与前一天的比较
            val prevDate = date.minusDays(1)
            val prevDayStart = prevDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val prevCount = dayCounts[prevDayStart]

            if (count == 0) {
                // 无消息：白色背景 + 灰色边框 + 黑色日期数字
                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, emptyCellPaint)
                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, cellStrokePaint)

                textPaint.color = 0xFF333333.toInt()
                textPaint.textSize = 11f * density
                val dateStr = day.toString()
                canvas.drawText(dateStr, cx + cellSize / 2f, cy + cellSize * 0.6f, textPaint)
            } else {
                // 有消息：根据比较结果着色
                val bgColor = when {
                    prevCount == null || prevCount == 0 -> COLOR_RED
                    count > prevCount -> COLOR_RED
                    count < prevCount -> COLOR_GREEN
                    else -> COLOR_GREEN // 持平
                }
                cellPaint.color = bgColor
                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, cellPaint)

                // 日期数字（白色）
                textPaint.color = 0xFFFFFFFF.toInt()
                textPaint.textSize = 11f * density
                val dateStr = day.toString()
                canvas.drawText(dateStr, cx + cellSize / 2f, cy + cellSize * 0.45f, textPaint)

                // 差值（与前一天的对比）
                countPaint.color = 0xCCFFFFFF.toInt()
                countPaint.textSize = 9f * density
                val diffStr = if (prevCount == null) {
                    "+$count"
                } else if (prevCount == 0) {
                    "+$count"
                } else {
                    val diff = count - prevCount
                    if (diff > 0) "+$diff" else diff.toString()
                }
                canvas.drawText(diffStr, cx + cellSize / 2f, cy + cellSize * 0.8f, countPaint)
            }

            // 今日标记：加粗边框
            if (date == today) {
                cellPaint.color = 0xFFFF5722.toInt()
                cellPaint.style = Paint.Style.STROKE
                cellPaint.strokeWidth = 2.5f * density
                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, cellPaint)
                cellPaint.style = Paint.Style.FILL
            }
        }

        // 左侧日期指示
        headerPaint.textSize = 10f * density
        headerPaint.color = 0xFF999999.toInt()
        headerPaint.textAlign = Paint.Align.RIGHT
        for (day in 1..daysInMonth step 7) {
            val date = yearMonth.atDay(day)
            val row = (firstDayOfWeek + day - 1) / 7
            val cy = gridTop + row * cellTotal + cellSize / 2f + 4f * density
            val label = date.dayOfMonth.toString()
            canvas.drawText(label, contentLeft - 4f * density, cy, headerPaint)
        }
    }

    /** 返回 0=周日, 1=周一, ..., 6=周六 */
    private fun getDayOfWeek(date: LocalDate): Int {
        return date.dayOfWeek.value % 7
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val y = event.y
            val x = event.x
            if (y <= headerHeight + 16f * density) {
                val mid = width / 2f
                if (x < mid) {
                    previousMonth()
                } else {
                    nextMonth()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val COLOR_RED = 0xFFFF6B6B.toInt()
        private const val COLOR_GREEN = 0xFF81C784.toInt()
        private val WEEKDAY_LABELS = arrayOf("日", "一", "二", "三", "四", "五", "六")
    }
}