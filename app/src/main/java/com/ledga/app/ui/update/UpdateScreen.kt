package com.ledga.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.BuildConfig
import com.ledga.app.data.repository.DownloadState
import com.ledga.app.ui.components.v2.BackLeading
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.components.v2.onTonal
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaAccentSoft
import com.ledga.app.ui.theme.LedgaDanger
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.InstallPermission
import com.ledga.app.worker.GitHubRelease

@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val release = state.release
    val context = LocalContext.current
    var permissionDialogOpen by remember { mutableStateOf(false) }

    if (permissionDialogOpen) {
        AlertDialog(
            onDismissRequest = { permissionDialogOpen = false },
            title = { Text("Allow installs from Ledga") },
            text = {
                Text(
                    "Android needs your permission to install apps from Ledga. " +
                            "Tap Open settings, flip the switch for Ledga, then come back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionDialogOpen = false
                    InstallPermission.openSettings(context)
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { permissionDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(
            title = "Updates",
            leading = { BackLeading(onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Screen)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            when {
                release == null && state.isChecking -> StatusCard("Checking for updates…")
                release == null -> UpToDateCard(onCheckAgain = { viewModel.checkAgain() })
                else -> ReleaseCard(
                    release = release,
                    downloadState = state.downloadState,
                    onDownload = { viewModel.startDownload(release) },
                    onInstall = { file ->
                        if (InstallPermission.canInstall(context)) {
                            viewModel.install(file)
                        } else {
                            permissionDialogOpen = true
                        }
                    },
                    onRemindLater = {
                        viewModel.remindLater(release.tag_name)
                        onBack()
                    },
                )
            }

            Box(modifier = Modifier.padding(bottom = Space.s5))
        }
    }
}

@Composable
private fun UpToDateCard(onCheckAgain: () -> Unit) {
    BentoCard(
        overline = "You're up to date",
        title = "Running Ledga v${BuildConfig.VERSION_NAME}",
        icon = Icons.Filled.CheckCircle,
        iconTint = LedgaAccentDeep,
        tonal = true,
        tonalColor = LedgaAccentSoft,
    ) {
        Text(
            text = "Nothing newer on GitHub Releases right now.",
            style = LedgaText.BodyM,
            color = onTonal(LedgaAccentSoft),
        )
        TextButton(onClick = onCheckAgain) {
            Text("Check again", color = LedgaAccentDeep)
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    BentoCard {
        Text(
            text = message,
            style = LedgaText.BodyM,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReleaseCard(
    release: GitHubRelease,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onRemindLater: () -> Unit,
) {
    val sections = parseChangelog(release.body.orEmpty())
    val sizeMb = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        ?.size
        ?.let { "%.1f MB".format(it / 1_000_000.0) }
        ?: "—"

    BentoCard(
        overline = "New version",
        title = release.name ?: "Ledga ${release.tag_name}",
        icon = Icons.Filled.NewReleases,
        iconTint = LedgaAccentDeep,
        tonal = true,
        tonalColor = LedgaAccentSoft,
    ) {
        Text(
            text = "Currently running v${BuildConfig.VERSION_NAME}  ·  Update is $sizeMb",
            style = LedgaText.BodyM,
            color = onTonal(LedgaAccentSoft),
        )
    }

    if (sections.whatsNew.isNotEmpty()) {
        BentoCard(title = "What's new") {
            sections.whatsNew.forEach { item ->
                ChangelogLine(icon = Icons.Filled.AutoAwesome, tint = LedgaAccentDeep, text = item)
            }
        }
    }
    if (sections.fixes.isNotEmpty()) {
        BentoCard(title = "Fixes") {
            sections.fixes.forEach { item ->
                ChangelogLine(icon = Icons.Filled.BugReport, tint = MaterialTheme.colorScheme.onSurfaceVariant, text = item)
            }
        }
    }
    if (sections.other.isNotEmpty()) {
        BentoCard(title = "Notes") {
            Text(
                text = sections.other.joinToString("\n"),
                style = LedgaText.BodyM,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    BentoCard {
        when (val s = downloadState) {
            DownloadState.Idle -> {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = LedgaAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = "  Download and install")
                }
                TextButton(onClick = onRemindLater, modifier = Modifier.fillMaxWidth()) {
                    Text("Remind me later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is DownloadState.Downloading -> {
                val total = s.totalBytes.coerceAtLeast(1)
                val pct = (s.bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                Text(
                    text = "Downloading… ${(pct * 100).toInt()}%",
                    style = LedgaText.BodyL,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth(),
                    color = LedgaAccent,
                )
                Text(
                    text = "${"%.1f".format(s.bytesRead / 1_000_000.0)} MB of " +
                            "${"%.1f".format(total / 1_000_000.0)} MB",
                    style = LedgaText.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is DownloadState.Ready -> {
                Button(
                    onClick = { onInstall(s.file) },
                    colors = ButtonDefaults.buttonColors(containerColor = LedgaAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Install now") }
                Text(
                    text = "Android will ask you to confirm the install.",
                    style = LedgaText.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is DownloadState.Failed -> {
                Text(
                    text = s.message,
                    style = LedgaText.BodyM,
                    color = LedgaDanger,
                )
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = LedgaAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun ChangelogLine(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Text(
            text = text,
            style = LedgaText.BodyM,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Minimal markdown-ish splitter. Looks for `What's new`, `Fixes`, or
 * `What is new` style headers and groups bullet lines under them. Anything
 * uncategorized falls into [other] verbatim.
 */
internal data class Changelog(
    val whatsNew: List<String>,
    val fixes: List<String>,
    val other: List<String>,
)

internal fun parseChangelog(body: String): Changelog {
    if (body.isBlank()) return Changelog(emptyList(), emptyList(), emptyList())
    val whatsNew = mutableListOf<String>()
    val fixes = mutableListOf<String>()
    val other = mutableListOf<String>()
    var bucket: MutableList<String> = other

    body.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        val lower = line.lowercase()
        when {
            line.startsWith("#") || lower.endsWith(":") -> {
                bucket = when {
                    "fix" in lower || "bug" in lower -> fixes
                    "new" in lower || "feature" in lower || "added" in lower -> whatsNew
                    else -> other
                }
            }
            line.startsWith("-") || line.startsWith("*") -> {
                bucket.add(line.removePrefix("-").removePrefix("*").trim())
            }
            else -> other.add(line)
        }
    }
    return Changelog(whatsNew, fixes, other)
}
