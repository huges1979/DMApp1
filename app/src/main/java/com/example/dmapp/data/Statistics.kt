package com.example.dmapp.data

import java.time.LocalDate

/**
 * Класс данных для хранения ежедневной статистики по заказам
 */
data class DailyStatistics(
    val date: LocalDate,
    val completedOrders: Int,
    val totalDistance: Double, // в километрах
    val totalWeight: Double // в килограммах
)

/**
 * Класс для хранения всей статистики приложения
 */
data class Statistics(
    val dailyStats: List<DailyStatistics> = emptyList()
) {
    // Получение статистики за конкретный день
    fun getStatsForDay(date: LocalDate): DailyStatistics? {
        val result = dailyStats.find { 
            it.date.year == date.year && 
            it.date.month == date.month && 
            it.date.dayOfMonth == date.dayOfMonth 
        }
        
        println("getStatsForDay: запрошена статистика за ${date}, ${if (result != null) "найдена" else "не найдена"}")
        
        return result
    }

    // Получение статистики за сегодня
    fun getTodayStats(): DailyStatistics? {
        val today = LocalDate.now()
        println("getTodayStats: запрашиваем статистику за сегодня (${today})")
        return getStatsForDay(today)
    }

    // Обновление статистики за день путем добавления новых данных
    fun updateDayStats(newStats: DailyStatistics): Statistics {
        val date = newStats.date
        println("updateDayStats: обновление статистики за ${date}")
        
        val existingStats = getStatsForDay(date)
        
        // Если статистика за этот день уже есть, объединяем
        val updatedStats = if (existingStats != null) {
            println("Найдена существующая статистика: ${existingStats.completedOrders} заказов, ${existingStats.totalWeight} кг, ${existingStats.totalDistance} км")
            
            existingStats.copy(
                completedOrders = existingStats.completedOrders + newStats.completedOrders,
                totalDistance = existingStats.totalDistance + newStats.totalDistance,
                totalWeight = existingStats.totalWeight + newStats.totalWeight
            ).also {
                println("Объединенная статистика: ${it.completedOrders} заказов, ${it.totalWeight} кг, ${it.totalDistance} км")
            }
        } else {
            // Если нет, просто используем новую статистику
            println("Существующей статистики не найдено, используем новую")
            newStats
        }
        
        // Удаляем старую статистику за этот день, сравнивая по году, месяцу и дню
        val filteredStats = dailyStats.filter { 
            !(it.date.year == date.year && 
              it.date.month == date.month && 
              it.date.dayOfMonth == date.dayOfMonth) 
        }
        
        println("После фильтрации: ${filteredStats.size} записей")
        
        // Добавляем обновленную статистику
        val result = this.copy(dailyStats = filteredStats + updatedStats)
        println("Итоговое количество записей: ${result.dailyStats.size}")
        
        return result
    }
    
    // Устаревший метод, для совместимости со старым кодом
    @Deprecated("Use updateDayStats instead", ReplaceWith("updateDayStats(stats)"))
    fun addDailyStats(stats: DailyStatistics): Statistics {
        return updateDayStats(stats)
    }
} 