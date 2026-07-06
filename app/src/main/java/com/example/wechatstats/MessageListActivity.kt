package com.example.wechatstats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.StatsRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessageListActivity : AppCompatActivity() {

    private lateinit var repository: StatsRepository
    private lateinit var adapter: MessageAdapter
    private var dayStart: Long = -1L
    private var dayEnd: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        val groupName = intent.getStringExtra(MainActivity.EXTRA_GROUP_NAME).orEmpty()
        val sender = intent.getStringExtra(MainActivity.EXTRA_SENDER).orEmpty()
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
    }
}
