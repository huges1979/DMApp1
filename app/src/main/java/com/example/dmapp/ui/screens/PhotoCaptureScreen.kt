package com.example.dmapp.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.example.dmapp.data.Order
import com.example.dmapp.ui.OrderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureScreen(
    order: Order,
    viewModel: OrderViewModel,
    onNavigateBack: () -> Unit,
    onPhotoSaved: () -> Unit
) {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(order.photoUri?.let { Uri.parse(it) }) }
    var permissionGranted by remember { mutableStateOf(false) }
    
    // Запуск камеры - объявляем перед permissionLauncher, чтобы избежать циклической зависимости
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        // Если фото сделано успешно, сохраняем URI в состоянии
        if (success) {
            println("Photo captured successfully: $photoUri")
        } else {
            println("Failed to capture photo")
            // Если фото не сделано, возвращаемся назад
            if (order.photoUri == null) {
                onNavigateBack()
            }
        }
    }
    
    // Запрос разрешения на камеру
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        println("Camera permission granted: $isGranted")
        permissionGranted = isGranted
        if (isGranted) {
            // Если разрешение получено, создаём файл и автоматически открываем камеру
            val newPhotoUri = createImageFile(context)
            println("Created new photo URI: $newPhotoUri")
            photoUri = newPhotoUri
            cameraLauncher.launch(newPhotoUri)
        }
    }
    
    // Запрашиваем разрешение при первом открытии экрана
    LaunchedEffect(Unit) {
        // Запрашиваем разрешение на камеру
        if (order.photoUri == null) {
            // Если у заказа нет фото, запрашиваем разрешение и открываем камеру
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            // Если у заказа уже есть фото, просто помечаем разрешение как полученное
            // чтобы можно было переснять фото
            permissionGranted = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фото для заказа №${order.orderNumber}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                // Если есть фото, показываем его и кнопки действий
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Предпросмотр фото
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        
                        // Загружаем изображение
                        LaunchedEffect(photoUri) {
                            try {
                                // Извлекаем файл из URI
                                val inputStream = context.contentResolver.openInputStream(photoUri!!)
                                bitmap = BitmapFactory.decodeStream(inputStream)
                                inputStream?.close()
                            } catch (e: Exception) {
                                println("Ошибка загрузки изображения: ${e.message}")
                            }
                        }
                        
                        bitmap?.let { loadedBitmap ->
                            Image(
                                bitmap = loadedBitmap.asImageBitmap(),
                                contentDescription = "Фото заказа",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } ?: Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Кнопки действий
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Кнопка "Переснять"
                        Button(
                            onClick = {
                                val newPhotoUri = createImageFile(context)
                                photoUri = newPhotoUri
                                cameraLauncher.launch(newPhotoUri)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Переснять")
                        }
                        
                        // Кнопка "Готово"
                        Button(
                            onClick = {
                                // Сохраняем URI фото в базе данных
                                viewModel.updateOrderPhoto(order.id, photoUri.toString())
                                onPhotoSaved()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Готово")
                        }
                    }
                }
            } else if (permissionGranted) {
                // Если разрешение получено, но фото нет - показываем кнопку съемки
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            val newPhotoUri = createImageFile(context)
                            photoUri = newPhotoUri
                            cameraLauncher.launch(newPhotoUri)
                        },
                        modifier = Modifier.size(120.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Camera,
                            contentDescription = "Сделать фото",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Нажмите для съемки фото",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Если разрешение не получено
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Для съемки фото требуется разрешение на использование камеры. Без этого разрешения функция не будет работать.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Предоставить разрешение")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = onNavigateBack
                    ) {
                        Text("Вернуться назад")
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenPhotoViewer(
    photoUri: Uri,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    // Загружаем изображение
    LaunchedEffect(photoUri) {
        try {
            // Извлекаем файл из URI
            val inputStream = context.contentResolver.openInputStream(photoUri)
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
        } catch (e: Exception) {
            println("Ошибка загрузки изображения: ${e.message}")
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Показываем фото на весь экран
        bitmap?.let { loadedBitmap ->
            Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = "Фото заказа",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White
            )
        }
        
        // Кнопка закрытия в верхнем углу
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Закрыть",
                tint = Color.White
            )
        }
    }
}

// Функция для создания файла изображения
private fun createImageFile(context: Context): Uri {
    try {
        // Создаем имя файла на основе времени
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        
        println("Creating image file with name: $imageFileName")
        
        // Получаем директорию для хранения фото
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        println("Storage directory: ${storageDir?.absolutePath}")
        
        // Проверяем, существует ли директория
        if (storageDir == null || !storageDir.exists()) {
            println("Storage directory does not exist, creating it")
            storageDir?.mkdirs()
        }
        
        // Создаем временный файл
        val imageFile = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
        println("Created image file at: ${imageFile.absolutePath}")
        
        // Создаем URI с использованием FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.dmapp.fileprovider",
            imageFile
        )
        println("Created URI: $uri")
        
        return uri
    } catch (e: Exception) {
        println("Error creating image file: ${e.message}")
        e.printStackTrace()
        throw e
    }
} 