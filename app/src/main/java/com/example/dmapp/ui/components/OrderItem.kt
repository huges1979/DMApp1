package com.example.dmapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderStatus
import java.time.format.DateTimeFormatter

@Composable
fun OrderItem(
    order: Order,
    onOrderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backgroundColor = when (order.status) {
        OrderStatus.NEW -> Color.White
        OrderStatus.IN_PROGRESS -> Color(0xFFE8F5E9) // Light Green
        OrderStatus.COMPLETED -> Color.White // Changed from Light Gray to White
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onOrderClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            // Заголовок с номером заказа и статусом
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("${order.orderNumber} Заказ ")
                        val externalNumber = order.externalOrderNumber
                        if (externalNumber.length >= 4) {
                            val mainPart = externalNumber.substring(0, externalNumber.length - 4)
                            val lastFourDigits = externalNumber.substring(externalNumber.length - 4)
                            append(mainPart)
                            withStyle(style = SpanStyle(
                                color = Color(0xFF03A9F4), // Light Blue
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )) {
                                append(lastFourDigits)
                            }
                        } else {
                            append(externalNumber)
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when(order.status) {
                        OrderStatus.NEW -> "Новый"
                        OrderStatus.IN_PROGRESS -> "В работе"
                        OrderStatus.COMPLETED -> "Выполнен"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (order.status) {
                        OrderStatus.NEW -> MaterialTheme.colorScheme.primary
                        OrderStatus.IN_PROGRESS -> Color(0xFF2E7D32) // Dark Green
                        OrderStatus.COMPLETED -> Color.Gray
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Время доставки
            Text(
                text = "Доставка: ${order.deliveryTimeStart.format(DateTimeFormatter.ofPattern("HH:mm"))} - " +
                        order.deliveryTimeEnd.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Адрес доставки
            Text(
                text = order.deliveryAddress,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Информация о клиенте и кнопки связи
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${order.clientName} • ${order.clientPhone}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 18.sp,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                
                // Кнопки связи
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${order.clientPhone}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Позвонить",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:${order.clientPhone}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "Отправить SMS",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://wa.me/${order.clientPhone.replace("+", "")}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text(
                            text = "💬",
                            fontSize = 24.sp,
                            color = Color(0xFF25D366) // WhatsApp green color
                        )
                    }
                }
            }
            
            // Заметки
            if (order.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📝 ${order.notes}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Дополнительная информация
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${order.weight} кг",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black
                )
                Text(
                    text = "${order.orderAmount} ₽",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (order.isPrepaid) Color(0xFF2E7D32) else Color.Black
                )
            }
        }
    }
} 