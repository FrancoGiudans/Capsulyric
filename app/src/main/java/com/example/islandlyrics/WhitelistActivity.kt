package com.example.islandlyrics

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.islandlyrics.WhitelistHelper.saveWhitelist
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Collections

class WhitelistActivity : BaseActivity() {

    private lateinit var adapter: WhitelistAdapter
    private lateinit var recyclerView: RecyclerView
    private var packageList: MutableList<WhitelistItem> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whitelist)

        // Fix Insets (Status Bar)
        val rootView = findViewById<View>(R.id.root_view)
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
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
        adapter = WhitelistAdapter()
        recyclerView.adapter = adapter

        // Add Button Logic
        findViewById<View>(R.id.btn_add_package).setOnClickListener { showAddDialog() }
    }

    private fun loadData() {
        packageList = WhitelistHelper.loadWhitelist(this)
    }

    private fun saveData() {
        saveWhitelist(this, packageList)
    }

    private fun showAddDialog() {
        val input = EditText(this)
        input.setHint(R.string.dialog_enter_pkg)

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 48
        params.rightMargin = 48
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_whitelist)
            .setView(container)
            .setPositiveButton(R.string.dialog_btn_add) { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isNotEmpty()) {
                    var exists = false
                    for (item in packageList) {
                        if (item.packageName == pkg) {
                            exists = true
                            break
                        }
                    }

                    if (!exists) {
                        packageList.add(WhitelistItem(pkg, true))
                        Collections.sort(packageList)
                        saveData()
                        adapter.notifyDataSetChanged()
                        AppLogger.getInstance().log("Whitelist", "Added: $pkg")
                    } else {
                        Toast.makeText(this, "Package already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_btn_cancel, null)
            .show()
    }

    // Inner Adapter Class
    private inner class WhitelistAdapter : RecyclerView.Adapter<WhitelistAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_whitelist, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = packageList[position]
            holder.tvPackage.text = item.packageName

            // Unbind listener before setting state to avoid infinite triggers (rare but possible)
            holder.switchToggle.setOnCheckedChangeListener(null)
            holder.switchToggle.isChecked = item.isEnabled

            holder.switchToggle.setOnCheckedChangeListener { _, isChecked ->
                item.isEnabled = isChecked
                saveData()
            }

            // Long Press to Delete
            holder.itemView.setOnLongClickListener {
                val actualPos = holder.adapterPosition
                if (actualPos != RecyclerView.NO_POSITION) {
                    AlertDialog.Builder(this@WhitelistActivity)
                        .setTitle(R.string.dialog_whitelist_title) // Reusing title
                        .setMessage(getString(R.string.dialog_delete_confirm, item.packageName))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            packageList.removeAt(actualPos)
                            saveData()
                            notifyItemRemoved(actualPos)
                            AppLogger.getInstance().log("Whitelist", "Removed: ${item.packageName}")
                        }
                        .setNegativeButton(R.string.dialog_btn_cancel, null)
                        .show()
                }
                true
            }
        }

        override fun getItemCount(): Int {
            return packageList.size
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvPackage: TextView = itemView.findViewById(R.id.tv_package_name)
            val switchToggle: MaterialSwitch = itemView.findViewById(R.id.switch_whitelist_toggle)
        }
    }
}
