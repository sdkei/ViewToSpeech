package io.github.sdkei.viewtospeech

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.Text.TextBlock
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import io.github.sdkei.viewtospeech.model.takePictureBitmap
import io.github.sdkei.viewtospeech.ui.theme.ViewToSpeechTheme
import io.github.sdkei.viewtospeech.util.timesFloor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current

            ViewToSpeechTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 撮影された画像
                    var takenPicture by remember { mutableStateOf<Bitmap?>(null) }
                    var recognizedText by remember { mutableStateOf<Text?>(null) }

                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        CameraPreviewAndCapture(
                            modifier = Modifier.weight(1f),
                            takenPicture = takenPicture,
                            recognizedText = recognizedText,
                            onTakePicture = { bitmap ->
                                takenPicture = bitmap

                                recognizedText = null // 一旦 null にする。
                                if (bitmap != null) {
                                    lifecycleScope.launch(
                                        CoroutineExceptionHandler { _, e ->
                                            Log.e("MainActivity", "文字認識できませんでした。", e)

                                            Toast.makeText(
                                                context,
                                                "エラーが発生しました。",
                                                Toast.LENGTH_SHORT,
                                            )
                                                .show()
                                        }
                                    ) {
                                        val rotationDegrees = 0
                                        val image = InputImage.fromBitmap(bitmap, rotationDegrees)

                                        val recognizer = TextRecognition.getClient(
                                            JapaneseTextRecognizerOptions.Builder().build()
                                        )
                                        recognizedText = recognizer.process(image).await()
                                    }
                                }
                            },
                            onError = { e ->
                                Toast.makeText(context, "エラーが発生しました。", Toast.LENGTH_SHORT)
                                    .show()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * カメラのプレビューと撮影された画像を表示する composable。
 *
 * 撮影された画像がなければ（[takenPicture] が null ならば）カメラのプレビューと撮影ボタンを表示する。
 * 撮影ボタンが押されると撮影が行われ、[onTakePicture] に撮影された画像が渡される。
 *
 * 撮影された画像があればその画像と再撮影ボタンを表示する。
 * 再撮影ボタンが押されると [onTakePicture] に null が渡される。
 */
@Composable
fun CameraPreviewAndCapture(
    modifier: Modifier = Modifier,
    takenPicture: Bitmap?,
    recognizedText: Text?,
    onTakePicture: (Bitmap?) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // カメラのパーミッション
    val permission = android.Manifest.permission.CAMERA
    // カメラのパーミッションを得られているか
    var isGranted by remember {
        val isGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(isGranted)
    }
    // カメラのパーミッションを求めるアクティビティを起動するランチャー
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted = it }
    )

    // カメラのパーミッションが得られていない場合
    if (isGranted.not()) {
        LaunchedEffect(Unit) {
            launcher.launch(permission)
        }

        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("カメラの使用を許可してください。")
        }
        return
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Column(modifier = modifier) {
        takenPicture
            ?.also {
                // 撮影された画像と再撮影ボタンを表示する。
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 撮影された画像とテキストブロック
                    ImageAndTextBlocks(
                        image = it,
                        textBlocks = recognizedText?.textBlocks,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 再撮影ボタン
                    Button(
                        onClick = { onTakePicture(null) },
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text("再撮影")
                    }
                }
            }
            ?: run {
                // カメラプレビューと撮影ボタンを表示する。
                Box(modifier = Modifier.weight(1f)) {
                    val imageCapture = remember { ImageCapture.Builder().build() }

                    // カメラプレビュー
                    CameraPreview(
                        cameraProviderFuture = cameraProviderFuture,
                        imageCapture = imageCapture,
                        modifier = Modifier.fillMaxSize(),
                        onError = onError,
                    )

                    // 撮影ボタン
                    Button(
                        onClick = {
                            lifecycleOwner.lifecycleScope.launch(
                                CoroutineExceptionHandler { _, e ->
                                    Log.e("CameraViewAndCapture", "撮影できませんでした。", e)
                                    onError(e)
                                }
                            ) {
                                imageCapture.takePictureBitmap()
                                    .also { onTakePicture(it) }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    ) {
                        Text("撮影")
                    }
                }
            }
    }
}

@Composable
fun ImageAndTextBlocks(
    image: Bitmap,
    textBlocks: List<TextBlock>?,
    modifier: Modifier = Modifier,
) {
    @SuppressLint("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(
        modifier = modifier
    ) {
        // 拡大率
        val scale = minOf(
            maxWidth.value / image.width,
            maxHeight.value / image.height,
        )
        // コンポーザブルのサイズ
        val size = DpSize(
            (image.width * scale).dp,
            (image.height * scale).dp,
        )

        // 撮影された画像
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "撮影された画像",
            modifier = Modifier
                .size(size)
                .align(Alignment.Center),
        )

        // テキストブロック
        if (textBlocks != null) {
            TextBlocks(
                textBlocks = textBlocks,
                scale = scale,
                modifier = Modifier
                    .size(size)
                    .align(Alignment.Center),
            )
        }
    }
}

@Composable
fun TextBlocks(
    textBlocks: List<TextBlock>,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    Box(
        modifier = modifier,
    ) {
        textBlocks.forEach { block ->
            val boundingBox = block.boundingBox
                ?.let {
                    android.graphics.Rect(
                        it.left.timesFloor(scale),
                        it.top.timesFloor(scale),
                        it.right.timesFloor(scale),
                        it.bottom.timesFloor(scale),
                    )
                }
                ?: return@forEach

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = boundingBox.left.dp,
                        y = boundingBox.top.dp,
                    )
                    .size(
                        width = boundingBox.width().dp,
                        height = boundingBox.height().dp,
                    )
                    // composable の外に枠線を描く。
                    .drawBehind {
                        val borderThickness = 4.dp.toPx()
                        drawRect(
                            color = Color.Yellow,
                            topLeft = Offset(
                                x = -borderThickness,
                                y = -borderThickness,
                            ),
                            size = Size(
                                width = size.width + borderThickness * 2,
                                height = size.height + borderThickness * 2,
                            ),
                            style = Stroke(
                                width = borderThickness,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
            )
        }
    }
}

@Composable
fun CameraPreview(
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            val previewView = PreviewView(context)

            val cameraSelector = CameraSelector.Builder()
                // 背面カメラ
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
            val preview = androidx.camera.core.Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            lifecycleOwner.lifecycleScope.launch(
                CoroutineExceptionHandler { _, e ->
                    Log.e("CameraPreview", "カメラプレビューを初期化できませんでした。", e)
                    onError(e)
                }
            ) {
                cameraProviderFuture.await().apply {
                    // 以前のバインディングを解除
                    unbindAll()

                    bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                    )
                }
            }

            previewView
        },
        modifier = modifier,
    )
}
