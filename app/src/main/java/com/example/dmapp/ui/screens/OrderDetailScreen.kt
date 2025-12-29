package com.example.dmapp.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onEditOrder: (Order) -> Unit = {},
    onTakePhoto: (Order) -> Unit = {},
    onViewPhoto: (Uri) -> Unit = {}
) {
    // Локальное состояние для отслеживания актуальной версии заказа
    var currentOrder by remember { mutableStateOf(order) }
    
    // Эффект для обновления локального состояния при изменении входного параметра
    LaunchedEffect(order) {
        currentOrder = order
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = buildAnnotatedString {
                            append("${currentOrder.orderNumber}.")
                            append("  ") // Расстояние перед словом "Заказ" (2 пробела)
                            append("Заказ ")
                            val externalNumber = currentOrder.externalOrderNumber
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
            // Используем компонент OrderDetailsContent с актуальной версией заказа
            OrderDetailsContent(
                order = currentOrder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                onStatusChange = { updatedOrder, newStatus ->
                    // Создаем копию заказа с новым статусом
                    val orderWithNewStatus = updatedOrder.copy(status = newStatus)
                    // Обновляем локальное состояние
                    currentOrder = orderWithNewStatus
                    // Вызываем обработчик редактирования заказа
                    onEditOrder(orderWithNewStatus)
                },
                onNotesChange = { updatedOrder, newNotes ->
                    // Создаем копию заказа с новыми заметками
                    val orderWithNewNotes = updatedOrder.copy(notes = newNotes)
                    // Обновляем локальное состояние
                    currentOrder = orderWithNewNotes
                    // Вызываем обработчик редактирования заказа
                    onEditOrder(orderWithNewNotes)
                },
                onTakePhoto = {
                    onTakePhoto(currentOrder)
                },
                onPhotoClick = { photoUri ->
                    onViewPhoto(photoUri)
                }
            )
        }
    }
} 