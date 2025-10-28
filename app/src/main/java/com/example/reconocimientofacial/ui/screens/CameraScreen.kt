package com.example.reconocimientofacial.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * Convierte ImageProxy a Bitmap con la rotación correcta
 *
 * PROBLEMA: Las imágenes de cámara pueden capturarse rotadas 90° debido a:
 * - Orientación del sensor de la cámara
 * - Orientación del dispositivo al capturar
 *
 * SOLUCIÓN: Lee los metadatos de rotación y aplica la transformación
 *
 * @return Bitmap correctamente orientado
 */
private fun ImageProxy.toBitmapWithRotation(): Bitmap {
    val bitmap = this.toBitmap()
    val rotationDegrees = this.imageInfo.rotationDegrees

    if (rotationDegrees == 0) {
        return bitmap
    }

    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
    }

    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )
}

/**
 * Pantalla de captura de cámara con vista previa
 *
 * FUNCIONALIDAD:
 * - Muestra vista previa en tiempo real de la cámara frontal
 * - Botón para capturar imagen
 * - Corrige automáticamente la rotación de la imagen capturada
 *
 * COMPONENTES:
 * - CameraX para gestión de cámara (API moderna de Android)
 * - LifecycleCameraController vinculado al ciclo de vida de la pantalla
 * - Cámara frontal por defecto (mejor para selfies faciales)
 *
 * @param onImageCaptured Callback con la imagen capturada correctamente orientada
 * @param onError Callback si ocurre un error en la captura
 */
@Composable
fun CameraScreen(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Configurar controlador de cámara
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA // Cámara frontal
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Vista previa de la cámara (AndroidView integra vista nativa en Compose)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = cameraController
                    cameraController.bindToLifecycle(lifecycleOwner)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón de captura en la parte inferior
        Button(
            onClick = {
                val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
                cameraController.takePicture(
                    mainExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            // Convertir y rotar correctamente
                            val bitmap = image.toBitmapWithRotation()
                            onImageCaptured(bitmap)
                            image.close()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onError("Error al capturar imagen: ${exception.message}")
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text("Capturar")
        }
    }
}
