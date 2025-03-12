package com.example.dmapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dmapp.data.Order

@Composable
fun OrdersList(
    modifier: Modifier = Modifier,
    activeOrders: List<Order>,
    completedOrders: List<Order>,
    activeOrdersCount: Int,
    completedOrdersCount: Int,
    onOrderClick: (Order) -> Unit,
    onClearCompleted: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Активные заказы
        item {
            Text(
                text = "Активные заказы ($activeOrdersCount)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        items(activeOrders) { order ->
            OrderItem(
                order = order,
                onOrderClick = { onOrderClick(order) }
            )
        }
        
        // Выполненные заказы
        if (completedOrders.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Выполненные заказы ($completedOrdersCount)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onClearCompleted) {
                        Text("Очистить")
                    }
                }
            }
            
            items(completedOrders) { order ->
                OrderItem(
                    order = order,
                    onOrderClick = { onOrderClick(order) }
                )
            }
        }
    }
} 