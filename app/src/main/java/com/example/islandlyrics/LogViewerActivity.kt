package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.ArrayList

class LogViewerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LogAdapter
    private lateinit var etSearch: EditText
    private lateinit var chipGroup: ChipGroup

    // Data Sources
    private var masterList: List<LogManager.LogEntry> = ArrayList()
    private var currentFilterText = ""
    private var currentFilterLevel = "ALL" // ALL, E, W, D

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        // Setup Toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initializeViews()
        setupListeners()

        refreshLogs()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.rv_logs)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true // Auto-scroll behavior
        recyclerView.layoutManager = layoutManager

        adapter = LogAdapter()
        recyclerView.adapter = adapter

        etSearch = findViewById(R.id.et_search)
        chipGroup = findViewById(R.id.chip_group_filter)

        val fabDown = findViewById<FloatingActionButton>(R.id.fab_scroll_down)
        fabDown.setOnClickListener {
            if (adapter.itemCount > 0) {
                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }

        val fabExport = findViewById<ExtendedFloatingActionButton>(R.id.fab_export)
        fabExport.setOnClickListener {
            LogManager.getInstance().exportLog(this)
        }
    }

    private fun setupListeners() {
        // Search Listener
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                currentFilterText = s.toString().lowercase()
                applyFilter()
            }
        })

        // Chip Listener
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) {
                currentFilterLevel = "ALL"
            } else {
                val id = checkedIds[0]
                currentFilterLevel = when (id) {
                    R.id.chip_error -> "E"
                    R.id.chip_warn -> "W"
                    R.id.chip_debug -> "D"
                    else -> "ALL"
                }
            }
            applyFilter()
        }
    }

    private fun refreshLogs() {
        Thread {
            val entries = LogManager.getInstance().getLogEntries(this)
            runOnUiThread {
                masterList = entries
                applyFilter() // This updates adapter

                // Only scroll to bottom on initial load or if user was already near bottom
                if (adapter.itemCount > 0) {
                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }.start()
    }

    private fun applyFilter() {
        val filtered = ArrayList<LogManager.LogEntry>()

        for (entry in masterList) {
            // Level Check
            val levelMatch = "ALL" == currentFilterLevel ||
                    (entry.level == currentFilterLevel)

            // Text Check
            val textMatch = currentFilterText.isEmpty() ||
                    entry.message.lowercase().contains(currentFilterText) ||
                    entry.tag.lowercase().contains(currentFilterText)

            if (levelMatch && textMatch) {
                filtered.add(entry)
            }
        }

        adapter.setList(filtered)
    }

    // Standard Menu for Back/Clear
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Programmatically add menu items
        menu.add(0, 1, 0, "Refresh").setIcon(android.R.drawable.ic_menu_rotate)
        menu.add(0, 2, 0, "Clear Logs").setIcon(android.R.drawable.ic_menu_delete)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        } else if (item.itemId == 1) { // Refresh
            refreshLogs()
            return true
        } else if (item.itemId == 2) { // Clear
            LogManager.getInstance().clearLog(this)
            // Can't clear local list directly due to final behavior in Java but here in Kotlin we can
            // But we should refresh to sync up or manually clear
            // Let's clear manually
            refreshLogs() 
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LogViewerActivity::class.java))
        }
    }
}
