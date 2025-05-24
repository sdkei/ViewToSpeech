package io.github.sdkei.viewtospeech

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import io.github.sdkei.viewtospeech.model.takePictureBitmap
import io.github.sdkei.viewtospeech.ui.theme.ViewToSpeechTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

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

                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        CameraPreviewAndCapture(
                            modifier = Modifier.weight(1f),
                            takenPicture = takenPicture,
                            onTakePicture = { bitmap ->
                                takenPicture = bitmap
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
                    // 撮影された画像
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "撮影された画像",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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
