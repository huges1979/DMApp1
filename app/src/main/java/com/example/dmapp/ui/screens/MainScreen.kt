package com.example.dmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dmapp.data.ImportResult
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderStatus
import com.example.dmapp.ui.components.OrderItem
import com.example.dmapp.ui.OrderViewModel
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.Color

// Добавляем импорты для функционала поиска
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    activeOrders: List<Order>,
    completedOrders: List<Order>,
    activeOrdersCount: Int,
    completedOrdersCount: Int,
    importResult: StateFlow<ImportResult?>,
    deleteResult: StateFlow<Int?>,
    onOrderClick: (Order) -> Unit,
    onImportClick: () -> Unit,
    onClearCompleted: () -> Unit,
    onImportDialogDismiss: () -> Unit,
    onDeleteResultDismiss: () -> Unit,
    onStatusUpdate: ((Order, OrderStatus) -> Unit)? = null,
    onStatisticsClick: () -> Unit,
    viewModel: OrderViewModel
) {
    println("\n=== MainScreen: Обновление UI ===")
    println("Активных заказов: ${activeOrders.size}, счетчик: $activeOrdersCount")
    println("Выполненных заказов: ${completedOrders.size}, счетчик: $completedOrdersCount")
    
    if (activeOrders.isNotEmpty()) {
        println("Первый активный заказ: №${activeOrders[0].orderNumber}, статус: ${activeOrders[0].status}")
    }
    
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showMap by remember { mutableStateOf(false) }
    val tabs = listOf("Активные ($activeOrdersCount)", "Выполненные ($completedOrdersCount)")

    // Состояния для поиска
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Фильтруем заказы на основе поискового запроса
    val filteredActiveOrders = remember(activeOrders, searchQuery) {
        if (searchQuery.isEmpty()) {
            activeOrders
        } else {
            activeOrders.filter { order ->
                order.containsSearchQuery(searchQuery)
            }
        }
    }
    
    val filteredCompletedOrders = remember(completedOrders, searchQuery) {
        if (searchQuery.isEmpty()) {
            completedOrders
        } else {
            completedOrders.filter { order ->
                order.containsSearchQuery(searchQuery)
            }
        }
    }

    if (showMap) {
        MapScreen(
            orders = activeOrders + completedOrders,
            onBackClick = { showMap = false },
            onStatusUpdate = onStatusUpdate,
            viewModel = viewModel
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text("Поиск заказов...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                    }
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Поиск"
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Очистить"
                                            )
                                        }
                                    }
                                }
                            )
                            
                            // Запрашиваем фокус при активации поиска
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            Text("Заказы")
                        }
                    },
                    actions = {
                        if (!isSearchActive) {
                            // Иконка поиска
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Поиск"
                                )
                            }
                            
                            // Остальные действия
                            IconButton(onClick = onStatisticsClick) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Статистика"
                                )
                            }
                            Button(
                                onClick = { showMap = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "Map",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            IconButton(onClick = onImportClick) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = "Импорт заказов"
                                )
                            }
                        } else {
                            // Кнопка для закрытия поиска
                            IconButton(onClick = { 
                                isSearchActive = false
                                searchQuery = ""
                                keyboardController?.hide()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть поиск"
                                )
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTabIndex) {
                    0 -> {
                        LazyColumn {
                            items(filteredActiveOrders) { order ->
                                OrderItem(
                                    order = order,
                                    onOrderClick = { onOrderClick(order) },
                                    // Кнопка для быстрого изменения статуса
                                    onStatusUpdate = {
                                        val newStatus = if (order.status == OrderStatus.COMPLETED) {
                                            OrderStatus.NEW
                                        } else {
                                            OrderStatus.COMPLETED
                                        }
                                        viewModel.updateOrderStatus(order, newStatus)
                                    }
                                )
                            }
                        }
                    }
                    1 -> {
                        LazyColumn {
                            items(filteredCompletedOrders) { order ->
                                OrderItem(
                                    order = order,
                                    onOrderClick = { onOrderClick(order) }
                                )
                            }
                            item {
                                if (filteredCompletedOrders.isNotEmpty()) {
                                    TextButton(
                                        onClick = onClearCompleted,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text("Очистить выполненные")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Диалоги для результатов импорта и удаления
        importResult.collectAsState().value?.let { result ->
            AlertDialog(
                onDismissRequest = onImportDialogDismiss,
                title = { Text("Результат импорта") },
                text = { 
                    Text(
                        "Импортировано новых заказов: ${result.newOrders}\n" +
                        "Найдено дублей: ${result.duplicates}"
                    )
                },
                confirmButton = {
                    TextButton(onClick = onImportDialogDismiss) {
                        Text("OK")
                    }
                }
            )
        }

        deleteResult.collectAsState().value?.let { count ->
            AlertDialog(
                onDismissRequest = onDeleteResultDismiss,
                title = { Text("Удаление заказов") },
                text = { Text("Удалено выполненных заказов: $count") },
                confirmButton = {
                    TextButton(onClick = onDeleteResultDismiss) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

/**
 * Проверяет, содержит ли заказ указанный поисковый запрос в любом из своих полей
 */
private fun Order.containsSearchQuery(query: String): Boolean {
    if (query.isEmpty()) return true
    
    val normalizedQuery = query.lowercase().trim()
    
    return externalOrderNumber?.lowercase()?.contains(normalizedQuery) == true ||
           orderNumber.toString().contains(normalizedQuery) ||
           clientName.lowercase().contains(normalizedQuery) ||
           clientPhone.lowercase().contains(normalizedQuery) ||
           deliveryAddress.lowercase().contains(normalizedQuery) ||
           clientComment?.lowercase()?.contains(normalizedQuery) == true ||
           pickupLocation.lowercase().contains(normalizedQuery) ||
           notes?.lowercase()?.contains(normalizedQuery) == true
} 