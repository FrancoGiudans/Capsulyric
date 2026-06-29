package com.example.islandlyrics.feature.licenses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsCardDivider
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenSourceLicensesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IslandLyricsMaterialTheme {
                PredictiveBackActivity {
                    OpenSourceLicensesScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenSourceLicensesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val applicationContext = remember(context) { context.applicationContext }
    val libraries by produceState<List<Library>?>(initialValue = null, applicationContext) {
        value = withContext(Dispatchers.IO) {
            Libs.Builder()
                .withJson(applicationContext, R.raw.aboutlibraries)
                .build()
                .libraries
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        }
    }
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.open_source_licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = neutralMaterialTopBarColors()
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SettingsSectionHeader(
                    text = stringResource(R.string.open_source_licenses_header),
                    marginTop = 8.dp
                )
            }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                        Text(
                            text = stringResource(R.string.open_source_licenses_page_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (libraries == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 10.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = stringResource(R.string.open_source_licenses_loading),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.open_source_licenses_count_format, libraries.orEmpty().size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(text = stringResource(R.string.open_source_licenses_libraries))
            }
            item {
                SettingsCard {
                    val currentLibraries = libraries
                    if (currentLibraries == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        currentLibraries.forEachIndexed { index, library ->
                            if (index > 0) SettingsCardDivider()
                            LibraryListItem(
                                library = library,
                                onClick = { selectedLibrary = library }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedLibrary?.let { library ->
        LibraryDetailsDialog(
            library = library,
            onDismiss = { selectedLibrary = null },
            onOpen = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
        )
    }
}

@Composable
private fun LibraryListItem(
    library: Library,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Policy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(
                text = library.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                library.versionText.takeIf { it.isNotBlank() }?.let { version ->
                    Text(
                        text = version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = library.licenseSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

@Composable
private fun LibraryDetailsDialog(
    library: Library,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    val projectUrl = library.primaryUrl
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Policy, contentDescription = null)
        },
        title = {
            Text(
                text = library.displayName,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow(
                    label = stringResource(R.string.open_source_licenses_version),
                    value = library.versionText.ifBlank { stringResource(R.string.open_source_licenses_unknown) }
                )
                DetailRow(
                    label = stringResource(R.string.open_source_licenses_license),
                    value = library.licenseSummary
                )
                library.authorsSummary.takeIf { it.isNotBlank() }?.let {
                    DetailRow(
                        label = stringResource(R.string.open_source_licenses_authors),
                        value = it
                    )
                }
                projectUrl?.let {
                    DetailRow(
                        label = stringResource(R.string.open_source_licenses_project),
                        value = it
                    )
                }
                val licenseText = library.licenses
                    .mapNotNull { it.licenseContent.orEmpty().takeIf { content -> content.isNotBlank() } }
                    .firstOrNull()
                if (!licenseText.isNullOrBlank()) {
                    DetailRow(
                        label = stringResource(R.string.open_source_licenses_license_text),
                        value = licenseText.lineSequence().take(24).joinToString("\n")
                    )
                }
            }
        },
        confirmButton = {
            if (projectUrl != null) {
                TextButton(onClick = { onOpen(projectUrl) }) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Text(
                        text = stringResource(R.string.open_source_licenses_open_project),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.community_dialog_close))
            }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 32.dp, top = 4.dp)
        )
    }
}

private val Library.displayName: String
    get() = name.orEmpty().ifBlank { artifactId.ifBlank { uniqueId } }

private val Library.versionText: String
    get() = artifactVersion.orEmpty()

private val Library.licenseSummary: String
    get() = licenses
        .map { license ->
            license.spdxId.orEmpty().takeIf { it.isNotBlank() }
                ?: license.name.orEmpty().takeIf { it.isNotBlank() }
                ?: license.url.orEmpty().takeIf { it.isNotBlank() }
                ?: "Unknown"
        }
        .distinct()
        .joinToString(", ")
        .ifBlank { "Unknown" }

private val Library.authorsSummary: String
    get() = buildList {
        organization?.name?.takeIf { it.isNotBlank() }?.let { add(it) }
        developers.mapNotNullTo(this) { it.name.orEmpty().takeIf { name -> name.isNotBlank() } }
    }.distinct().joinToString(", ")

private val Library.primaryUrl: String?
    get() = website.orEmpty().takeIf { it.isNotBlank() }
        ?: scm?.url?.takeIf { it.isNotBlank() }
        ?: organization?.url?.takeIf { it.isNotBlank() }
