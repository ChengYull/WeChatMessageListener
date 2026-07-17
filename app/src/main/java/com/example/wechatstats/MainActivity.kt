package com.example.wechatstats

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.DateUtils
import com.example.wechatstats.data.ExportUtils
import com.example.wechatstats.data.GroupRow
import com.example.wechatstats.data.ImportUtils
import com.example.wechatstats.data.StatsRepository
import kotlinx.coroutines.Job
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repository: StatsRepository
    private lateinit var adapter: GroupAdapter
    private lateinit var btnOpenListener: Button
    private lateinit var btnBackToAll: Button

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = ImportUtils.parseImportForGroupList(
                this@MainActivity, uri
            )
            if (result.isFailure) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.import_fail)
                    .setMessage(result.exceptionOrNull()?.message)
                    .setPositiveButton(R.string.dialog_confirm, null)
                    .show()
                return@launch
            }
            val data = result.getOrThrow()
            var inserted = 0
            for (record in data.records) {
                val id = repository.insert(record)
                if (id != -1L) inserted++
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.menu_import)
                .setMessage(
                    getString(
                        R.string.import_success,
                        data.records.size,
                        inserted,
                        data.records.size - inserted
                    )
                )
                .setPositiveButton(R.string.dialog_confirm, null)
                .show()
            if (useAllTime) loadHeatmap()
        }
    }

    private var groupsJob: Job? = null
    private var chartJob: Job? = null
    private var heatmapJob: Job? = null
    private var monthChartJob: Job? = null
    private var currentMonth: YearMonth = YearMonth.now()
    private var selectedDayStart: Long = DateUtils.dayStartMillis(DateUtils.today())
    private var selectedDayEnd: Long = DateUtils.dayEndMillis(DateUtils.today())
    private var useAllTime: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        repository = StatsRepository(AppDatabase.getDatabase(applicationContext).messageDao())

        adapter = GroupAdapter { group -> openMembers(group) }
        findViewById<RecyclerView>(R.id.recyclerViewStats).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = this@MainActivity.adapter
        }

        btnOpenListener = findViewById(R.id.btnOpenListener)
        btnBackToAll = findViewById(R.id.btnBackToAll)

        btnOpenListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btnBackToAll.setOnClickListener {
            useAllTime = true
            launchGroupsFlow()
            loadChart()
        }

        launchGroupsFlow()
        loadChart()
    }

    override fun onResume() {
        super.onResume()
        btnOpenListener.text = if (WeChatNotificationListener.isEnabled(this)) {
            getString(R.string.listener_enabled)
        } else {
            getString(R.string.btn_open_listener)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_delete_export, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import -> {
                importLauncher.launch(arrayOf("application/json", "*/*"))
                true
            }
            R.id.action_export -> {
                doExport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun launchGroupsFlow() {
        groupsJob?.cancel()
        groupsJob = lifecycleScope.launch {
            val flow = if (useAllTime) repository.groupsFlow()
            else repository.groupsFlow(selectedDayStart, selectedDayEnd)
            flow.collectLatest { adapter.submitList(it) }
        }
    }

    private fun loadChart() {
        chartJob?.cancel()
        monthChartJob?.cancel()
        val chart = findViewById<StatsChartView>(R.id.mainChart) ?: return
        val heatmap = findViewById<CalendarHeatmapView>(R.id.calendarHeatmap) ?: return
        val tvMonthTotal = findViewById<TextView>(R.id.tvMonthTotal)
        val monthChart = findViewById<StatsChartView>(R.id.monthChart)

        if (useAllTime) {
            chart.visibility = android.view.View.GONE
            btnBackToAll.visibility = android.view.View.GONE
            heatmap.visibility = android.view.View.VISIBLE
            tvMonthTotal?.visibility = android.view.View.VISIBLE
            monthChart?.visibility = android.view.View.VISIBLE
            loadHeatmap()
            return
        }
        chart.visibility = android.view.View.VISIBLE
        btnBackToAll.visibility = android.view.View.VISIBLE
        heatmap.visibility = android.view.View.GONE
        tvMonthTotal?.visibility = android.view.View.GONE
        monthChart?.visibility = android.view.View.GONE
        chartJob = lifecycleScope.launch {
            repository.chartFlow(selectedDayStart, selectedDayEnd)
                .collectLatest { points ->
                    chart.setData(points, selectedDayStart, selectedDayEnd)
                }
        }
    }

    private fun loadHeatmap() {
        heatmapJob?.cancel()
        monthChartJob?.cancel()
        val heatmap = findViewById<CalendarHeatmapView>(R.id.calendarHeatmap) ?: return
        val tvMonthTotal = findViewById<TextView>(R.id.tvMonthTotal)
        val monthChart = findViewById<StatsChartView>(R.id.monthChart)

        val monthStart = DateUtils.dayStartMillis(currentMonth.atDay(1))
        val monthEnd = DateUtils.dayStartMillis(currentMonth.plusMonths(1).atDay(1))

        val queryStart = DateUtils.dayStartMillis(currentMonth.atDay(1).minusDays(1))
        val queryEnd = monthEnd

        // 月消息总数 + 月折线图
        tvMonthTotal?.visibility = android.view.View.VISIBLE
        monthChart?.visibility = android.view.View.VISIBLE
        monthChartJob = lifecycleScope.launch {
            repository.dailyCountsFlow(monthStart, monthEnd)
                .collectLatest { points ->
                    val total = points.sumOf { it.count }
                    tvMonthTotal?.text = "${currentMonth.monthValue}月 共 $total 条消息"
                    monthChart?.setData(points, monthStart, monthEnd, StatsChartView.XLABEL_DATE)
                }
        }

        heatmap.onMonthChange = { newMonth ->
            currentMonth = newMonth
            loadHeatmap()
        }

        heatmap.onDateClicked = { date ->
            useAllTime = false
            selectedDayStart = DateUtils.dayStartMillis(date)
            selectedDayEnd = DateUtils.dayEndMillis(date)
            launchGroupsFlow()
            loadChart()
        }

        heatmapJob = lifecycleScope.launch {
            repository.dailyCountsFlow(queryStart, queryEnd)
                .collectLatest { points ->
                    heatmap.setData(points, currentMonth)
                }
        }
    }

    private fun doExport() {
        lifecycleScope.launch {
            val allMessages = if (useAllTime) {
                repository.getAllMessages()
            } else {
                repository.getAllMessages(selectedDayStart, selectedDayEnd)
            }
            val uri = ExportUtils.exportGroup(
                this@MainActivity, "", allMessages,
                if (useAllTime) -1L else selectedDayStart,
                if (useAllTime) -1L else selectedDayEnd
            )
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_export)))
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(R.string.export_fail)
                    .setPositiveButton(R.string.dialog_confirm, null)
                    .show()
            }
        }
    }

    private fun openMembers(group: GroupRow) {
        startActivity(
            Intent(this, MemberListActivity::class.java)
                .putExtra(EXTRA_GROUP_NAME, group.groupName)
                .putExtra(EXTRA_DAY_START, if (useAllTime) -1L else selectedDayStart)
                .putExtra(EXTRA_DAY_END, if (useAllTime) -1L else selectedDayEnd)
        )
    }

    companion object {
        const val EXTRA_GROUP_NAME = "extra_group_name"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_DAY_START = "extra_day_start"
        const val EXTRA_DAY_END = "extra_day_end"
    }
}