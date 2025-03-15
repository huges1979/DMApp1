package com.example.dmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderStatus
import com.example.dmapp.ui.components.OrderDetailsContent

/**
 * Экран деталей заказа, который отображается при выборе заказа из списка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: Order,
    onNavigateBack: () -> Unit,
    onEditOrder: (Order) -> Unit = {}
) {
    // Больше не используем диалоговое окно для редактирования
    var editedOrder by remember { mutableStateOf(order) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заказ №${order.orderNumber}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Используем тот же компонент OrderDetailsContent, что и в MapScreen
            OrderDetailsContent(
                order = order,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                onStatusChange = { updatedOrder, newStatus ->
                    // Создаем копию заказа с новым статусом
                    val orderWithNewStatus = updatedOrder.copy(status = newStatus)
                    // Вызываем обработчик редактирования заказа
                    onEditOrder(orderWithNewStatus)
                },
                onNotesChange = { updatedOrder, newNotes ->
                    // Создаем копию заказа с новыми заметками
                    val orderWithNewNotes = updatedOrder.copy(notes = newNotes)
                    // Вызываем обработчик редактирования заказа
                    onEditOrder(orderWithNewNotes)
                }
            )
        }
    }
} 