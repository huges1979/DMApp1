package com.example.dmapp.data

import androidx.room.*
import java.time.LocalDate

@Dao
interface StatisticsOrderDao {
    @Insert
    suspend fun insert(order: StatisticsOrder): Long

    @Insert
    suspend fun insertAll(orders: List<StatisticsOrder>)

    @Query("SELECT * FROM statistics_orders WHERE date(completionDate) = :date")
    suspend fun getOrdersCompletedOnDate(date: String): List<StatisticsOrder>

    @Query("SELECT * FROM statistics_orders")
    suspend fun getAllOrders(): List<StatisticsOrder>

    @Query("SELECT COUNT(*) FROM statistics_orders WHERE date(completionDate) = :date")
    suspend fun getOrderCountForDate(date: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM statistics_orders WHERE externalOrderNumber = :externalOrderNumber LIMIT 1)")
    suspend fun orderExistsInStatistics(externalOrderNumber: String): Boolean
} 