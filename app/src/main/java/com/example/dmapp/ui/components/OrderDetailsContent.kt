package com.example.dmapp.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.dmapp.data.Order
import com.example.dmapp.data.OrderStatus
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import android.widget.Toast

/**
 * Общий компонент для отображения детальной информации о заказе.
 * Используется как в карточке заказа на карте, так и в списке заказов.
 */
@Composable
fun OrderDetailsContent(
    order: Order,
    modifier: Modifier = Modifier,
    onStatusChange: ((Order, OrderStatus) -> Unit)? = null,
    onNotesChange: ((Order, String) -> Unit)? = null,
    onTakePhoto: (() -> Unit)? = null,
    onPhotoClick: ((Uri) -> Unit)? = null
) {
    // Локальное состояние для заметок
    var notesText by remember { mutableStateOf(order.notes ?: "") }
    
    // Определяем цвета для кнопок
    val blueButtonColor = Color(0xFF2196F3) // Светло-синий для "В работу"
    val greenButtonColor = Color(0xFF4CAF50) // Зеленый для "Завершить"
    val whatsappColor = Color(0xFF25D366) // Зеленый цвет WhatsApp
    
    // Получаем контекст для запуска Intent'ов
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    
    // Функция для копирования номера заказа и открытия сайта
    fun copyOrderNumberAndOpenSite() {
        // Копируем номер заказа в буфер обмена
        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Номер заказа", order.externalOrderNumber)
        clipboardManager.setPrimaryClip(clip)
        
        // Показываем короткое сообщение пользователю
        android.widget.Toast.makeText(
            context, 
            "Номер заказа ${order.externalOrderNumber} скопирован",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        // Открываем сайт курьера
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://courier.detmir.ru/?settings=eyJDbGVhbiI6dHJ1ZSwiQ2FzaFdlYkJhc2VVcmwiOiJodHRwOi8vMTcyLjE2LjQ0LjExNyIsIlNob3dJbnN0YWxsTWVzc2FnZSI6ZmFsc2UsIkN1cnJlbmN5U3ltYm9sIjoiXHUyMEJEIiwiT3JkZXJBY2Nlc3NDb2RlVGltZW91dCI6MzAsIkRlZmF1bHRPcmRlcklkUHJlZml4IjoiU1NTU1MiLCJBbGxvd0RlbGV0ZU9ubHlLbVByb2R1Y3QiOnRydWV9"
        ))
        context.startActivity(intent)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Время доставки
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Доставка: ${order.deliveryTimeStart.format(DateTimeFormatter.ofPattern("HH:mm"))} - " +
                      "${order.deliveryTimeEnd.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Адрес
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = order.deliveryAddress,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            
            // Иконка для открытия Яндекс Навигатора
            IconButton(
                onClick = {
                    if (order.latitude != null && order.longitude != null) {
                        // Открываем Яндекс Навигатор с маршрутом
                        val uri = Uri.parse("yandexnavi://build_route_on_map?lat_to=${order.latitude}&lon_to=${order.longitude}")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        
                        // Проверяем, установлен ли Яндекс Навигатор
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            // Если не установлен, открываем Яндекс Карты в браузере
                            val webUri = Uri.parse("https://yandex.ru/maps/?mode=routes&rtext=~${order.latitude},${order.longitude}")
                            val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                            context.startActivity(webIntent)
                        }
                    } else {
                        // Если координат нет, пытаемся открыть по адресу
                        val escapedAddress = Uri.encode(order.deliveryAddress)
                        val webUri = Uri.parse("https://yandex.ru/maps/?mode=search&text=$escapedAddress")
                        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                        context.startActivity(webIntent)
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = "Открыть навигатор",
                    tint = Color(0xFF2196F3) // Синий цвет для навигации
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Клиент
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.clientName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = order.clientPhone,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Иконка для звонка
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${order.clientPhone}")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Call,
                            contentDescription = "Позвонить",
                            tint = Color(0xFF2196F3) // Светло-синий для звонка
                        )
                    }
                    
                    // Иконка для SMS
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:${order.clientPhone}")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Message,
                            contentDescription = "Отправить SMS",
                            tint = Color.Gray
                        )
                    }
                    
                    // Иконка для WhatsApp
                    IconButton(
                        onClick = {
                            val phoneNumber = order.clientPhone.replace(Regex("[^0-9]"), "")
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Если WhatsApp не установлен, открываем веб-версию
                                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://web.whatsapp.com/send?phone=$phoneNumber")
                                }
                                context.startActivity(webIntent)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        // Используем кастомную иконку для WhatsApp
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.dmapp.R.drawable.ic_whatsapp),
                            contentDescription = "Написать в WhatsApp",
                            tint = Color.Unspecified // Не тонируем, так как иконка уже имеет нужный цвет
                        )
                    }
                }
            }
        }

        if (!order.clientComment.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            // Комментарий
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Comment,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = order.clientComment ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Информация о заказе
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${order.weight} кг",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp
                )
                Text(
                    text = "Вес",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${order.volume} м³",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp
                )
                Text(
                    text = "Объем",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${order.orderAmount} ₽",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp
                )
                Text(
                    text = if (order.isPrepaid) "Оплачен" else "К оплате",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = if (order.isPrepaid) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Инструкции по доставке
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Инструкции по доставке",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = order.clientComment ?: "Особых инструкций нет",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp
                )
                
                if (order.clientComment.isNullOrBlank()) {
                    Text(
                        text = "• Позвоните клиенту за 15-20 минут до прибытия\n• Проверьте комплектность заказа перед вручением\n• Будьте вежливы и приветливы",
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        
        // Заметки к заказу - теперь всегда отображаем секцию заметок
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Заметки",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Поле для ввода заметок
        OutlinedTextField(
            value = notesText,
            onValueChange = { newText ->
                notesText = newText
                // Вызываем обработчик изменения заметок, если он передан
                onNotesChange?.invoke(order, newText)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Добавьте заметку...", fontSize = 16.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
            minLines = 3,
            maxLines = 5
        )
        
        // Фото заказа
        if (order.photoUri != null) {
            var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            
            // Загружаем изображение
            LaunchedEffect(order.photoUri) {
                try {
                    val uri = Uri.parse(order.photoUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                } catch (e: Exception) {
                    println("Ошибка загрузки изображения: ${e.message}")
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onPhotoClick?.invoke(Uri.parse(order.photoUri)) }
                ) {
                    bitmap?.let { loadedBitmap ->
                        Image(
                            bitmap = loadedBitmap.asImageBitmap(),
                            contentDescription = "Фото заказа",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                
                // Кнопка WhatsApp
                IconButton(
                    onClick = {
                        try {
                            // Копируем фото в буфер обмена
                            bitmap?.let { photoBitmap ->
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                
                                // Создаем временный файл для фото
                                val tempFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
                                val outputStream = FileOutputStream(tempFile)
                                photoBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                                outputStream.close()
                                
                                // Создаем URI для файла
                                val photoUri = FileProvider.getUriForFile(
                                    context,
                                    "com.example.dmapp.fileprovider",
                                    tempFile
                                )
                                
                                // Копируем фото в буфер обмена
                                val clip = android.content.ClipData.newUri(
                                    context.contentResolver,
                                    "Фото заказа",
                                    photoUri
                                )
                                clipboardManager.setPrimaryClip(clip)
                                
                                // Показываем уведомление
                                Toast.makeText(
                                    context,
                                    "Фото скопировано в буфер обмена",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            
                            // Открываем WhatsApp
                            val phoneNumber = order.clientPhone.replace(Regex("[^0-9]"), "")
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Если WhatsApp не установлен, открываем веб-версию
                            val phoneNumber = order.clientPhone.replace(Regex("[^0-9]"), "")
                            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://web.whatsapp.com/send?phone=$phoneNumber")
                            }
                            context.startActivity(webIntent)
                        }
                    },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.dmapp.R.drawable.ic_whatsapp),
                        contentDescription = "Отправить в WhatsApp",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Отображение даты и времени фотографии
            order.photoDateTime?.let { dateTime ->
                Text(
                    text = "Фото сделано: ${dateTime.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Кнопка сделать фото
        if (onTakePhoto != null) {
            Button(
                onClick = { onTakePhoto() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Сделать фото")
            }
        }
        
        // Кнопка смены статуса перемещена в самый низ
        if (onStatusChange != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    // Определяем следующий статус
                    val nextStatus = when (order.status) {
                        OrderStatus.NEW -> OrderStatus.IN_PROGRESS
                        OrderStatus.IN_PROGRESS -> OrderStatus.COMPLETED
                        OrderStatus.COMPLETED -> OrderStatus.NEW
                        else -> OrderStatus.NEW
                    }
                    
                    // Если новый статус - COMPLETED, копируем номер и открываем сайт
                    if (nextStatus == OrderStatus.COMPLETED) {
                        copyOrderNumberAndOpenSite()
                    }
                    
                    // Вызываем обработчик смены статуса
                    onStatusChange(order, nextStatus)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (order.status) {
                        OrderStatus.NEW -> blueButtonColor // Светло-синий для "В работу"
                        OrderStatus.IN_PROGRESS -> greenButtonColor // Зеленый для "Завершить"
                        OrderStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                        else -> blueButtonColor
                    }
                )
            ) {
                Text(
                    text = when (order.status) {
                        OrderStatus.NEW -> "В работу"
                        OrderStatus.IN_PROGRESS -> "Завершить"
                        OrderStatus.COMPLETED -> "Сбросить"
                        else -> "Изменить статус"
                    },
                    fontSize = 16.sp
                )
            }
        }
    }
} 