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
import com.yandex.mapkit.search.*
import com.yandex.runtime.Error
import kotlinx.coroutines.*
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import kotlin.coroutines.resume
import com.yandex.runtime.image.ImageProvider
import android.graphics.*
import androidx.core.content.ContextCompat
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.DrawableCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    orders: List<Order>,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val searchManager = remember { SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED) }
    var geocodedOrders by remember { mutableStateOf<List<Pair<Order, Point?>>>(emptyList()) }

    // Функция для вычисления расстояния между точками в метрах
    fun calculateDistance(point1: Point, point2: Point): Double {
        val earthRadius = 6371000.0 // радиус Земли в метрах
        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val deltaLat = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    // Функция для смещения точки
    fun offsetPoint(point: Point, bearing: Double, distanceMeters: Double): Point {
        val earthRadius = 6371000.0 // радиус Земли в метрах
        val bearingRad = Math.toRadians(bearing)
        val lat1 = Math.toRadians(point.latitude)
        val lon1 = Math.toRadians(point.longitude)
        val angularDistance = distanceMeters / earthRadius

        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(angularDistance) +
            Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearingRad)
        )
        val lon2 = lon1 + Math.atan2(
            Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(lat1),
            Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2)
        )

        return Point(
            Math.toDegrees(lat2),
            Math.toDegrees(lon2)
        )
    }

    // Функция для определения цвета маркера на основе времени заказа и статуса
    fun getMarkerColor(order: Order): Int {
        // Если заказ в работе, возвращаем светло-зеленый
        if (order.status == OrderStatus.IN_PROGRESS) {
            return Color.rgb(144, 238, 144) // Light Green
        }

        // Получаем час из времени доставки
        val deliveryHour = order.deliveryTimeStart.hour

        return when {
            deliveryHour in 11..14 -> Color.rgb(135, 206, 235) // Sky Blue (11:00 - 15:00)
            deliveryHour in 14..16 -> Color.rgb(255, 165, 0)   // Orange (14:00 - 17:00)
            deliveryHour in 17..19 -> Color.RED                 // Red (17:00 - 20:00)
            deliveryHour in 20..22 -> Color.rgb(148, 0, 211)   // Purple (20:00 - 23:00)
            else -> Color.GRAY                                  // Default color
        }
    }

    // Функция для создания маркера с номером
    fun createMarkerBitmap(context: android.content.Context, order: Order): Bitmap {
        val size = 120 // Размер маркера в пикселях
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = getMarkerColor(order)
        paint.style = Paint.Style.FILL
        
        // Рисуем тень
        paint.setShadowLayer(4f, 0f, 2f, Color.argb(50, 0, 0, 0))
        
        // Рисуем круглую головку булавки
        val circleRadius = size * 0.35f
        canvas.drawCircle(size/2f, size * 0.35f, circleRadius, paint)
        
        // Рисуем острие булавки
        val path = Path()
        val pinWidth = circleRadius * 0.7f
        path.moveTo(size/2f - pinWidth, size * 0.35f)
        path.lineTo(size/2f, size * 0.85f)  // Кончик булавки
        path.lineTo(size/2f + pinWidth, size * 0.35f)
        path.close()
        canvas.drawPath(path, paint)
        
        // Убираем тень для текста
        paint.clearShadowLayer()
        
        // Рисуем белый круг для номера
        paint.color = Color.WHITE
        paint.alpha = 230
        canvas.drawCircle(size/2f, size * 0.35f, circleRadius * 0.7f, paint)
        
        // Рисуем номер
        paint.color = Color.BLACK
        paint.alpha = 255
        paint.textSize = circleRadius * 0.9f
        paint.textAlign = Paint.Align.CENTER
        val textHeight = paint.descent() - paint.ascent()
        val textOffset = textHeight / 2 - paint.descent()
        canvas.drawText(
            order.orderNumber.toString(),
            size/2f,
            size * 0.35f + textOffset,
            paint
        )
        
        return bitmap
    }

    // Функция для форматирования адреса
    fun formatAddress(address: String): String {
        return address.trim()
            .replace("\\s+".toRegex(), " ") // Убираем лишние пробелы
            .replace(Regex("(?i)г\\.?\\s*москва[,\\s]*"), "") // Убираем "г. Москва"
            .replace(Regex("(?i)москва[,\\s]*"), "") // Убираем "Москва"
            .replace(Regex("(?i)город\\s+"), "") // Убираем "город"
            .replace(Regex("(?i)гор\\.?\\s*"), "") // Убираем "гор."
            .replace(Regex("(?i)дом\\s+(\\d)"), "д. $1") // Заменяем "дом N" на "д. N"
            .replace(Regex("(?i)д\\s+(\\d)"), "д. $1") // Заменяем "д N" на "д. N"
            .replace(Regex("(?i)улица\\s+"), "ул. ") // Заменяем "улица" на "ул."
            .replace(Regex("(?i)ул\\s+"), "ул. ") // Заменяем "ул " на "ул."
            .replace(Regex("(?i)проспект\\s+"), "пр-т ") // Заменяем "проспект" на "пр-т"
            .replace(Regex("(?i)пр\\s+"), "пр-т ") // Заменяем "пр " на "пр-т"
            .let { addr -> 
                "Москва, $addr"
            }
            .trim()
    }

    LaunchedEffect(orders) {
        scope.launch {
            println("\n=== Starting geocoding process for ${orders.size} orders ===\n")
            
            geocodedOrders = orders.map { order ->
                if (order.latitude != null && order.longitude != null) {
                    // Проверяем, что координаты не являются дефолтными (центр Москвы)
                    if (order.latitude != 55.751574 && order.longitude != 37.573856) {
                        println("Order ${order.orderNumber} has valid coordinates: ${order.latitude}, ${order.longitude}")
                        order to Point(order.latitude, order.longitude)
                    } else {
                        println("\n=== Order ${order.orderNumber} has default coordinates, will try geocoding ===")
                        println("Original address: ${order.deliveryAddress}")
                        null
                    }
                } else {
                    println("\n=== Order ${order.orderNumber} has no coordinates, will try geocoding ===")
                    println("Original address: ${order.deliveryAddress}")
                    null
                }
            }.filterNotNull() + orders.filter { order ->
                (order.latitude == null || order.longitude == null ||
                 (order.latitude == 55.751574 && order.longitude == 37.573856))
            }.map { order ->
                val formattedAddress = formatAddress(order.deliveryAddress)
                println("\nProcessing Order ${order.orderNumber}")
                println("Original address: ${order.deliveryAddress}")
                println("Formatted address: $formattedAddress")
                
                val point = suspendCancellableCoroutine<Point?> { continuation ->
                    var session: Session? = null
                    try {
                        val options = SearchOptions().apply {
                            searchTypes = SearchType.GEO.value
                            resultPageSize = 1
                        }

                        session = searchManager.submit(
                            formattedAddress,
                            Geometry.fromPoint(Point(55.751574, 37.573856)), // Центр Москвы как отправная точка
                            options,
                            object : Session.SearchListener {
                                override fun onSearchResponse(response: Response) {
                                    val result = response.collection.children.firstOrNull()?.obj
                                    val point = result?.geometry?.firstOrNull()?.point
                                    
                                    if (point != null) {
                                        println("\nGeocoding SUCCESS for order ${order.orderNumber}")
                                        println("Input address: $formattedAddress")
                                        println("Found location: ${result.name}")
                                        println("Found address: ${result.descriptionText}")
                                        println("Coordinates: $point")
                                        continuation.resume(point) { session?.cancel() }
                                    } else {
                                        println("\nGeocoding FAILED for order ${order.orderNumber}")
                                        println("Input address: $formattedAddress")
                                        println("No results found")
                                        continuation.resume(null) { session?.cancel() }
                                    }
                                }

                                override fun onSearchError(error: Error) {
                                    println("\nGeocoding ERROR for order ${order.orderNumber}")
                                    println("Input address: $formattedAddress")
                                    println("Error: $error")
                                    continuation.resume(null) { session?.cancel() }
                                }
                            }
                        )

                        continuation.invokeOnCancellation {
                            session?.cancel()
                        }
                    } catch (e: Exception) {
                        println("\nEXCEPTION during geocoding for order ${order.orderNumber}")
                        println("Input address: $formattedAddress")
                        println("Error: ${e.message}")
                        e.printStackTrace()
                        continuation.resume(null) { session?.cancel() }
                    }
                }
                order to point
            }
            
            println("\n=== Geocoding process completed ===\n")
            println("Successfully geocoded: ${geocodedOrders.count { it.second != null }}")
            println("Failed to geocode: ${geocodedOrders.count { it.second == null }}")
            
            // Выводим адреса, которые не удалось геокодировать
            geocodedOrders.filter { it.second == null }.forEach { (order, _) ->
                println("\nFailed to geocode order ${order.orderNumber}")
                println("Address: ${order.deliveryAddress}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mapView?.onStop()
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

            errorMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = { errorMessage = null },
                    title = { Text("Ошибка") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = { errorMessage = null }) {
                            Text("OK")
                        }
                    }
                )
            }

            AndroidView(
                factory = { context ->
                    try {
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Ошибка при инициализации карты: ${e.message}"
                        MapView(context)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    try {
                        val map = view.mapWindow.map
                        map.mapObjects.clear()
                        
                        // Фильтруем и создаем список точек с возможностью модификации
                        val adjustedPoints = geocodedOrders
                            .filter { (order, _) -> order.status != OrderStatus.COMPLETED } // Фильтруем завершенные заказы
                            .mapNotNull { (order, point) ->
                                point?.let { Triple(order, it, false) }
                            }.toMutableList()

                        // Минимальное расстояние между маркерами в метрах
                        val minDistance = 50.0
                        // Расстояние смещения в метрах
                        val offsetDistance = 30.0

                        // Корректируем позиции близких маркеров
                        for (i in adjustedPoints.indices) {
                            if (adjustedPoints[i].third) continue // Пропускаем уже обработанные точки
                            
                            for (j in i + 1 until adjustedPoints.size) {
                                if (adjustedPoints[j].third) continue // Пропускаем уже обработанные точки
                                
                                val distance = calculateDistance(adjustedPoints[i].second, adjustedPoints[j].second)
                                if (distance < minDistance) {
                                    // Смещаем вторую точку
                                    val bearing = (j * 60.0) % 360.0 // Распределяем точки по кругу
                                    val newPoint = offsetPoint(adjustedPoints[i].second, bearing, offsetDistance)
                                    adjustedPoints[j] = Triple(adjustedPoints[j].first, newPoint, true)
                                }
                            }
                        }

                        // Отображаем маркеры с учетом скорректированных позиций
                        adjustedPoints.forEach { (order, point, _) ->
                            val placemark = map.mapObjects.addPlacemark(point)
                            val markerBitmap = createMarkerBitmap(view.context, order)
                            placemark.setIcon(ImageProvider.fromBitmap(markerBitmap))
                        }

                        // Центрируем карту на первой точке
                        adjustedPoints.firstOrNull()?.let { (_, point, _) ->
                            map.move(
                                CameraPosition(
                                    point,
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
                        errorMessage = "Ошибка при обновлении карты: ${e.message}"
                    }
                }
            )
        }
    }
} 