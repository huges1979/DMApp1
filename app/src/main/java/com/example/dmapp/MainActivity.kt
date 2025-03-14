package com.example.dmapp

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.dmapp.data.AppDatabase
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderRepository
import com.example.dmapp.ui.OrderViewModel
import com.example.dmapp.ui.components.ImportDialog
import com.example.dmapp.ui.components.OrdersList
import com.example.dmapp.ui.screens.MainScreen
import com.example.dmapp.ui.screens.MapScreen
import com.example.dmapp.ui.screens.OrderDetailScreen
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.search.SearchFactory

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            MapKitFactory.setApiKey("2a96384f-ba39-4724-b534-2cb3097f3e0d")
            MapKitFactory.initialize(this)
            SearchFactory.initialize(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: OrderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = OrderRepository(database.orderDao())
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(
            this,
            OrderViewModel.Factory(repository)
        )[OrderViewModel::class.java]

        setContent {
            MaterialTheme {
                var showImportDialog by remember { mutableStateOf(false) }
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        val activeOrders by viewModel.activeOrders.collectAsState(initial = emptyList())
                        val completedOrders by viewModel.completedOrders.collectAsState(initial = emptyList())
                        val activeOrdersCount by viewModel.activeOrdersCount.collectAsState(initial = 0)
                        val completedOrdersCount by viewModel.completedOrdersCount.collectAsState(initial = 0)

                        MainScreen(
                            activeOrders = activeOrders,
                            completedOrders = completedOrders,
                            activeOrdersCount = activeOrdersCount,
                            completedOrdersCount = completedOrdersCount,
                            importResult = viewModel.importResult,
                            deleteResult = viewModel.deleteResult,
                            onOrderClick = { order ->
                                navController.currentBackStackEntry?.savedStateHandle?.set("order", order)
                                navController.navigate("order_detail")
                            },
                            onImportClick = { showImportDialog = true },
                            onClearCompleted = { viewModel.clearCompletedOrders() },
                            onImportDialogDismiss = { viewModel.clearImportResult() },
                            onDeleteResultDismiss = { viewModel.clearDeleteResult() }
                        )

                        if (showImportDialog) {
                            ImportDialog(
                                onDismiss = { showImportDialog = false },
                                onImport = { text ->
                                    viewModel.importOrders(text)
                                    showImportDialog = false
                                }
                            )
                        }
                    }

                    composable("order_detail") {
                        val order = navController.previousBackStackEntry?.savedStateHandle?.get<Order>("order")
                        order?.let {
                            OrderDetailScreen(
                                order = it,
                                onStatusUpdate = { order, newStatus ->
                                    viewModel.updateOrderStatus(order, newStatus)
                                    navController.popBackStack()
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNotesUpdate = { orderId, notes ->
                                    viewModel.updateOrderNotes(orderId, notes)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            MapKitFactory.getInstance().onStart()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        try {
            MapKitFactory.getInstance().onStop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    activeOrders: List<Order>,
    completedOrders: List<Order>,
    activeOrdersCount: Int,
    completedOrdersCount: Int,
    importResult: String,
    deleteResult: String,
    onOrderClick: (Order) -> Unit,
    onImportClick: () -> Unit,
    onClearCompleted: () -> Unit,
    onImportDialogDismiss: () -> Unit,
    onDeleteResultDismiss: () -> Unit
) {
    var showMap by remember { mutableStateOf(false) }
    
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
                        // Кнопка карты с текстом
                        TextButton(
                            onClick = { showMap = true },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Map", color = MaterialTheme.colorScheme.primary)
                        }
                        // Кнопка импорта
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
            Box(modifier = Modifier.fillMaxSize()) {
                OrdersList(
                    modifier = Modifier.padding(paddingValues),
                    activeOrders = activeOrders,
                    completedOrders = completedOrders,
                    activeOrdersCount = activeOrdersCount,
                    completedOrdersCount = completedOrdersCount,
                    onOrderClick = onOrderClick,
                    onClearCompleted = onClearCompleted
                )
                
                // Показываем сообщения об импорте/удалении
                if (importResult.isNotEmpty()) {
                    AlertDialog(
                        onDismissRequest = onImportDialogDismiss,
                        title = { Text("Результат импорта") },
                        text = { Text(importResult) },
                        confirmButton = {
                            TextButton(onClick = onImportDialogDismiss) {
                                Text("OK")
                            }
                        }
                    )
                }
                
                if (deleteResult.isNotEmpty()) {
                    AlertDialog(
                        onDismissRequest = onDeleteResultDismiss,
                        title = { Text("Удаление заказов") },
                        text = { Text(deleteResult) },
                        confirmButton = {
                            TextButton(onClick = onDeleteResultDismiss) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}