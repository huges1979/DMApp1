package com.example.dmapp.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

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
    COMPLETED
} 