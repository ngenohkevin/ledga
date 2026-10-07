package com.ledga.app.ui.activity

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.type.LedgaType
import java.time.LocalDate

/**
 * M3's date picker in its dialog (R70): Nairobi days through UTC midnights, nothing after today. The calendar needs about
 * 570 dp of height; on a shorter screen (a phone on its side) it is clipped, so the date is typed there instead and the
 * switch to the calendar is left out (S26).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RangeDatePicker(initial: LocalDate, today: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val c = LedgaTheme.colors
    val short = LocalConfiguration.current.screenHeightDp < CALENDAR_MIN_HEIGHT_DP
    val state = rememberDatePickerState(
        initialSelectedDateMillis = PickerDates.toMillis(initial),
        selectableDates = PickerDates.selectable(today),
        initialDisplayMode = if (short) DisplayMode.Input else DisplayMode.Picker,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPicked(PickerDates.fromMillis(it)) } ?: onDismiss() }) {
                Text("OK", style = LedgaType.label, color = c.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = LedgaType.label, color = c.primary) } },
        colors = DatePickerDefaults.colors(containerColor = c.surfaceSheet),
    ) {
        DatePicker(state = state, showModeToggle = !short, colors = DatePickerDefaults.colors(containerColor = c.surfaceSheet))
    }
}

/** The height the M3 calendar fits in, with the dialog's own margins. */
private const val CALENDAR_MIN_HEIGHT_DP = 600
