package com.example.dmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.dmapp.data.Order
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.app.DatePickerDialog

@Composable
fun StatisticsDialogs(
    showSalaryCalculator: Boolean,
    onDismissSalaryCalculator: () -> Unit,
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateSelected: (LocalDate) -> Unit,
    onEndDateSelected: (LocalDate) -> Unit,
    calculateSalaryForPeriod: (LocalDate, LocalDate) -> Double,
    showClearConfirmDialog: Boolean,
    onDismissClearConfirm: () -> Unit,
    onClearStatistics: () -> Unit,
    showClearDateConfirmDialog: Boolean,
    onDismissClearDateConfirm: () -> Unit,
    selectedDate: LocalDate,
    onClearDateStatistics: () -> Unit,
    orderToDelete: Order?,
    onDismissDeleteOrder: () -> Unit,
    onDeleteOrder: (Order) -> Unit,
    fullDateFormatter: DateTimeFormatter
) {
    val context = LocalContext.current

    // Диалог расчета заработной платы
    if (showSalaryCalculator) {
        AlertDialog(
            onDismissRequest = onDismissSalaryCalculator,
            title = { Text("Расчет заработной платы") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Выберите период для расчета заработной платы")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Выбор начальной даты
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    onStartDateSelected(LocalDate.of(year, month + 1, day))
                                },
                                startDate.year,
                                startDate.monthValue - 1,
                                startDate.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Начальная дата: ${startDate.format(fullDateFormatter)}")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Выбор конечной даты
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    onEndDateSelected(LocalDate.of(year, month + 1, day))
                                },
                                endDate.year,
                                endDate.monthValue - 1,
                                endDate.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Конечная дата: ${endDate.format(fullDateFormatter)}")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Отображение результата
                    val totalSalary = calculateSalaryForPeriod(startDate, endDate)
                    Text(
                        text = "Заработная плата за период: ${String.format("%.2f", totalSalary)} ₽",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissSalaryCalculator) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Диалог подтверждения очистки всей статистики
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearConfirm,
            title = { Text("Очистить статистику") },
            text = { Text("Вы уверены, что хотите очистить всю статистику? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearStatistics()
                        onDismissClearConfirm()
                    }
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearConfirm) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог подтверждения очистки статистики за дату
    if (showClearDateConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearDateConfirm,
            title = { Text("Очистить статистику за дату") },
            text = { Text("Вы уверены, что хотите очистить статистику за ${selectedDate.format(fullDateFormatter)}? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearDateStatistics()
                        onDismissClearDateConfirm()
                    }
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearDateConfirm) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог подтверждения удаления заказа
    if (orderToDelete != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteOrder,
            title = { Text("Удалить заказ") },
            text = { Text("Вы уверены, что хотите удалить заказ №${orderToDelete.id}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteOrder(orderToDelete)
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteOrder) {
                    Text("Отмена")
                }
            }
        )
    }
} 