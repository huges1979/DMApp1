package com.example.dmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderRepository
import com.example.dmapp.data.OrderStatus
import com.example.dmapp.ui.components.OrderItem
import kotlinx.coroutines.launch

@Composable
fun OrdersScreen(
    orders: List<Order>,
    orderRepository: OrderRepository,
    onOrderClick: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // Диалог подтверждения удаления
    var orderToDelete by remember { mutableStateOf<Order?>(null) }
    
    if (orderToDelete != null) {
        AlertDialog(
            onDismissRequest = { orderToDelete = null },
            title = { Text("Удаление заказа") },
            text = { Text("Вы уверены, что хотите удалить заказ №${orderToDelete?.orderNumber}? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        orderToDelete?.let { order ->
                            scope.launch {
                                orderRepository.deleteOrder(order.id)
                                orderToDelete = null
                            }
                        }
                    }
                ) {
                    Text("Удалить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(orders) { order ->
            OrderItem(
                order = order,
                onOrderClick = { onOrderClick(order) },
                onStatusChange = { updatedOrder, newStatus ->
                    scope.launch {
                        orderRepository.updateOrderStatus(updatedOrder, newStatus)
                    }
                },
                onDeleteOrder = { orderToDelete = it }
            )
        }
    }
} 