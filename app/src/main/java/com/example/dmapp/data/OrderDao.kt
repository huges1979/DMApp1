package com.example.dmapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE status != 'COMPLETED' ORDER BY deliveryTimeStart ASC")
    fun getActiveOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE status = 'COMPLETED' ORDER BY deliveryTimeStart DESC")
    fun getCompletedOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE status = 'COMPLETED'")
    suspend fun getAllCompletedOrders(): List<Order>

    @Query("SELECT * FROM orders WHERE status = 'COMPLETED'")
    suspend fun getAllCompletedOrdersForStatistics(): List<Order> {
        println("OrderDao.getAllCompletedOrdersForStatistics: Запрос выполненных заказов")
        val orders = getAllCompletedOrders()
        println("OrderDao.getAllCompletedOrdersForStatistics: Получено "+orders.size+" выполненных заказов")
        if (orders.isNotEmpty()) {
            println("Первый заказ: номер="+orders[0].orderNumber+", статус="+orders[0].status+", isCompleted="+orders[0].isCompleted)
        }
        return orders
    }

    @Query("SELECT COUNT(*) FROM orders WHERE status != 'COMPLETED'")
    fun getActiveOrdersCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'")
    fun getCompletedOrdersCount(): Flow<Int>

    @Query("SELECT * FROM orders WHERE externalOrderNumber = :number LIMIT 1")
    suspend fun findDuplicateOrder(number: String): Order? {
        println("OrderDao.findDuplicateOrder: Поиск заказа с номером: $number")
        val order = findDuplicateOrderInternal(number)
        if (order != null) {
            println("OrderDao.findDuplicateOrder: Найден существующий заказ: №${order.orderNumber}, внешний номер: ${order.externalOrderNumber}")
        } else {
            println("OrderDao.findDuplicateOrder: Заказ с номером $number не найден")
        }
        return order
    }

    @Query("SELECT * FROM orders WHERE externalOrderNumber = :number LIMIT 1")
    suspend fun findDuplicateOrderInternal(number: String): Order?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: Order): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(order: Order): Int

    @Query("DELETE FROM orders WHERE status = 'COMPLETED'")
    suspend fun deleteCompletedOrders(): Int

    @Query("SELECT MAX(orderNumber) FROM orders")
    suspend fun getMaxOrderNumber(): Int?

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun findOrderById(orderId: Long): Order?

    @Query("UPDATE orders SET notes = :notes WHERE id = :orderId")
    suspend fun updateOrderNotes(orderId: Long, notes: String): Int
    
    @Query("UPDATE orders SET latitude = :latitude, longitude = :longitude WHERE id = :orderId")
    suspend fun updateOrderCoordinates(orderId: Long, latitude: Double, longitude: Double): Int

    @Query("UPDATE orders SET photoUri = :photoUri WHERE id = :orderId")
    suspend fun updateOrderPhoto(orderId: Long, photoUri: String?): Int {
        println("OrderDao.updateOrderPhoto: Updating photo for order $orderId with URI: $photoUri")
        try {
            val result = updateOrderPhotoInternal(orderId, photoUri)
            println("OrderDao.updateOrderPhoto: Successfully updated photo for order $orderId")
            return result
        } catch (e: Exception) {
            println("OrderDao.updateOrderPhoto: Error updating photo for order $orderId: ${e.message}")
            throw e
        }
    }

    @Query("UPDATE orders SET photoUri = :photoUri WHERE id = :orderId")
    suspend fun updateOrderPhotoInternal(orderId: Long, photoUri: String?): Int

    @Query("UPDATE orders SET photoDateTime = :photoDateTime WHERE id = :orderId")
    suspend fun updateOrderPhotoDateTime(orderId: Long, photoDateTime: LocalDateTime?): Int {
        println("OrderDao.updateOrderPhotoDateTime: Updating photo datetime for order $orderId with value: $photoDateTime")
        try {
            val result = updateOrderPhotoDateTimeInternal(orderId, photoDateTime)
            println("OrderDao.updateOrderPhotoDateTime: Successfully updated photo datetime for order $orderId")
            return result
        } catch (e: Exception) {
            println("OrderDao.updateOrderPhotoDateTime: Error updating photo datetime for order $orderId: ${e.message}")
            throw e
        }
    }

    @Query("UPDATE orders SET photoDateTime = :photoDateTime WHERE id = :orderId")
    suspend fun updateOrderPhotoDateTimeInternal(orderId: Long, photoDateTime: LocalDateTime?): Int

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrderById(orderId: Long): Int
} 