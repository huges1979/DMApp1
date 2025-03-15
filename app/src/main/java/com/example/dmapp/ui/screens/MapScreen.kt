package com.example.dmapp.ui.screens

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderStatus
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.search.*
import com.yandex.runtime.Error
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.*
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import kotlin.coroutines.resume
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import java.time.format.DateTimeFormatter
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.layout.WindowInsets
import com.example.dmapp.ui.components.OrderDetailsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import com.yandex.mapkit.map.MapObjectTapListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    orders: List<Order>,
    onNavigateBack: () -> Unit,
    onStatusUpdate: ((Order, OrderStatus) -> Unit)? = null
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Используем MutableState для selectedOrder
    val selectedOrderState = remember { mutableStateOf<Order?>(null) }
    var selectedOrder by selectedOrderState
    
    println("MapScreen initialized with selectedOrder = $selectedOrder")
    val scope = rememberCoroutineScope()
    val searchManager = remember { SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED) }
    var geocodedOrders by remember { mutableStateOf<List<Pair<Order, Point?>>>(emptyList()) }

    // Константы для границ Москвы
    val MOSCOW_BOUNDS = BoundingBox(
        Point(55.48992, 37.319331), // юго-западная граница Москвы
        Point(55.957565, 37.907543) // северо-восточная граница Москвы
    )

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
        if (order.status == OrderStatus.IN_PROGRESS) {
            return android.graphics.Color.rgb(144, 238, 144) // Light Green - для заказов в работе
        }

        val deliveryHour = order.deliveryTimeStart.hour
        return when {
            deliveryHour in 11..13 -> android.graphics.Color.rgb(135, 206, 235) // Sky Blue (11:00 - 13:59)
            deliveryHour in 14..16 -> android.graphics.Color.rgb(255, 165, 0)   // Orange (14:00 - 16:59)
            deliveryHour in 17..19 -> android.graphics.Color.RED                 // Red (17:00 - 19:59)
            deliveryHour in 20..22 -> android.graphics.Color.rgb(148, 0, 211)   // Purple (20:00 - 22:59)
            else -> android.graphics.Color.GRAY                                  // Gray - для остальных времен
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
        paint.setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(50, 0, 0, 0))
        
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
        paint.color = android.graphics.Color.WHITE
        paint.alpha = 230
        canvas.drawCircle(size/2f, size * 0.35f, circleRadius * 0.7f, paint)
        
        // Рисуем номер
        paint.color = android.graphics.Color.BLACK
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
            .replace("\\s+".toRegex(), " ")
            // Стандартизация обозначений
            .replace(Regex("(?i)г\\.?\\s*москва[,\\s]*"), "")
            .replace(Regex("(?i)москва[,\\s]*"), "")
            .replace(Regex("(?i)город\\s+"), "")
            .replace(Regex("(?i)гор\\.?\\s*"), "")
            // Удаляем лишнюю информацию
            .replace(Regex("(?i)подъезд\\s*№?\\s*\\d+"), "")
            .replace(Regex("(?i)эт\\.?\\s*\\d+"), "")
            .replace(Regex("(?i)кв\\.?\\s*\\d+[а-я]?"), "")
            // Стандартизация номеров домов с корпусами
            .replace(Regex("(?i)дом\\s*(\\d+)\\s*к\\s*(\\d+)"), "$1 корпус $2")
            .replace(Regex("(?i)д\\.?\\s*(\\d+)\\s*к\\s*(\\d+)"), "$1 корпус $2")
            .replace(Regex("(?i)д\\s*(\\d+)\\s*к\\s*(\\d+)"), "$1 корпус $2")
            .replace(Regex("(?i)(\\d+)\\s*к\\s*(\\d+)"), "$1 корпус $2")
            // Стандартизация одиночных номеров домов
            .replace(Regex("(?i)дом\\s*(\\d+)"), "$1")
            .replace(Regex("(?i)д\\.?\\s*(\\d+)"), "$1")
            .replace(Regex("(?i)д\\s*(\\d+)"), "$1")
            // Стандартизация улиц
            .replace(Regex("(?i)улица\\s+"), "улица ")
            .replace(Regex("(?i)ул\\s+"), "улица ")
            .replace(Regex("(?i)ул\\.\\s+"), "улица ")
            // Стандартизация проспектов
            .replace(Regex("(?i)проспект\\s+"), "проспект ")
            .replace(Regex("(?i)пр\\s+"), "проспект ")
            .replace(Regex("(?i)пр-т\\s+"), "проспект ")
            .replace(Regex("(?i)пр\\.\\s+"), "проспект ")
            // Стандартизация переулков
            .replace(Regex("(?i)переулок\\s+"), "переулок ")
            .replace(Regex("(?i)пер\\s+"), "переулок ")
            .replace(Regex("(?i)пер\\.\\s+"), "переулок ")
            // Стандартизация бульваров
            .replace(Regex("(?i)бульвар\\s+"), "бульвар ")
            .replace(Regex("(?i)бул\\s+"), "бульвар ")
            .replace(Regex("(?i)бул\\.\\s+"), "бульвар ")
            .replace(Regex("(?i)б-р\\s+"), "бульвар ")
            // Стандартизация шоссе
            .replace(Regex("(?i)шоссе\\s+"), "шоссе ")
            .replace(Regex("(?i)ш\\s+"), "шоссе ")
            .replace(Regex("(?i)ш\\.\\s+"), "шоссе ")
            // Стандартизация площадей
            .replace(Regex("(?i)площадь\\s+"), "площадь ")
            .replace(Regex("(?i)пл\\s+"), "площадь ")
            .replace(Regex("(?i)пл\\.\\s+"), "площадь ")
            // Добавляем город в начало
            .let { addr -> "город Москва, $addr" }
            .trim()
    }

    // Функция для обработки нажатия на маркер
    val onMarkerTap: (Order) -> Unit = { order ->
        println("Marker tapped for order ${order.orderNumber}")  // Добавляем логирование
        selectedOrder = order  // Просто устанавливаем новый заказ
    }

    suspend fun geocodeAddress(address: String): Point? = suspendCancellableCoroutine { continuation ->
        var session: Session? = null
        try {
            val searchOptions = SearchOptions().apply {
                searchTypes = SearchType.GEO.value
                resultPageSize = 1
                geometry = true
                disableSpellingCorrection = false
                origin = "DMApp-Courier"
            }

            // Пробуем сначала с полным адресом
            session = searchManager.submit(
                address,
                Geometry.fromBoundingBox(MOSCOW_BOUNDS),
                searchOptions,
                object : Session.SearchListener {
                    override fun onSearchResponse(response: Response) {
                        val result = response.collection.children.firstOrNull()?.obj
                        val point = result?.geometry?.firstOrNull()?.point
                        
                        if (point != null) {
                            println("\nGeocoding SUCCESS")
                            println("Input address: $address")
                            println("Found location: ${result.name}")
                            println("Found address: ${result.descriptionText}")
                            println("Coordinates: $point")
                            
                            // Проверяем, что точка находится в пределах Москвы
                            if (point.latitude in 55.48992..55.957565 &&
                                point.longitude in 37.319331..37.907543) {
                                continuation.resume(point)
                            } else {
                                println("Point is outside Moscow bounds")
                                continuation.resume(null)
                            }
                        } else {
                            // Если не нашли, пробуем без слова "город"
                            val alternativeAddress = address.replace("город ", "")
                            searchManager.submit(
                                alternativeAddress,
                                Geometry.fromBoundingBox(MOSCOW_BOUNDS),
                                searchOptions,
                                object : Session.SearchListener {
                                    override fun onSearchResponse(response: Response) {
                                        val result = response.collection.children.firstOrNull()?.obj
                                        val point = result?.geometry?.firstOrNull()?.point
                                        
                                        if (point != null && 
                                            point.latitude in 55.48992..55.957565 &&
                                            point.longitude in 37.319331..37.907543) {
                                            println("\nGeocoding SUCCESS with alternative address")
                                            println("Input address: $alternativeAddress")
                                            println("Found location: ${result.name}")
                                            println("Found address: ${result.descriptionText}")
                                            println("Coordinates: $point")
                                            continuation.resume(point)
                                        } else {
                                            println("\nGeocoding FAILED")
                                            println("Input address: $address")
                                            println("Alternative address: $alternativeAddress")
                                            println("No results found")
                                            continuation.resume(null)
                                        }
                                    }

                                    override fun onSearchError(error: Error) {
                                        println("\nGeocoding ERROR")
                                        println("Input address: $address")
                                        println("Error: $error")
                                        continuation.resume(null)
                                    }
                                }
                            )
                        }
                    }

                    override fun onSearchError(error: Error) {
                        println("\nGeocoding ERROR")
                        println("Input address: $address")
                        println("Error: $error")
                        continuation.resume(null)
                    }
                }
            )

            continuation.invokeOnCancellation {
                session?.cancel()
            }
        } catch (e: Exception) {
            println("\nEXCEPTION during geocoding")
            println("Input address: $address")
            println("Error: ${e.message}")
            e.printStackTrace()
            continuation.resume(null)
        }
    }

    LaunchedEffect(orders) {
        scope.launch {
            println("\n=== Starting geocoding process for ${orders.size} orders ===\n")
            println("Moscow bounds: ${MOSCOW_BOUNDS.southWest} to ${MOSCOW_BOUNDS.northEast}")
            
            geocodedOrders = orders.map { order ->
                println("\nProcessing order ${order.orderNumber}")
                println("Original address: ${order.deliveryAddress}")
                
                if (order.latitude != null && order.longitude != null) {
                    // Проверяем, что координаты не являются дефолтными (центр Москвы)
                    if (order.latitude != 55.751574 && order.longitude != 37.573856) {
                        println("Using existing coordinates: ${order.latitude}, ${order.longitude}")
                        order to Point(order.latitude, order.longitude)
                    } else {
                        println("Found default coordinates, will try geocoding")
                        null
                    }
                } else {
                    println("No coordinates found, will try geocoding")
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
                            geometry = true
                            disableSpellingCorrection = false
                            origin = "DMApp-Courier"
                        }

                        session = searchManager.submit(
                            formattedAddress,
                            Geometry.fromBoundingBox(MOSCOW_BOUNDS),
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

    // Отслеживаем изменения selectedOrder с улучшенным логированием
    LaunchedEffect(selectedOrderState.value) {
        val order = selectedOrderState.value
        println("LaunchedEffect: selectedOrder changed to: ${order?.orderNumber}")
        if (order != null) {
            println("LaunchedEffect: Selected order details: ${order.orderNumber}, ${order.deliveryAddress}")
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

    // Создаем слушатель нажатий на маркер
    val mapObjectTapListener = remember {
        MapObjectTapListener { mapObject, point ->
            println("MapObjectTapListener: Marker tap detected at $point")
            
            val tappedOrder = mapObject.userData as? Order
            if (tappedOrder != null) {
                println("MapObjectTapListener: Tap listener triggered for order ${tappedOrder.orderNumber}")
                
                // Обновляем UI в основном потоке
                scope.launch(Dispatchers.Main) {
                    println("MapObjectTapListener: Setting selectedOrder to ${tappedOrder.orderNumber}")
                    // Сначала сбрасываем значение, чтобы гарантировать обновление UI
                    selectedOrderState.value = null
                    // Небольшая задержка для обновления UI
                    delay(50)
                    // Устанавливаем новое значение
                    selectedOrderState.value = tappedOrder
                    println("MapObjectTapListener: After setting, selectedOrder is now ${selectedOrderState.value?.orderNumber}")
                }
                true
            } else {
                println("MapObjectTapListener: ERROR: Tapped order is null!")
                false
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
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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

                        // Создаем коллекцию маркеров
                        val mapObjects = map.mapObjects
                        mapObjects.clear()

                        // Отображаем маркеры с учетом скорректированных позиций
                        adjustedPoints.forEach { (order, point, _) ->
                            val placemark = mapObjects.addPlacemark(point).apply {
                                val markerBitmap = createMarkerBitmap(view.context, order)
                                setIcon(ImageProvider.fromBitmap(markerBitmap))
                                userData = order // Сохраняем заказ в userData маркера
                                
                                // Добавляем логирование для отслеживания создания маркера
                                println("Created marker for order ${order.orderNumber} at $point")
                            }
                            
                            // Используем созданный слушатель нажатий
                            placemark.addTapListener(mapObjectTapListener)
                        }

                        // Центрируем карту на первой точке только при первой загрузке
                        if (!isMapReady) {
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
                                isMapReady = true
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Ошибка при обновлении карты: ${e.message}"
                    }
                }
            )
            
            // Нижняя панель с информацией о заказе - теперь внутри Box
            if (selectedOrder != null) {
                println("Showing card for order ${selectedOrder?.orderNumber}")
                
                // Используем Card вместо ModalBottomSheet с анимацией появления
                AnimatedVisibility(
                    visible = selectedOrder != null,
                    enter = slideInVertically(
                        initialOffsetY = { it }, // Начинаем снизу экрана
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it }, // Уходим вниз экрана
                        animationSpec = tween(durationMillis = 200)
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = ComposeColor.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Заголовок с кнопкой закрытия
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Добавляем заголовок с номером заказа
                                Text(
                                    text = "${selectedOrder?.orderNumber}. Заказ ${selectedOrder?.externalOrderNumber ?: "Н/Д"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                
                                IconButton(onClick = { 
                                    println("Close button clicked")
                                    selectedOrderState.value = null 
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                                }
                            }
                            
                            Divider()
                            
                            // Используем общий компонент OrderDetailsContent
                            OrderDetailsContent(
                                order = selectedOrder!!,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                onStatusChange = { order, newStatus ->
                                    // Обновляем статус заказа локально
                                    println("Status changed from ${order.status} to $newStatus")
                                    selectedOrderState.value = order.copy(status = newStatus)
                                    
                                    // Вызываем обработчик обновления статуса, если он передан
                                    onStatusUpdate?.invoke(order, newStatus)
                                },
                                onNotesChange = { order, newNotes ->
                                    // Обновляем заметки заказа локально
                                    println("Notes changed for order ${order.orderNumber}")
                                    selectedOrderState.value = order.copy(notes = newNotes)
                                    
                                    // Вызываем обработчик обновления заметок, если он передан
                                    onStatusUpdate?.invoke(order.copy(notes = newNotes), order.status)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
} 