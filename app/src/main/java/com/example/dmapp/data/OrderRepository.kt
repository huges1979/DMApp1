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
        return suspendCancellableCoroutine { continuation ->
            try {
                val searchSession = searchManager.submit(
                    address,
                    Geometry.fromBoundingBox(BoundingBox(
                        Point(55.751574, 37.573856), // Москва
                        Point(55.751574, 37.573856)
                    )),
                    SearchOptions(),
                    object : Session.SearchListener {
                        override fun onSearchResponse(response: Response) {
                            val point = response.collection.children.firstOrNull()?.obj?.geometry?.get(0)?.point
                            continuation.resume(point)
                        }

                        override fun onSearchError(error: Error) {
                            println("Search error: ${error}")
                            continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume(null)
            }
        }
    }

    suspend fun importOrders(text: String): ImportResult {
        var newOrders = 0
        var duplicates = 0
        
        // Get the current max order number
        val currentMaxOrderNumber = orderDao.getMaxOrderNumber() ?: 0
        var nextOrderNumber = currentMaxOrderNumber + 1

        // Split text into individual orders
        val orders = text.split("\n\n").filter { it.trim().startsWith("Заказ") }

        orders.forEach { orderText ->
            val orderMap = parseOrderText(orderText)
            val externalOrderNumber = orderMap["orderNumber"] ?: return@forEach

            val existingOrder = orderDao.findDuplicateOrder(externalOrderNumber)
            if (existingOrder == null) {
                // Parse delivery time interval
                val (startTime, endTime) = parseDeliveryInterval(orderMap["deliveryInterval"] ?: "")
                
                // Geocode the delivery address
                val deliveryAddress = orderMap["deliveryAddress"] ?: ""
                val coordinates = geocodeAddress(deliveryAddress)
                
                // Create new order
                val order = Order(
                    orderNumber = nextOrderNumber,
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
                
                orderDao.insert(order)
                newOrders++
                nextOrderNumber++
            } else {
                duplicates++
            }
        }
        
        return ImportResult(newOrders, duplicates)
    }

    private fun parseOrderText(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        // Extract order number
        "Заказ (\\d+):".toRegex().find(text)?.let {
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
                    result["deliveryAddress"] = line.substringAfter("Адрес клиента:").trim()
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
        val count = orderDao.getCompletedOrdersCount().first()
        orderDao.deleteCompletedOrders()
        return count
    }

    suspend fun updateOrderNotes(orderId: Long, notes: String) {
        val order = orderDao.findOrderById(orderId) ?: return
        orderDao.update(order.copy(notes = notes))
    }
}

data class ImportResult(
    val newOrders: Int,
    val duplicates: Int
) 