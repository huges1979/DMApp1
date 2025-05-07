package com.example.dmapp.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dmapp.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class OrderViewModel(
    private val repository: OrderRepository,
    private val context: Context
) : ViewModel() {
    val activeOrders = repository.activeOrders
    val completedOrders = repository.completedOrders
    val activeOrdersCount = repository.activeOrdersCount
    val completedOrdersCount = repository.completedOrdersCount
    
    private val statisticsRepository = StatisticsRepository(context)
    
    private val _statistics = MutableStateFlow(statisticsRepository.getStatistics())
    val statistics: StateFlow<Statistics> = _statistics.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _deleteResult = MutableStateFlow<Int?>(null)
    val deleteResult: StateFlow<Int?> = _deleteResult.asStateFlow()

    private val _completedOrdersForDate = MutableStateFlow<List<Order>>(emptyList())
    val completedOrdersForDate: StateFlow<List<Order>> = _completedOrdersForDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    fun importOrders(text: String) {
        println("\n=== OrderViewModel.importOrders: Начало импорта заказов ===")
        viewModelScope.launch {
            try {
                println("Вызываем repository.importOrders")
                val result = repository.importOrders(text)
                println("Импорт завершен: ${result.newOrders} новых заказов, ${result.duplicates} дубликатов, ${result.errors} ошибок")
                
                // Проверяем, что заказы действительно добавились
                val activeOrders = repository.activeOrders.first()
                println("Текущее количество активных заказов: ${activeOrders.size}")
                if (activeOrders.isNotEmpty()) {
                    println("Первый активный заказ: №${activeOrders[0].orderNumber}, статус: ${activeOrders[0].status}")
                }
                
                _importResult.value = result
                println("=== OrderViewModel.importOrders: Завершено ===\n")
            } catch (e: Exception) {
                println("Ошибка при импорте заказов: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun updateOrderStatus(order: Order, newStatus: OrderStatus) {
        println("\n=== updateOrderStatus: Начало обновления статуса заказа ===")
        println("Заказ №${order.orderNumber}, текущий статус: ${order.status}, новый статус: $newStatus")
        
        viewModelScope.launch {
            try {
                // Обновляем статус в базе данных
                repository.updateOrderStatus(order, newStatus)
                println("Статус заказа обновлен в базе данных")
                
                // Получаем обновленный заказ
                val updatedOrder = repository.getOrderById(order.id)
                println("Получен обновленный заказ: ${updatedOrder?.orderNumber}, статус: ${updatedOrder?.status}")
                
                // Обновляем UI
                _completedOrdersForDate.value = _completedOrdersForDate.value.map { 
                    if (it.id == order.id) updatedOrder ?: it else it 
                }
                println("UI обновлен")
            } catch (e: Exception) {
                println("Ошибка при обновлении статуса заказа: ${e.message}")
                e.printStackTrace()
            }
        }
        
        println("=== updateOrderStatus: Завершено ===\n")
    }

    fun clearCompletedOrders() {
        viewModelScope.launch {
            println("Очистка выполненных заказов")
            var deletedCount = 0
            
            try {
                // Получаем все выполненные заказы перед удалением
                val completedOrders = repository.getAllCompletedOrders()
                println("Получено ${completedOrders.size} выполненных заказов для сохранения в статистику")
                
                if (completedOrders.isEmpty()) {
                    println("Нет выполненных заказов для сохранения в статистику")
                    return@launch
                }
                
                // Выводим информацию о каждом заказе
                completedOrders.forEach { order ->
                    println("Заказ для сохранения: номер=${order.orderNumber}, внешний номер=${order.externalOrderNumber}, статус=${order.status}")
                }
                
                // Удаляем выполненные заказы
                deletedCount = repository.deleteCompletedOrders()
                println("Удалено $deletedCount выполненных заказов")
                
                // Обновляем статистику с удаленными заказами
                println("Начинаем сохранение заказов в статистику")
                statisticsRepository.updateStatisticsFromCompletedOrders(completedOrders)
                println("Все заказы обработаны, обновляем статистику")
                refreshStatistics()
            } catch (e: Exception) {
                println("Ошибка при очистке выполненных заказов: ${e.message}")
                e.printStackTrace()
            }
            
            // Обновляем UI
            _deleteResult.value = deletedCount
            println("Очистка выполненных заказов завершена")
        }
    }
    
    private fun updateStatisticsWithCompletedOrder(order: Order) {
        viewModelScope.launch {
            println("updateStatisticsWithCompletedOrder: Обновление статистики для заказа ${order.orderNumber}")
            statisticsRepository.updateStatisticsFromCompletedOrders(listOf(order))
            refreshStatistics()
        }
    }
    
    private fun updateStatisticsWithCompletedOrders(orders: List<Order>) {
        viewModelScope.launch {
            println("updateStatisticsWithCompletedOrders: Обновление статистики для ${orders.size} заказов")
            statisticsRepository.updateStatisticsFromCompletedOrders(orders)
            refreshStatistics()
        }
    }
    
    private fun refreshStatistics() {
        println("refreshStatistics: Обновление Flow статистики")
        _statistics.value = statisticsRepository.getStatistics()
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun clearDeleteResult() {
        _deleteResult.value = null
    }

    fun updateOrderNotes(orderId: Long, notes: String) {
        viewModelScope.launch {
            repository.updateOrderNotes(orderId, notes)
        }
    }

    fun updateOrderCoordinates(orderId: Long, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            repository.updateOrderCoordinates(orderId, latitude, longitude)
        }
    }

    /**
     * Восстановить статистику из существующих выполненных заказов
     * Используется в случаях, когда статистика была потеряна или некорректна
     */
    fun rebuildStatistics() {
        viewModelScope.launch {
            println("Начинаем восстановление статистики из выполненных заказов")
            
            // Получаем все выполненные заказы
            val completedOrders = repository.getAllCompletedOrders()
            
            if (completedOrders.isEmpty()) {
                println("Выполненных заказов не найдено, статистика не восстановлена")
                return@launch
            }
            
            println("Найдено ${completedOrders.size} выполненных заказов для восстановления статистики")
            
            // Сначала очищаем существующую статистику
            val emptyStats = Statistics(emptyList())
            statisticsRepository.saveStatistics(emptyStats)
            
            // Затем обновляем с новыми данными
            statisticsRepository.updateStatisticsFromCompletedOrders(completedOrders)
            
            // Обновляем UI
            refreshStatistics()
            
            println("Восстановление статистики завершено")
        }
    }
    
    /**
     * Очистить всю статистику
     */
    fun clearStatistics() {
        viewModelScope.launch {
            println("Очистка всей статистики")
            
            // Очищаем статистику
            statisticsRepository.clearStatistics()
            
            // Обновляем UI
            refreshStatistics()
            
            println("Статистика успешно очищена")
        }
    }

    /**
     * Очистить статистику за конкретную дату
     */
    fun clearStatisticsForDate(date: LocalDate) {
        viewModelScope.launch {
            println("Очистка статистики за ${date}")
            
            // Очищаем статистику за дату
            statisticsRepository.clearStatisticsForDate(date)
            
            // Обновляем UI
            refreshStatistics()
            
            println("Статистика за ${date} успешно очищена")
        }
    }

    /**
     * Принудительно обновить статистику для указанного заказа
     * Используется, когда нужно гарантировать обновление статистики
     */
    fun forceUpdateStatisticsForOrder(order: Order) {
        viewModelScope.launch {
            println("forceUpdateStatisticsForOrder: Принудительное обновление статистики для заказа ${order.orderNumber}")
            statisticsRepository.updateStatisticsFromCompletedOrders(listOf(order))
            refreshStatistics()
        }
    }

    /**
     * Получить выполненные заказы за конкретную дату
     */
    fun getCompletedOrdersForDate(date: LocalDate) {
        viewModelScope.launch {
            try {
                println("getCompletedOrdersForDate: Загрузка заказов за дату ${date}")
                _isLoading.value = true
                _loadError.value = null
                
                // Получаем заказы из статистики
                val orders = statisticsRepository.getDisplayOrdersForDate(date)
                println("getCompletedOrdersForDate: Получено ${orders.size} заказов")
                
                // Обновляем UI
                _completedOrdersForDate.value = orders
                
                // Проверяем соответствие с общей статистикой
                val stats = statistics.value.getStatsForDay(date)
                if (stats != null && stats.completedOrders != orders.size) {
                    println("getCompletedOrdersForDate: Обнаружено несоответствие между статистикой (${stats.completedOrders}) и количеством заказов (${orders.size})")
                    // Обновляем статистику
                    statisticsRepository.updateStatisticsFromCompletedOrders(orders)
                    refreshStatistics()
                }
            } catch (e: Exception) {
                println("getCompletedOrdersForDate: Ошибка при загрузке заказов: ${e.message}")
                e.printStackTrace()
                _loadError.value = "Ошибка при загрузке заказов: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun migrateCompletedOrdersToStatistics() {
        viewModelScope.launch {
            try {
                println("Начинаем перенос всех выполненных заказов в таблицу статистики")
                val allCompletedOrders = repository.getAllCompletedOrders()
                println("Найдено ${allCompletedOrders.size} выполненных заказов для переноса в статистику")
                
                var successCount = 0
                for (order in allCompletedOrders) {
                    try {
                        statisticsRepository.saveOrderToStatistics(order)
                        successCount++
                    } catch (e: Exception) {
                        println("Ошибка при переносе заказа ${order.orderNumber} в статистику: ${e.message}")
                    }
                }
                
                println("Успешно перенесено $successCount из ${allCompletedOrders.size} заказов в статистику")
                refreshStatistics()
            } catch (e: Exception) {
                println("Ошибка при переносе заказов в статистику: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun updateOrderPhoto(orderId: Long, photoUri: String?) {
        viewModelScope.launch {
            println("\n=== updateOrderPhoto: Начало обновления фото заказа ===")
            println("DEBUG: Заказ ID: $orderId, новый URI фото: $photoUri")
            
            try {
                // Получаем текущий заказ
                val currentOrder = repository.getOrderById(orderId)
                if (currentOrder == null) {
                    println("ERROR: Заказ с ID $orderId не найден")
                    return@launch
                }
                println("DEBUG: Текущий заказ: №${currentOrder.orderNumber}, внешний №${currentOrder.externalOrderNumber}")
                println("DEBUG: Текущее фото: URI=${currentOrder.photoUri}, DateTime=${currentOrder.photoDateTime}")
                
                // Обновляем фото в базе данных
                println("DEBUG: Обновляем фото в базе данных...")
                repository.updateOrderPhoto(orderId, photoUri)
                println("DEBUG: Фото успешно обновлено в базе данных")
                
                // Получаем обновленный заказ для проверки
                val updatedOrder = repository.getOrderById(orderId)
                if (updatedOrder == null) {
                    println("ERROR: Не удалось получить обновленный заказ")
                    return@launch
                }
                println("DEBUG: Заказ после обновления: №${updatedOrder.orderNumber}")
                println("DEBUG: Фото после обновления: URI=${updatedOrder.photoUri}, DateTime=${updatedOrder.photoDateTime}")
                
                // Обновляем потоки заказов, чтобы UI увидел изменения
                println("DEBUG: Обновляем UI...")
                val currentOrders = _completedOrdersForDate.value
                println("DEBUG: Текущее количество заказов в UI: ${currentOrders.size}")
                
                _completedOrdersForDate.value = currentOrders.map { order ->
                    if (order.id == orderId) {
                        println("DEBUG: Обновляем фото в UI для заказа №${order.orderNumber}")
                        order.copy(photoUri = photoUri)
                    } else {
                        order
                    }
                }
                
                println("DEBUG: UI успешно обновлен с новым фото")
            } catch (e: Exception) {
                println("ERROR: Ошибка при обновлении фото: ${e.message}")
                e.printStackTrace()
            }
            
            println("=== updateOrderPhoto: Завершено ===\n")
        }
    }

    fun updateOrderPhotoDateTime(orderId: Long, photoDateTime: LocalDateTime?) {
        viewModelScope.launch {
            println("\n=== updateOrderPhotoDateTime: Начало обновления даты фото ===")
            println("DEBUG: Заказ ID: $orderId, новая дата фото: $photoDateTime")
            
            try {
                // Получаем текущий заказ
                val currentOrder = repository.getOrderById(orderId)
                if (currentOrder == null) {
                    println("ERROR: Заказ с ID $orderId не найден")
                    return@launch
                }
                println("DEBUG: Текущий заказ: №${currentOrder.orderNumber}, внешний №${currentOrder.externalOrderNumber}")
                println("DEBUG: Текущее фото: URI=${currentOrder.photoUri}, DateTime=${currentOrder.photoDateTime}")
                
                // Обновляем дату фото в базе данных
                println("DEBUG: Обновляем дату фото в базе данных...")
                repository.updateOrderPhotoDateTime(orderId, photoDateTime)
                println("DEBUG: Дата фото успешно обновлена в базе данных")
                
                // Получаем обновленный заказ для проверки
                val updatedOrder = repository.getOrderById(orderId)
                if (updatedOrder == null) {
                    println("ERROR: Не удалось получить обновленный заказ")
                    return@launch
                }
                println("DEBUG: Заказ после обновления: №${updatedOrder.orderNumber}")
                println("DEBUG: Фото после обновления: URI=${updatedOrder.photoUri}, DateTime=${updatedOrder.photoDateTime}")
                
                // Обновляем потоки заказов, чтобы UI увидел изменения
                println("DEBUG: Обновляем UI...")
                val currentOrders = _completedOrdersForDate.value
                println("DEBUG: Текущее количество заказов в UI: ${currentOrders.size}")
                
                _completedOrdersForDate.value = currentOrders.map { order ->
                    if (order.id == orderId) {
                        println("DEBUG: Обновляем дату фото в UI для заказа №${order.orderNumber}")
                        order.copy(photoDateTime = photoDateTime)
                    } else {
                        order
                    }
                }
                
                println("DEBUG: UI успешно обновлен с новой датой фото")
            } catch (e: Exception) {
                println("ERROR: Ошибка при обновлении даты фото: ${e.message}")
                e.printStackTrace()
            }
            
            println("=== updateOrderPhotoDateTime: Завершено ===\n")
        }
    }

    // Методы навигации для работы с фотографиями
    fun navigateToPhotoCapture(order: Order) {
        _navigationEvent.value = NavigationEvent.PhotoCapture(order)
    }

    fun navigateToPhotoViewer(photoUri: Uri) {
        _navigationEvent.value = NavigationEvent.PhotoViewer(photoUri)
    }

    sealed class NavigationEvent {
        data class PhotoCapture(val order: Order) : NavigationEvent()
        data class PhotoViewer(val photoUri: Uri) : NavigationEvent()
    }

    class Factory(
        private val repository: OrderRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OrderViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return OrderViewModel(repository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 