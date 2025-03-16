package com.example.dmapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE status != 'COMPLETED' ORDER BY deliveryTimeStart ASC")
    fun getActiveOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE status = 'COMPLETED' ORDER BY deliveryTimeStart DESC")
    fun getCompletedOrders(): Flow<List<Order>>

    @Query("SELECT COUNT(*) FROM orders WHERE status != 'COMPLETED'")
    fun getActiveOrdersCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'")
    fun getCompletedOrdersCount(): Flow<Int>

    @Query("SELECT * FROM orders WHERE externalOrderNumber = :number LIMIT 1")
    suspend fun findDuplicateOrder(number: String): Order?

    @Insert
    suspend fun insert(order: Order): Long

    @Update
    suspend fun update(order: Order)

    @Query("DELETE FROM orders WHERE status = 'COMPLETED'")
    suspend fun deleteCompletedOrders()

    @Query("SELECT MAX(orderNumber) FROM orders")
    suspend fun getMaxOrderNumber(): Int?

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun findOrderById(orderId: Long): Order?

    @Query("UPDATE orders SET notes = :notes WHERE id = :orderId")
    suspend fun updateOrderNotes(orderId: Long, notes: String)
    
    @Query("UPDATE orders SET latitude = :latitude, longitude = :longitude WHERE id = :orderId")
    suspend fun updateOrderCoordinates(orderId: Long, latitude: Double, longitude: Double)
} 