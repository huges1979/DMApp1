package com.example.dmapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.*
import com.yandex.runtime.Error
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import android.content.Context

class OrderRepository(
    private val orderDao: OrderDao,
    private val context: Context
) {
    private val searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)

    val activeOrders = orderDao.getActiveOrders()
    val completedOrders = orderDao.getCompletedOrders()
    val activeOrdersCount = orderDao.getActiveOrdersCount()
    val completedOrdersCount = orderDao.getCompletedOrdersCount()

    private suspend fun geocodeAddress(address: String): Point? {
        // Константы для границ Москвы
        val MOSCOW_BOUNDS = BoundingBox(
            Point(55.48992, 37.319331), // юго-западная граница Москвы
            Point(55.957565, 37.907543) // северо-восточная граница Москвы
        )
        
        println("\n=== Starting geocoding for address: $address ===")
        
        // Создаем различные варианты адреса для геокодинга
        val formattedAddress = formatAddress(address)
        
        // Варианты адреса для попыток геокодинга
        val addressVariants = mutableListOf<String>()
        addressVariants.add(formattedAddress) // Основной формат
        
        // Вариант без слова "город"
        addressVariants.add(formattedAddress.replace("город Москва, ", ""))
        
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
        }
        
        // Простой вариант адреса (без деталей о доме)
        val simpleAddress = address.replace(Regex("(?i)д\\.?\\s*\\d+.*$"), "").trim()
        if (simpleAddress.length > 10) { // Убедимся, что у нас остается достаточно информации
            addressVariants.add("город Москва, $simpleAddress")
        }
        
        println("Will try geocoding with following address variants:")
        addressVariants.forEachIndexed { index, variant -> 
            println("$index: $variant") 
        }
        
        // Пробуем каждый вариант адреса
        for (addressToGeocode in addressVariants) {
            try {
                val point = suspendCancellableCoroutine<Point?> { continuation ->
                    val searchOptions = SearchOptions().apply {
                        searchTypes = SearchType.GEO.value
                        resultPageSize = 1
                        geometry = true
                        disableSpellingCorrection = false
                    }
                    
                    println("Trying to geocode: $addressToGeocode")
                    val session = searchManager.submit(
                        addressToGeocode,
                        Geometry.fromBoundingBox(MOSCOW_BOUNDS),
                        searchOptions,
                        object : Session.SearchListener {
                            override fun onSearchResponse(response: Response) {
                                val result = response.collection.children.firstOrNull()?.obj
                                val pt = result?.geometry?.firstOrNull()?.point
                                
                                if (pt != null && 
                                    pt.latitude in 55.48992..55.957565 &&
                                    pt.longitude in 37.319331..37.907543) {
                                    println("\nGeocoding SUCCESS")
                                    println("Input address: $addressToGeocode")
                                    println("Found location: ${result.name}")
                                    println("Found address: ${result.descriptionText}")
                                    println("Coordinates: $pt")
                                    continuation.resume(pt)
                                } else {
                                    println("No valid result found for variant: $addressToGeocode")
                                    continuation.resume(null)
                                }
                            }

                            override fun onSearchError(error: Error) {
                                println("Search error for variant: $addressToGeocode, error: $error")
                                continuation.resume(null)
                            }
                        }
                    )
                    
                    continuation.invokeOnCancellation {
                        session.cancel()
                    }
                }
                
                if (point != null) {
                    println("Found coordinates: $point for address: $address using variant: $addressToGeocode")
                    return point
                }
            } catch (e: Exception) {
                println("Exception during geocoding: ${e.message}")
                e.printStackTrace()
            }
        }
        
        println("All variants failed, couldn't geocode address: $address")
        return null
    }

    suspend fun importOrders(text: String): ImportResult {
        println("\n=== importOrders: Начало импорта заказов ===")
        println("Получен текст для импорта: ${text.take(100)}${if (text.length > 100) "..." else ""}")
        
        // Получаем текущие выполненные заказы из статистики
        val statisticsRepository = StatisticsRepository(context)
        val existingStatsOrders = statisticsRepository.getOrdersFromStatisticsForDate(LocalDate.now())
        println("Найдено ${existingStatsOrders.size} существующих заказов в статистике")
        
        val lines = text.split("\n")
        println("Разбор ${lines.size} строк")
        
        var importedCount = 0
        var skippedCount = 0
        var errorCount = 0
        
        // Получаем максимальный номер заказа
        val maxOrderNumber = orderDao.getMaxOrderNumber() ?: 0
        println("Текущий максимальный номер заказа: $maxOrderNumber")
        
        var currentOrderNumber = maxOrderNumber + 1
        println("Начинаем импорт с номера: $currentOrderNumber")
        
        var currentOrder = mutableMapOf<String, String>()
        var isProcessingOrder = false
        
        for (line in lines) {
            if (line.isBlank()) {
                if (isProcessingOrder) {
                    // Завершаем обработку текущего заказа
                    try {
                        val externalOrderNumber = currentOrder["orderNumber"] ?: continue
                        println("Обработка заказа с внешним номером: $externalOrderNumber")
                        
                        // Проверяем, не существует ли уже такой заказ
                        val existingOrder = orderDao.findDuplicateOrder(externalOrderNumber)
                        if (existingOrder != null) {
                            println("Заказ $externalOrderNumber уже существует в базе, пропускаем")
                            skippedCount++
                            currentOrder.clear()
                            isProcessingOrder = false
                            continue
                        }
                        
                        println("Заказ $externalOrderNumber не найден в базе, создаем новый")
                        
                        // Парсим время доставки
                        val deliveryInterval = currentOrder["deliveryInterval"] ?: "с 09:00 до 18:00"
                        val (startTime, endTime) = parseDeliveryInterval(deliveryInterval)
                        
                        // Парсим вес
                        val weightStr = currentOrder["weight"] ?: "0.0"
                        val weight = weightStr.replace("кг", "").trim().toDoubleOrNull() ?: 0.0
                        
                        // Парсим сумму заказа
                        val amountStr = currentOrder["orderAmount"] ?: "0.0"
                        val amount = amountStr.replace("₽", "").trim().toDoubleOrNull() ?: 0.0
                        
                        // Создаем новый заказ
                        val order = Order(
                            orderNumber = currentOrderNumber++,
                            externalOrderNumber = externalOrderNumber,
                            pickupLocation = currentOrder["pickupLocation"] ?: "",
                            sector = currentOrder["sector"] ?: "",
                            place = currentOrder["place"] ?: "",
                            deliveryAddress = currentOrder["deliveryAddress"] ?: "",
                            clientName = currentOrder["clientName"] ?: "",
                            clientPhone = currentOrder["clientPhone"] ?: "",
                            clientComment = currentOrder["clientComment"],
                            deliveryTimeStart = startTime,
                            deliveryTimeEnd = endTime,
                            weight = weight,
                            volume = 0.0,
                            isPrepaid = currentOrder["isPrepaid"]?.contains("Да") == true,
                            courierName = currentOrder["courierName"] ?: "",
                            courierPhone = currentOrder["courierPhone"] ?: "",
                            orderAmount = amount,
                            status = OrderStatus.NEW,
                            isCompleted = false
                        )
                        
                        println("Создан новый заказ: №${order.orderNumber}, внешний номер: ${order.externalOrderNumber}")
                        
                        // Сохраняем заказ в базу данных
                        val id = orderDao.insert(order)
                        println("Заказ сохранен в базу данных с id: $id")
                        
                        importedCount++
                    } catch (e: Exception) {
                        println("Ошибка при обработке заказа: ${e.message}")
                        e.printStackTrace()
                        errorCount++
                    }
                    
                    currentOrder.clear()
                    isProcessingOrder = false
                }
                continue
            }
            
            when {
                line.startsWith("Заказ") -> {
                    isProcessingOrder = true
                    // Извлекаем номер заказа из строки вида "Заказ 1234567890 (предоплачен):"
                    val fullLine = line.substringAfter("Заказ").trim()
                    // Ищем последовательность из 10 цифр
                    val orderNumberMatch = "\\d{10}".toRegex().find(fullLine)
                    if (orderNumberMatch != null) {
                        val orderNumber = orderNumberMatch.value
                        currentOrder["orderNumber"] = orderNumber
                        println("Найден номер заказа: $orderNumber")
                    } else {
                        println("Не удалось найти номер заказа (10 цифр) в строке: $line")
                    }
                }
                line.startsWith("Забрать из:") -> {
                    currentOrder["pickupLocation"] = line.substringAfter("Забрать из:").trim()
                }
                line.startsWith("Cектор:") -> {
                    val parts = line.split("Место:")
                    currentOrder["sector"] = parts[0].substringAfter("Cектор:").trim()
                    if (parts.size > 1) {
                        currentOrder["place"] = parts[1].trim()
                    }
                }
                line.startsWith("Телефон клиента:") -> {
                    currentOrder["clientPhone"] = line.substringAfter("Телефон клиента:").trim()
                }
                line.startsWith("ФИО клиента:") -> {
                    currentOrder["clientName"] = line.substringAfter("ФИО клиента:").trim()
                }
                line.startsWith("Адрес клиента:") -> {
                    currentOrder["deliveryAddress"] = line.substringAfter("Адрес клиента:").trim()
                }
                line.startsWith("Комментарий клиента:") -> {
                    currentOrder["clientComment"] = line.substringAfter("Комментарий клиента:").trim()
                }
                line.startsWith("Интервал доставки:") -> {
                    currentOrder["deliveryInterval"] = line.substringAfter("Интервал доставки:").trim()
                }
                line.startsWith("Вес заказа:") -> {
                    currentOrder["weight"] = line.substringAfter("Вес заказа:").trim()
                }
                line.startsWith("Заказ предоплачен:") -> {
                    currentOrder["isPrepaid"] = line.substringAfter("Заказ предоплачен:").trim()
                }
                line.startsWith("Назначен курьер:") -> {
                    val courierInfo = line.substringAfter("Назначен курьер:").trim()
                    val parts = courierInfo.split("+")
                    if (parts.size > 1) {
                        currentOrder["courierName"] = parts[0].trim()
                        currentOrder["courierPhone"] = "+" + parts[1].trim()
                    }
                }
                line.startsWith("Сумма заказа:") -> {
                    currentOrder["orderAmount"] = line.substringAfter("Сумма заказа:").trim()
                }
            }
        }
        
        // Обрабатываем последний заказ, если он есть
        if (isProcessingOrder && currentOrder.isNotEmpty()) {
            try {
                val externalOrderNumber = currentOrder["orderNumber"] ?: return ImportResult(importedCount, skippedCount, errorCount)
                
                // Проверяем, не существует ли уже такой заказ
                val existingOrder = orderDao.findDuplicateOrder(externalOrderNumber)
                if (existingOrder != null) {
                    println("Заказ $externalOrderNumber уже существует в основной базе, пропускаем")
                    skippedCount++
                    return ImportResult(importedCount, skippedCount, errorCount)
                }
                
                println("Заказ $externalOrderNumber не найден в основной базе, создаем новый")
                
                // Парсим время доставки
                val deliveryInterval = currentOrder["deliveryInterval"] ?: "с 09:00 до 18:00"
                val (startTime, endTime) = parseDeliveryInterval(deliveryInterval)
                
                // Парсим вес
                val weightStr = currentOrder["weight"] ?: "0.0"
                val weight = weightStr.replace("кг", "").trim().toDoubleOrNull() ?: 0.0
                
                // Парсим сумму заказа
                val amountStr = currentOrder["orderAmount"] ?: "0.0"
                val amount = amountStr.replace("₽", "").trim().toDoubleOrNull() ?: 0.0
                
                // Создаем новый заказ
                val order = Order(
                    orderNumber = currentOrderNumber,
                    externalOrderNumber = externalOrderNumber,
                    pickupLocation = currentOrder["pickupLocation"] ?: "",
                    sector = currentOrder["sector"] ?: "",
                    place = currentOrder["place"] ?: "",
                    deliveryAddress = currentOrder["deliveryAddress"] ?: "",
                    clientName = currentOrder["clientName"] ?: "",
                    clientPhone = currentOrder["clientPhone"] ?: "",
                    clientComment = currentOrder["clientComment"],
                    deliveryTimeStart = startTime,
                    deliveryTimeEnd = endTime,
                    weight = weight,
                    volume = 0.0,
                    isPrepaid = currentOrder["isPrepaid"]?.contains("Да") == true,
                    courierName = currentOrder["courierName"] ?: "",
                    courierPhone = currentOrder["courierPhone"] ?: "",
                    orderAmount = amount,
                    status = OrderStatus.NEW,
                    isCompleted = false
                )
                
                println("Создан новый заказ: №${order.orderNumber}, внешний номер: ${order.externalOrderNumber}")
                
                // Сохраняем заказ в базу данных
                val id = orderDao.insert(order)
                println("Заказ сохранен в базу данных с id: $id")
                
                importedCount++
            } catch (e: Exception) {
                println("Ошибка при обработке последнего заказа: ${e.message}")
                e.printStackTrace()
                errorCount++
            }
        }
        
        println("Импорт завершен:")
        println("- Импортировано заказов: $importedCount")
        println("- Пропущено заказов: $skippedCount")
        println("- Ошибок: $errorCount")
        println("=== importOrders: Завершено ===\n")
        
        return ImportResult(importedCount, skippedCount, errorCount)
    }

    private fun parseOrderText(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        // Extract order number - улучшенное распознавание номера
        val orderNumberRegex = "Заказ\\s*(?:№)?\\s*(\\d+)".toRegex()
        orderNumberRegex.find(text)?.let {
            result["orderNumber"] = it.groupValues[1]
        }

        // Extract other fields
        val lines = text.split("\n")
        lines.forEach { line ->
            when {
                line.startsWith("Забрать из:") -> {
                    result["pickupLocation"] = line.substringAfter("Забрать из:").trim()
                }
                line.startsWith("Cектор:") -> {
                    val parts = line.split("Место:")
                    result["sector"] = parts[0].substringAfter("Cектор:").trim()
                    if (parts.size > 1) {
                        result["place"] = parts[1].trim()
                    }
                }
                line.startsWith("Телефон клиента:") -> {
                    result["clientPhone"] = line.substringAfter("Телефон клиента:").trim()
                }
                line.startsWith("ФИО клиента:") -> {
                    result["clientName"] = line.substringAfter("ФИО клиента:").trim()
                }
                line.startsWith("Адрес клиента:") -> {
                    // Извлекаем адрес и заменяем "ё" на "е"
                    val originalAddress = line.substringAfter("Адрес клиента:").trim()
                    val addressWithoutYo = originalAddress.replace('ё', 'е').replace('Ё', 'Е')
                    
                    // Сохраняем адрес без "ё"
                    result["deliveryAddress"] = addressWithoutYo
                }
                line.startsWith("Комментарий клиента:") -> {
                    result["clientComment"] = line.substringAfter("Комментарий клиента:").trim()
                }
                line.startsWith("Интервал доставки:") -> {
                    result["deliveryInterval"] = line.substringAfter("Интервал доставки:").trim()
                }
                line.startsWith("Вес заказа:") -> {
                    result["weight"] = line.substringAfter("Вес заказа:").trim()
                }
                line.startsWith("Объем заказа:") -> {
                    result["volume"] = line.substringAfter("Объем заказа:").trim()
                }
                line.startsWith("Заказ предоплачен:") -> {
                    result["isPrepaid"] = line.substringAfter("Заказ предоплачен:").trim()
                }
                line.startsWith("Назначен курьер:") -> {
                    val courierInfo = line.substringAfter("Назначен курьер:").trim()
                    val parts = courierInfo.split("+")
                    if (parts.size > 1) {
                        result["courierName"] = parts[0].trim()
                        result["courierPhone"] = "+" + parts[1].trim()
                    }
                }
                line.startsWith("Сумма заказа:") -> {
                    result["orderAmount"] = line.substringAfter("Сумма заказа:").trim()
                }
            }
        }
        
        return result
    }

    private fun parseDeliveryInterval(interval: String): Pair<LocalDateTime, LocalDateTime> {
        val pattern = Pattern.compile("с (\\d{2}:\\d{2}) до (\\d{2}:\\d{2})")
        val matcher = pattern.matcher(interval)
        
        if (matcher.find()) {
            val startTimeStr = matcher.group(1)
            val endTimeStr = matcher.group(2)
            
            val today = LocalDate.now()
            val startTime = LocalTime.parse(startTimeStr)
            val endTime = LocalTime.parse(endTimeStr)
            
            return Pair(
                LocalDateTime.of(today, startTime),
                LocalDateTime.of(today, endTime)
            )
        }
        
        // Если не удалось распарсить интервал, возвращаем текущее время
        val now = LocalDateTime.now()
        return Pair(now, now.plusHours(1))
    }

    suspend fun updateOrderStatus(order: Order, newStatus: OrderStatus) {
        println("\n=== updateOrderStatus: Начало обновления статуса заказа ===")
        println("Заказ №${order.orderNumber}, внешний №${order.externalOrderNumber}")
        println("Текущий статус: ${order.status}, isCompleted: ${order.isCompleted}")
        println("Новый статус: $newStatus")
        
        val updatedOrder = order.copy(
            status = newStatus,
            isCompleted = newStatus == OrderStatus.COMPLETED
        )
        println("Подготовленный заказ для обновления:")
        println("- Статус: ${updatedOrder.status}")
        println("- isCompleted: ${updatedOrder.isCompleted}")
        
        // Сначала обновляем статус в основной базе данных
        orderDao.update(updatedOrder)
        println("Заказ обновлен в базе данных")
        
        // Проверяем, что заказ действительно обновился
        val checkOrder = orderDao.findOrderById(order.id)
        println("Проверка после обновления:")
        println("- Статус: ${checkOrder?.status}")
        println("- isCompleted: ${checkOrder?.isCompleted}")
        
        // Если заказ помечен как выполненный, сохраняем его в статистику
        if (newStatus == OrderStatus.COMPLETED && checkOrder != null) {
            println("Заказ помечен как выполненный, сохраняем в статистику")
            val statisticsRepository = StatisticsRepository(context)
            statisticsRepository.saveOrderToStatistics(checkOrder)
            println("Заказ сохранен в статистику")
        }
        
        println("=== updateOrderStatus: Завершено ===\n")
    }

    suspend fun deleteCompletedOrders(): Int {
        return orderDao.deleteCompletedOrders()
    }

    suspend fun getAllCompletedOrders(): List<Order> {
        return orderDao.getAllCompletedOrders()
    }

    suspend fun getCompletedOrdersForDate(date: LocalDate): List<Order> {
        val formattedDate = date.toString()
        println("Getting completed orders for date: $formattedDate")
        
        // Получаем все выполненные заказы
        val allCompletedOrders = orderDao.getAllCompletedOrdersForStatistics()
        println("Total completed orders in DB: ${allCompletedOrders.size}")
        
        if (allCompletedOrders.isEmpty()) {
            println("В базе данных нет выполненных заказов")
            return emptyList()
        }
        
        // Выводим информацию о первом заказе для отладки
        if (allCompletedOrders.isNotEmpty()) {
            val firstOrder = allCompletedOrders[0]
            println("Пример заказа: №${firstOrder.orderNumber}, дата: ${firstOrder.deliveryTimeStart}, статус: ${firstOrder.status}")
        }
        
        // Фильтруем их вручную по дате
        val ordersForDate = allCompletedOrders.filter { order ->
            val orderDate = order.deliveryTimeStart.toLocalDate()
            val result = orderDate == date
            println("Заказ ${order.orderNumber}, дата ${orderDate}, совпадает с $date: $result")
            result
        }
        
        println("Filtered orders for date $date: ${ordersForDate.size}")
        return ordersForDate
    }

    suspend fun updateOrderNotes(orderId: Long, notes: String) {
        val order = orderDao.findOrderById(orderId) ?: return
        val updatedOrder = order.copy(notes = notes)
        orderDao.update(updatedOrder)
    }

    /**
     * Обновляет координаты заказа в базе данных
     */
    suspend fun updateOrderCoordinates(orderId: Long, latitude: Double, longitude: Double) {
        val order = orderDao.findOrderById(orderId) ?: return
        val updatedOrder = order.copy(latitude = latitude, longitude = longitude)
        orderDao.update(updatedOrder)
        println("Updated coordinates for order ${order.orderNumber}: lat=$latitude, lng=$longitude")
    }

    fun formatAddress(address: String): String {
        // Сначала нормализуем текст и заменяем "ё" на "е"
        var formatted = address.trim()
            .replace("\\s+".toRegex(), " ") // Заменяем множественные пробелы на один
            .replace('ё', 'е') // Заменяем строчную "ё" на "е"
            .replace('Ё', 'Е') // Заменяем заглавную "Ё" на "Е"

        // Логируем, если была произведена замена
        if (address.contains('ё') || address.contains('Ё')) {
            println("В formatAddress заменена буква 'ё' на 'е': '$address' -> '$formatted'")
        }
        
        // Приводим слово "Москва" к стандартному виду
        formatted = formatted.replace(Regex("(?i)г\\.?\\s*москва[,\\s]*"), "")
            .replace(Regex("(?i)москва[,\\s]*"), "")
            .replace(Regex("(?i)город\\s+"), "")
            .replace(Regex("(?i)гор\\.?\\s*"), "")
        
        // Удаляем лишнюю информацию
        formatted = formatted.replace(Regex("(?i)подъезд\\s*№?\\s*\\d+"), "")
            .replace(Regex("(?i)этаж\\s*\\d+"), "")
            .replace(Regex("(?i)эт\\.?\\s*\\d+"), "")
            .replace(Regex("(?i)кв\\.?\\s*\\d+[а-я]?"), "")
            .replace(Regex("(?i)квартира\\s*\\d+[а-я]?"), "")
        
        // Нормализуем типы улиц
        formatted = formatted.replace(Regex("(?i)улица\\s+"), "улица ")
            .replace(Regex("(?i)ул\\s+"), "улица ")
            .replace(Regex("(?i)ул\\.\\s+"), "улица ")
            .replace(Regex("(?i)проспект\\s+"), "проспект ")
            .replace(Regex("(?i)пр\\s+"), "проспект ")
            .replace(Regex("(?i)пр-т\\s+"), "проспект ")
            .replace(Regex("(?i)пр\\.\\s+"), "проспект ")
            .replace(Regex("(?i)переулок\\s+"), "переулок ")
            .replace(Regex("(?i)пер\\s+"), "переулок ")
            .replace(Regex("(?i)пер\\.\\s+"), "переулок ")
            .replace(Regex("(?i)бульвар\\s+"), "бульвар ")
            .replace(Regex("(?i)бул\\s+"), "бульвар ")
            .replace(Regex("(?i)бул\\.\\s+"), "бульвар ")
            .replace(Regex("(?i)б-р\\s+"), "бульвар ")
            .replace(Regex("(?i)шоссе\\s+"), "шоссе ")
            .replace(Regex("(?i)ш\\s+"), "шоссе ")
            .replace(Regex("(?i)ш\\.\\s+"), "шоссе ")
            .replace(Regex("(?i)площадь\\s+"), "площадь ")
            .replace(Regex("(?i)пл\\s+"), "площадь ")
            .replace(Regex("(?i)пл\\.\\s+"), "площадь ")
            .replace(Regex("(?i)набережная\\s+"), "набережная ")
            .replace(Regex("(?i)наб\\s+"), "набережная ")
            .replace(Regex("(?i)наб\\.\\s+"), "набережная ")
        
        // Особый случай: Обработка улицы и номера дома как наиболее важных компонентов
        var streetMatch: MatchResult? = null
        val streetPatterns = listOf(
            Regex("(?i)(улица|проспект|переулок|бульвар|шоссе|площадь|набережная)\\s+[А-Яа-я\\s-]+"),
            Regex("(?i)([А-Яа-я]+)\\s+(улица|проспект|переулок|бульвар|шоссе|площадь|набережная)")
        )
        
        for (pattern in streetPatterns) {
            streetMatch = pattern.find(formatted)
            if (streetMatch != null) break
        }
        
        val streetPart = streetMatch?.value?.trim() ?: ""
        
        // Обрабатываем номера домов с корпусами
        val housePatterns = listOf(
            Regex("(?i)дом\\s*(\\d+)\\s*корпус\\s*(\\d+)"),
            Regex("(?i)дом\\s*(\\d+)\\s*корп\\.?\\s*(\\d+)"),
            Regex("(?i)дом\\s*(\\d+)\\s*к\\s*(\\d+)"),
            Regex("(?i)д\\.?\\s*(\\d+)\\s*корпус\\s*(\\d+)"),
            Regex("(?i)д\\.?\\s*(\\d+)\\s*корп\\.?\\s*(\\d+)"),
            Regex("(?i)д\\.?\\s*(\\d+)\\s*к\\s*(\\d+)"),
            Regex("(?i)д\\s*(\\d+)\\s*к\\s*(\\d+)"),
            Regex("(?i)(\\d+)\\s*к\\s*(\\d+)"),
            Regex("(?i)(\\d+)\\s*корп\\.?\\s*(\\d+)")
        )
        
        var houseWithCorpus = ""
        
        for (pattern in housePatterns) {
            val match = pattern.find(formatted)
            if (match != null) {
                val houseNumber = match.groupValues[1]
                val corpusNumber = match.groupValues[2]
                houseWithCorpus = "дом $houseNumber корпус $corpusNumber"
                break
            }
        }
        
        // Если не нашли дом с корпусом, ищем обычный номер дома
        if (houseWithCorpus.isEmpty()) {
            val singleHousePatterns = listOf(
                Regex("(?i)дом\\s*(\\d+)"),
                Regex("(?i)д\\.?\\s*(\\d+)"),
                Regex("(?i)д\\s*(\\d+)"),
                Regex("(?i)\\b(\\d+)\\b") // Просто число, которое может быть номером дома
            )
            
            for (pattern in singleHousePatterns) {
                val match = pattern.find(formatted)
                if (match != null) {
                    houseWithCorpus = "дом ${match.groupValues[1]}"
                    break
                }
            }
        }
        
        // Проверяем на строения
        val buildingPatterns = listOf(
            Regex("(?i)\\bстр\\.?\\s*(\\d+)\\b"),
            Regex("(?i)\\bстроение\\s*(\\d+)\\b")
        )
        
        var buildingPart = ""
        
        for (pattern in buildingPatterns) {
            val match = pattern.find(formatted)
            if (match != null) {
                buildingPart = "строение ${match.groupValues[1]}"
                break
            }
        }
        
        // Соединяем все компоненты вместе
        val addressComponents = mutableListOf<String>()
        if (streetPart.isNotEmpty()) addressComponents.add(streetPart)
        if (houseWithCorpus.isNotEmpty()) addressComponents.add(houseWithCorpus)
        if (buildingPart.isNotEmpty()) addressComponents.add(buildingPart)
        
        val resultAddress = if (addressComponents.isNotEmpty()) {
            addressComponents.joinToString(", ")
        } else {
            // Если не удалось разобрать адрес, оставляем исходный
            formatted
        }
        
        // Добавляем "город Москва" в начало адреса
        return "город Москва, $resultAddress".trim()
    }

    suspend fun updateOrderPhoto(orderId: Long, photoUri: String?) {
        orderDao.updateOrderPhoto(orderId, photoUri)
    }

    suspend fun updateOrderPhotoDateTime(orderId: Long, photoDateTime: LocalDateTime?) {
        orderDao.updateOrderPhotoDateTime(orderId, photoDateTime)
    }

    suspend fun getOrderById(orderId: Long): Order? {
        return orderDao.findOrderById(orderId)
    }

    private fun parseAddress(addressParts: List<String>): String {
        val address = addressParts.joinToString(" ")
            .replace("К ", "")
            .replace("Москва, ", "")
        
        // Разбиваем адрес на части
        val parts = address.split(", ").map { it.trim() }
        if (parts.isEmpty()) return address

        val result = mutableListOf<String>()
        
        // Обрабатываем первую часть (улица)
        val firstPart = parts[0]
        result.add(firstPart)
        
        // Обрабатываем остальные части
        for (i in 1 until parts.size) {
            val part = parts[i]
            
            when {
                // Если это вторая часть и она содержит цифры (возможно с буквой или слешем) - это номер дома
                i == 1 && (part.any { it.isDigit() } && (part.length <= 5 || part.contains("/"))) -> {
                    result.add("дом $part")
                }
                // Если это последняя часть и она длиннее 2 символов, это квартира
                i == parts.size - 1 && part.length > 2 -> {
                    result.add("кв. $part")
                }
                // Если это одна цифра, это корпус
                part.length == 1 && part.all { it.isDigit() } -> {
                    result.add("корп. $part")
                }
                // Если это буква или буква с цифрой, это корпус
                part.length <= 2 && part.any { it.isLetter() } -> {
                    result.add("корп. $part")
                }
                // В остальных случаях добавляем как есть
                else -> {
                    result.add(part)
                }
            }
        }
        
        return result.joinToString(", ")
    }

    suspend fun importOrdersNewFormat(text: String): ImportResult {
        println("\n=== importOrdersNewFormat: Начало импорта заказов в новом формате ===")
        println("Получен текст для импорта: ${text.take(100)}${if (text.length > 100) "..." else ""}")
        
        val lines = text.split("\n")
        println("Разбор ${lines.size} строк")
        
        var importedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var errorCount = 0
        
        // Получаем максимальный номер заказа
        val maxOrderNumber = orderDao.getMaxOrderNumber() ?: 0
        println("Текущий максимальный номер заказа: $maxOrderNumber")
        
        var currentOrderNumber = maxOrderNumber + 1
        println("Начинаем импорт с номера: $currentOrderNumber")
        
        for (line in lines) {
            if (line.isBlank()) continue
            
            try {
                // Разбиваем строку на части
                val parts = line.split(" ")
                println("\n=== Обработка строки заказа ===")
                println("Строка целиком: '$line'")
                println("Разбитая строка на части: ${parts.joinToString(" | ")}")
                
                if (parts.size < 4) {
                    println("Строка слишком короткая, пропускаем: $line")
                    continue
                }
                
                // Извлекаем данные в правильном порядке
                // 1. Пропускаем "М" (parts[0])
                // 2. Пропускаем "Москва" (parts[1])
                // 3. Пропускаем "Черёмушки" (parts[2])
                
                // 4. Вес (был в parts[5])
                val weight = parts[3].replace(",", ".").toDoubleOrNull() ?: 0.0
                println("Вес заказа: $weight")
                
                // 5. Номер заказа (был в parts[3])
                val externalOrderNumber = parts[4]
                println("Внешний номер заказа: $externalOrderNumber")
                
                // 6. Телефон клиента (был в parts[4])
                val phone = parts[5]
                println("Телефон клиента: $phone")
                
                // Находим индекс начала адреса (после телефона)
                var addressStartIndex = 6
                
                // Находим индекс интервала доставки
                val deliveryIntervalIndex = parts.indexOfFirst { it.contains("_to_") }
                println("Индекс интервала доставки: $deliveryIntervalIndex")

                // Извлекаем статус из всей строки, добавляя пробел после интервала
                val statusStr = line.substringAfter("_to_ ").trim()
                println("\n=== Обработка статуса заказа ===")
                println("Строка целиком: '$line'")
                println("Все части строки: ${parts.joinToString(" | ")}")
                println("Полный статус: '$statusStr'")
                println("Длина статуса: ${statusStr.length}")
                println("Коды символов статуса: ${statusStr.map { it.code }}")

                val orderStatus = when {
                    statusStr.contains("Готов к выдаче") -> {
                        println("Найден статус 'Готов к выдаче', устанавливаем OrderStatus.READY")
                        OrderStatus.READY
                    }
                    statusStr.contains("Реализация") -> {
                        println("Найден статус 'Реализация', устанавливаем OrderStatus.REALIZATION")
                        OrderStatus.REALIZATION
                    }
                    statusStr.contains("Готов") -> {
                        println("Найден статус 'Готов', устанавливаем OrderStatus.READY")
                        OrderStatus.READY
                    }
                    statusStr.contains("Новый") -> {
                        println("Найден статус 'Новый', устанавливаем OrderStatus.NEW")
                        OrderStatus.NEW
                    }
                    statusStr.contains("Отгружен") -> {
                        println("Найден статус 'Отгружен', устанавливаем OrderStatus.SHIPPED")
                        OrderStatus.SHIPPED
                    }
                    statusStr.contains("Отменен") || statusStr.contains("Отмена") -> {
                        println("Найден статус 'Отменен', устанавливаем OrderStatus.CANCELLED")
                        OrderStatus.CANCELLED
                    }
                    else -> {
                        println("Неизвестный статус: '$statusStr', используем статус 'Новый'")
                        OrderStatus.NEW
                    }
                }
                println("Итоговый статус заказа: $orderStatus")
                println("=== Конец обработки статуса ===\n")
                
                // Извлекаем адрес (все части между телефоном и интервалом доставки)
                val addressParts = parts.subList(addressStartIndex, deliveryIntervalIndex)
                val deliveryAddress = parseAddress(addressParts)
                println("Адрес доставки: $deliveryAddress")
                
                // Извлекаем интервал доставки
                val deliveryInterval = parts[deliveryIntervalIndex]
                val (startTime, endTime) = parseDeliveryIntervalNewFormat(deliveryInterval)
                println("Интервал доставки: $deliveryInterval (с $startTime до $endTime)")
                
                // Проверяем, существует ли уже такой заказ
                val existingOrder = orderDao.findDuplicateOrder(externalOrderNumber)
                if (existingOrder != null) {
                    println("\n=== Обновление существующего заказа ===")
                    println("Найден существующий заказ $externalOrderNumber")
                    println("Текущий статус: ${existingOrder.status}")
                    println("Новый статус: $orderStatus")
                    
                    // Если статус изменился, обновляем заказ
                    if (existingOrder.status != orderStatus) {
                        println("Статус заказа изменился с ${existingOrder.status} на $orderStatus")
                        val updatedOrder = existingOrder.copy(
                            status = orderStatus,
                            isCompleted = orderStatus == OrderStatus.COMPLETED
                        )
                        orderDao.update(updatedOrder)
                        println("Заказ обновлен с новым статусом")
                        updatedCount++
                    } else {
                        println("Статус заказа не изменился, пропускаем")
                        skippedCount++
                    }
                    println("=== Конец обновления заказа ===\n")
                    continue
                }
                
                // Создаем новый заказ
                val order = Order(
                    orderNumber = currentOrderNumber++,
                    externalOrderNumber = externalOrderNumber,
                    pickupLocation = "", // Пустое поле
                    sector = "", // Пустое поле
                    place = "", // Пустое поле
                    deliveryAddress = deliveryAddress,
                    clientName = "", // Пустое поле
                    clientPhone = phone,
                    clientComment = null, // Пустое поле
                    deliveryTimeStart = startTime,
                    deliveryTimeEnd = endTime,
                    weight = weight,
                    volume = 0.0, // Пустое поле
                    isPrepaid = false, // Пустое поле
                    courierName = "", // Пустое поле
                    courierPhone = "", // Пустое поле
                    orderAmount = 0.0, // Пустое поле
                    status = orderStatus,
                    isCompleted = orderStatus == OrderStatus.COMPLETED
                )
                
                println("\n=== Создание нового заказа ===")
                println("Номер заказа: ${order.orderNumber}")
                println("Внешний номер: ${order.externalOrderNumber}")
                println("Статус: ${order.status}")
                println("isCompleted: ${order.isCompleted}")
                
                // Сохраняем заказ в базу данных
                val id = orderDao.insert(order)
                println("Заказ сохранен в базу данных с id: $id")
                println("=== Конец создания заказа ===\n")
                
                importedCount++
            } catch (e: Exception) {
                println("Ошибка при обработке заказа: ${e.message}")
                e.printStackTrace()
                errorCount++
            }
        }
        
        println("Импорт завершен:")
        println("- Импортировано новых заказов: $importedCount")
        println("- Обновлено существующих заказов: $updatedCount")
        println("- Пропущено заказов: $skippedCount")
        println("- Ошибок: $errorCount")
        println("=== importOrdersNewFormat: Завершено ===\n")
        
        return ImportResult(importedCount + updatedCount, skippedCount, errorCount)
    }

    private fun parseDeliveryIntervalNewFormat(interval: String): Pair<LocalDateTime, LocalDateTime> {
        val pattern = Pattern.compile("(\\d+)_to_(\\d+)")
        val matcher = pattern.matcher(interval)
        
        if (matcher.find()) {
            val startHour = matcher.group(1).toInt()
            val endHour = matcher.group(2).toInt()
            
            val today = LocalDate.now()
            val startTime = LocalTime.of(startHour, 0)
            val endTime = LocalTime.of(endHour, 0)
            
            return Pair(
                LocalDateTime.of(today, startTime),
                LocalDateTime.of(today, endTime)
            )
        }
        
        // Если не удалось распарсить интервал, возвращаем текущее время
        val now = LocalDateTime.now()
        return Pair(now, now.plusHours(1))
    }

    suspend fun deleteOrder(orderId: Long) {
        println("OrderRepository.deleteOrder: Удаление заказа с id: $orderId")
        orderDao.deleteOrderById(orderId)
        println("OrderRepository.deleteOrder: Заказ успешно удален")
    }
}

data class ImportResult(
    val newOrders: Int,
    val duplicates: Int,
    val errors: Int
) 