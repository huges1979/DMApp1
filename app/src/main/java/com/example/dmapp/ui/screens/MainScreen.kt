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
import kotlinx.coroutines.launch

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
import com.example.dmapp.data.OrderRepository
import androidx.compose.ui.Alignment

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
    viewModel: OrderViewModel,
    orderRepository: OrderRepository
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
    
    // Состояния для фильтра по статусу
    var selectedActiveStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var expandedActive by remember { mutableStateOf(false) }
    var selectedCompletedStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var expandedCompleted by remember { mutableStateOf(false) }
    
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

    // Фильтрация по статусу + поиск
    val filteredActiveOrdersByStatus = remember(filteredActiveOrders, selectedActiveStatus) {
        if (selectedActiveStatus == null) filteredActiveOrders else filteredActiveOrders.filter { it.status == selectedActiveStatus }
    }
    val filteredCompletedOrdersByStatus = remember(filteredCompletedOrders, selectedCompletedStatus) {
        if (selectedCompletedStatus == null) filteredCompletedOrders else filteredCompletedOrders.filter { it.status == selectedCompletedStatus }
    }

    val scope = rememberCoroutineScope()
    var orderToDelete by remember { mutableStateOf<Order?>(null) }

    // Диалог подтверждения удаления
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
            },
            floatingActionButton = {
                // ... existing code ...
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
                // Фильтр по статусу для активных/выполненных
                if (selectedTabIndex == 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Статус:", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            Button(onClick = { expandedActive = true }) {
                                Text(selectedActiveStatus?.getDisplayName() ?: "Все")
                            }
                            DropdownMenu(
                                expanded = expandedActive,
                                onDismissRequest = { expandedActive = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Все") },
                                    onClick = {
                                        selectedActiveStatus = null
                                        expandedActive = false
                                    }
                                )
                                OrderStatus.values().forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.getDisplayName()) },
                                        onClick = {
                                            selectedActiveStatus = status
                                            expandedActive = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Статус:", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            Button(onClick = { expandedCompleted = true }) {
                                Text(selectedCompletedStatus?.getDisplayName() ?: "Все")
                            }
                            DropdownMenu(
                                expanded = expandedCompleted,
                                onDismissRequest = { expandedCompleted = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Все") },
                                    onClick = {
                                        selectedCompletedStatus = null
                                        expandedCompleted = false
                                    }
                                )
                                OrderStatus.values().forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.getDisplayName()) },
                                        onClick = {
                                            selectedCompletedStatus = status
                                            expandedCompleted = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                when (selectedTabIndex) {
                    0 -> {
                        LazyColumn {
                            items(filteredActiveOrdersByStatus) { order ->
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
                    1 -> {
                        LazyColumn {
                            items(filteredCompletedOrdersByStatus) { order ->
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
                            item {
                                if (filteredCompletedOrdersByStatus.isNotEmpty()) {
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