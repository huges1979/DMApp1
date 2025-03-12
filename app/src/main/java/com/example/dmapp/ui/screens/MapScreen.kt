package com.example.dmapp.ui.screens

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderStatus
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.mapview.MapView

private var isMapKitInitialized = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    orders: List<Order>,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        try {
            if (!isMapKitInitialized) {
                MapKitFactory.setApiKey("2a96384f-ba39-4724-b534-2cb3097f3e0d")
                MapKitFactory.initialize(context)
                isMapKitInitialized = true
            }
            MapKitFactory.getInstance().onStart()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try {
                mapView?.onStop()
                MapKitFactory.getInstance().onStop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карта заказов") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (!isMapReady) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(width = 48.dp, height = 48.dp)
                        .align(androidx.compose.ui.Alignment.Center)
                )
            }

            AndroidView(
                factory = { context ->
                    MapView(context).also { view ->
                        mapView = view
                        view.mapWindow.map.move(
                            CameraPosition(
                                Point(55.751574, 37.573856),
                                11.0f,
                                0.0f,
                                0.0f
                            )
                        )
                        isMapReady = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    try {
                        val map = view.mapWindow.map
                        map.mapObjects.clear()
                        orders.forEach { order ->
                            if (order.latitude != null && order.longitude != null) {
                                val point = Point(order.latitude, order.longitude)
                                val placemark = map.mapObjects.addPlacemark(point)
                                placemark.opacity = if (order.status == OrderStatus.COMPLETED) 0.7f else 1.0f
                            }
                        }

                        orders.firstOrNull { it.latitude != null && it.longitude != null }?.let { order ->
                            map.move(
                                CameraPosition(
                                    Point(order.latitude!!, order.longitude!!),
                                    11.0f,
                                    0.0f,
                                    0.0f
                                ),
                                Animation(Animation.Type.SMOOTH, 0.3f),
                                null
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
        }
    }
} 