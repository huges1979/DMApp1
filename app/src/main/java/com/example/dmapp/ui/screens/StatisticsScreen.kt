package com.example.dmapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dmapp.data.DailyStatistics
import com.example.dmapp.data.Order
import com.example.dmapp.data.Statistics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Calendar
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    statistics: Statistics,
    onNavigateBack: () -> Unit,
    onRebuildStatistics: () -> Unit = {},
    onClearStatistics: () -> Unit = {}
) {
    // Состояние для выбранной даты
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    
    // Состояние для диалога подтверждения очистки статистики
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    
    // Форматеры дат
    val fullDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
    val shortDateFormatter = DateTimeFormatter.ofPattern("dd.MM")
    
    // Получение статистики за выбранную дату
    val dateStats = statistics.getStatsForDay(selectedDate)
    
    // Получение последних дат из статистики для чипсов
    val recentDates = remember(statistics) {
        statistics.dailyStats
            .map { it.date }
            .distinct()
            .sortedByDescending { it }
            .take(5)
    }
    
    // Контекст для DatePickerDialog
    val context = LocalContext.current
    
    // Календарь для установки начальной даты в DatePickerDialog
    val calendar = Calendar.getInstance()
    calendar.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
    
    // Создаем DatePickerDialog
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    // При изменении selectedDate обновляем datePickerDialog
    LaunchedEffect(selectedDate) {
        calendar.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
        datePickerDialog.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Кнопка для очистки всей статистики
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Очистить всю статистику"
                        )
                    }
                    // Кнопка для восстановления статистики
                    IconButton(onClick = onRebuildStatistics) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить статистику"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Секция выбора даты
            item {
                Text(
                    text = "Выберите дату",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Поле выбора даты
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { datePickerDialog.show() },
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedDate.format(fullDateFormatter),
                                fontSize = 18.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Кнопка "Сегодня"
                    Button(
                        onClick = { selectedDate = LocalDate.now() },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Сегодня")
                    }
                }
            }
            
            // Быстрый выбор дат (чипсы)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentDates) { date ->
                        val isSelected = date == selectedDate
                        DateChip(
                            date = date,
                            shortDateFormatter = shortDateFormatter,
                            isSelected = isSelected,
                            onClick = { selectedDate = date }
                        )
                    }
                }
            }
            
            // Карточки со статистикой
            item {
                if (dateStats != null) {
                    // Карточка с заказами
                    StatCard(
                        title = "Выполненные заказы",
                        value = dateStats.completedOrders.toString(),
                        backgroundColor = Color(0xFFECE5FF)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Карточка с весом
                    StatCard(
                        title = "Общий вес (кг)",
                        value = String.format("%.1f", dateStats.totalWeight),
                        backgroundColor = Color(0xFFECE5FF)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Карточка с пробегом
                    StatCard(
                        title = "Приблизительный пробег (км)",
                        value = String.format("%.1f", dateStats.totalDistance),
                        backgroundColor = Color(0xFFFFE5E5)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет статистики за выбранную дату",
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Секция списка заказов
            item {
                Text(
                    text = "Список заказов за ${selectedDate.format(fullDateFormatter)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                if (dateStats != null && dateStats.completedOrders > 0) {
                    // В будущем здесь может быть список заказов за день
                    // Сейчас у нас нет доступа к списку выполненных заказов за конкретную дату
                    Text(
                        text = "Данные по отдельным заказам недоступны",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Нет выполненных заказов\nза выбранную дату",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Диалог подтверждения очистки статистики
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Очистка статистики") },
            text = { Text("Вы действительно хотите очистить всю статистику? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearStatistics()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun DateChip(
    date: LocalDate,
    shortDateFormatter: DateTimeFormatter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(24.dp)
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = date.format(shortDateFormatter),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    backgroundColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color.DarkGray
            )
            
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
} 