package com.example.wechatstats

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.StatsRow
import com.example.wechatstats.ui.StatsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: StatsViewModel
    private lateinit var adapter: StatsAdapter
    private lateinit var btnOpenListener: Button
    private lateinit var btnClear: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel = ViewModelProvider(this, StatsViewModel.Factory(application as Application))
            .get(StatsViewModel::class.java)

        adapter = StatsAdapter()
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
            viewModel.clear()
        }

        lifecycleScope.launch {
            viewModel.stats.collectLatest { statsList: List<StatsRow> ->
                adapter.submitList(statsList)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateListenerButton()
    }

    private fun updateListenerButton() {
        val enabled = WeChatNotificationListener.isEnabled(this)
        btnOpenListener.text = if (enabled) {
            getString(R.string.listener_enabled)
        } else {
            getString(R.string.btn_open_listener)
        }
    }
}
