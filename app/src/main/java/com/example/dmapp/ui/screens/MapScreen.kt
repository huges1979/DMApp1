package com.example.dmapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Divider
import com.example.dmapp.ui.components.OrderDetailsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import com.yandex.mapkit.map.MapObjectTapListener
import com.example.dmapp.ui.OrderViewModel
import com.yandex.mapkit.map.IconStyle
import androidx.compose.foundation.clickable
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.location.LocationManager as AndroidLocationManager
import com.yandex.mapkit.location.Location as YandexLocation
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager as YandexLocationManager
import com.yandex.mapkit.location.LocationStatus
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.map.Map as YandexMap
import com.yandex.mapkit.map.PlacemarkMapObject
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
// import com.example.dmapp.ui.components.OrderMiniCard

// Определяем временные интервалы как enum на уровне файла
enum class DeliveryTimeRange(val title: String, val startHour: Int, val endHour: Int) {
    MORNING("11:00-15:00", 11, 15),
    AFTERNOON("14:00-17:00", 14, 17),
    EVENING("17:00-20:00", 17, 20),
    NIGHT("20:00-23:00", 20, 23)
}

// Вспомогательный объект для работы с картой
object MapUtils {
    // Функция для создания круглого битмапа для точки местоположения пользователя
    fun createCircleBitmap(color: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }
    
    // Функция для создания большой красной точки местоположения пользователя
    fun createUserLocationBitmap(): Bitmap {
        val size = 64 // Увеличенный размер (еще больше)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Рисуем внешний круг с полупрозрачностью (область точности)
        val outerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        outerCirclePaint.color = android.graphics.Color.argb(70, 255, 0, 0) // Полупрозрачный красный
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, outerCirclePaint)
        
