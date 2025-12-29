// ВРЕМЕННО УДАЛЕНО ДЛЯ СБРОСА ROOM
// package com.example.dmapp.data

package com.example.dmapp.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime
import com.example.dmapp.R

@Parcelize
@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderNumber: Int, // Порядковый номер в приложении
    val externalOrderNumber: String, // Номер заказа из импорта (например, "6157110364")
    val pickupLocation: String, // Место забора
    val sector: String, // Сектор
    val place: String, // Место
    val clientPhone: String, // Телефон клиента
    val clientName: String, // ФИО клиента
    val deliveryAddress: String, // Адрес доставки
    val clientComment: String?, // Комментарий клиента
    val deliveryTimeStart: LocalDateTime, // Начало интервала доставки
    val deliveryTimeEnd: LocalDateTime, // Конец интервала доставки
    val weight: Double, // Вес заказа
    val volume: Double, // Объем заказа
    val isPrepaid: Boolean, // Предоплачен ли заказ
    val courierName: String, // ФИО курьера
    val courierPhone: String, // Телефон курьера
    val orderAmount: Double, // Сумма заказа
    val status: OrderStatus = OrderStatus.NEW,
    val importedAt: LocalDateTime = LocalDateTime.now(),
    var notes: String = "", // Заметки курьера
    val isCompleted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    var photoUri: String? = null, // Путь к фото заказа
    var photoDateTime: LocalDateTime? = null // Дата и время создания фото
) : Parcelable

enum class OrderStatus {
    NEW,
    IN_PROGRESS,
    COMPLETED,
    REALIZATION,    // Реализация
    READY,          // Готов к выдаче
    SHIPPED,        // Отгружен
    CANCELLED;      // Отменен

    fun getDisplayName(): String {
        return when (this) {
            NEW -> "Новый"
            IN_PROGRESS -> "В работе"
            COMPLETED -> "Завершен"
            REALIZATION -> "Реализация"
            READY -> "Готов к выдаче"
            SHIPPED -> "Отгружен"
            CANCELLED -> "Отменен"
        }
    }

    fun getIcon(): Int {
        return when (this) {
            NEW -> R.drawable.ic_status_new
            IN_PROGRESS -> R.drawable.ic_status_in_progress
            COMPLETED -> R.drawable.ic_status_completed
            REALIZATION -> R.drawable.ic_status_realization
            READY -> R.drawable.ic_status_ready
            SHIPPED -> R.drawable.ic_status_shipped
            CANCELLED -> R.drawable.ic_status_cancelled
        }
    }

    fun getBackgroundColor(): Int {
        return when (this) {
            NEW -> R.color.status_new
            IN_PROGRESS -> R.color.status_in_progress
            COMPLETED -> R.color.status_completed
            REALIZATION -> R.color.status_realization
            READY -> R.color.status_ready
            SHIPPED -> R.color.status_shipped
            CANCELLED -> R.color.status_cancelled
        }
    }
} 