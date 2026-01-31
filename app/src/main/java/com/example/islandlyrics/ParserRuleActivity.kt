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

    private lateinit var adapter: ParserRuleAdapter
    private lateinit var recyclerView: RecyclerView
    private var ruleList: MutableList<ParserRule> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parser_rule)

        // Fix Insets (Status Bar)
        val rootView = findViewById<View>(R.id.root_view)
        rootView?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // Load Data
        loadData()

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ParserRuleAdapter(this, ruleList) { rule, action ->
            when (action) {
                "edit" -> showEditDialog(rule)
                "delete" -> confirmDelete(rule)
                "toggle" -> {
                    saveData()
                }
            }
        }
        recyclerView.adapter = adapter

        // Add Button
        findViewById<Button>(R.id.btn_add_rule).setOnClickListener {
            showEditDialog(null)
        }
    }

    private fun loadData() {
        ruleList.clear()
        ruleList.addAll(ParserRuleHelper.loadRules(this))
    }

    private fun saveData() {
        ParserRuleHelper.saveRules(this, ruleList)
        AppLogger.getInstance().log("ParserRule", "Rules saved: ${ruleList.size} entries")
    }

    private fun showEditDialog(existingRule: ParserRule?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_parser_rule, null)
        
        val etPackageName = dialogView.findViewById<TextInputEditText>(R.id.et_package_name)
        val switchCarProtocol = dialogView.findViewById<MaterialSwitch>(R.id.switch_car_protocol)
        val spinnerSeparator = dialogView.findViewById<Spinner>(R.id.spinner_separator)
        val spinnerFieldOrder = dialogView.findViewById<Spinner>(R.id.spinner_field_order)

        // Setup Separator Spinner
        val separators = listOf("-", " - ", " | ")
        val separatorLabels = listOf(
            getString(R.string.parser_separator_tight),
            getString(R.string.parser_separator_spaced),
            getString(R.string.parser_separator_pipe)
        )
        spinnerSeparator.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, separatorLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Setup Field Order Spinner
        val fieldOrders = listOf(FieldOrder.ARTIST_TITLE, FieldOrder.TITLE_ARTIST)
        val fieldOrderLabels = listOf(
            getString(R.string.parser_order_artist_title),
            getString(R.string.parser_order_title_artist)
        )
        spinnerFieldOrder.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fieldOrderLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Populate existing data
        if (existingRule != null) {
            etPackageName.setText(existingRule.packageName)
            etPackageName.isEnabled = false  // Can't change package name
            switchCarProtocol.isChecked = existingRule.usesCarProtocol
            spinnerSeparator.setSelection(separators.indexOf(existingRule.separatorPattern).coerceAtLeast(0))
            spinnerFieldOrder.setSelection(fieldOrders.indexOf(existingRule.fieldOrder).coerceAtLeast(0))
        } else {
            switchCarProtocol.isChecked = true
        }

        AlertDialog.Builder(this)
            .setTitle(if (existingRule == null) R.string.parser_add_rule else R.string.parser_edit)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pkg = etPackageName.text.toString().trim()
                if (pkg.isEmpty()) {
                    Toast.makeText(this, "Package name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedSeparator = separators[spinnerSeparator.selectedItemPosition]
                val selectedFieldOrder = fieldOrders[spinnerFieldOrder.selectedItemPosition]

                val newRule = ParserRule(
                    packageName = pkg,
                    enabled = existingRule?.enabled ?: true,
                    usesCarProtocol = switchCarProtocol.isChecked,
                    separatorPattern = selectedSeparator,
                    fieldOrder = selectedFieldOrder
                )

                if (existingRule == null) {
                    // Add new
                    if (ruleList.any { it.packageName == pkg }) {
                        Toast.makeText(this, "Package already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        ruleList.add(newRule)
                        ruleList.sort()
                        saveData()
                        adapter.notifyDataSetChanged()
                        AppLogger.getInstance().log("ParserRule", "Added: $pkg")
                    }
                } else {
                    // Update existing
                    val index = ruleList.indexOf(existingRule)
                    if (index >= 0) {
                        ruleList[index] = newRule
                        saveData()
                        adapter.notifyItemChanged(index)
                        AppLogger.getInstance().log("ParserRule", "Updated: $pkg")
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(rule: ParserRule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.parser_delete)
            .setMessage(getString(R.string.dialog_delete_confirm, rule.packageName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val index = ruleList.indexOf(rule)
                if (index >= 0) {
                    ruleList.removeAt(index)
                    saveData()
                    adapter.notifyItemRemoved(index)
                    AppLogger.getInstance().log("ParserRule", "Deleted: ${rule.packageName}")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        
        holder.tvPackageName.text = rule.packageName
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
        val protocol = if (rule.usesCarProtocol) "✓ Car Protocol" else "✗ Car Protocol"
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
