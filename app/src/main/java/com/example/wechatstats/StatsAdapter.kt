package com.example.wechatstats

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.StatsRow

class StatsAdapter : ListAdapter<StatsRow, StatsAdapter.StatsViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stats, parent, false)
        return StatsViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatsViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvNickname.text = "${position + 1}. ${item.nickname}"
        holder.tvCount.text = "${item.count} 次"
    }

    class StatsViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvNickname: TextView = view.findViewById(R.id.tvNickname)
        val tvCount: TextView = view.findViewById(R.id.tvCount)
    }

    class DiffCallback : DiffUtil.ItemCallback<StatsRow>() {
        override fun areItemsTheSame(oldItem: StatsRow, newItem: StatsRow) =
            oldItem.nickname == newItem.nickname

        override fun areContentsTheSame(oldItem: StatsRow, newItem: StatsRow) =
            oldItem == newItem
    }
}
