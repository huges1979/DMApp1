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
import com.example.dmapp.ui.OrderViewModel
import com.example.dmapp.ui.components.OrderItem
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
    onClearStatistics: () -> Unit = {},
    viewModel: OrderViewModel
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val completedOrdersForDate by viewModel.completedOrdersForDate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    
    // Форматтеры для дат
    val shortDateFormatter = DateTimeFormatter.ofPattern("dd.MM")
    val fullDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    
    // Получаем статистику за выбранную дату
    val dateStats = statistics.getStatsForDay(selectedDate)
    
    // Получаем список последних 7 дней для отображения
    val recentDates = remember {
        List(7) { i ->
            LocalDate.now().minusDays(i.toLong())
        }
    }
    
    // Загружаем заказы при изменении выбранной даты
    LaunchedEffect(selectedDate) {
        viewModel.getCompletedOrdersForDate(selectedDate)
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
                    IconButton(onClick = onRebuildStatistics) {
                        Icon(Icons.Default.Refresh, contentDescription = "Перестроить статистику")
                    }
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить статистику")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Выбор даты
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
                    // Контейнер для всех карточек с отступом
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Карточка с заказами
                        StatCard(
                            title = "Выполненные заказы",
                            value = dateStats.completedOrders.toString(),
                            backgroundColor = Color(0xFFECE5FF),
                            onClick = { viewModel.getCompletedOrdersForDate(selectedDate) }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Карточка с весом
                        StatCard(
                            title = "Общий вес (кг)",
                            value = String.format("%.1f", dateStats.totalWeight),
                            backgroundColor = Color(0xFFECE5FF)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Карточка с пробегом
                        StatCard(
                            title = "Приблизительный пробег (км)",
                            value = String.format("%.1f", dateStats.totalDistance),
                            backgroundColor = Color(0xFFFFE5E5)
                        )
                    }
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
                
                println("Статистика за день: ${dateStats?.completedOrders}, Заказов в списке: ${completedOrdersForDate.size}")
                
                if (dateStats != null && dateStats.completedOrders > 0) {
                    if (completedOrdersForDate.isNotEmpty()) {
                        println("Отображаем ${completedOrdersForDate.size} заказов")
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            completedOrdersForDate.forEach { order ->
                                println("Отображаем заказ №${order.orderNumber}")
                                OrderItem(
                                    order = order,
                                    onOrderClick = { },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    } else if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Загрузка заказов...",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (loadError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { viewModel.getCompletedOrdersForDate(selectedDate) },
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Ошибка загрузки данных. Нажмите, чтобы повторить.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            text = "Нет данных о заказах за выбранную дату",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    Card(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = date.format(shortDateFormatter),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    backgroundColor: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)  // Фиксированная высота для всех карточек
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 8.dp),
                    color = Color.Transparent
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontSize = 28.sp
                    )
                }
            }
        }
    }
} 