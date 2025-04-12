package com.example.dmapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dmapp.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    fun importOrders(text: String) {
        viewModelScope.launch {
            val result = repository.importOrders(text)
            _importResult.value = result
        }
    }

    fun updateOrderStatus(order: Order, newStatus: OrderStatus) {
        viewModelScope.launch {
            println("Обновление статуса заказа ${order.orderNumber} с ${order.status} на $newStatus")
            
            // Создаем копию заказа с новым статусом
            val updatedOrder = order.copy(status = newStatus)
            
            // Сохраняем обновленный заказ в базе данных
            repository.updateOrderStatus(updatedOrder, newStatus)
            
            // Если заказ переведен в статус COMPLETED, обновляем статистику
            if (newStatus == OrderStatus.COMPLETED) {
                println("Заказ ${order.orderNumber} помечен как выполненный, обновляем статистику (один раз)")
                updateStatisticsWithCompletedOrder(updatedOrder)
            }
        }
    }

    fun clearCompletedOrders() {
        viewModelScope.launch {
            // Получаем список выполненных заказов (только для информации)
            val ordersToProcess = repository.getAllCompletedOrders()
            println("Очистка выполненных заказов. Найдено ${ordersToProcess.size} заказов")
            
            // Удаляем заказы из базы данных
            val count = repository.deleteCompletedOrders()
            _deleteResult.value = count
            println("Удалено $count выполненных заказов")
            
            // Удаляем обновление статистики, так как эти заказы уже учтены в статистике при их закрытии
            // Если удалить эту часть, задвоение статистики исчезнет
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
            
            // Создаем пустую статистику
            val emptyStats = Statistics(emptyList())
            
            // Сохраняем пустую статистику
            statisticsRepository.saveStatistics(emptyStats)
            
            // Обновляем UI
            refreshStatistics()
            
            println("Статистика успешно очищена")
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