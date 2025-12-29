package com.example.dmapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64

class YandexOcrTest(private val context: Context) {
    private val TAG = "YandexOcrTest"
    private val IAM_TOKEN = "" // TODO: Add IAM token from environment or secure storage
    // ID папки можно найти в консоли Яндекс Cloud:
    // 1. Перейдите в раздел "Облака и каталоги"
    // 2. Найдите нужное облако
    // 3. ID папки будет в формате b1gxxxxxxxxxxxxxxxxxxxx
    private val FOLDER_ID = "b1glgsd4n1r9f61uvo63"
    
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            // Конвертируем Bitmap в Base64
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)

            // Создаем JSON для запроса
            val jsonBody = JSONObject().apply {
                put("folderId", FOLDER_ID)
                put("analyzeSpecs", JSONObject().apply {
                    put("content", base64Image)
                    put("mimeType", "image/jpeg")
                    put("features", JSONObject().apply {
                        put("type", "TEXT_DETECTION")
                        put("textDetectionConfig", JSONObject().apply {
                            put("languageCodes", arrayOf("ru", "en"))
                        })
                    })
                })
            }

            // Создаем HTTP клиент
            val client = OkHttpClient()
            val mediaType = "application/json".toMediaType()
            val body = jsonBody.toString().toRequestBody(mediaType)

            // Создаем запрос
            val request = Request.Builder()
                .url("https://vision.api.cloud.yandex.net/vision/v1/batchAnalyze")
                .addHeader("Authorization", "Bearer $IAM_TOKEN")
                .post(body)
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Error: ${response.code} - ${response.message}")
                    return@withContext "Ошибка распознавания: ${response.code}"
                }

                val responseBody = response.body?.string()
                Log.d(TAG, "Response: $responseBody")

                // Парсим ответ
                val jsonResponse = JSONObject(responseBody)
                val results = jsonResponse.optJSONArray("results")
                
                if (results != null && results.length() > 0) {
                    val firstResult = results.getJSONObject(0)
                    val results = firstResult.optJSONArray("results")
                    
                    if (results != null && results.length() > 0) {
                        val textDetection = results.getJSONObject(0)
                        val text = textDetection.optString("text", "")
                        return@withContext text
                    }
                }
                
                return@withContext "Текст не найден"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during OCR", e)
            return@withContext "Ошибка: ${e.message}"
        }
    }
} 