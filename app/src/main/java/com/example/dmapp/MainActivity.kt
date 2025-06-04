package com.example.dmapp

import android.app.Application
import android.net.Uri
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
import androidx.compose.material.icons.filled.BarChart
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
import com.example.dmapp.data.OrderStatus
import com.example.dmapp.data.ImportResult
import com.example.dmapp.data.StatisticsRepository
import com.example.dmapp.ui.OrderViewModel
import com.example.dmapp.ui.components.ImportDialog
import com.example.dmapp.ui.components.OrdersList
import com.example.dmapp.ui.screens.MainScreen
import com.example.dmapp.ui.screens.MapScreen
import com.example.dmapp.ui.screens.OrderDetailScreen
import com.example.dmapp.ui.screens.StatisticsScreen
import com.example.dmapp.ui.screens.PhotoCaptureScreen
import com.example.dmapp.ui.screens.FullScreenPhotoViewer
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.search.SearchFactory
import kotlinx.coroutines.flow.StateFlow

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
    private lateinit var orderRepository: OrderRepository
    private lateinit var statisticsRepository: StatisticsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and repository
        val database = AppDatabase.getDatabase(applicationContext)
        orderRepository = OrderRepository(
            orderDao = database.orderDao(),
            context = this
        )
        statisticsRepository = StatisticsRepository(this)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(
            this,
            OrderViewModel.Factory(orderRepository, applicationContext)
        )[OrderViewModel::class.java]

        // Migrate existing orders to statistics
        viewModel.migrateCompletedOrdersToStatistics()

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
                            onDeleteResultDismiss = { viewModel.clearDeleteResult() },
                            onStatusUpdate = { order, newStatus: OrderStatus ->
                                viewModel.updateOrderStatus(order, newStatus)
                            },
                            onStatisticsClick = {
                                navController.navigate("statistics")
                            },
                            viewModel = viewModel,
                            orderRepository = orderRepository
                        )

                        if (showImportDialog) {
                            ImportDialog(
                                onDismiss = { showImportDialog = false },
                                onImport = { text, isNewFormat ->
                                    if (isNewFormat) {
                                        viewModel.importOrdersNewFormat(text)
                                    } else {
                                        viewModel.importOrders(text)
                                    }
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
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onEditOrder = { editedOrder ->
                                    // Например, обновление статуса или заметок
                                    if (editedOrder.status != it.status) {
                                        viewModel.updateOrderStatus(editedOrder, editedOrder.status)
                                        
                                        // Закрываем экран только при изменении статуса
                                        navController.popBackStack()
                                    } else if (editedOrder.notes != it.notes) {
                                        // При изменении заметок просто обновляем данные, но не закрываем экран
                                        viewModel.updateOrderNotes(editedOrder.id, editedOrder.notes ?: "")
                                        // Обновляем сохраненный заказ в навигационном стеке
                                        navController.currentBackStackEntry?.savedStateHandle?.set("order", editedOrder)
                                    }
                                },
                                onTakePhoto = { orderToPhotograph ->
                                    // Сохраняем заказ в навигационном стеке и переходим на экран фото
                                    navController.currentBackStackEntry?.savedStateHandle?.set("order_for_photo", orderToPhotograph)
                                    navController.navigate("photo_capture")
                                },
                                onViewPhoto = { photoUri ->
                                    // Сохраняем URI фото и переходим на экран просмотра
                                    navController.currentBackStackEntry?.savedStateHandle?.set("photo_uri", photoUri.toString())
                                    navController.navigate("photo_viewer")
                                }
                            )
                        }
                    }
                    
                    composable("statistics") {
                        val statistics by viewModel.statistics.collectAsState()
                        StatisticsScreen(
                            statistics = statistics,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onRebuildStatistics = {
                                viewModel.rebuildStatistics()
                            },
                            onClearStatistics = {
                                viewModel.clearStatistics()
                            },
                            onOrderClick = { order ->
                                navController.currentBackStackEntry?.savedStateHandle?.set("order", order)
                                navController.navigate("order_detail")
                            },
                            viewModel = viewModel,
                            navController = navController,
                            orderRepository = orderRepository
                        )
                    }
                    
                    composable("photo_capture") {
                        val order = navController.previousBackStackEntry?.savedStateHandle?.get<Order>("order_for_photo")
                        order?.let {
                            PhotoCaptureScreen(
                                order = it,
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onPhotoSaved = {
                                    // После сохранения фото возвращаемся назад
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                    
                    composable("photo_viewer") {
                        val photoUriString = navController.previousBackStackEntry?.savedStateHandle?.get<String>("photo_uri")
                        photoUriString?.let { uriString ->
                            FullScreenPhotoViewer(
                                photoUri = Uri.parse(uriString),
                                onClose = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }

                    composable("map") {
                        val orders by viewModel.activeOrders.collectAsState(initial = emptyList())
                        val completedOrders by viewModel.completedOrders.collectAsState(initial = emptyList())
                        MapScreen(
                            orders = orders + completedOrders,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onStatusUpdate = { order, newStatus ->
                                viewModel.updateOrderStatus(order, newStatus)
                            },
                            viewModel = viewModel
                        )
                    }
                }

                // Обработка событий навигации
                LaunchedEffect(Unit) {
                    viewModel.navigationEvent.collect { event ->
                        when (event) {
                            is OrderViewModel.NavigationEvent.PhotoCapture -> {
                                navController.currentBackStackEntry?.savedStateHandle?.set("order_for_photo", event.order)
                                navController.navigate("photo_capture")
                            }
                            is OrderViewModel.NavigationEvent.PhotoViewer -> {
                                navController.currentBackStackEntry?.savedStateHandle?.set("photo_uri", event.photoUri.toString())
                                navController.navigate("photo_viewer")
                            }
                            null -> {}
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

    private fun getMarkerColor(order: Order): Int {
        if (order.status == OrderStatus.IN_PROGRESS) {
            return android.graphics.Color.rgb(144, 238, 144) // Light Green
        }

        val deliveryHour = order.deliveryTimeStart.hour
        return when {
            deliveryHour in 11..13 -> android.graphics.Color.rgb(135, 206, 235) // Sky Blue (11:00 - 14:00)
            deliveryHour in 14..16 -> android.graphics.Color.rgb(255, 165, 0)   // Orange (14:00 - 17:00)
            deliveryHour in 17..19 -> android.graphics.Color.RED                 // Red (17:00 - 20:00)
            deliveryHour in 20..22 -> android.graphics.Color.rgb(148, 0, 211)   // Purple (20:00 - 23:00)
            else -> android.graphics.Color.GRAY
        }
    }
}