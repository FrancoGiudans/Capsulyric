package com.example.islandlyrics

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val logs = ArrayList<LogManager.LogEntry>()

    fun setList(newLogs: List<LogManager.LogEntry>) {
        this.logs.clear()
        this.logs.addAll(newLogs)
        notifyDataSetChanged()
    }

    fun addEntry(entry: LogManager.LogEntry) {
        this.logs.add(entry)
        notifyItemInserted(logs.size - 1)
    }

    fun clear() {
        this.logs.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(v)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val e = logs[position]

        // Timestamp: Truncate to easy read (HH:mm:ss.SSS)
        // Original format: MM-dd HH:mm:ss.SSS
        if (e.timestamp.length > 6) {
            holder.tvTime.text = e.timestamp.substring(6)
        } else {
            holder.tvTime.text = e.timestamp
        }

        holder.tvTag.text = e.tag
        holder.tvMsg.text = e.message

        // Colors & Indicators
        var statusColor = -0x444445 // 0xFFBBBBBB Default Grey
        when (e.level) {
            "E" -> statusColor = -0x9498 // 0xFFFF6B68 Red
            "W" -> statusColor = -0x2ab1 // 0xFFFFD54F Yellow
            "D" -> statusColor = -0x6f3507 // 0xFF90CAF9 Blue
            "I" -> statusColor = -0x7e387c // 0xFF81C784 Green
        }

        holder.vIndicator.backgroundTintList = ColorStateList.valueOf(statusColor)
    }

    override fun getItemCount(): Int {
        return logs.size
    }

    class LogViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val vIndicator: View = v.findViewById(R.id.view_level_indicator)
        val tvTag: TextView = v.findViewById(R.id.tv_tag)
        val tvTime: TextView = v.findViewById(R.id.tv_timestamp)
        val tvMsg: TextView = v.findViewById(R.id.tv_message)
    }
}
