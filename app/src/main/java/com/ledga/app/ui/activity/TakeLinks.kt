package com.ledga.app.ui.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * R93: Activity's screen takes the waiting hand-off while it is shown (started), once. A ViewModel that outlived its
 * screen (4d's S26: a text-size change rebuilt the navigation) can't take what the screen on top was sent.
 */
@Composable
fun TakeLinks(pending: StateFlow<ActivityLink?>, take: (ActivityLink) -> Unit) {
    val link by pending.collectAsStateWithLifecycle()
    LaunchedEffect(link) { link?.let(take) }
}