        // Рисуем яркий внутренний круг
        val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        innerCirclePaint.color = android.graphics.Color.RED
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, innerCirclePaint)
        
        // Рисуем белую окантовку для лучшей видимости
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        borderPaint.color = android.graphics.Color.WHITE
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 4f // Более толстая окантовка
        canvas.drawCircle(size / 2f, size / 2f, size / 3f - 2f, borderPaint)
        
        return bitmap
    }
    
    // Функция для создания стрелки направления
    fun createArrowBitmap(color: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        
        val path = Path()
        // Рисуем простую стрелку, направленную вверх
        path.moveTo(size / 2f, 0f)  // Вершина стрелки
        path.lineTo(0f, size.toFloat())  // Левый угол основания
        path.lineTo(size.toFloat(), size.toFloat())  // Правый угол основания
        path.close()
        
        canvas.drawPath(path, paint)
        return bitmap
    }
    
    // Вспомогательная функция для проверки наличия маркера в коллекции объектов карты
    fun isMarkerValid(marker: PlacemarkMapObject?): Boolean {
        if (marker == null) return false
        
        try {
            // Пробуем обратиться к свойству маркера, чтобы проверить его валидность
            val unused = marker.geometry
            return true
        } catch (e: Exception) {
            println("Маркер недействителен: ${e.message}")
            return false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    orders: List<Order>,
    onBackClick: () -> Unit,
    onStatusUpdate: ((Order, OrderStatus) -> Unit)? = null,
    viewModel: OrderViewModel
) {
    // Флаг для отслеживания готовности карты
    var isMapReady by remember { mutableStateOf(false) }
    // Для показа ошибки, если такая возникнет при инициализации карты
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Для отображения деталей заказа при нажатии
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    // Для управления диалогом фильтра времени
    var showTimeFilterDialog by remember { mutableStateOf(false) }
    
    // Контекст для Toast и других Android API
    val context = LocalContext.current

    // Получаем контекст для создания карты
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    
    // Переменные для работы с местоположением пользователя
    var userLocationLayer by remember { mutableStateOf<UserLocationLayer?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<Point?>(null) }
    // Переменная для собственного маркера местоположения пользователя
    var userLocationMarker by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    
    // Функция для запроса разрешений на доступ к местоположению
    fun requestLocationPermissions() {
        if (context is Activity) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                100
            )
            println("Запрошены разрешения на доступ к местоположению")
        } else {
            println("Контекст не является Activity, не могу запросить разрешения")
        }
    }
    
    // Функция для получения текущего местоположения
    fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
                
                println("GPS включен: $isGpsEnabled, Сеть включена: $isNetworkEnabled")
                
                if (!isGpsEnabled && !isNetworkEnabled) {
                    Toast.makeText(context, "Включите службы геолокации на устройстве", Toast.LENGTH_LONG).show()
                    return
                }
                
                // Пробуем получить местоположение через GPS
                var location: Location? = null
                if (isGpsEnabled) {
                    location = locationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
                    println("GPS местоположение: $location")
                }
                
                // Если через GPS не получилось, пробуем через сеть
                if (location == null && isNetworkEnabled) {
                    location = locationManager.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)
                    println("Сетевое местоположение: $location")
                }
                
                if (location != null) {
                    userLocation = Point(location.latitude, location.longitude)
                    println("Установлено местоположение пользователя: $userLocation")
                    
                    // Сначала проверяем, что mapView не была уничтожена
                    val currentMapView = mapView
                    if (currentMapView == null) {
                        println("MapView был уничтожен, не обновляем местоположение")
                        return
                    }
                    
                    // Обновляем или создаем маркер местоположения пользователя
                    val map = currentMapView.mapWindow?.map
                    if (map != null) {
                        // Создаем локальную копию userLocation для безопасного использования
                        val currentLocation = userLocation
                        if (currentLocation != null) {
                            try {
                                if (userLocationMarker == null) {
                                    // Создаем новый маркер
                                    userLocationMarker = map.mapObjects.addPlacemark().apply {
                                        geometry = currentLocation
                                        setIcon(ImageProvider.fromBitmap(MapUtils.createUserLocationBitmap()))
                                        userData = "userLocation"
                                        println("Создан маркер местоположения пользователя")
                                    }
                                } else {
                                    // Обновляем существующий маркер
                                    userLocationMarker?.let { marker ->
                                        if (MapUtils.isMarkerValid(marker)) {
                                            marker.geometry = currentLocation
                                            println("Обновлен маркер местоположения пользователя")
                                        } else {
                                            println("Маркер был удален или недействителен, создаем новый")
                                            userLocationMarker = map.mapObjects.addPlacemark().apply {
                                                geometry = currentLocation
                                                setIcon(ImageProvider.fromBitmap(MapUtils.createUserLocationBitmap()))
                                                userData = "userLocation"
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                println("Ошибка при обновлении маркера: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    }
                    
                    // Центрируем карту на местоположении пользователя, если оно доступно
                    try {
                        userLocation?.let { location ->
                            currentMapView.mapWindow?.map?.move(
                                CameraPosition(
                                    location,
                                    15.0f,
                                    0.0f,
                                    0.0f
                                ),
                                Animation(Animation.Type.SMOOTH, 1f),
                                null
                            )
                            Toast.makeText(context, "Карта центрирована на вашем местоположении", Toast.LENGTH_SHORT).show()
                        } ?: run {
                            Toast.makeText(context, "Местоположение недоступно. Проверьте, включена ли геолокация на устройстве", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        println("Ошибка при центрировании карты: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    println("Не удалось получить местоположение")
                    Toast.makeText(context, "Не удалось получить местоположение", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                println("Ошибка при получении местоположения: ${e.message}")
                e.printStackTrace()
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            println("Нет разрешений на доступ к местоположению")
            locationPermissionGranted = false
            requestLocationPermissions()
        }
    }
    
    // Проверяем, есть ли разрешение на доступ к местоположению при инициализации
    LaunchedEffect(Unit) {
        locationPermissionGranted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        println("Статус разрешения на местоположение: $locationPermissionGranted")
        
        if (locationPermissionGranted) {
            getCurrentLocation()
        } else {
            requestLocationPermissions()
        }
    }
    
    println("MapScreen initialized with selectedOrder = $selectedOrder")
    val searchManager = remember { SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED) }
    var geocodedOrders by remember { mutableStateOf<List<Pair<Order, Point?>>>(emptyList()) }

    // Состояния для фильтрации по времени
    var selectedTimeRanges by remember { mutableStateOf(setOf<DeliveryTimeRange>()) }
    
    // Функция для фильтрации заказов по временным интервалам
    fun isOrderInSelectedTimeRanges(order: Order): Boolean {
        // Если ничего не выбрано, показываем все заказы
        if (selectedTimeRanges.isEmpty()) {
            return true
        }
        
        // Получаем час начала доставки заказа
        val orderStartHour = order.deliveryTimeStart.hour
        
        // Проверяем, попадает ли заказ хотя бы в один из выбранных интервалов
        return selectedTimeRanges.any { timeRange ->
            orderStartHour in timeRange.startHour until timeRange.endHour
        }
    }

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

    // Функция для извлечения номера дома из адреса
    fun extractHouseNumber(address: String): String {
        val houseNumberPattern = Regex("(дом|д\\.?|д)\\s*(\\d+)(?:\\s*(к|корп\\.?|корпус)\\s*(\\d+))?", RegexOption.IGNORE_CASE)
        val match = houseNumberPattern.find(address)
        
        return if (match != null) {
            val number = match.groupValues[2]
            val building = if (match.groupValues.size > 4 && match.groupValues[4].isNotEmpty()) 
                            "к${match.groupValues[4]}" else ""
            "$number$building"
        } else {
            // Если шаблон не сработал, пробуем найти просто число после "дом"
            val fallbackPattern = Regex("дом\\s*(\\d+)", RegexOption.IGNORE_CASE)
            val fallbackMatch = fallbackPattern.find(address)
            fallbackMatch?.groupValues?.getOrNull(1) ?: "1" // Возвращаем "1" как запасной вариант
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

    // Форматирование адреса для геокодинга
    fun formatAddress(address: String): String {
        // Сначала запоминаем, был ли адрес с буквой ё
        val containsYo = address.contains("ё", ignoreCase = true)
        
        // Обрабатываем специфичные случаи
        if (address.contains("новочер", ignoreCase = true) && 
            (address.contains("мушинск", ignoreCase = true) || address.contains("мушенск", ignoreCase = true)) &&
            address.contains("17", ignoreCase = true)) {
            println("Обнаружен адрес на Новочерёмушинской улице, применяю специальное форматирование")
            return "Москва, Новочерёмушинская улица, дом 17"
        }
        
        val result = address
            // Удаляем лишнюю информацию
            .replace(Regex("(?i)подъезд\\s*№?\\s*\\d+"), "")
            .replace(Regex("(?i)эт\\.?\\s*\\d+"), "")
            .replace(Regex("(?i)кв\\.?\\s*\\d+[а-я]?"), "")
            // Стандартизация номеров домов с корпусами
            .replace(Regex("(?i)дом\\s*(\\d+)\\s*корпус\\s*(\\d+)"), "дом $1 корпус $2")
            .replace(Regex("(?i)дом\\s*(\\d+)\\s*корп\\.?\\s*(\\d+)"), "дом $1 корпус $2")
            .replace(Regex("(?i)дом\\s*(\\d+)\\s*к\\s*(\\d+)"), "дом $1 корпус $2")
            .replace(Regex("(?i)д\\.?\\s*(\\d+)\\s*корпус\\s*(\\d+)"), "дом $1 корпус $2")
            .replace(Regex("(?i)д\\.?\\s*(\\d+)\\s*корп\\.?\\s*(\\d+)"), "дом $1 корпус $2")
            .replace(Regex("(?i)д\\.?\\s*(\\d+)\\s*к\\s*(\\d+)"), "дом $1 корпус $2")
            .replace(Regex("(?i)д\\s*(\\d+)\\s*к\\s*(\\d+)"), "дом $1 корпус $2")
            .replace(Regex("(?i)(\\d+)\\s*к\\s*(\\d+)"), "дом $1 корпус $2")
            .replace(Regex("(?i)(\\d+)\\s*корп\\.?\\s*(\\d+)"), "дом $1 корпус $2")
            // Стандартизация одиночных номеров домов
            .replace(Regex("(?i)дом\\s*(\\d+)"), "дом $1")
            .replace(Regex("(?i)д\\.?\\s*(\\d+)"), "дом $1")
            .replace(Regex("(?i)д\\s*(\\d+)"), "дом $1")
            // Стандартизация строений
            .replace(Regex("(?i)стр\\.?\\s*(\\d+)"), "строение $1")
            .replace(Regex("(?i)строение\\s*(\\d+)"), "строение $1")
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
            // Добавляем город в начало, если его нет
            .let { addr -> 
                if (!addr.lowercase().contains("москва")) {
                    "город Москва, $addr"
                } else {
                    addr
                }
            }
            .trim()
            
        // Возвращаем букву "ё" в адрес, если она была изначально (для улучшения распознавания)
        return if (containsYo && result.contains("новочеремушинская", ignoreCase = true)) {
            result.replace("новочеремушинская", "новочерёмушинская", ignoreCase = true)
        } else {
            result
        }
    }

    // Функция для обработки специальных случаев адресов
    fun handleSpecialAddresses(address: String): Pair<String, Point?> {
        // Карта известных проблемных адресов и их координат
        val knownAddresses = mapOf(
            // Адрес на ул. Островитянова 5
            Regex(".*(островитянова|островитяново).*(дом|д|д\\.|дом) ?5.*", RegexOption.IGNORE_CASE) to 
                Point(55.64529, 37.47979),
            
            // Адрес на ул. Наметкина 11 - улучшенное распознавание
            Regex(".*(нам[её]ткина|нам[её]ткиной).*([дд]ом|д|д\\.|дом)?.* ?11.*", RegexOption.IGNORE_CASE) to 
                Point(55.66231, 37.55527),
                
            // Еще один вариант для Наметкина 11 - для прямого совпадения
            Regex(".*нам[её]ткина.*11.*", RegexOption.IGNORE_CASE) to
                Point(55.66231, 37.55527),
            
            // Профсоюзная 29к1
            Regex(".*(профсоюзная).*(дом|д|д\\.|дом)? ?29(\\s|\\-)?(к|корп|корпус)?\\.?\\s?1.*", RegexOption.IGNORE_CASE) to 
                Point(55.67853, 37.56627),
                
            // Добавляем больше специальных случаев
            Regex(".*(ленинский|ленинском).*(проспект|пр|просп)?.*(дом|д|д\\.|дом)? ?30.*", RegexOption.IGNORE_CASE) to 
                Point(55.70410, 37.58508),

            Regex(".*(ломоносовский|ломоносовском).*(проспект|пр|просп)?.*(дом|д|д\\.|дом)? ?23.*", RegexOption.IGNORE_CASE) to 
                Point(55.69266, 37.53172),
                
            Regex(".*(мичуринский|мичуринском).*(проспект|пр|просп)?.*(дом|д|д\\.|дом)? ?31.*", RegexOption.IGNORE_CASE) to 
                Point(55.69674, 37.49809),
                
            // Новочерёмушинская улица, д.17 - улучшенное регулярное выражение
            Regex(".*новочер[её]мушинск[а-я]+.*(улица|ул|ул\\.)?.*(?:дом|д|д\\.|дом)?\\s*17.*", RegexOption.IGNORE_CASE) to 
                Point(55.68022, 37.59315),
                
            // Дополнительные варианты для Новочерёмушинской улицы
            Regex(".*черёмуш.*17.*", RegexOption.IGNORE_CASE) to 
                Point(55.68022, 37.59315),
                
            Regex(".*черемуш.*17.*", RegexOption.IGNORE_CASE) to 
                Point(55.68022, 37.59315)
        )
        
        // Проверяем, соответствует ли адрес одному из известных проблемных адресов
        for ((pattern, point) in knownAddresses) {
            if (pattern.containsMatchIn(address)) {
                println("Found special case address match: $address -> $point")
                return Pair(address, point)
            }
        }
        
        // Явно проверяем наличие Новочерёмушинской улицы
        if ((address.contains("новочерёмушинск", ignoreCase = true) || 
             address.contains("новочеремушинск", ignoreCase = true) || 
             address.contains("черёмуш", ignoreCase = true) || 
             address.contains("черемуш", ignoreCase = true)) && 
            address.contains("17")) {
            println("Found exact address match for Novocheremushkinskaya 17: $address")
            return Pair(address, Point(55.68022, 37.59315))
        }
        
        // Явно проверяем конкретные адреса
        if (address.contains("намёткина", ignoreCase = true) || 
            address.contains("наметкина", ignoreCase = true)) {
            if (address.contains("11")) {
                println("Found exact address match for Nametkina 11: $address")
                return Pair(address, Point(55.66231, 37.55527))
            }
        }
        
        return Pair(address, null)
    }

    suspend fun geocodeAddress(address: String): Point? = suspendCancellableCoroutine { continuation ->
        var session: Session? = null
        try {
            // Создаем адрес с буквой "ё" и без неё для улучшенной обработки
            val withYo = address.replace("е", "ё")
            val withoutYo = address.replace("ё", "е")
            
            // Функция для проверки специальных случаев для обоих вариантов написания
            fun checkSpecialCase(addr: String): Point? {
                // Сначала проверяем, является ли адрес особым случаем
                val (_, specialPoint) = handleSpecialAddresses(addr)
                if (specialPoint != null) {
                    println("Using pre-defined coordinates for special address: $addr")
                    return specialPoint
                }
                
                // Проверяем наличие Новочерёмушинской улицы
                if ((addr.contains("новочер", ignoreCase = true) && 
                     (addr.contains("мушинск", ignoreCase = true) || 
                      addr.contains("мушенск", ignoreCase = true) || 
                      addr.contains("черёмуш", ignoreCase = true) || 
                      addr.contains("черемуш", ignoreCase = true))) &&
                    addr.contains("17", ignoreCase = true)) {
                    println("Быстрое определение координат для Новочерёмушинской улицы, д.17")
                    return Point(55.68022, 37.59315)
                }
                
                return null
            }
            
            // Проверяем оба варианта написания адреса
            val point = checkSpecialCase(address) ?: checkSpecialCase(withYo) ?: checkSpecialCase(withoutYo)
            if (point != null) {
                continuation.resume(point)
                return@suspendCancellableCoroutine
            }
            
            val searchOptions = SearchOptions().apply {
                searchTypes = SearchType.GEO.value
                resultPageSize = 1
                geometry = true
                disableSpellingCorrection = false
                origin = "DMApp-Courier"
            }

            // Создаем различные варианты адреса для геокодинга
            val formattedAddress = formatAddress(address)
            
            // Варианты адреса для попыток геокодинга
            val addressVariants = mutableListOf<String>()
            addressVariants.add(formattedAddress) // Основной формат
            
            // Вариант с буквой ё, если в адресе содержится "черемуш"
            if (formattedAddress.contains("черемуш", ignoreCase = true)) {
                addressVariants.add(formattedAddress.replace("черемуш", "черёмуш", ignoreCase = true))
            }
            
            // Вариант с буквой е, если в адресе содержится "черёмуш"
            if (formattedAddress.contains("черёмуш", ignoreCase = true)) {
                addressVariants.add(formattedAddress.replace("черёмуш", "черемуш", ignoreCase = true))
            }
            
            // Вариант без слова "город"
            addressVariants.add(formattedAddress.replace("город Москва, ", ""))
            
            // Добавляем вариант с "Россия, Москва"
            addressVariants.add("Россия, Москва, ${formattedAddress.replace("город Москва, ", "")}")
            
            // Уточнение района для определенных улиц (расширяем список)
            if (formattedAddress.contains("островитянова", ignoreCase = true)) {
                addressVariants.add("Москва, улица Островитянова, 5, Коньково")
                addressVariants.add("Москва, Коньково, улица Островитянова, 5")
            }
            
            if (formattedAddress.contains("намёткина", ignoreCase = true) || 
                formattedAddress.contains("наметкина", ignoreCase = true)) {
                addressVariants.add("Москва, улица Наметкина, 11, Черемушки")
                addressVariants.add("Москва, Черемушки, улица Наметкина, 11")
                addressVariants.add("Москва, улица Намёткина, 11")
                addressVariants.add("Москва, Обручевский район, улица Намёткина, 11")
                addressVariants.add("Намёткина, 11, Москва")
            }
            
            if (formattedAddress.contains("профсоюзная", ignoreCase = true) && 
                formattedAddress.contains("29", ignoreCase = true)) {
                addressVariants.add("Москва, Профсоюзная улица, 29 корпус 1")
                addressVariants.add("Москва, Профсоюзная улица, 29к1")
                addressVariants.add("Москва, Академический район, Профсоюзная улица, 29к1")
            }
            
            if (formattedAddress.contains("ленинский", ignoreCase = true)) {
                addressVariants.add("Москва, Ленинский проспект, ${extractHouseNumber(formattedAddress)}")
                addressVariants.add("Москва, Гагаринский район, Ленинский проспект, ${extractHouseNumber(formattedAddress)}")
            }
            
            if (formattedAddress.contains("ломоносовский", ignoreCase = true)) {
                addressVariants.add("Москва, Ломоносовский проспект, ${extractHouseNumber(formattedAddress)}")
                addressVariants.add("Москва, Гагаринский район, Ломоносовский проспект, ${extractHouseNumber(formattedAddress)}")
            }
            
            if (formattedAddress.contains("мичуринский", ignoreCase = true)) {
                addressVariants.add("Москва, Мичуринский проспект, ${extractHouseNumber(formattedAddress)}")
                addressVariants.add("Москва, Раменки, Мичуринский проспект, ${extractHouseNumber(formattedAddress)}")
            }
            
            // Специальный случай для Новочерёмушинской улицы
            if (formattedAddress.contains("новочер", ignoreCase = true) && 
                (formattedAddress.contains("мушинск", ignoreCase = true) || formattedAddress.contains("мушенск", ignoreCase = true))) {
                addressVariants.add("Москва, Новочерёмушинская улица, 17")
                addressVariants.add("Москва, район Черёмушки, Новочерёмушинская улица, 17")
                addressVariants.add("Москва, район Академический, Новочерёмушинская улица, 17")
                addressVariants.add("Москва, ЮЗАО, Новочерёмушинская улица, 17")
                
                // Добавляем варианты без буквы ё
                addressVariants.add("Москва, Новочеремушинская улица, 17")
                addressVariants.add("Москва, район Черемушки, Новочеремушинская улица, 17")
                addressVariants.add("Москва, район Академический, Новочеремушинская улица, 17")
                addressVariants.add("Москва, ЮЗАО, Новочеремушинская улица, 17")
            }
            
            // Альтернативные форматы для домов с корпусами
            if (formattedAddress.contains("корпус")) {
                // Вариант с заменой "корпус" на "к" 
                addressVariants.add(formattedAddress.replace("корпус", "к"))
                
                // Вариант с номером дома и корпусом через дефис
                val withHyphen = formattedAddress.replace(
                    Regex("дом (\\d+) корпус (\\d+)"), 
                    "дом $1-$2"
                )
                addressVariants.add(withHyphen)
                
                // Вариант без слова "дом" 
                val withoutHouse = formattedAddress.replace(
                    Regex("дом (\\d+) корпус (\\d+)"), 
                    "$1 корпус $2"
                )
                addressVariants.add(withoutHouse)
                
                // Вариант через слеш
                val withSlash = formattedAddress.replace(
                    Regex("дом (\\d+) корпус (\\d+)"), 
                    "дом $1/$2"
                )
                addressVariants.add(withSlash)
                
                // Добавляем еще один вариант записи корпуса
                val withK = formattedAddress.replace(
                    Regex("дом (\\d+) корпус (\\d+)"), 
                    "дом $1к$2"
                )
                addressVariants.add(withK)
            }
            
            // Еще один вариант - попробовать удалить все кроме улицы и номера дома
            val simpleAddress = try {
                val streetPattern = Regex("(улица|ул\\.?|проспект|пр-т|пр\\.?|бульвар|бул\\.?|переулок|пер\\.?|шоссе|ш\\.?)\\s+[А-Яа-я\\-]+", RegexOption.IGNORE_CASE)
                val housePattern = Regex("(дом|д\\.?|д)\\s*\\d+", RegexOption.IGNORE_CASE)
                
                val streetMatch = streetPattern.find(formattedAddress)?.value ?: ""
                val houseMatch = housePattern.find(formattedAddress)?.value ?: ""
                
                if (streetMatch.isNotEmpty() && houseMatch.isNotEmpty()) {
                    "Москва, $streetMatch, $houseMatch"
                } else {
                    ""
                }
            } catch (e: Exception) {
                println("Error extracting simple address: ${e}")
                ""
            }
            
            if (simpleAddress.isNotEmpty()) {
                addressVariants.add(simpleAddress)
            }
            
            println("\nWill try geocoding with following address variants:")
            addressVariants.forEachIndexed { index, variant -> 
                println("$index: $variant") 
            }
            
            // Функция для извлечения следующего варианта адреса для геокодинга
            fun getNextVariant(index: Int): String? {
                return if (index < addressVariants.size) addressVariants[index] else null
            }
            
            // Функция для выполнения запроса геокодинга с одним вариантом адреса
            fun submitGeocodingRequest(index: Int) {
                val addressToGeocode = getNextVariant(index) ?: run {
                    println("All variants failed, couldn't geocode address: $address")
                    continuation.resume(null)
                    return
                }
                
                val isLastAttempt = index == addressVariants.size - 1
                
                println("\nTrying to geocode variant $index: $addressToGeocode")
                session = searchManager.submit(
                    addressToGeocode,
                    Geometry.fromBoundingBox(MOSCOW_BOUNDS),
                    searchOptions,
                    object : Session.SearchListener {
                        override fun onSearchResponse(response: Response) {
                            val result = response.collection.children.firstOrNull()?.obj
                            val point = result?.geometry?.firstOrNull()?.point
                            
                            if (point != null && 
                                point.latitude in 55.48992..55.957565 &&
                                point.longitude in 37.319331..37.907543) {
                                println("\nGeocoding SUCCESS for variant $index")
                                println("Input address: $addressToGeocode")
                                println("Found location: ${result.name}")
                                println("Found address: ${result.descriptionText}")
                                println("Coordinates: $point")
                                
                                // Проверяем, что это не центр Москвы
                                if (Math.abs(point.latitude - 55.751574) < 0.0001 && 
                                    Math.abs(point.longitude - 37.573856) < 0.0001) {
                                    println("WARNING: Point is at Moscow center, likely incorrect geocoding")
                                    if (!isLastAttempt) {
                                        println("Trying next variant...")
                                        submitGeocodingRequest(index + 1)
                                        return
                                    }
                                }
                                
                                continuation.resume(point)
                            } else if (!isLastAttempt) {
                                println("No valid result found for variant $index: $addressToGeocode")
                                submitGeocodingRequest(index + 1)
                            } else {
                                println("\nGeocoding FAILED after all attempts")
                                println("Last address tried: $addressToGeocode")
                                continuation.resume(null)
                            }
                        }

                        override fun onSearchError(error: Error) {
                            println("Search error for variant $index: $addressToGeocode - ${error}")
                            if (!isLastAttempt) {
                                submitGeocodingRequest(index + 1)
                            } else {
                                println("\nGeocoding ERROR on last attempt")
                                println("Input address: $addressToGeocode")
                                println("Error: $error")
                                continuation.resume(null)
                            }
                        }
                    }
                )
            }
            
            // Начинаем с первого варианта
            submitGeocodingRequest(0)
            
            continuation.invokeOnCancellation {
                session?.cancel()
            }
        } catch (e: Exception) {
            println("\nEXCEPTION during geocoding")
            println("Input address: $address")
            println("Error: ${e}")
            e.printStackTrace()
            continuation.resume(null)
        }
    }
    
    // Функция для нормализации адреса перед сравнением
    fun normalizeAddress(address: String): String {
        var normalizedAddress = address.lowercase()
            // Удаляем информацию о подъездах, этажах, квартирах
            .replace(Regex("(подъезд|эт|этаж|кв)[\\s.№]*\\d+", RegexOption.IGNORE_CASE), "")
            // Заменяем "ё" на "е"
            .replace("ё", "е")
            // Нормализуем формат номера дома
            .replace(Regex("(дом|д|д\\.)[\\s.]*(\\d+)", RegexOption.IGNORE_CASE), "д.$2")
            // Нормализуем формат корпуса
            .replace(Regex("(корпус|корп|к)[\\s.]*(\\d+)", RegexOption.IGNORE_CASE), "к$2")
            // Удаляем лишние пробелы
            .replace(Regex("\\s+"), " ")
            .trim()
            
        // Создаем два варианта для поиска в словаре: с "ё" и с "е"
        // Специальная обработка для случаев с "ё"
        if (address.contains("черёмуш", ignoreCase = true) || address.contains("черемуш", ignoreCase = true)) {
            // Для улучшения поиска в словаре, проверим оба варианта
            val withYo = normalizedAddress.replace("черемуш", "черёмуш")
            val withoutYo = normalizedAddress.replace("черёмуш", "черемуш")
            
            // Если адрес содержит улицу с номером 17, используем специальный формат
            if (withYo.contains("17") || withoutYo.contains("17")) {
                println("Нормализация специального адреса (Новочерёмушинская)")
                if (withYo.contains("новочерёмуш")) return "новочерёмушинская, 17"
                if (withoutYo.contains("новочеремуш")) return "новочеремушинская, 17"
            }
        }
        
        // Специальная обработка для адресов с "намёткина/наметкина"
        if (address.contains("намёткин", ignoreCase = true) || address.contains("наметкин", ignoreCase = true)) {
            // Если адрес содержит улицу с номером 11, используем специальный формат
            if (normalizedAddress.contains("11")) {
                println("Нормализация специального адреса (Намёткина)")
                return "наметкина, 11"
            }
        }
        
        return normalizedAddress
    }
    
    // Новый метод для хранения известных адресов и их координат
    fun getKnownAddressCoordinates(): Map<String, Point> {
        return mapOf(
            // Адреса на улице Наметкина
            "москва, улица наметкина, 11" to Point(55.66231, 37.55527),
            "улица наметкина, 11" to Point(55.66231, 37.55527),
            "наметкина, 11" to Point(55.66231, 37.55527),
            "наметкина 11" to Point(55.66231, 37.55527),
            "д.11 наметкина" to Point(55.66231, 37.55527),
            
            // Адреса на улице Островитянова
            "москва, улица островитянова, 5" to Point(55.64529, 37.47979),
            "улица островитянова, 5" to Point(55.64529, 37.47979),
            "островитянова, 5" to Point(55.64529, 37.47979),
            "островитянова 5" to Point(55.64529, 37.47979),
            "д.5 островитянова" to Point(55.64529, 37.47979),
            
            // Адреса на Профсоюзной улице
            "москва, профсоюзная улица, 29к1" to Point(55.67853, 37.56627),
            "профсоюзная улица, 29к1" to Point(55.67853, 37.56627),
            "профсоюзная, 29к1" to Point(55.67853, 37.56627),
            "профсоюзная 29к1" to Point(55.67853, 37.56627),
            "д.29к1 профсоюзная" to Point(55.67853, 37.56627),
            
            // Адреса на Ленинском проспекте
            "москва, ленинский проспект, 30" to Point(55.70410, 37.58508),
            "ленинский проспект, 30" to Point(55.70410, 37.58508),
            "ленинский, 30" to Point(55.70410, 37.58508),
            "ленинский 30" to Point(55.70410, 37.58508),
            "д.30 ленинский" to Point(55.70410, 37.58508),
            
            // Адреса на Ломоносовском проспекте
            "москва, ломоносовский проспект, 23" to Point(55.69266, 37.53172),
            "ломоносовский проспект, 23" to Point(55.69266, 37.53172),
            "ломоносовский, 23" to Point(55.69266, 37.53172),
            "ломоносовский 23" to Point(55.69266, 37.53172),
            "д.23 ломоносовский" to Point(55.69266, 37.53172),
            
            // Адреса на Мичуринском проспекте
            "москва, мичуринский проспект, 31" to Point(55.69674, 37.49809),
            "мичуринский проспект, 31" to Point(55.69674, 37.49809),
            "мичуринский, 31" to Point(55.69674, 37.49809),
            "мичуринский 31" to Point(55.69674, 37.49809),
            "д.31 мичуринский" to Point(55.69674, 37.49809),
            
            // Адреса на Новочерёмушинской улице
            "москва, новочерёмушинская улица, 17" to Point(55.68022, 37.59315),
            "москва, новочеремушинская улица, 17" to Point(55.68022, 37.59315),
            "новочерёмушинская улица, 17" to Point(55.68022, 37.59315),
            "новочеремушинская улица, 17" to Point(55.68022, 37.59315),
            "новочерёмушинская, 17" to Point(55.68022, 37.59315),
            "новочеремушинская, 17" to Point(55.68022, 37.59315),
            "новочерёмушинская 17" to Point(55.68022, 37.59315),
            "новочеремушинская 17" to Point(55.68022, 37.59315),
            "д.17 новочерёмушинская" to Point(55.68022, 37.59315),
            "д.17 новочеремушинская" to Point(55.68022, 37.59315)
        )
    }
    
    LaunchedEffect(orders) {
        scope.launch {
            println("\n=== Starting geocoding process for ${orders.size} orders ===\n")
            println("Moscow bounds: ${MOSCOW_BOUNDS.southWest} to ${MOSCOW_BOUNDS.northEast}")
            
            val results = mutableListOf<Pair<Order, Point?>>()
            val knownAddresses = getKnownAddressCoordinates()
            
            orders.forEach { order ->
                println("\n==========================================")
                println("Processing order ${order.orderNumber}: ${order.externalOrderNumber}")
                println("Original address: ${order.deliveryAddress}")
                
                // Сначала проверяем наличие адреса в списке известных адресов
                val normalizedAddress = normalizeAddress(order.deliveryAddress)
                println("Normalized address: $normalizedAddress")
                
                // Прямое совпадение
                var knownPoint = knownAddresses[normalizedAddress]
                
                // Если прямое совпадение не найдено, проверяем частичное
                if (knownPoint == null) {
                    val matchingEntry = knownAddresses.entries.firstOrNull { (knownAddr, _) ->
                        normalizedAddress.contains(knownAddr) || knownAddr.contains(normalizedAddress)
                    }
                    
                    if (matchingEntry != null) {
                        println("Found partially matching known address: ${matchingEntry.key}")
                        knownPoint = matchingEntry.value
                    }
                }
                
                if (knownPoint != null) {
                    println("Found in known addresses dictionary: $normalizedAddress -> $knownPoint")
                    results.add(order to knownPoint)
                    
                    // Сохраняем координаты в базе данных
                    try {
                        viewModel.updateOrderCoordinates(
                            order.id, 
                            knownPoint.latitude, 
                            knownPoint.longitude
                        )
                        println("Saved known address coordinates to database for order ${order.orderNumber}")
                    } catch (e: Exception) {
                        println("Failed to save known address coordinates: ${e}")
                    }
                    return@forEach
                }
                
                // Проверяем особые случаи адресов через регулярные выражения
                val (_, specialPoint) = handleSpecialAddresses(order.deliveryAddress)
                if (specialPoint != null) {
                    println("Using special case coordinates for order ${order.orderNumber}")
                    results.add(order to specialPoint)
                    
                    // Сохраняем координаты в базе данных
                    try {
                        viewModel.updateOrderCoordinates(
                            order.id, 
                            specialPoint.latitude, 
                            specialPoint.longitude
                        )
                        println("Saved special case coordinates to database for order ${order.orderNumber}")
                    } catch (e: Exception) {
                        println("Failed to save special case coordinates: ${e}")
                    }
                    return@forEach
                }
                
                // Проверяем, есть ли уже корректные координаты
                if (order.latitude != null && order.longitude != null) {
                    // Проверяем, что координаты не являются дефолтными (центр Москвы)
                    if (Math.abs(order.latitude - 55.751574) > 0.0001 || 
                        Math.abs(order.longitude - 37.573856) > 0.0001) {
                        println("Using existing coordinates: ${order.latitude}, ${order.longitude}")
                        results.add(order to Point(order.latitude, order.longitude))
                        return@forEach
                    } else {
                        println("Found default coordinates (center of Moscow), will try geocoding")
                    }
                } else {
                    println("No coordinates found, will try geocoding")
                }
                
                // Пробуем геокодировать адрес
                val formattedAddress = formatAddress(order.deliveryAddress)
                println("Formatted address: $formattedAddress")
                
                val point = geocodeAddress(formattedAddress)
                
                if (point != null) {
                    println("\nSuccessfully geocoded address for order ${order.orderNumber}")
                    println("Final coordinates: $point")
                    results.add(order to point)
                    
                    // Сохраняем координаты в базе данных
                    try {
                        viewModel.updateOrderCoordinates(
                            order.id, 
                            point.latitude, 
                            point.longitude
                        )
                        println("Saved coordinates to database for order ${order.orderNumber}")
                    } catch (e: Exception) {
                        println("Failed to save coordinates: ${e}")
                    }
                } else {
                    println("\nFailed to geocode address for order ${order.orderNumber}")
                    results.add(order to null)
                }
            }
            
            geocodedOrders = results
            
            println("\n=== Geocoding process completed ===\n")
            println("Successfully geocoded: ${geocodedOrders.count { it.second != null }}")
            println("Failed to geocode: ${geocodedOrders.count { it.second == null }}")
            
            // Выводим адреса, которые не удалось геокодировать
            println("\nFailed to geocode the following orders:")
            geocodedOrders.filter { it.second == null }.forEach { (order, _) ->
                println("- Order ${order.orderNumber}: ${order.externalOrderNumber}")
                println("  Address: ${order.deliveryAddress}")
            }
        }
    }

    // Отслеживаем изменения selectedOrder с улучшенным логированием
    LaunchedEffect(selectedOrder) {
        println("LaunchedEffect: selectedOrder changed to: ${selectedOrder?.orderNumber}")
        selectedOrder?.let { order ->
            println("LaunchedEffect: Selected order details: ${order.orderNumber}, ${order.deliveryAddress}")
        }
    }

    // Правильно управляем жизненным циклом MapView
    DisposableEffect(Unit) {
        // Вызываем onStart при создании экрана
        try {
            mapView?.onStart()
            println("MapView onStart вызван")
        } catch (e: Exception) {
            println("Ошибка при вызове onStart: ${e.message}")
            e.printStackTrace()
        }
        
        onDispose {
            // Освобождаем все ресурсы при уничтожении экрана
            try {
                println("Останавливаем MapView")
                userLocationMarker = null // Освобождаем ссылку на маркер
                mapView?.mapWindow?.map?.mapObjects?.clear() // Очищаем все объекты с карты
                mapView?.onStop() // Останавливаем MapView
                mapView = null // Освобождаем ссылку на MapView
                println("MapView успешно остановлен и освобожден")
            } catch (e: Exception) {
                println("Ошибка при освобождении MapView: ${e.message}")
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
                    // Напрямую обновляем selectedOrder, поскольку он связан с selectedOrderState
                    selectedOrder = tappedOrder
                    println("MapObjectTapListener: After setting, selectedOrder is now ${selectedOrder?.orderNumber}")
                }
                true
            } else {
                println("MapObjectTapListener: ERROR: Tapped order is null!")
                false
            }
        }
    }

    // Отфильтрованный список заказов по времени доставки
    val filteredOrdersWithPoints = remember(geocodedOrders, selectedTimeRanges) {
        if (selectedTimeRanges.isEmpty()) {
            geocodedOrders // Если фильтр не применен, возвращаем все заказы
        } else {
            // Фильтруем заказы по выбранным временным интервалам
            geocodedOrders.filter { (order, _) ->
                isOrderInSelectedTimeRanges(order)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карта доставок") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    // Кнопка для центрирования карты на местоположении пользователя
                    IconButton(
                        onClick = {
                            try {
                                if (locationPermissionGranted) {
                                    // Проверка на доступность mapView
                                    if (mapView == null) {
                                        Toast.makeText(context, "Карта не инициализирована", Toast.LENGTH_SHORT).show()
                                        return@IconButton
                                    }
                                    
                                    // Обновляем местоположение
                                    getCurrentLocation()
                                    
                                    // Центрируем карту на местоположении пользователя, если оно доступно
                                    // (эта логика уже встроена в getCurrentLocation())
                                } else {
                                    Toast.makeText(context, "Необходимо разрешение на доступ к местоположению", Toast.LENGTH_LONG).show()
                                    requestLocationPermissions()
                                }
                            } catch (e: Exception) {
                                println("Ошибка при нажатии на кнопку местоположения: ${e.message}")
                                e.printStackTrace()
                                Toast.makeText(context, "Произошла ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Моё местоположение"
                        )
                    }
                    
                    // Кнопка для фильтрации по временным интервалам
                    IconButton(
                        onClick = { 
                            showTimeFilterDialog = true 
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Фильтр по времени доставки"
                        )
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
                            // Начальное положение карты - центр Москвы
                            view.mapWindow.map.move(
                                CameraPosition(
                                    Point(55.751574, 37.573856),
                                    11.0f,
                                    0.0f,
                                    0.0f
                                )
                            )
                            
                            // Включаем отображение местоположения пользователя, если есть разрешение
                            if (locationPermissionGranted) {
                                try {
                                    // Запрашиваем текущее местоположение пользователя
                                    getCurrentLocation()
                                    
                                    println("Запрошено местоположение пользователя")
                                } catch (e: Exception) {
                                    println("Ошибка при получении местоположения пользователя: ${e.message}")
                                    e.printStackTrace()
                                }
                            } else {
                                println("Нет разрешения на доступ к местоположению, не могу показать местоположение пользователя")
                            }
                            
                            isMapReady = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Ошибка при инициализации карты: ${e}"
                        MapView(context)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    try {
                        // Защита от доступа к уничтоженному объекту
                        if (view.mapWindow == null) {
                            println("MapView.mapWindow равен null, пропускаем обновление")
                            return@AndroidView
                        }
                        
                        val map = view.mapWindow.map
                        
                        // Безопасная очистка объектов
                        try {
                            map.mapObjects.clear()
                        } catch (e: Exception) {
                            println("Ошибка при очистке объектов на карте: ${e.message}")
                            e.printStackTrace()
                            return@AndroidView
                        }
                        
                        // Используем отфильтрованные заказы вместо всех заказов
                        val adjustedPoints = filteredOrdersWithPoints
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
                        
                        // Отображаем маркеры с учетом скорректированных позиций
                        adjustedPoints.forEach { (order, point, _) ->
                            try {
                                // Создаем опции для маркера
                                val markerBitmap = createMarkerBitmap(view.context, order)
                                
                                // Создаем маркер с использованием новых API
                                val placemark = mapObjects.addPlacemark().apply {
                                    geometry = point
                                    setIcon(ImageProvider.fromBitmap(markerBitmap))
                                    userData = order // Сохраняем заказ в userData маркера
                                    
                                    // Добавляем логирование для отслеживания создания маркера
                                    println("Created marker for order ${order.orderNumber} at $point")
                                }
                                
                                // Используем созданный слушатель нажатий
                                try {
                                    placemark.addTapListener(mapObjectTapListener)
                                } catch (e: Exception) {
                                    println("Ошибка при добавлении слушателя нажатий: ${e.message}")
                                }
                            } catch (e: Exception) {
                                println("Ошибка при создании маркера для заказа ${order.orderNumber}: ${e.message}")
                            }
                        }

                        // Центрируем карту на первой точке только при первой загрузке
                        if (!isMapReady && adjustedPoints.isNotEmpty()) {
                            try {
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
                            } catch (e: Exception) {
                                println("Ошибка при центрировании карты: ${e.message}")
                                isMapReady = true // Помечаем как готовую, чтобы избежать повторных попыток
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Ошибка при обновлении карты: ${e}"
                    }
                }
            )
            
            // Нижняя панель с информацией о заказе
            selectedOrder?.let { currentOrder ->
                var isExpanded by remember { mutableStateOf(false) }
                // Компактная карточка
                AnimatedVisibility(
                    visible = !isExpanded,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val cardColor = if (currentOrder.status == OrderStatus.IN_PROGRESS) ComposeColor(0xFFB9F6CA) else ComposeColor.White
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                            .clickable { isExpanded = true },
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            // Крестик в правом верхнем углу
                            IconButton(
                                onClick = { selectedOrder = null },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 40.dp) // Оставляем место под крестик
                                ) {
                                    Text(
                                        text = "${currentOrder.orderNumber}. ${currentOrder.externalOrderNumber ?: ""}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${currentOrder.orderAmount} ₽",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(currentOrder.clientName ?: "", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = ComposeColor.Gray, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(currentOrder.deliveryAddress, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = ComposeColor.Gray, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${currentOrder.deliveryTimeStart.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))} - ${currentOrder.deliveryTimeEnd?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: ""}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                if (!currentOrder.notes.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = ComposeColor.Gray, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(currentOrder.notes ?: "", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                // Детальная карточка
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val cardColor = if (currentOrder.status == OrderStatus.IN_PROGRESS) ComposeColor(0xFFB9F6CA) else ComposeColor.White
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${currentOrder.orderNumber}. Заказ ${currentOrder.externalOrderNumber ?: "Н/Д"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                IconButton(onClick = { isExpanded = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Свернуть")
                                }
                            }
                            Divider()
                            OrderDetailsContent(
                                order = currentOrder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                onStatusChange = { order, newStatus ->
                                    selectedOrder = order.copy(status = newStatus)
                                    onStatusUpdate?.invoke(order, newStatus)
                                },
                                onNotesChange = { order, newNotes ->
                                    selectedOrder = order.copy(notes = newNotes)
                                    onStatusUpdate?.invoke(order.copy(notes = newNotes), order.status)
                                },
                                onTakePhoto = {
                                    selectedOrder = null
                                    viewModel.navigateToPhotoCapture(currentOrder)
                                },
                                onPhotoClick = { uri ->
                                    viewModel.navigateToPhotoViewer(uri)
                                }
                            )
                        }
                    }
                }
            }

            // Отображаем диалог фильтра времени если showTimeFilterDialog = true
            if (showTimeFilterDialog) {
                AlertDialog(
                    onDismissRequest = { showTimeFilterDialog = false },
                    title = { Text("Фильтр по времени доставки") },
                    text = {
                        Column {
                            // Кнопка для выбора всех временных интервалов
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Если все выбраны, снимаем выбор со всех, иначе выбираем все
                                        selectedTimeRanges = if (selectedTimeRanges.size == DeliveryTimeRange.values().size) {
                                            emptySet()
                                        } else {
                                            DeliveryTimeRange.values().toSet()
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = selectedTimeRanges.size == DeliveryTimeRange.values().size,
                                    onCheckedChange = { checked ->
                                        selectedTimeRanges = if (checked) {
                                            DeliveryTimeRange.values().toSet()
                                        } else {
                                            emptySet()
                                        }
                                    }
                                )
                                Text("Выбрать все", modifier = Modifier.padding(start = 8.dp))
                            }
                            
                            Divider()
                            
                            // Список временных интервалов
                            DeliveryTimeRange.values().forEach { timeRange ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedTimeRanges = if (selectedTimeRanges.contains(timeRange)) {
                                                selectedTimeRanges - timeRange
                                            } else {
                                                selectedTimeRanges + timeRange
                                            }
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedTimeRanges.contains(timeRange),
                                        onCheckedChange = { checked ->
                                            selectedTimeRanges = if (checked) {
                                                selectedTimeRanges + timeRange
                                            } else {
                                                selectedTimeRanges - timeRange
                                            }
                                        }
                                    )
                                    Text(timeRange.title, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTimeFilterDialog = false }) {
                            Text("Применить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            selectedTimeRanges = emptySet() 
                            showTimeFilterDialog = false
                        }) {
                            Text("Сбросить")
                        }
                    }
                )
            }
        }
    }
} 