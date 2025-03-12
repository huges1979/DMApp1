package com.example.dmapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dmapp.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OrderViewModel(private val repository: OrderRepository) : ViewModel() {
    val activeOrders = repository.activeOrders
    val completedOrders = repository.completedOrders
    val activeOrdersCount = repository.activeOrdersCount
    val completedOrdersCount = repository.completedOrdersCount

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
            repository.updateOrderStatus(order, newStatus)
        }
    }

    fun clearCompletedOrders() {
        viewModelScope.launch {
            val count = repository.deleteCompletedOrders()
            _deleteResult.value = count
        }
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

    class Factory(private val repository: OrderRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OrderViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return OrderViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 