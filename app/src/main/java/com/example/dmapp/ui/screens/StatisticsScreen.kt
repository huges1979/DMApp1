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
import androidx.compose.material.icons.filled.AttachMoney
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
import com.example.dmapp.data.OrderRepository
import com.example.dmapp.data.OrderStatus
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
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    statistics: Statistics,
    onNavigateBack: () -> Unit,
    onRebuildStatistics: () -> Unit = {},
    onClearStatistics: () -> Unit = {},
    onOrderClick: (Order) -> Unit,
    viewModel: OrderViewModel,
    navController: NavController,
    orderRepository: OrderRepository
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showClearDateConfirmDialog by remember { mutableStateOf(false) }
    var showSalaryCalculator by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    val completedOrdersForDate by viewModel.completedOrdersForDate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val scope = rememberCoroutineScope()
    var orderToDelete by remember { mutableStateOf<Order?>(null) }
    
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
    
    // Функция для расчета заработной платы за период
    fun calculateSalaryForPeriod(start: LocalDate, end: LocalDate): Double {
        var totalSalary = 0.0
        var currentDate = start
        while (!currentDate.isAfter(end)) {
            val dayStats = statistics.getStatsForDay(currentDate)
            if (dayStats != null) {
                totalSalary += dayStats.completedOrders * 221.54 // Ставка за заказ
            }
            currentDate = currentDate.plusDays(1)
        }
        return totalSalary
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
                    IconButton(onClick = { showSalaryCalculator = true }) {
                        Icon(Icons.Default.AttachMoney, contentDescription = "Расчет заработной платы")
                    }
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                // Выбор даты
                item {
                    // Календарная иконка для выбора даты
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { showCalendar = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Открыть календарь")
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
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Карточка с заработком
                            StatCard(
                                title = "Заработано",
                                value = String.format("%.2f ₽", dateStats.completedOrders * 221.54),
                                backgroundColor = Color(0xFFE5FFE5) // Светло-зеленый цвет
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Список заказов за ${selectedDate.format(fullDateFormatter)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (dateStats != null && dateStats.completedOrders > 0) {
                            IconButton(
                                onClick = { showClearDateConfirmDialog = true }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Очистить статистику за дату",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    
                    if (dateStats != null && dateStats.completedOrders > 0) {
                        if (completedOrdersForDate.isNotEmpty()) {
                            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                completedOrdersForDate.forEach { order ->
                                    OrderItem(
                                        order = order,
                                        onOrderClick = { onOrderClick(order) },
                                        onStatusChange = { updatedOrder, newStatus ->
                                            scope.launch {
                                                orderRepository.updateOrderStatus(updatedOrder, newStatus)
                                            }
                                        },
                                        onDeleteOrder = { orderToDelete = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        onStatusUpdate = {
                                            scope.launch {
                                                val newStatus = if (order.status == OrderStatus.COMPLETED) {
                                                    OrderStatus.NEW
                                                } else {
                                                    OrderStatus.COMPLETED
                                                }
                                                orderRepository.updateOrderStatus(order, newStatus)
                                            }
                                        },
                                        onPhotoViewClick = { photoUri ->
                                            // Сохраняем URI фото и переходим на экран просмотра
                                            navController.currentBackStackEntry?.savedStateHandle?.set("photo_uri", photoUri.toString())
                                            navController.navigate("photo_viewer")
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
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
                            text = "Нет данных о заказах за выбранную дату",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Календарь
            if (showCalendar) {
                CalendarDialog(
                    markedDates = statistics.dailyStats.map { it.date },
                    onDateSelected = { date ->
                        selectedDate = date
                        showCalendar = false
                    },
                    onDismiss = { showCalendar = false }
                )
            }

            // Диалоги
            StatisticsDialogs(
                showSalaryCalculator = showSalaryCalculator,
                onDismissSalaryCalculator = { showSalaryCalculator = false },
                startDate = startDate,
                endDate = endDate,
                onStartDateSelected = { startDate = it },
                onEndDateSelected = { endDate = it },
                calculateSalaryForPeriod = ::calculateSalaryForPeriod,
                showClearConfirmDialog = showClearConfirmDialog,
                onDismissClearConfirm = { showClearConfirmDialog = false },
                onClearStatistics = onClearStatistics,
                showClearDateConfirmDialog = showClearDateConfirmDialog,
                onDismissClearDateConfirm = { showClearDateConfirmDialog = false },
                selectedDate = selectedDate,
                onClearDateStatistics = { viewModel.clearStatisticsForDate(selectedDate) },
                orderToDelete = orderToDelete,
                onDismissDeleteOrder = { orderToDelete = null },
                onDeleteOrder = { order ->
                    scope.launch {
                        orderRepository.deleteOrder(order.id)
                        orderToDelete = null
                    }
                },
                fullDateFormatter = fullDateFormatter
            )
        }
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
            .height(65.dp)  // Еще больше уменьшаем высоту карточки
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  // Уменьшаем тень
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),  // Уменьшаем отступы
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp  // Немного уменьшаем размер заголовка
                )
                Spacer(modifier = Modifier.height(2.dp))  // Минимальный отступ
                Surface(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 2.dp),
                    color = Color.Transparent
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp  // Немного уменьшаем размер значения
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarDialog(
    markedDates: List<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    var currentMonth by remember { mutableStateOf(today.withDayOfMonth(1)) }
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.dayOfWeek.value % 7 // 0 - воскресенье
    val weeks = ((daysInMonth + firstDayOfWeek - 1) / 7) + 1

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Text("<")
                }
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy")),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Text(">")
                }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                for (week in 0 until weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (dayOfWeek in 0..6) {
                            val dayOfMonth = week * 7 + dayOfWeek - firstDayOfWeek + 2
                            if (dayOfMonth in 1..daysInMonth) {
                                val date = currentMonth.withDayOfMonth(dayOfMonth)
                                val isMarked = markedDates.contains(date)
                                val isToday = date == today
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isMarked -> Color(0xFFB9F6CA)
                                                else -> Color.White
                                            }
                                        )
                                        .border(
                                            width = if (isToday) 2.dp else 1.dp,
                                            color = if (isToday) Color(0xFF2196F3) else Color.LightGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { if (isMarked) onDateSelected(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayOfMonth.toString(),
                                        color = if (isMarked) Color.Black else Color.Gray,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
} 