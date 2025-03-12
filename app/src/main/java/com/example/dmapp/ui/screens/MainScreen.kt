package com.example.dmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dmapp.data.ImportResult
import com.example.dmapp.data.Order
import com.example.dmapp.ui.components.OrderItem
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
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
    onDeleteResultDismiss: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showMap by remember { mutableStateOf(false) }
    val tabs = listOf("Активные ($activeOrdersCount)", "Выполненные ($completedOrdersCount)")

    if (showMap) {
        MapScreen(
            orders = activeOrders + completedOrders,
            onNavigateBack = { showMap = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Заказы") },
                    actions = {
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
                            items(activeOrders) { order ->
                                OrderItem(
                                    order = order,
                                    onOrderClick = { onOrderClick(order) }
                                )
                            }
                        }
                    }
                    1 -> {
                        LazyColumn {
                            items(completedOrders) { order ->
                                OrderItem(
                                    order = order,
                                    onOrderClick = { onOrderClick(order) }
                                )
                            }
                            item {
                                if (completedOrders.isNotEmpty()) {
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