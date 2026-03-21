package com.ledga.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.ui.components.CategoryChip
import com.ledga.app.ui.components.TransactionCard

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Detail bottom sheet
    if (state.selectedTransaction != null) {
        TransactionDetailSheet(
            transaction = state.selectedTransaction!!.transaction,
            category = state.selectedTransaction!!.category,
            categories = state.categories,
            onDismiss = { viewModel.clearSelection() },
            onCategoryChange = { categoryId ->
                viewModel.changeCategory(
                    state.selectedTransaction!!.transaction.id,
                    categoryId
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Search bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search transactions...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TransactionsViewModel.FILTERS.keys.toList()) { filter ->
                CategoryChip(
                    label = filter,
                    isSelected = state.activeFilter == filter,
                    onClick = { viewModel.setFilter(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transactions grouped by day
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.groupedTransactions.forEach { (day, transactions) ->
                // Day header
                item(key = "header_$day") {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Transactions for that day
                items(
                    items = transactions,
                    key = { it.transaction.id }
                ) { twc ->
                    TransactionCard(
                        transaction = twc.transaction,
                        category = twc.category,
                        onClick = { viewModel.selectTransaction(twc) }
                    )
                }
            }

            if (state.groupedTransactions.isEmpty()) {
                item {
                    Text(
                        text = "No transactions found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
