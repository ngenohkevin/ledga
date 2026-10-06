package com.ledga.app.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ledga.app.ui.design.components.EmptyState

/** R32: a tab whose screens arrive in a later Phase 4 plan. Development builds only; 4d deletes it. */
@Composable
fun ComingNext(tab: Tab, modifier: Modifier = Modifier) {
    val (icon, body) = when (tab) {
        Tab.HOME -> "fluent_house" to "Home arrives in the next build."
        Tab.ACTIVITY -> "fluent_memo" to "Transactions, spending and people arrive in the next build."
        Tab.TRACKERS -> "fluent_bar_chart" to "Electricity, water, fuel and car costs, month by month, arrive soon."
        Tab.YOU -> "fluent_busts_in_silhouette" to "Lines, categories, notifications and appearance arrive soon."
    }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        ScreenTitle(tab.nav.label)
        EmptyState(icon, "Coming in a later build", body)
    }
}
