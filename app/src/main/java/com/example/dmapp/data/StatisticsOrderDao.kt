package com.example.dmapp.data

import androidx.room.*
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface StatisticsOrderDao {
    @Insert
    suspend fun insert(order: StatisticsOrder): Long {
        println("DEBUG: StatisticsOrderDao.insert: Сохранение заказа в статистику")
        println("DEBUG: Заказ №${order.orderNumber}, внешний №${order.externalOrderNumber}")
        println("DEBUG: Фото: URI=${order.photoUri}, DateTime=${order.photoDateTime}")
        return insertInternal(order)
    }

    @Insert
    suspend fun insertInternal(order: StatisticsOrder): Long

    @Insert
    suspend fun insertAll(orders: List<StatisticsOrder>)

    @Query("SELECT * FROM statistics_orders WHERE date(completionDate) = :dateString")
    suspend fun getOrdersCompletedOnDate(dateString: String): List<StatisticsOrder> {
        println("DEBUG: StatisticsOrderDao.getOrdersCompletedOnDate: Получение выполненных заказов за дату $dateString")
        val orders = getOrdersCompletedOnDateInternal(dateString)
        println("DEBUG: Получено ${orders.size} заказов")
        orders.forEach { order ->
            println("DEBUG: Заказ №${order.orderNumber}, фото: URI=${order.photoUri}, DateTime=${order.photoDateTime}")
        }
        return orders
    }

    @Query("SELECT * FROM statistics_orders WHERE date(completionDate) = :dateString")
    suspend fun getOrdersCompletedOnDateInternal(dateString: String): List<StatisticsOrder>

    @Query("SELECT * FROM statistics_orders")
    suspend fun getAllOrders(): List<StatisticsOrder>

    @Query("SELECT COUNT(*) FROM statistics_orders WHERE date(completionDate) = :date")
    suspend fun getOrderCountForDate(date: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM statistics_orders WHERE externalOrderNumber = :externalOrderNumber LIMIT 1)")
    suspend fun orderExistsInStatistics(externalOrderNumber: String): Boolean

    @Query("SELECT * FROM statistics_orders WHERE orderNumber = :orderNumber LIMIT 1")
    suspend fun findByOrderNumber(orderNumber: Int): StatisticsOrder?

    @Query("SELECT * FROM statistics_orders WHERE externalOrderNumber = :externalOrderNumber LIMIT 1")
    suspend fun findByExternalOrderNumber(externalOrderNumber: String): StatisticsOrder? {
        println("DEBUG: StatisticsOrderDao.findByExternalOrderNumber: Поиск заказа с внешним номером $externalOrderNumber")
        val order = findByExternalOrderNumberInternal(externalOrderNumber)
        if (order != null) {
            println("DEBUG: Найден заказ: №${order.orderNumber}")
            println("DEBUG: Фото: URI=${order.photoUri}, DateTime=${order.photoDateTime}")
        } else {
            println("DEBUG: Заказ не найден")
        }
        return order
    }

    @Query("SELECT * FROM statistics_orders WHERE externalOrderNumber = :externalOrderNumber LIMIT 1")
    suspend fun findByExternalOrderNumberInternal(externalOrderNumber: String): StatisticsOrder?

    @Update
    suspend fun updateOrder(order: StatisticsOrder) {
        println("DEBUG: StatisticsOrderDao.updateOrder: Обновление заказа в статистике")
        println("DEBUG: Заказ №${order.orderNumber}, внешний №${order.externalOrderNumber}")
        println("DEBUG: Фото: URI=${order.photoUri}, DateTime=${order.photoDateTime}")
        updateOrderInternal(order)
    }

    @Update
    suspend fun updateOrderInternal(order: StatisticsOrder)

    @Query("SELECT COUNT(*) FROM statistics_orders WHERE date(completionDate) = :date")
    suspend fun getCompletedOrdersCountForDate(date: String): Int

    @Query("SELECT * FROM statistics_orders WHERE date(completionDate) = :dateString")
    suspend fun getOrdersForDate(dateString: String): List<StatisticsOrder> {
        println("DEBUG: StatisticsOrderDao.getOrdersForDate: Получение заказов за дату $dateString")
        val orders = getOrdersForDateInternal(dateString)
        println("DEBUG: Получено ${orders.size} заказов")
        orders.forEach { order ->
            println("DEBUG: Заказ №${order.orderNumber}, фото: URI=${order.photoUri}, DateTime=${order.photoDateTime}")
        }
        return orders
    }

    @Query("SELECT * FROM statistics_orders WHERE date(completionDate) = :dateString")
    suspend fun getOrdersForDateInternal(dateString: String): List<StatisticsOrder>

    @Query("UPDATE statistics_orders SET photoUri = :photoUri WHERE id = :orderId")
    suspend fun updateOrderPhoto(orderId: Long, photoUri: String?)

    @Query("UPDATE statistics_orders SET photoDateTime = :photoDateTime WHERE id = :orderId")
    suspend fun updateOrderPhotoDateTime(orderId: Long, photoDateTime: LocalDateTime?)

    @Query("DELETE FROM statistics_orders WHERE date(completionDate) = :dateString")
    suspend fun deleteOrdersForDate(dateString: String) {
        println("DEBUG: StatisticsOrderDao.deleteOrdersForDate: Удаление заказов за дату $dateString")
        deleteOrdersForDateInternal(dateString)
        println("DEBUG: Заказы за дату $dateString удалены")
    }

    @Query("DELETE FROM statistics_orders WHERE date(completionDate) = :dateString")
    suspend fun deleteOrdersForDateInternal(dateString: String)

    @Query("DELETE FROM statistics_orders")
    suspend fun deleteAllOrders() {
        println("DEBUG: StatisticsOrderDao.deleteAllOrders: Удаление всех заказов из статистики")
        deleteAllOrdersInternal()
        println("DEBUG: Все заказы удалены из статистики")
    }

    @Query("DELETE FROM statistics_orders")
    suspend fun deleteAllOrdersInternal()
} 