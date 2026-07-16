package com.example.wechatstats

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.ExportUtils
import com.example.wechatstats.data.ChartPoint
import com.example.wechatstats.data.ImportUtils
import com.example.wechatstats.data.StatsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessageListActivity : AppCompatActivity() {

    private lateinit var repository: StatsRepository
    private lateinit var adapter: MessageAdapter
    private lateinit var groupName: String
    private lateinit var sender: String
    private var dayStart: Long = -1L
    private var dayEnd: Long = -1L
    private var chartJob: Job? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = ImportUtils.parseImportForMessage(
                this@MessageListActivity, uri, groupName, sender
            )
            if (result.isFailure) {
                AlertDialog.Builder(this@MessageListActivity)
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
            AlertDialog.Builder(this@MessageListActivity)
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

    private fun doImport() {
        importLauncher.launch(arrayOf("application/json", "*/*"))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        groupName = intent.getStringExtra(MainActivity.EXTRA_GROUP_NAME).orEmpty()
        sender = intent.getStringExtra(MainActivity.EXTRA_SENDER).orEmpty()
        dayStart = intent.getLongExtra(MainActivity.EXTRA_DAY_START, -1L)
        dayEnd = intent.getLongExtra(MainActivity.EXTRA_DAY_END, -1L)
        title = "$groupName · $sender"

        repository = StatsRepository(AppDatabase.getDatabase(applicationContext).messageDao())
        adapter = MessageAdapter()

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MessageListActivity)
            this.adapter = this@MessageListActivity.adapter
        }

        lifecycleScope.launch {
            val flow = if (dayStart == -1L) repository.messagesFlow(groupName, sender)
            else repository.messagesFlow(groupName, sender, dayStart, dayEnd)
            flow.collectLatest { adapter.submitList(it) }
        }

        loadChart()
    }

    private fun loadChart() {
        val chart = findViewById<StatsChartView>(R.id.detailChart) ?: return
        if (dayStart == -1L) {
            chart.visibility = android.view.View.GONE
            return
        }
        chart.visibility = android.view.View.VISIBLE
        chartJob?.cancel()
        chartJob = lifecycleScope.launch {
            repository.chartFlow(dayStart, dayEnd, groupName, sender)
                .collectLatest { points ->
                    chart.setData(points, dayStart, dayEnd)
                }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_delete_export, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import -> {
                doImport()
                true
            }
            R.id.action_delete -> {
                showDeleteConfirmDialog()
                true
            }
            R.id.action_export -> {
                doExport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                lifecycleScope.launch {
                    if (dayStart == -1L) repository.deleteSender(groupName, sender)
                    else repository.deleteSender(groupName, sender, dayStart, dayEnd)
                    finish()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun doExport() {
        lifecycleScope.launch {
            val messages = if (dayStart == -1L) repository.getSenderMessages(groupName, sender)
            else repository.getSenderMessages(groupName, sender, dayStart, dayEnd)

            val uri = ExportUtils.exportSender(
                this@MessageListActivity, groupName, sender, messages, dayStart, dayEnd
            )
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_export)))
            } else {
                AlertDialog.Builder(this@MessageListActivity)
                    .setMessage(R.string.export_fail)
                    .setPositiveButton(R.string.dialog_confirm, null)
                    .show()
            }
        }
    }
}
