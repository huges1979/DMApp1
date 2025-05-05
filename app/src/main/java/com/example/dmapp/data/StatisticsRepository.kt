package com.example.dmapp.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.room.Room

/**
 * Репозиторий для работы со статистикой
 */
class StatisticsRepository(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        STATS_PREFS_NAME, Context.MODE_PRIVATE
    )
    
    // Настраиваем Gson с адаптерами для корректной сериализации LocalDate
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .create()
    
    // Получаем доступ к базе данных
    private val database = AppDatabase.getDatabase(context)
    private val statisticsOrderDao = database.statisticsOrderDao()
    
    companion object {
        private const val STATS_PREFS_NAME = "statistics_prefs"
        private const val STATS_KEY = "all_statistics"
    }
    
    /**
     * Адаптер для сериализации/десериализации LocalDate в формате ISO
     */
    class LocalDateAdapter : TypeAdapter<LocalDate>() {
        override fun write(out: JsonWriter, value: LocalDate?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(value.toString()) // ISO-8601 формат (2022-12-31)
        }

        override fun read(input: JsonReader): LocalDate? {
            val dateStr = input.nextString()
            return if (dateStr.isNullOrEmpty()) null else LocalDate.parse(dateStr)
        }
    }
    
    /**
     * Получить все статистические данные
     */
    fun getStatistics(): Statistics {
        val statsJson = sharedPreferences.getString(STATS_KEY, null)
        println("Получение статистики из хранилища. JSON: ${statsJson?.take(100)}${if (statsJson?.length ?: 0 > 100) "..." else ""}")
        
        if (statsJson == null) {
            println("В хранилище нет данных статистики, возвращаем пустую статистику")
            return Statistics()
        }
        
        val type = object : TypeToken<List<DailyStatistics>>() {}.type
        val dailyStats: List<DailyStatistics> = try {
            gson.fromJson(statsJson, type)
        } catch (e: Exception) {
            println("Ошибка при десериализации статистики: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
        
        println("Загружена статистика: ${dailyStats.size} записей")
        if (dailyStats.isNotEmpty()) {
            dailyStats.forEach { stat ->
                println("- Дата: ${stat.date}, заказов: ${stat.completedOrders}, вес: ${stat.totalWeight}, пробег: ${stat.totalDistance}")
            }
        }
        
        return Statistics(dailyStats)
    }
    
    /**
     * Сохранить все статистические данные
     */
    fun saveStatistics(statistics: Statistics) {
        try {
            // Проверяем количество статистики за сегодня
            val today = LocalDate.now()
            val todayEntries = statistics.dailyStats.filter { 
                it.date.year == today.year && 
                it.date.month == today.month && 
                it.date.dayOfMonth == today.dayOfMonth 
            }
            
            println("Сохранение статистики: всего записей ${statistics.dailyStats.size}, записей за сегодня: ${todayEntries.size}")
            
            // Если есть дубликаты за сегодня, консолидируем их
            if (todayEntries.size > 1) {
                println("Обнаружены дубликаты записей за сегодня, консолидируем")
                
                // Создаем консолидированную запись
                val consolidatedStats = DailyStatistics(
                    date = today,
                    completedOrders = todayEntries.sumOf { it.completedOrders },
                    totalDistance = todayEntries.sumOf { it.totalDistance },
                    totalWeight = todayEntries.sumOf { it.totalWeight }
                )
                
                println("Консолидированная статистика: заказов ${consolidatedStats.completedOrders}, вес ${consolidatedStats.totalWeight}, пробег ${consolidatedStats.totalDistance}")
                
                // Удаляем все записи за сегодня
                val filteredStats = statistics.dailyStats.filter { 
                    !(it.date.year == today.year && 
                      it.date.month == today.month && 
                      it.date.dayOfMonth == today.dayOfMonth)
                }
                
                // Добавляем консолидированную запись
                val updatedStats = Statistics(filteredStats + consolidatedStats)
                
                // Сохраняем консолидированную статистику
                val statsJson = gson.toJson(updatedStats.dailyStats)
                sharedPreferences.edit().putString(STATS_KEY, statsJson).apply()
                println("Сохранена консолидированная статистика: ${updatedStats.dailyStats.size} записей")
                return
            }
            
            // Стандартное сохранение, если нет дубликатов
            val statsJson = gson.toJson(statistics.dailyStats)
            sharedPreferences.edit().putString(STATS_KEY, statsJson).apply()
            println("Сохранена статистика: ${statistics.dailyStats.size} записей")
            println("JSON: ${statsJson.take(100)}${if (statsJson.length > 100) "..." else ""}")
            
            // Проверяем, что сохранение прошло успешно
            val savedJson = sharedPreferences.getString(STATS_KEY, null)
            if (savedJson == null) {
                println("ОШИБКА: Не удалось сохранить статистику в SharedPreferences")
            } else {
                println("Проверка сохранения: данные успешно записаны")
            }
        } catch (e: Exception) {
            println("Ошибка при сохранении статистики: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Обновить статистику на основе выполненных заказов
     */
    fun updateStatisticsFromCompletedOrders(completedOrders: List<Order>) {
        if (completedOrders.isEmpty()) {
            println("updateStatisticsFromCompletedOrders: Список заказов пуст, обновление не требуется")
            return
        }
        
        println("updateStatisticsFromCompletedOrders: Получено ${completedOrders.size} заказов для обновления статистики")
        completedOrders.forEachIndexed { index, order -> 
            println("Заказ #${index + 1}: номер ${order.orderNumber}, статус ${order.status}, вес ${order.weight}") 
        }
        
        val today = LocalDate.now()
        val currentStats = getStatistics()
        
        // Логируем текущее состояние статистики
        val todayStatsBeforeUpdate = currentStats.getStatsForDay(today)
        println("Статистика до обновления: ${todayStatsBeforeUpdate?.completedOrders ?: 0} заказов, ${todayStatsBeforeUpdate?.totalWeight ?: 0.0} кг, ${todayStatsBeforeUpdate?.totalDistance ?: 0.0} км")
        
        // Создаем новую запись статистики для этой порции заказов
        val ordersCount = completedOrders.size
        val totalWeight = completedOrders.sumOf { it.weight }
        
        // Проверяем, есть ли уже статистика за сегодня
        if (todayStatsBeforeUpdate != null) {
            // Обновляем существующую статистику, добавляя новую информацию
            val updatedStats = todayStatsBeforeUpdate.copy(
                completedOrders = todayStatsBeforeUpdate.completedOrders + ordersCount,
                totalWeight = todayStatsBeforeUpdate.totalWeight + totalWeight,
                totalDistance = todayStatsBeforeUpdate.totalDistance + (ordersCount * 5.0) // примерный пробег
            )
            
            println("Обновление существующей статистики за сегодня: ${updatedStats.completedOrders} заказов, ${updatedStats.totalWeight} кг")
            
            // Заменяем статистику за сегодня на обновленную
            val filteredStats = currentStats.dailyStats.filter { 
                !(it.date.year == today.year && it.date.month == today.month && it.date.dayOfMonth == today.dayOfMonth)
            }
            val updatedStatsList = filteredStats + updatedStats
            
            saveStatistics(Statistics(updatedStatsList))
        } else {
            // Создаем новую запись статистики для сегодня
            val newStats = DailyStatistics(
                date = today,
                completedOrders = ordersCount,
                totalDistance = ordersCount * 5.0, // примерный пробег
                totalWeight = totalWeight
            )
            
            println("Создание новой статистики за сегодня: ${newStats.completedOrders} заказов, ${newStats.totalWeight} кг")
            
            // Обновляем статистику, добавляя новую запись
            val updatedStats = Statistics(currentStats.dailyStats + newStats)
            saveStatistics(updatedStats)
        }
    }
    
    /**
     * Сохранить выполненный заказ в таблицу статистики
     */
    suspend fun saveOrderToStatistics(order: Order) {
        try {
            // Проверяем, не существует ли заказ уже в статистике
            val exists = statisticsOrderDao.orderExistsInStatistics(order.externalOrderNumber)
            if (exists) {
                println("Заказ ${order.externalOrderNumber} уже существует в статистике, пропускаем")
                return
            }
            
            // Конвертируем Order в StatisticsOrder
            val statisticsOrder = StatisticsOrder(
                orderNumber = order.orderNumber,
                externalOrderNumber = order.externalOrderNumber,
                deliveryAddress = order.deliveryAddress,
                clientName = order.clientName,
                clientPhone = order.clientPhone,
                deliveryTimeStart = order.deliveryTimeStart,
                deliveryTimeEnd = order.deliveryTimeEnd,
                weight = order.weight,
                orderAmount = order.orderAmount,
                completionDate = LocalDate.now().atStartOfDay() // Текущая дата как дата выполнения
            )
            
            // Сохраняем в базу данных
            statisticsOrderDao.insert(statisticsOrder)
            println("Заказ ${order.externalOrderNumber} успешно сохранен в статистику")
            
            // Также обновляем статистику в SharedPreferences для обратной совместимости
            updateStatisticsFromCompletedOrders(listOf(order))
        } catch (e: Exception) {
            println("Ошибка при сохранении заказа в статистику: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Получить заказы из статистики по дате
     */
    suspend fun getOrdersFromStatisticsForDate(date: LocalDate): List<StatisticsOrder> {
        return try {
            val dateString = date.toString()
            println("Запрашиваем заказы из статистики за дату: $dateString")
            val orders = statisticsOrderDao.getOrdersCompletedOnDate(dateString)
            println("Получено ${orders.size} заказов из статистики за дату $dateString")
            orders
        } catch (e: Exception) {
            println("Ошибка при получении заказов из статистики: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Конвертировать StatisticsOrder в Order для отображения
     */
    fun convertToOrder(statisticsOrder: StatisticsOrder): Order {
        return Order(
            id = statisticsOrder.id,
            orderNumber = statisticsOrder.orderNumber,
            externalOrderNumber = statisticsOrder.externalOrderNumber,
            pickupLocation = "",  // Эти поля не важны для отображения в статистике
            sector = "",
            place = "",
            clientPhone = statisticsOrder.clientPhone,
            clientName = statisticsOrder.clientName,
            deliveryAddress = statisticsOrder.deliveryAddress,
            clientComment = null,
            deliveryTimeStart = statisticsOrder.deliveryTimeStart,
            deliveryTimeEnd = statisticsOrder.deliveryTimeEnd,
            weight = statisticsOrder.weight,
            volume = 0.0,  // Не важно для статистики
            isPrepaid = false,
            courierName = "",
            courierPhone = "",
            orderAmount = statisticsOrder.orderAmount,
            status = OrderStatus.COMPLETED
        )
    }
    
    /**
     * Получить и преобразовать заказы для отображения в UI
     */
    suspend fun getDisplayOrdersForDate(date: LocalDate): List<Order> {
        val statisticsOrders = getOrdersFromStatisticsForDate(date)
        return statisticsOrders.map { convertToOrder(it) }
    }
} 