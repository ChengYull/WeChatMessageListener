package com.example.wechatstats

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.GroupRow
import com.example.wechatstats.data.StatsRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repository: StatsRepository
    private lateinit var adapter: GroupAdapter
    private lateinit var btnOpenListener: Button
    private lateinit var btnClear: Button

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

        lifecycleScope.launch {
            repository.groupsFlow().collectLatest { adapter.submitList(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        btnOpenListener.text = if (WeChatNotificationListener.isEnabled(this)) {
            getString(R.string.listener_enabled)
        } else {
            getString(R.string.btn_open_listener)
        }
    }

    private fun openMembers(group: GroupRow) {
        startActivity(
            Intent(this, MemberListActivity::class.java).putExtra(EXTRA_GROUP_NAME, group.groupName)
        )
    }

    companion object {
        const val EXTRA_GROUP_NAME = "extra_group_name"
        const val EXTRA_SENDER = "extra_sender"
    }
}
