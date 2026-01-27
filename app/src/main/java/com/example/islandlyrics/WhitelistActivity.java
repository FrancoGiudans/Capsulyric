package com.example.islandlyrics;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WhitelistActivity extends BaseActivity {

    private static final String PREFS_NAME = "IslandLyricsPrefs";
    private static final String PREF_WHITELIST = "whitelist_packages";
    
    // Default packages if list is empty
    private static final Set<String> DEFAULTS = new HashSet<>();
    static {
        DEFAULTS.add("com.tencent.qqmusic");
        DEFAULTS.add("com.miui.player");
        DEFAULTS.add("com.netease.cloudmusic");
    }

    private RecyclerView recyclerView;
    private WhitelistAdapter adapter;
    private List<String> packageList;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whitelist);

        // Fix Insets (Status Bar)
        View rootView = findViewById(R.id.root_view);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Load Data
        loadData();

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WhitelistAdapter();
        recyclerView.setAdapter(adapter);

        // Add Button Logic
        findViewById(R.id.btn_add_package).setOnClickListener(v -> showAddDialog());
    }

    private void loadData() {
        Set<String> set = prefs.getStringSet(PREF_WHITELIST, null);
        if (set == null) {
            // First run, populate defaults
            set = new HashSet<>(DEFAULTS);
            prefs.edit().putStringSet(PREF_WHITELIST, set).apply();
        }
        packageList = new ArrayList<>(set);
        Collections.sort(packageList);
    }

    private void saveData() {
        Set<String> set = new HashSet<>(packageList);
        prefs.edit().putStringSet(PREF_WHITELIST, set).apply();
    }

    private void showAddDialog() {
        final EditText input = new EditText(this);
        input.setHint("e.g. com.spotify.music");
        
        // Add some padding to the edit text container
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 48; // px (approx)
        params.rightMargin = 48;
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
            .setTitle("Add Package")
            .setView(container)
            .setPositiveButton("Add", (dialog, which) -> {
                String pkg = input.getText().toString().trim();
                if (!pkg.isEmpty()) {
                    if (!packageList.contains(pkg)) {
                        packageList.add(pkg);
                        Collections.sort(packageList);
                        saveData();
                        adapter.notifyDataSetChanged();
                        AppLogger.getInstance().log("Whitelist", "Added: " + pkg);
                    } else {
                        Toast.makeText(this, "Package already exists", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Inner Adapter Class
    private class WhitelistAdapter extends RecyclerView.Adapter<WhitelistAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_whitelist, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String pkg = packageList.get(position);
            holder.tvPackage.setText(pkg);
            holder.btnDelete.setOnClickListener(v -> {
                // Delete logic
                int actualPos = holder.getAdapterPosition();
                if (actualPos != RecyclerView.NO_POSITION) {
                    packageList.remove(actualPos);
                    saveData();
                    notifyItemRemoved(actualPos);
                    AppLogger.getInstance().log("Whitelist", "Removed: " + pkg);
                }
            });
        }

        @Override
        public int getItemCount() {
            return packageList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPackage;
            ImageView btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvPackage = itemView.findViewById(R.id.tv_package_name);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
