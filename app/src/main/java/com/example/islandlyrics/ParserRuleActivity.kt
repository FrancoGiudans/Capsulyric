package com.example.islandlyrics

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class ParserRuleActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use Compose
        androidx.activity.compose.setContent {
            AppTheme {
                ParserRuleScreen(
                    onBack = { finish() }
                )
            }
        }
    }


// Adapter
class ParserRuleAdapter(
    private val context: Context,
    private val rules: MutableList<ParserRule>,
    private val callback: (ParserRule, String) -> Unit
) : RecyclerView.Adapter<ParserRuleAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_parser_rule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = rules[position]
        
        // Display Logic: "Name (package.name)" or just "package.name"
        if (!rule.customName.isNullOrEmpty()) {
            holder.tvPackageName.text = "${rule.customName} (${rule.packageName})"
        } else {
            holder.tvPackageName.text = rule.packageName
        }
        
        holder.tvDetails.text = buildRuleDetails(rule)
        
        // Unbind listener before setting state
        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = rule.enabled
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            rules[position] = rule.copy(enabled = isChecked)
            callback(rule, "toggle")
        }

        holder.btnEdit.setOnClickListener {
            callback(rule, "edit")
        }

        holder.btnDelete.setOnClickListener {
            callback(rule, "delete")
        }
    }

    override fun getItemCount() = rules.size

    private fun buildRuleDetails(rule: ParserRule): String {
        val separator = when (rule.separatorPattern) {
            "-" -> context.getString(R.string.parser_separator_tight)
            " - " -> context.getString(R.string.parser_separator_spaced)
            " | " -> context.getString(R.string.parser_separator_pipe)
            else -> rule.separatorPattern
        }
        val order = when (rule.fieldOrder) {
            FieldOrder.ARTIST_TITLE -> context.getString(R.string.parser_order_artist_title)
            FieldOrder.TITLE_ARTIST -> context.getString(R.string.parser_order_title_artist)
        }
        val protocol = if (rule.usesCarProtocol) "✓ Media Notification Lyrics" else "✗ Media Notification Lyrics"
        return "$separator | $order | $protocol"
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPackageName: TextView = itemView.findViewById(R.id.tv_package_name)
        val tvDetails: TextView = itemView.findViewById(R.id.tv_rule_details)
        val switchEnabled: MaterialSwitch = itemView.findViewById(R.id.switch_enabled)
        val btnEdit: Button = itemView.findViewById(R.id.btn_edit)
        val btnDelete: Button = itemView.findViewById(R.id.btn_delete)
    }
}
