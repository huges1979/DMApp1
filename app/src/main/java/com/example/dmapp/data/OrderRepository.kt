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

class OrderRepository(private val orderDao: OrderDao) {
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
        var newOrders = 0
        var duplicates = 0
        
        // Получаем текущий максимальный номер заказа
        val currentMaxOrderNumber = orderDao.getMaxOrderNumber() ?: 0
        var nextOrderNumber = currentMaxOrderNumber + 1

        // Разбиваем текст на отдельные заказы и создаем временный список
        val ordersToImport = mutableListOf<Order>()
        
        println("\n=== Starting order import process ===")
        
        // Улучшенное разбиение текста на заказы
        // Ищем все строки, содержащие "Заказ" с помощью регулярного выражения
        val orderRegex = "(?:^|\\n)\\s*(Заказ\\s*(?:№)?\\s*\\d+.+?)(?=\\n\\s*Заказ\\s*(?:№)?\\s*\\d+|$)".toRegex(RegexOption.DOT_MATCHES_ALL)
        val orderMatches = orderRegex.findAll(text)
        
        val allOrders = orderMatches.map { it.groupValues[1].trim() }.toList()
        println("Found ${allOrders.size} orders in text")
        
        // Если не найдено ни одного заказа, пробуем запасной вариант разделения
        val processedOrders = if (allOrders.isEmpty()) {
            println("No orders found using regex, trying fallback method")
            text.split("\n\n")
                .filter { it.trim().isNotEmpty() }
                .filter { order ->
                    val containsOrderWord = order.contains("Заказ", ignoreCase = true)
                    if (!containsOrderWord) {
                        println("Skipping text block without 'Заказ' keyword: ${order.take(50)}...")
                    }
                    containsOrderWord
                }
        } else {
            allOrders
        }
        
        println("Processing ${processedOrders.size} orders")
        
        // Обрабатываем каждый заказ
        processedOrders.forEach { orderText ->
            try {
                val orderMap = parseOrderText(orderText)
                val externalOrderNumber = orderMap["orderNumber"]
                
                if (externalOrderNumber == null) {
                    println("WARNING: Could not extract order number from text. First 100 chars: ${orderText.take(100)}...")
                    return@forEach
                }
                
                println("Processing order $externalOrderNumber")

                // Проверяем на дубликаты
                val existingOrder = orderDao.findDuplicateOrder(externalOrderNumber)
                if (existingOrder == null) {
                    // Парсим время доставки
                    val (startTime, endTime) = parseDeliveryInterval(orderMap["deliveryInterval"] ?: "")
                    
                    // Извлекаем адрес доставки и заменяем "ё" на "е"
                    var deliveryAddress = orderMap["deliveryAddress"] ?: ""
                    
                    // Заменяем "ё" на "е" в адресе при импорте
                    val addressWithoutYo = deliveryAddress.replace('ё', 'е').replace('Ё', 'Е')
                    
                    // Если была произведена замена, логируем её
                    if (deliveryAddress != addressWithoutYo) {
                        println("В адресе заменена 'ё' на 'е': '$deliveryAddress' -> '$addressWithoutYo'")
                        deliveryAddress = addressWithoutYo
                    }
                    
                    println("Delivery address: $deliveryAddress")
                    
                    // Геокодируем адрес
                    println("Starting geocoding for address: $deliveryAddress")
                    val coordinates = geocodeAddress(deliveryAddress)
                    
                    if (coordinates != null) {
                        println("Successfully geocoded address: $deliveryAddress")
                        println("Coordinates: lat=${coordinates.latitude}, lng=${coordinates.longitude}")
                    } else {
                        println("Failed to geocode address: $deliveryAddress")
                        println("Will use default coordinates (center of Moscow)")
                    }
                    
                    // Создаем новый заказ (пока без номера)
                    val order = Order(
                        orderNumber = 0, // Временный номер
                        externalOrderNumber = externalOrderNumber,
                        pickupLocation = orderMap["pickupLocation"] ?: "",
                        sector = orderMap["sector"] ?: "",
                        place = orderMap["place"] ?: "",
                        clientPhone = orderMap["clientPhone"] ?: "",
                        clientName = orderMap["clientName"] ?: "",
                        deliveryAddress = deliveryAddress,
                        clientComment = orderMap["clientComment"],
                        deliveryTimeStart = startTime,
                        deliveryTimeEnd = endTime,
                        weight = orderMap["weight"]?.replace(" кг", "")?.toDoubleOrNull() ?: 0.0,
                        volume = orderMap["volume"]?.replace(" м³", "")?.toDoubleOrNull() ?: 0.0,
                        isPrepaid = orderMap["isPrepaid"]?.contains("Да") ?: false,
                        courierName = orderMap["courierName"] ?: "",
                        courierPhone = orderMap["courierPhone"] ?: "",
                        orderAmount = orderMap["orderAmount"]?.replace("[^0-9.]".toRegex(), "")?.toDoubleOrNull() ?: 0.0,
                        latitude = coordinates?.latitude,
                        longitude = coordinates?.longitude
                    )
                    
                    ordersToImport.add(order)
                    newOrders++
                    println("Order $externalOrderNumber added for import")
                } else {
                    duplicates++
                    println("Order $externalOrderNumber is a duplicate, skipping")
                }
            } catch (e: Exception) {
                println("ERROR processing order text: ${e}")
                println("Problematic text: ${orderText.take(200)}...")
            }
        }
        
        // Сортируем заказы по времени доставки
        val sortedOrders = ordersToImport.sortedBy { it.deliveryTimeStart }
        
        // Присваиваем порядковые номера и сохраняем в базу
        sortedOrders.forEach { order ->
            val orderWithNumber = order.copy(orderNumber = nextOrderNumber)
            orderDao.insert(orderWithNumber)
            println("Saved order ${orderWithNumber.externalOrderNumber} with number $nextOrderNumber")
            nextOrderNumber++
        }
        
        println("=== Import completed: $newOrders new orders, $duplicates duplicates ===\n")
        return ImportResult(newOrders, duplicates)
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
        orderDao.update(order.copy(status = newStatus))
    }

    suspend fun deleteCompletedOrders(): Int {
        return orderDao.deleteCompletedOrders()
    }

    suspend fun getAllCompletedOrders(): List<Order> {
        return orderDao.getAllCompletedOrders()
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
}

data class ImportResult(
    val newOrders: Int,
    val duplicates: Int
) 