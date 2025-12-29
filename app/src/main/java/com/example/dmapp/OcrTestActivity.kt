package com.example.dmapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.dmapp.utils.YandexOcrTest
import kotlinx.coroutines.launch
import java.io.IOException

class OcrTestActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private lateinit var selectButton: Button
    private lateinit var recognizeButton: Button
    private var selectedImage: Bitmap? = null
    private lateinit var ocrTest: YandexOcrTest

    private val selectImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            try {
                imageUri?.let {
                    val inputStream = contentResolver.openInputStream(it)
                    selectedImage = BitmapFactory.decodeStream(inputStream)
                    imageView.setImageBitmap(selectedImage)
                    recognizeButton.isEnabled = true
                }
            } catch (e: IOException) {
                Log.e("OcrTestActivity", "Error loading image", e)
                Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr_test)

        ocrTest = YandexOcrTest(this)
        
        imageView = findViewById(R.id.imageView)
        resultText = findViewById(R.id.resultText)
        selectButton = findViewById(R.id.selectButton)
        recognizeButton = findViewById(R.id.recognizeButton)
        
        recognizeButton.isEnabled = false

        selectButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            selectImage.launch(intent)
        }

        recognizeButton.setOnClickListener {
            selectedImage?.let { bitmap ->
                lifecycleScope.launch {
                    try {
                        resultText.text = "Распознавание..."
                        val result = ocrTest.recognizeText(bitmap)
                        resultText.text = result
                    } catch (e: Exception) {
                        Log.e("OcrTestActivity", "Error recognizing text", e)
                        resultText.text = "Ошибка: ${e.message}"
                    }
                }
            }
        }
    }
} 