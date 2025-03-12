package com.example.dmapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderStatus
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: Order,
    onStatusUpdate: (Order, OrderStatus) -> Unit,
    onNavigateBack: () -> Unit,
    onNotesUpdate: (Long, String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заказ #${order.orderNumber}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Статус заказа
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (order.status) {
                        OrderStatus.NEW -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        OrderStatus.IN_PROGRESS -> Color(0xFFE8F5E9)
                        OrderStatus.COMPLETED -> Color(0xFFF5F5F5)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Статус заказа",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when(order.status) {
                            OrderStatus.NEW -> "Новый"
                            OrderStatus.IN_PROGRESS -> "В работе"
                            OrderStatus.COMPLETED -> "Выполнен"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = when (order.status) {
                            OrderStatus.NEW -> MaterialTheme.colorScheme.primary
                            OrderStatus.IN_PROGRESS -> Color(0xFF2E7D32)
                            OrderStatus.COMPLETED -> Color.Gray
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Основная информация
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Внешний номер заказа
                    Text(
                        text = "Номер заказа в системе: ${order.externalOrderNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Время доставки
                    Text(
                        text = "Интервал доставки",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${order.deliveryTimeStart.format(DateTimeFormatter.ofPattern("HH:mm"))} - " +
                                order.deliveryTimeEnd.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Информация о клиенте
                    Text(
                        text = "Информация о получателе",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = order.clientName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Text(
                        text = order.clientPhone,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Text(
                        text = order.deliveryAddress,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    
                    if (!order.clientComment.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Комментарий: ${order.clientComment}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Информация о заказе
                    Text(
                        text = "Детали заказа",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Вес: ${order.weight} кг",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Объем: ${order.volume} м³",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${order.orderAmount} ₽",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (order.isPrepaid) "Предоплачен" else "Не предоплачен",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (order.isPrepaid) Color(0xFF2E7D32) else Color.Gray
                            )
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Информация о месте забора
                    Text(
                        text = "Место забора",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = order.pickupLocation,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Сектор ${order.sector}, Место ${order.place}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Заметки
                    Text(
                        text = "Заметки",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    var notes by remember { mutableStateOf(order.notes) }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { 
                            notes = it
                            onNotesUpdate(order.id, it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Добавьте заметку...") },
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Кнопка изменения статуса
            when (order.status) {
                OrderStatus.NEW -> {
                    Button(
                        onClick = { onStatusUpdate(order, OrderStatus.IN_PROGRESS) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Взять в работу")
                    }
                }
                OrderStatus.IN_PROGRESS -> {
                    Button(
                        onClick = { onStatusUpdate(order, OrderStatus.COMPLETED) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32)
                        )
                    ) {
                        Text("Закончить")
                    }
                }
                OrderStatus.COMPLETED -> {
                    // Нет кнопки для выполненных заказов
                }
            }
        }
    }
} 