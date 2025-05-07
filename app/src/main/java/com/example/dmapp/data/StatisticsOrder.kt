package com.example.dmapp.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
@Entity(tableName = "statistics_orders")
data class StatisticsOrder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderNumber: Int,                // Порядковый номер в приложении
    val externalOrderNumber: String,     // Номер заказа из импорта
    val deliveryAddress: String,         // Адрес доставки
    val clientName: String,              // ФИО клиента
    val clientPhone: String,             // Телефон клиента
    val deliveryTimeStart: LocalDateTime, // Начало интервала доставки
    val deliveryTimeEnd: LocalDateTime,  // Конец интервала доставки
    val weight: Double,                  // Вес заказа
    val orderAmount: Double,             // Сумма заказа
    val completionDate: LocalDateTime,   // Дата выполнения заказа
    var photoUri: String? = null,        // Путь к фото заказа
    var photoDateTime: LocalDateTime? = null // Дата и время создания фото
) : Parcelable 