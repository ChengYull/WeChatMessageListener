package com.example.wechatstats

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.DateUtils
import com.example.wechatstats.data.GroupRow
import com.example.wechatstats.data.ImportUtils
import com.example.wechatstats.data.StatsRepository
import kotlinx.coroutines.Job
import java.time.LocalDate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repository: StatsRepository
    private lateinit var adapter: GroupAdapter
    private lateinit var btnOpenListener: Button
    private lateinit var btnClear: Button
    private lateinit var btnImport: Button
    private lateinit var dateAdapter: DateAdapter

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
        }
    }

    private var groupsJob: Job? = null
    private var chartJob: Job? = null
    private var selectedDayStart: Long = -1L
    private var selectedDayEnd: Long = -1L
    private var useAllTime: Boolean = true
    private var lastBuildDate: LocalDate? = null

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
        btnClear = findViewById(R.id.btnClear)

        btnOpenListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btnClear.setOnClickListener {
            lifecycleScope.launch { repository.clear() }
        }

        btnImport = findViewById(R.id.btnImport)
        btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        // 日期 Chip 条
        lastBuildDate = DateUtils.today()
        buildDateChips()
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
        // 跨过 0 点后刷新日期 Chip
        val today = DateUtils.today()
        if (lastBuildDate != today) {
            lastBuildDate = today
            buildDateChips()
            launchGroupsFlow()
            loadChart()
        }
    }

    private fun buildDateChips() {
        val dates = listOf<LocalDate?>(null) + DateUtils.recentDates()
        if (::dateAdapter.isInitialized) {
            dateAdapter.replaceDates(dates, resetSelection = true)
        } else {
            dateAdapter = DateAdapter(dates, 0) { _, date ->
                if (date == null) {
                    useAllTime = true
                } else {
                    useAllTime = false
                    selectedDayStart = DateUtils.dayStartMillis(date)
                    selectedDayEnd = DateUtils.dayEndMillis(date)
                }
                launchGroupsFlow()
                loadChart()
            }
        }
        findViewById<RecyclerView>(R.id.dateChipStrip).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@MainActivity.dateAdapter
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
        val chart = findViewById<StatsChartView>(R.id.mainChart) ?: return
        if (useAllTime) {
            chart.visibility = android.view.View.GONE
            return
        }
        chart.visibility = android.view.View.VISIBLE
        chartJob = lifecycleScope.launch {
            repository.chartFlow(selectedDayStart, selectedDayEnd)
                .collectLatest { points ->
                    chart.setData(points, selectedDayStart, selectedDayEnd)
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
