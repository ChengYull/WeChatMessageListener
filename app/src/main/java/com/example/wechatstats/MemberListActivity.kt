package com.example.wechatstats

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.StatsRepository
import com.example.wechatstats.data.StatsRow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MemberListActivity : AppCompatActivity() {

    private lateinit var repository: StatsRepository
    private lateinit var adapter: MemberAdapter
    private lateinit var groupName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        groupName = intent.getStringExtra(MainActivity.EXTRA_GROUP_NAME).orEmpty()
        title = groupName

        repository = StatsRepository(AppDatabase.getDatabase(applicationContext).messageDao())
        adapter = MemberAdapter { member -> openMessages(member) }

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MemberListActivity)
            this.adapter = this@MemberListActivity.adapter
        }

        lifecycleScope.launch {
            repository.membersFlow(groupName).collectLatest { adapter.submitList(it) }
        }
    }

    private fun openMessages(member: StatsRow) {
        startActivity(
            Intent(this, MessageListActivity::class.java)
                .putExtra(MainActivity.EXTRA_GROUP_NAME, groupName)
                .putExtra(MainActivity.EXTRA_SENDER, member.nickname)
        )
    }
}
