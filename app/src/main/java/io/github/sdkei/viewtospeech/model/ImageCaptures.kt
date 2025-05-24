package io.github.sdkei.viewtospeech.model

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.takePicture
import kotlin.use

/**
 * 写真を撮影し、正しい向きに回転させた画像を返す。
 */
suspend fun ImageCapture.takePictureBitmap(): Bitmap {
    val imageCapture = this

    return imageCapture.takePicture()
        // 必ず ImageProxy を閉じる
        .use { image ->
            val matrix = Matrix().apply {
                postRotate(image.imageInfo.rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(
                image.toBitmap(), 0, 0, image.width, image.height, matrix, true
            )
        }
}
