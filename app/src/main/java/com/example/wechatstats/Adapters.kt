package com.example.wechatstats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.GroupRow
import com.example.wechatstats.data.MessageRecord
import com.example.wechatstats.data.StatsRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupAdapter(private val onClick: (GroupRow) -> Unit) :
    ListAdapter<GroupRow, GroupAdapter.VH>(object : DiffUtil.ItemCallback<GroupRow>() {
        override fun areItemsTheSame(oldItem: GroupRow, newItem: GroupRow) =
            oldItem.groupName == newItem.groupName
        override fun areContentsTheSame(oldItem: GroupRow, newItem: GroupRow) =
            oldItem == newItem
    }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_stats, parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.label.text = "${position + 1}. ${item.groupName}"
        holder.count.visibility = android.view.View.GONE
        holder.itemView.setOnClickListener { onClick(item) }
    }
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.tvNickname)
        val count: TextView = view.findViewById(R.id.tvCount)
    }
}

class MemberAdapter(private val onClick: (StatsRow) -> Unit) :
    ListAdapter<StatsRow, MemberAdapter.VH>(object : DiffUtil.ItemCallback<StatsRow>() {
        override fun areItemsTheSame(oldItem: StatsRow, newItem: StatsRow) =
            oldItem.nickname == newItem.nickname
        override fun areContentsTheSame(oldItem: StatsRow, newItem: StatsRow) =
            oldItem == newItem
    }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_stats, parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.label.text = "${position + 1}. ${item.nickname}"
        holder.count.text = "${item.count} 次"
        holder.itemView.setOnClickListener { onClick(item) }
    }
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.tvNickname)
        val count: TextView = view.findViewById(R.id.tvCount)
    }
}

class MessageAdapter : ListAdapter<MessageRecord, MessageAdapter.VH>(object : DiffUtil.ItemCallback<MessageRecord>() {
    override fun areItemsTheSame(oldItem: MessageRecord, newItem: MessageRecord) =
        oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: MessageRecord, newItem: MessageRecord) =
        oldItem == newItem
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.text.text = if (item.text.isEmpty()) "(空消息)" else item.text
        holder.time.text = TIME_FMT.format(Date(item.timestamp))
    }
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvMessageTime)
    }
    companion object {
        private val TIME_FMT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    }
}
