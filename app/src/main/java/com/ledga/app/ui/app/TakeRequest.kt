package com.ledga.app.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * A one-shot request another screen left (R100: a Fuliza reminder's sheet), taken while this screen is shown (started),
 * once; like Activity's `TakeLinks` (R93).
 */
@Composable
fun <T : Any> TakeRequest(requested: StateFlow<T?>, take: (T) -> Unit) {
    val asked by requested.collectAsStateWithLifecycle()
    LaunchedEffect(asked) { asked?.let(take) }
}
