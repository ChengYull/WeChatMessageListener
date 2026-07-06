package com.example.wechatstats

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.ExportUtils
import com.example.wechatstats.data.StatsRepository
import com.example.wechatstats.data.StatsRow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MemberListActivity : AppCompatActivity() {

    private lateinit var repository: StatsRepository
    private lateinit var adapter: MemberAdapter
    private lateinit var groupName: String
    private var dayStart: Long = -1L
    private var dayEnd: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        groupName = intent.getStringExtra(MainActivity.EXTRA_GROUP_NAME).orEmpty()
        dayStart = intent.getLongExtra(MainActivity.EXTRA_DAY_START, -1L)
        dayEnd = intent.getLongExtra(MainActivity.EXTRA_DAY_END, -1L)
        title = groupName

        repository = StatsRepository(AppDatabase.getDatabase(applicationContext).messageDao())
        adapter = MemberAdapter { member -> openMessages(member) }

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MemberListActivity)
            this.adapter = this@MemberListActivity.adapter
        }

        lifecycleScope.launch {
            val flow = if (dayStart == -1L) repository.membersFlow(groupName)
            else repository.membersFlow(groupName, dayStart, dayEnd)
            flow.collectLatest { adapter.submitList(it) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_delete_export, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
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
                    if (dayStart == -1L) repository.deleteGroup(groupName)
                    else repository.deleteGroup(groupName, dayStart, dayEnd)
                    finish()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun doExport() {
        lifecycleScope.launch {
            val messages = if (dayStart == -1L) repository.getGroupMessages(groupName)
            else repository.getGroupMessages(groupName, dayStart, dayEnd)

            val uri = ExportUtils.exportGroup(this@MemberListActivity, groupName, messages, dayStart, dayEnd)
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_export)))
            } else {
                AlertDialog.Builder(this@MemberListActivity)
                    .setMessage(R.string.export_fail)
                    .setPositiveButton(R.string.dialog_confirm, null)
                    .show()
            }
        }
    }

    private fun openMessages(member: StatsRow) {
        startActivity(
            Intent(this, MessageListActivity::class.java)
                .putExtra(MainActivity.EXTRA_GROUP_NAME, groupName)
                .putExtra(MainActivity.EXTRA_SENDER, member.nickname)
                .putExtra(MainActivity.EXTRA_DAY_START, dayStart)
                .putExtra(MainActivity.EXTRA_DAY_END, dayEnd)
        )
    }
}
