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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        val groupName = intent.getStringExtra(MainActivity.EXTRA_GROUP_NAME).orEmpty()
        val sender = intent.getStringExtra(MainActivity.EXTRA_SENDER).orEmpty()
        title = "$groupName · $sender"

        repository = StatsRepository(AppDatabase.getDatabase(applicationContext).messageDao())
        adapter = MessageAdapter()

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MessageListActivity)
            this.adapter = this@MessageListActivity.adapter
        }

        lifecycleScope.launch {
            repository.messagesFlow(groupName, sender).collectLatest { adapter.submitList(it) }
        }
    }
}
