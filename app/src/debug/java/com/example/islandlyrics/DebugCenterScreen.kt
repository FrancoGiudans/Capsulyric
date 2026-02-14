package com.example.islandlyrics

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCenterScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Center") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Debug Lyric API ──
            DebugMenuButton(
                text = "Debug Lyric API",
                description = "Test lyric fetching and parsing",
                onClick = {
                    val intent = Intent(context, DebugLyricActivity::class.java)
                    context.startActivity(intent)
                }
            )

            // ── Test Update Dialog ──
            DebugMenuButton(
                text = "Test Update Dialog",
                description = "Show a dummy update available dialog",
                onClick = { showUpdateDialog = true }
            )

            // ── Xiaomi Mi Play ──
            DebugMenuButton(
                text = "Launch Xiaomi Mi Play",
                description = "Try to launch miui.systemui.miplay.MiPlayDetailActivity",
                onClick = {
                    try {
                        val intent = Intent()
                        intent.component = android.content.ComponentName(
                            "com.android.systemui",
                            "miui.systemui.miplay.MiPlayDetailActivity"
                        )
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    if (showUpdateDialog) {
        val dummyRelease = UpdateChecker.ReleaseInfo(
            tagName = "v9.9.9_DEBUG",
            name = "Debug Test Update",
            body = "### New Features\n- This is a test update dialog.\n- It verifies that the dialog renders correctly.",
            htmlUrl = "https://github.com/FrancoGiudans/Capsulyric/releases",
            publishedAt = "2026-01-01T00:00:00Z",
            prerelease = true
        )

        UpdateDialog(
            releaseInfo = dummyRelease,
            onDismiss = { showUpdateDialog = false },
            onIgnore = { /* No-op in debug */ }
        )
    }
}

@Composable
private fun DebugMenuButton(
    text: String,
    description: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
