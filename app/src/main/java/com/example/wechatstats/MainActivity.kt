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
            val ids = repository.insertAll(data.records)
            val inserted = ids.count { it != -1L }
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
    private var currentMonth: YearMonth = YearMonth.now()
    private var selectedDayStart: Long = DateUtils.dayStartMillis(DateUtils.today())
    private var selectedDayEnd: Long = DateUtils.dayEndMillis(DateUtils.today())
    private var useAllTime: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        repository = StatsRepository(AppDatabase.getDatabase(applicationContext).messageDao())

        adapter = GroupAdapter(
            onClick = { group -> openMembers(group) },
            onLongClick = { group -> showDeleteGroupDialog(group) }
        )
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
        menuInflater.inflate(R.menu.menu_import_export, menu)
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
        heatmapJob?.cancel()
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

        // 两个 Flow 共用同一个查询，合并到同一个协程中避免竞争
        heatmapJob = lifecycleScope.launch {
            repository.dailyCountsFlow(queryStart, queryEnd)
                .collectLatest { points ->
                    // 过滤出本月数据给月折线图
                    val monthPoints = points.filter {
                        it.bucketStartMillis >= monthStart && it.bucketStartMillis < monthEnd
                    }
                    val total = monthPoints.sumOf { it.count }
                    tvMonthTotal?.text = "${currentMonth.monthValue}月 共 $total 条消息"
                    monthChart?.setData(monthPoints, monthStart, monthEnd, StatsChartView.XLABEL_DATE)
                    // 全部数据（含前一天）给日历图
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

    private fun showDeleteGroupDialog(group: GroupRow) {
        val anchor = findViewById<RecyclerView>(R.id.recyclerViewStats).findViewHolderForAdapterPosition(
            adapter.currentList.indexOf(group)
        )?.itemView
        val popup = android.widget.PopupMenu(this, anchor ?: findViewById(R.id.recyclerViewStats))
        popup.menu.add("导出记录")
        popup.menu.add("删除记录")
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "导出记录" -> {
                    lifecycleScope.launch {
                        val messages = if (useAllTime) {
                            repository.getGroupMessages(group.groupName)
                        } else {
                            repository.getGroupMessages(group.groupName, selectedDayStart, selectedDayEnd)
                        }
                        val uri = ExportUtils.exportGroup(
                            this@MainActivity, group.groupName, messages,
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
                    true
                }
                "删除记录" -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_delete_title)
                        .setMessage("确定删除「${group.groupName}」的所有统计记录？此操作不可恢复。")
                        .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                            lifecycleScope.launch {
                                repository.deleteGroup(group.groupName)
                            }
                        }
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .show()
                    true
                }
                else -> false
            }
        }
        popup.show()
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