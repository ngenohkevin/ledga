package com.ledga.app.ui.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.SoftPill
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import java.io.File

/**
 * Spec §8 step 1: the migration to v2 failed. The database is untouched (Room rolls the migration back) and the
 * pre-v6 copy exists, so: "Send me the database" (when there is a copy) and "Try again". [reason] is the technical
 * cause, small, for whoever receives the file.
 */
@Composable
fun RecoveryScreen(reason: String, canShare: Boolean, onShare: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(c.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroIcon("fluent_warning", Modifier.padding(top = Spacing.xxxl))
        Text(
            "Ledga couldn't update your data",
            Modifier.padding(top = Spacing.xl).semantics { heading() },
            style = LedgaType.screenTitle,
            color = c.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            if (canShare) {
                "Nothing was lost. Ledga kept a copy of your data from before the update. Send it to the developer so this can be fixed, then try again."
            } else {
                "Ledga couldn't open its data. Close Ledga and try again. If it keeps happening, tell the developer what the details below say."
            },
            Modifier.padding(top = Spacing.s),
            style = LedgaType.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        Text(
            "Details: $reason",
            Modifier.padding(top = Spacing.l),
            style = LedgaType.caption,
            color = c.muted,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xxl))
        if (canShare) PrimaryPill("Send me the database", onShare, Modifier.fillMaxWidth())
        SoftPill("Try again", onRetry, Modifier.fillMaxWidth().padding(top = Spacing.s))
    }
}

object RecoveryShare {
    /** "Send me the database": the pre-v6 copy, shared as a file through Ledga's FileProvider (`files/pre-v6/`). */
    fun intent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Ledga database (before the update)")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Send the database")
    }
}
