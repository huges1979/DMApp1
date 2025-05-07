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
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
    suspend fun updateStatisticsFromCompletedOrders(completedOrders: List<Order>) {
        println("\n=== updateStatisticsFromCompletedOrders: Начало обновления статистики ===")
        println("DEBUG: Метод updateStatisticsFromCompletedOrders вызван")
        
        if (completedOrders.isEmpty()) {
            println("DEBUG: Нет завершенных заказов для обновления статистики")
            return
        }
        
        println("DEBUG: Получено ${completedOrders.size} заказов для обновления статистики")
        completedOrders.forEach { order ->
            println("\nDEBUG: Детали заказа для обновления статистики:")
            println("DEBUG: Заказ №${order.orderNumber}, внешний №${order.externalOrderNumber}")
            println("DEBUG: Статус: ${order.status}, вес: ${order.weight}")
            println("DEBUG: Фото: URI=${order.photoUri}, DateTime=${order.photoDateTime}")
        }
        
        val today = LocalDate.now()
        val todayString = today.toString()
        println("DEBUG: Обрабатываем заказы за дату: $todayString")
        
        // Получаем все заказы за текущий день
        val todayOrders = statisticsOrderDao.getOrdersForDate(todayString)
        println("\nDEBUG: Получено ${todayOrders.size} заказов за ${todayString}")
        todayOrders.forEach { order ->
            println("DEBUG: Заказ в статистике: №${order.orderNumber}, фото: URI=${order.photoUri}, DateTime=${order.photoDateTime}")
        }
        
        // Создаем новую статистику
        val totalWeight = todayOrders.sumOf { it.weight }
        val newStatistics = DailyStatistics(
            date = today,
            completedOrders = todayOrders.size,
            totalWeight = totalWeight,
            totalDistance = todayOrders.size * 5.0
        )
        
        // Обновляем статистику
        val existingStats = getStatistics()
        if (existingStats.dailyStats.any { it.date == today }) {
            println("DEBUG: Обновляем существующую статистику за ${todayString}")
            val updatedStats = existingStats.copy(
                dailyStats = existingStats.dailyStats.map {
                    if (it.date == today) newStatistics else it
                }
            )
            saveStatistics(updatedStats)
        } else {
            println("DEBUG: Создаем новую статистику за ${todayString}")
            val updatedStats = existingStats.copy(
                dailyStats = existingStats.dailyStats + newStatistics
            )
            saveStatistics(updatedStats)
        }
        
        println("=== updateStatisticsFromCompletedOrders: Статистика обновлена ===\n")
    }
    
    /**
     * Сохранить выполненный заказ в таблицу статистики
     */
    suspend fun saveOrderToStatistics(order: Order) {
        println("\n=== saveOrderToStatistics: Начало сохранения заказа в статистику ===")
        println("Заказ №${order.orderNumber}, внешний №${order.externalOrderNumber}, статус: ${order.status}, isCompleted: ${order.isCompleted}")
        
        if (order.status != OrderStatus.COMPLETED) {
            println("Заказ не выполнен, пропускаем сохранение в статистику")
            return
        }
        
        val completionDate = LocalDateTime.now()
        val dateString = completionDate.toLocalDate().toString()
        
        // Проверяем только по внешнему номеру заказа и дате выполнения
        val existingOrder = statisticsOrderDao.findByExternalOrderNumber(order.externalOrderNumber)
        
        // Проверяем, что существующий заказ выполнен в тот же день
        val isExistingOrderFromSameDay = existingOrder?.let { 
            it.completionDate.toLocalDate().toString() == dateString 
        } ?: false
        
        if (existingOrder != null && isExistingOrderFromSameDay) {
            println("Заказ с внешним номером ${order.externalOrderNumber} уже существует в статистике за ${dateString}, обновляем данные")
            // Обновляем существующий заказ
            val updatedOrder = existingOrder.copy(
                orderNumber = order.orderNumber, // Обновляем порядковый номер
                externalOrderNumber = order.externalOrderNumber,
                deliveryAddress = order.deliveryAddress,
                clientName = order.clientName,
                clientPhone = order.clientPhone,
                deliveryTimeStart = order.deliveryTimeStart,
                deliveryTimeEnd = order.deliveryTimeEnd,
                weight = order.weight,
                orderAmount = order.orderAmount,
                completionDate = completionDate,
                photoUri = order.photoUri, // Сохраняем информацию о фото
                photoDateTime = order.photoDateTime // Сохраняем дату и время фото
            )
            statisticsOrderDao.updateOrder(updatedOrder)
            println("Заказ обновлен в статистике")
            return
        }
        
        println("Создаем запись в статистике для заказа №${order.orderNumber} (внешний №${order.externalOrderNumber}) за ${dateString}")
        
        // Создаем запись в статистике
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
            completionDate = completionDate,
            photoUri = order.photoUri, // Сохраняем информацию о фото
            photoDateTime = order.photoDateTime // Сохраняем дату и время фото
        )
        
        println("Запись создана: ${statisticsOrder}")
        
        try {
            // Сохраняем в базу данных
            val id = statisticsOrderDao.insert(statisticsOrder)
            println("Запись сохранена в базу данных с id: $id")
            
            // Не обновляем статистику здесь, так как это будет сделано в updateStatisticsFromCompletedOrders
            println("Статистика будет обновлена в updateStatisticsFromCompletedOrders")
        } catch (e: Exception) {
            println("Ошибка при сохранении в статистику: ${e.message}")
            e.printStackTrace()
        }
        
        println("=== saveOrderToStatistics: Завершено ===\n")
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
            if (orders.isNotEmpty()) {
                println("Первый заказ: №${orders[0].orderNumber}, дата: ${orders[0].completionDate}")
            }
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
            status = OrderStatus.COMPLETED,
            photoUri = statisticsOrder.photoUri,  // Копируем URI фото
            photoDateTime = statisticsOrder.photoDateTime  // Копируем дату и время фото
        )
    }
    
    /**
     * Получить и преобразовать заказы для отображения в UI
     */
    suspend fun getDisplayOrdersForDate(date: LocalDate): List<Order> {
        println("\n=== getDisplayOrdersForDate: Начало получения заказов для отображения ===")
        println("Запрашиваем заказы за дату: $date")
        
        try {
            // Получаем заказы из базы данных статистики
            println("Запрашиваем заказы из статистики за дату: $date")
            val statisticsOrders = statisticsOrderDao.getOrdersCompletedOnDate(date.toString())
            println("Получено ${statisticsOrders.size} заказов из статистики за дату $date")
            
            if (statisticsOrders.isNotEmpty()) {
                println("Первый заказ: №${statisticsOrders[0].orderNumber}, внешний №${statisticsOrders[0].externalOrderNumber}")
            }
            
            // Преобразуем StatisticsOrder в Order
            val orders = statisticsOrders.map { statisticsOrder ->
                convertToOrder(statisticsOrder)
            }
            
            println("Преобразовано ${orders.size} заказов для отображения")
            if (orders.isNotEmpty()) {
                println("Первый заказ после преобразования: №${orders[0].orderNumber}, статус: ${orders[0].status}")
            }
            
            println("=== getDisplayOrdersForDate: Завершено ===")
            return orders
        } catch (e: Exception) {
            println("Ошибка при получении заказов для отображения: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Получить статистику за конкретный день
     */
    private fun getDailyStatistics(date: LocalDate): DailyStatistics {
        val stats = getStatistics()
        return stats.getStatsForDay(date) ?: DailyStatistics(
            date = date,
            completedOrders = 0,
            totalDistance = 0.0,
            totalWeight = 0.0
        )
    }

    /**
     * Сохранить статистику за конкретный день
     */
    private fun saveDailyStatistics(date: LocalDate, stats: DailyStatistics) {
        val currentStats = getStatistics()
        val updatedStats = currentStats.updateDayStats(stats)
        saveStatistics(updatedStats)
    }

    fun getStatisticsForDate(date: LocalDate): Flow<DailyStatistics> = flow {
        try {
            println("Получение статистики за ${date.format(DateTimeFormatter.ISO_DATE)}")
            
            // Получаем заказы из базы данных
            val orders = statisticsOrderDao.getOrdersCompletedOnDate(date.toString())
            println("Найдено ${orders.size} заказов в базе данных")
            
            // Получаем статистику из SharedPreferences
            val stats = getDailyStatistics(date)
            println("Статистика из SharedPreferences: $stats")
            
            // Если есть заказы в базе, но нет в SharedPreferences или количество не совпадает,
            // обновляем статистику
            if (orders.isNotEmpty() && (stats.completedOrders == 0 || stats.completedOrders != orders.size)) {
                println("Обнаружено несоответствие между базой данных и SharedPreferences. Обновляем статистику...")
                val newStats = DailyStatistics(
                    date = date,
                    completedOrders = orders.size,
                    totalDistance = orders.size * 5.0, // примерный пробег
                    totalWeight = orders.sumOf { it.weight }
                )
                saveDailyStatistics(date, newStats)
                println("Статистика обновлена: $newStats")
                emit(newStats)
            } else {
                emit(stats)
            }
        } catch (e: Exception) {
            println("Ошибка при получении статистики: ${e.message}")
            e.printStackTrace()
            emit(DailyStatistics(
                date = date,
                completedOrders = 0,
                totalDistance = 0.0,
                totalWeight = 0.0
            ))
        }
    }

    /**
     * Проверяет, существует ли заказ в статистике по его внешнему номеру
     */
    suspend fun orderExistsInStatistics(externalOrderNumber: String): Boolean {
        println("StatisticsRepository.orderExistsInStatistics: Проверка заказа с номером: $externalOrderNumber")
        val order = statisticsOrderDao.findByExternalOrderNumber(externalOrderNumber)
        if (order != null) {
            println("StatisticsRepository.orderExistsInStatistics: Заказ найден в статистике: №${order.orderNumber}, внешний номер: ${order.externalOrderNumber}")
        } else {
            println("StatisticsRepository.orderExistsInStatistics: Заказ не найден в статистике")
        }
        return order != null
    }

    suspend fun clearStatistics() {
        println("\n=== clearStatistics: Начало очистки статистики ===")
        try {
            // Очищаем статистику в SharedPreferences
            val emptyStats = Statistics(emptyList())
            saveStatistics(emptyStats)
            
            // Очищаем все заказы из базы данных
            statisticsOrderDao.deleteAllOrders()
            
            println("Статистика успешно очищена")
        } catch (e: Exception) {
            println("Ошибка при очистке статистики: ${e.message}")
            e.printStackTrace()
        }
        println("=== clearStatistics: Завершено ===\n")
    }

    /**
     * Очистить статистику за конкретную дату
     */
    suspend fun clearStatisticsForDate(date: LocalDate) {
        println("\n=== clearStatisticsForDate: Начало очистки статистики за ${date} ===")
        try {
            // Получаем текущую статистику
            val currentStats = getStatistics()
            
            // Удаляем статистику за указанную дату
            val updatedStats = currentStats.copy(
                dailyStats = currentStats.dailyStats.filter { 
                    !(it.date.year == date.year && 
                      it.date.month == date.month && 
                      it.date.dayOfMonth == date.dayOfMonth) 
                }
            )
            
            // Сохраняем обновленную статистику
            saveStatistics(updatedStats)
            
            // Удаляем заказы за указанную дату из базы данных
            statisticsOrderDao.deleteOrdersForDate(date.toString())
            
            println("Статистика за ${date} успешно очищена")
        } catch (e: Exception) {
            println("Ошибка при очистке статистики за ${date}: ${e.message}")
            e.printStackTrace()
        }
        println("=== clearStatisticsForDate: Завершено ===\n")
    }
} 