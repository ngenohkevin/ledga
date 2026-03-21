package com.ledga.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Atm
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

fun categoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        "shopping_cart" -> Icons.Default.ShoppingCart
        "directions_car" -> Icons.Default.DirectionsCar
        "receipt" -> Icons.Default.Receipt
        "phone_android" -> Icons.Default.PhoneAndroid
        "restaurant" -> Icons.Default.Restaurant
        "person" -> Icons.Default.Person
        "account_balance_wallet" -> Icons.Default.AccountBalanceWallet
        "atm" -> Icons.Default.Atm
        "savings" -> Icons.Default.Savings
        "shopping_bag" -> Icons.Default.ShoppingBag
        "public" -> Icons.Default.Public
        "account_balance" -> Icons.Default.AccountBalance
        "category" -> Icons.Default.Category
        else -> Icons.Default.Category
    }
}
