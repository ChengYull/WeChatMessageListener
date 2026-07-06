package com.example.wechatstats

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatstats.data.DateUtils
import java.time.LocalDate

class DateAdapter(
    private val dates: List<LocalDate?>,
    private var selectedIndex: Int = 0,
    private val onDateSelected: (Int, LocalDate?) -> Unit
) : RecyclerView.Adapter<DateAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_date_chip, parent, false)
        return VH(view)
    }

    override fun getItemCount() = dates.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val date = dates[position]
        holder.label.text = if (date == null) {
            holder.itemView.context.getString(R.string.chip_all)
        } else {
            DateUtils.formatLabel(date)
        }
        if (position == selectedIndex) {
            holder.label.setTextColor(0xFFFF5722.toInt())
            holder.label.setTypeface(null, Typeface.BOLD)
        } else {
            holder.label.setTextColor(0xFF666666.toInt())
            holder.label.setTypeface(null, Typeface.NORMAL)
        }
        holder.itemView.setOnClickListener {
            val old = selectedIndex
            if (old != position) {
                selectedIndex = position
                notifyItemChanged(old)
                notifyItemChanged(position)
                onDateSelected(position, date)
            }
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view as TextView
    }
}
