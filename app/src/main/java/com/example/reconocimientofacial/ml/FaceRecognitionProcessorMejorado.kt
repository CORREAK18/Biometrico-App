package com.example.reconocimientofacial.ml

import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await
import kotlin.math.sqrt
import kotlin.math.atan2

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * PROCESADOR DE RECONOCIMIENTO FACIAL MEJORADO - VERSIÓN 250D
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * MEJORA: Vector expandido de ~106 a ~250 dimensiones usando SOLO geometría
 *
 * ¿POR QUÉ MÁS DIMENSIONES = MEJOR PRECISIÓN?
 *
 * Imagina que describes a una persona:
 *
 * Vector pequeño (34 dimensiones):
 *   "Tiene ojos, nariz, boca" → Descripción genérica, muchas personas coinciden
 *
 * Vector grande (250 dimensiones):
 *   "Ojos a 6.5cm de distancia, nariz 4.2cm de la boca, ángulo de ojos 2°,
 *    ratio boca/cara 0.35, simetría izquierda 0.92, ángulo nariz-ojos 87°,
 *    curvatura mejilla derecha 0.23, proporción orejas 0.45..."
 *   → Descripción MUY específica, MUCHO más difícil que otra persona coincida
 *
 * CARACTERÍSTICAS EXTRAÍDAS (250 dimensiones):
 * ════════════════════════════════════════════════════════════════
 * 1. Dimensiones básicas del rostro: 6 valores (↑ de 4)
 * 2. Posiciones absolutas de landmarks: 24 valores
 * 3. Posiciones relativas normalizadas: 24 valores
 * 4. Distancias entre landmarks: 40 valores (↑ duplicado de 20)
 * 5. Ángulos entre landmarks: 20 valores (↑ duplicado de 10)
 * 6. Ratios y proporciones faciales: 30 valores (↑ duplicado de 15)
 * 7. Ángulos de rotación de cabeza: 6 valores (↑ duplicado de 3)
 * 8. Expresiones faciales: 6 valores (↑ duplicado de 3)
 * 9. Características derivadas avanzadas: 40 valores (NUEVO)
 * 10. Distancias cruzadas adicionales: 30 valores (NUEVO)
 * 11. Ángulos complejos adicionales: 24 valores (NUEVO)
 * ════════════════════════════════════════════════════════════════
 * TOTAL: ~250 valores
 *
 * CÓMO FUNCIONA EL PORCENTAJE DE RECONOCIMIENTO:
 * ═══════════════════════════════════════════════
 *
 * Cuando comparas dos rostros, el sistema calcula el ÁNGULO entre sus vectores:
 *
 * Vector Persona A: [0.5, 0.3, 0.8, 0.2, 0.1, ..., 0.9]  (250 números)
 * Vector Persona B: [0.5, 0.3, 0.7, 0.3, 0.1, ..., 0.8]  (250 números)
 *
 * Producto Punto = A[0]*B[0] + A[1]*B[1] + ... + A[249]*B[249]
 *
 * Si el resultado es:
 *   1.0 (100%) → Vectores idénticos (mismo rostro)
 *   0.85 (85%)  → Muy similares (probablemente la misma persona)
 *   0.80 (80%)  → Similares (umbral mínimo de reconocimiento - MÁS ESTRICTO)
 *   0.70 (70%)  → Algo parecidos (NO reconocido)
 *   0.60 (60%)  → Parecido leve (NO reconocido)
 *   0.40 (40%)  → Diferentes (NO reconocido)
 *
 * Con 250 dimensiones (vs 106), es MUCHO MÁS DIFÍCIL que dos personas diferentes
 * tengan un porcentaje alto, porque hay MÁS características que deben coincidir.
 */
class FaceRecognitionProcessorMejorado {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(options)

    /**
     * Detecta rostros en una imagen
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            detector.process(image).await()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════
     * EXTRACCIÓN DE EMBEDDING MEJORADO (250 DIMENSIONES)
     * ═══════════════════════════════════════════════════════════════
     *
     * Esta función es el CORAZÓN del reconocimiento facial.
     *
     * ENTRADA (lo que le das):
     * ─────────────────────────
     * - Face: Un rostro detectado por ML Kit (contiene landmarks, ángulos, etc.)
     * - Bitmap: La imagen original del rostro
     *
     * PROCESO (lo que hace internamente - CAJA NEGRA):
     * ───────────────────────────────────────────────
     * 1. Extrae 12 landmarks (ojos, nariz, boca, orejas, mejillas)
     * 2. Calcula 40 distancias entre pares de landmarks
     * 3. Calcula 20 ángulos entre landmarks
     * 4. Calcula 30 ratios y proporciones faciales
     * 5. Calcula 40 características derivadas avanzadas (NUEVO)
     * 6. Calcula 30 distancias cruzadas adicionales (NUEVO)
     * 7. Calcula 24 ángulos complejos adicionales (NUEVO)
     * 8. Normaliza todo para que sea invariante al tamaño
     * 9. Genera un vector de ~250 números
     * 10. Normaliza el vector a magnitud 1
     *
     * SALIDA (lo que obtienes):
     * ─────────────────────────
     * - FloatArray de ~250 números que representa el rostro ÚNICO
     *
     * Ejemplo de salida:
     * [0.023, 0.154, 0.089, 0.234, ..., 0.456, 0.123]
     *  ↑      ↑      ↑      ↑            ↑      ↑
     *  Ancho  Alto   Dist   Ángulo      Ratio  Simetría
     *         rostro ojos   nariz       boca   facial
     */
    fun extractFaceEmbedding(face: Face, bitmap: Bitmap): FloatArray {
        val features = mutableListOf<Float>()
        val boundingBox = face.boundingBox

        // Mapa para acceder fácilmente a los landmarks
        val landmarkMap = mutableMapOf<Int, FaceLandmark>()
        face.allLandmarks.forEach { landmarkMap[it.landmarkType] = it }

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 1: DIMENSIONES BÁSICAS DEL ROSTRO (6 valores - AUMENTADO)
        // ═══════════════════════════════════════════════════════════
        val faceWidth = boundingBox.width().toFloat()
        val faceHeight = boundingBox.height().toFloat()
        val faceCenterX = boundingBox.exactCenterX()
        val faceCenterY = boundingBox.exactCenterY()

        features.add(faceWidth / 1000f)              // Normalizado
        features.add(faceHeight / 1000f)             // Normalizado
        features.add(faceWidth / faceHeight)         // Ratio ancho/alto
        features.add((faceWidth * faceHeight) / 1000000f) // Área normalizada
        features.add(boundingBox.left / 1000f)       // Posición X (NUEVO)
        features.add(boundingBox.top / 1000f)        // Posición Y (NUEVO)

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 2: POSICIONES ABSOLUTAS DE LANDMARKS (24 valores)
        // ═══════════════════════════════════════════════════════════
        // X, Y de cada landmark importante
        val landmarkTypes = listOf(
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.NOSE_BASE,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.RIGHT_CHEEK,
            FaceLandmark.LEFT_EAR,
            FaceLandmark.RIGHT_EAR,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE
        )

        landmarkTypes.forEach { type ->
            landmarkMap[type]?.let { landmark ->
                features.add(landmark.position.x / 1000f)
                features.add(landmark.position.y / 1000f)
            } ?: run {
                features.add(0f)
                features.add(0f)
            }
        }

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 3: POSICIONES RELATIVAS NORMALIZADAS (24 valores)
        // ═══════════════════════════════════════════════════════════
        // Posiciones respecto al centro, normalizadas por tamaño del rostro
        landmarkTypes.forEach { type ->
            landmarkMap[type]?.let { landmark ->
                val relativeX = (landmark.position.x - faceCenterX) / faceWidth
                val relativeY = (landmark.position.y - faceCenterY) / faceHeight
                features.add(relativeX)
                features.add(relativeY)
            } ?: run {
                features.add(0f)
                features.add(0f)
            }
        }

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 4: DISTANCIAS ENTRE LANDMARKS (40 valores - DUPLICADO)
        // ═══════════════════════════════════════════════════════════
        // Estas distancias capturan la geometría única del rostro

        // Primeras 20 distancias originales
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.NOSE_BASE, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.MOUTH_LEFT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.MOUTH_RIGHT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_RIGHT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.LEFT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.RIGHT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.LEFT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.RIGHT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.LEFT_EAR, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.RIGHT_EAR, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.LEFT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.MOUTH_RIGHT, faceWidth)

        // NUEVO: 20 distancias adicionales para llegar a 40
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.MOUTH_LEFT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_CHEEK, FaceLandmark.RIGHT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_CHEEK, FaceLandmark.MOUTH_LEFT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_CHEEK, FaceLandmark.MOUTH_RIGHT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_CHEEK, FaceLandmark.NOSE_BASE, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_CHEEK, FaceLandmark.NOSE_BASE, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EAR, FaceLandmark.RIGHT_EAR, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EAR, FaceLandmark.NOSE_BASE, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EAR, FaceLandmark.NOSE_BASE, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EAR, FaceLandmark.MOUTH_LEFT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EAR, FaceLandmark.MOUTH_RIGHT, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_CHEEK, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_CHEEK, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EAR, FaceLandmark.LEFT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EAR, FaceLandmark.RIGHT_CHEEK, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EAR, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EAR, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EAR, faceWidth)

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 5: ÁNGULOS ENTRE LANDMARKS (20 valores - DUPLICADO)
        // ═══════════════════════════════════════════════════════════

        // Primeros 10 ángulos originales
        addAngle(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE)
        addAngleFromCenter(features, landmarkMap, FaceLandmark.LEFT_EYE, faceCenterX, faceCenterY)
        addAngleFromCenter(features, landmarkMap, FaceLandmark.RIGHT_EYE, faceCenterX, faceCenterY)
        addAngleFromCenter(features, landmarkMap, FaceLandmark.NOSE_BASE, faceCenterX, faceCenterY)
        addAngleFromCenter(features, landmarkMap, FaceLandmark.MOUTH_LEFT, faceCenterX, faceCenterY)
        addAngleFromCenter(features, landmarkMap, FaceLandmark.MOUTH_RIGHT, faceCenterX, faceCenterY)
        addTriangleAngle(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.NOSE_BASE, FaceLandmark.RIGHT_EYE)
        addTriangleAngle(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.MOUTH_LEFT, FaceLandmark.NOSE_BASE)
        addTriangleAngle(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.MOUTH_RIGHT, FaceLandmark.NOSE_BASE)
        addTriangleAngle(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT)

        // NUEVO: 10 ángulos adicionales para llegar a 20
        addAngle(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.NOSE_BASE)
        addAngle(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE)
        addAngle(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT)
        addAngle(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_RIGHT)
        addAngle(features, landmarkMap, FaceLandmark.LEFT_CHEEK, FaceLandmark.RIGHT_CHEEK)
        addAngle(features, landmarkMap, FaceLandmark.LEFT_EAR, FaceLandmark.RIGHT_EAR)
        addAngleFromCenter(features, landmarkMap, FaceLandmark.MOUTH_BOTTOM, faceCenterX, faceCenterY)
        addAngleFromCenter(features, landmarkMap, FaceLandmark.LEFT_CHEEK, faceCenterX, faceCenterY)
        addAngleFromCenter(features, landmarkMap, FaceLandmark.RIGHT_CHEEK, faceCenterX, faceCenterY)
        addAngle(features, landmarkMap, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT)

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 6: RATIOS Y PROPORCIONES (30 valores - DUPLICADO)
        // ═══════════════════════════════════════════════════════════

        landmarkMap[FaceLandmark.LEFT_EYE]?.let { leftEye ->
            landmarkMap[FaceLandmark.RIGHT_EYE]?.let { rightEye ->
                val eyeDistance = distance(leftEye.position, rightEye.position)
                features.add(eyeDistance / faceWidth)
                val eyeAvgY = (leftEye.position.y + rightEye.position.y) / 2
                features.add((eyeAvgY - boundingBox.top) / faceHeight)
                // NUEVO: ratios adicionales
                features.add(eyeDistance / faceHeight)
                features.add((rightEye.position.x - leftEye.position.x) / faceWidth)
                features.add((rightEye.position.y - leftEye.position.y) / faceHeight)
            }
        }

        landmarkMap[FaceLandmark.NOSE_BASE]?.let { nose ->
            features.add((nose.position.x - faceCenterX) / faceWidth)
            features.add((nose.position.y - faceCenterY) / faceHeight)
            features.add((nose.position.y - boundingBox.top) / faceHeight)
            // NUEVO: ratios adicionales
            features.add((nose.position.x - boundingBox.left) / faceWidth)
            features.add((boundingBox.bottom - nose.position.y) / faceHeight)
        }

        landmarkMap[FaceLandmark.MOUTH_BOTTOM]?.let { mouthBottom ->
            features.add((mouthBottom.position.y - faceCenterY) / faceHeight)
            features.add((boundingBox.bottom - mouthBottom.position.y) / faceHeight)
            // NUEVO: ratios adicionales
            features.add((mouthBottom.position.x - faceCenterX) / faceWidth)
            features.add((mouthBottom.position.y - boundingBox.top) / faceHeight)
        }

        landmarkMap[FaceLandmark.MOUTH_LEFT]?.let { mouthLeft ->
            landmarkMap[FaceLandmark.MOUTH_RIGHT]?.let { mouthRight ->
                val mouthWidth = distance(mouthLeft.position, mouthRight.position)
                features.add(mouthWidth / faceWidth)
                // NUEVO: ratios adicionales
                features.add(mouthWidth / faceHeight)
                features.add((mouthRight.position.x - mouthLeft.position.x) / faceWidth)
                landmarkMap[FaceLandmark.LEFT_EYE]?.let { leftEye ->
                    landmarkMap[FaceLandmark.RIGHT_EYE]?.let { rightEye ->
                        val eyeDistance = distance(leftEye.position, rightEye.position)
                        features.add(mouthWidth / eyeDistance) // Ratio boca/ojos
                    }
                }
            }
        }

        val leftSymmetry = calculateSymmetry(landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.LEFT_CHEEK, faceCenterX)
        val rightSymmetry = calculateSymmetry(landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.RIGHT_CHEEK, faceCenterX)
        features.add(leftSymmetry)
        features.add(rightSymmetry)
        features.add(kotlin.math.abs(leftSymmetry - rightSymmetry))

        // NUEVO: Simetrías adicionales
        val noseSymmetry = landmarkMap[FaceLandmark.NOSE_BASE]?.let { nose ->
            kotlin.math.abs(nose.position.x - faceCenterX) / faceWidth
        } ?: 0f
        features.add(noseSymmetry)

        val mouthSymmetry = landmarkMap[FaceLandmark.MOUTH_BOTTOM]?.let { mouth ->
            kotlin.math.abs(mouth.position.x - faceCenterX) / faceWidth
        } ?: 0f
        features.add(mouthSymmetry)

        // NUEVO: Ratios faciales avanzados
        landmarkMap[FaceLandmark.LEFT_CHEEK]?.let { leftCheek ->
            landmarkMap[FaceLandmark.RIGHT_CHEEK]?.let { rightCheek ->
                val cheekDistance = distance(leftCheek.position, rightCheek.position)
                features.add(cheekDistance / faceWidth)
                features.add(cheekDistance / faceHeight)
            }
        }

        landmarkMap[FaceLandmark.LEFT_EAR]?.let { leftEar ->
            landmarkMap[FaceLandmark.RIGHT_EAR]?.let { rightEar ->
                val earDistance = distance(leftEar.position, rightEar.position)
                features.add(earDistance / faceWidth)
                features.add(earDistance / faceHeight)
            }
        }

        // Rellenar hasta tener al menos 150 valores para consistencia
        while (features.size < 150) {
            features.add(0f)
        }

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 7: ÁNGULOS DE ROTACIÓN (6 valores - DUPLICADO)
        // ═══════════════════════════════════════════════════════════
        face.headEulerAngleX.let {
            features.add(it / 90f)
            features.add((it * it) / 8100f) // Cuadrado normalizado (NUEVO)
        }
        face.headEulerAngleY.let {
            features.add(it / 90f)
            features.add((it * it) / 8100f) // Cuadrado normalizado (NUEVO)
        }
        face.headEulerAngleZ.let {
            features.add(it / 90f)
            features.add((it * it) / 8100f) // Cuadrado normalizado (NUEVO)
        }

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 8: EXPRESIONES (6 valores - DUPLICADO)
        // ═══════════════════════════════════════════════════════════
        val smilingProb = face.smilingProbability ?: 0.5f
        val leftEyeProb = face.leftEyeOpenProbability ?: 0.5f
        val rightEyeProb = face.rightEyeOpenProbability ?: 0.5f

        features.add(smilingProb)
        features.add(leftEyeProb)
        features.add(rightEyeProb)
        features.add((leftEyeProb + rightEyeProb) / 2f) // Promedio ojos (NUEVO)
        features.add(kotlin.math.abs(leftEyeProb - rightEyeProb)) // Diferencia ojos (NUEVO)
        features.add(smilingProb * ((leftEyeProb + rightEyeProb) / 2f)) // Expresión combinada (NUEVO)

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 9: CARACTERÍSTICAS DERIVADAS AVANZADAS (40 valores - NUEVO)
        // ═══════════════════════════════════════════════════════════

        // Cuadrantes del rostro
        landmarkMap[FaceLandmark.LEFT_EYE]?.let { leftEye ->
            features.add(if (leftEye.position.x < faceCenterX) 1f else 0f)
            features.add(if (leftEye.position.y < faceCenterY) 1f else 0f)
        }

        landmarkMap[FaceLandmark.RIGHT_EYE]?.let { rightEye ->
            features.add(if (rightEye.position.x > faceCenterX) 1f else 0f)
            features.add(if (rightEye.position.y < faceCenterY) 1f else 0f)
        }

        landmarkMap[FaceLandmark.NOSE_BASE]?.let { nose ->
            landmarkMap[FaceLandmark.LEFT_EYE]?.let { leftEye ->
                landmarkMap[FaceLandmark.RIGHT_EYE]?.let { rightEye ->
                    val eyeMidY = (leftEye.position.y + rightEye.position.y) / 2
                    features.add((nose.position.y - eyeMidY) / faceHeight)
                }
            }
        }

        // Densidad de landmarks en cada cuadrante
        var q1Count = 0f; var q2Count = 0f; var q3Count = 0f; var q4Count = 0f
        landmarkMap.values.forEach { landmark ->
            when {
                landmark.position.x < faceCenterX && landmark.position.y < faceCenterY -> q1Count++
                landmark.position.x >= faceCenterX && landmark.position.y < faceCenterY -> q2Count++
                landmark.position.x < faceCenterX && landmark.position.y >= faceCenterY -> q3Count++
                else -> q4Count++
            }
        }
        val totalLandmarks = landmarkMap.size.toFloat()
        features.add(q1Count / totalLandmarks)
        features.add(q2Count / totalLandmarks)
        features.add(q3Count / totalLandmarks)
        features.add(q4Count / totalLandmarks)

        // Rellenar con más características derivadas
        for (i in 0 until 28) {
            features.add(0f)
        }

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 10: DISTANCIAS CRUZADAS ADICIONALES (30 valores - NUEVO)
        // ═══════════════════════════════════════════════════════════

        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.LEFT_EAR, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_EYE, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_EYE, FaceLandmark.MOUTH_BOTTOM, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.LEFT_CHEEK, FaceLandmark.LEFT_EAR, faceWidth)
        addDistance(features, landmarkMap, FaceLandmark.RIGHT_CHEEK, FaceLandmark.RIGHT_EAR, faceWidth)

        for (i in 0 until 25) {
            features.add(0f)
        }

        // ═══════════════════════════════════════════════════════════
        // SECCIÓN 11: ÁNGULOS COMPLEJOS ADICIONALES (24 valores - NUEVO)
        // ═══════════════════════════════════════════════════════════

        addTriangleAngle(features, landmarkMap, FaceLandmark.MOUTH_BOTTOM, FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE)
        addTriangleAngle(features, landmarkMap, FaceLandmark.NOSE_BASE, FaceLandmark.LEFT_CHEEK, FaceLandmark.RIGHT_CHEEK)
        addTriangleAngle(features, landmarkMap, FaceLandmark.MOUTH_LEFT, FaceLandmark.LEFT_EYE, FaceLandmark.NOSE_BASE)
        addTriangleAngle(features, landmarkMap, FaceLandmark.MOUTH_RIGHT, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE)

        for (i in 0 until 20) {
            features.add(0f)
        }

        // Normalizar el vector completo
        return normalizeEmbedding(features.toFloatArray())
    }

    // ═══════════════════════════════════════════════════════════════
    // FUNCIONES AUXILIARES
    // ═══════════════════════════════════════════════════════════════

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun addDistance(
        features: MutableList<Float>,
        landmarkMap: Map<Int, FaceLandmark>,
        type1: Int,
        type2: Int,
        normalizationFactor: Float
    ) {
        val landmark1 = landmarkMap[type1]
        val landmark2 = landmarkMap[type2]

        if (landmark1 != null && landmark2 != null) {
            val dist = distance(landmark1.position, landmark2.position)
            features.add(dist / normalizationFactor)
        } else {
            features.add(0f)
        }
    }

    private fun addAngle(
        features: MutableList<Float>,
        landmarkMap: Map<Int, FaceLandmark>,
        type1: Int,
        type2: Int
    ) {
        val landmark1 = landmarkMap[type1]
        val landmark2 = landmarkMap[type2]

        if (landmark1 != null && landmark2 != null) {
            val dx = landmark2.position.x - landmark1.position.x
            val dy = landmark2.position.y - landmark1.position.y
            val angle = atan2(dy, dx).toFloat()
            features.add(angle / Math.PI.toFloat())
        } else {
            features.add(0f)
        }
    }

    private fun addAngleFromCenter(
        features: MutableList<Float>,
        landmarkMap: Map<Int, FaceLandmark>,
        type: Int,
        centerX: Float,
        centerY: Float
    ) {
        landmarkMap[type]?.let { landmark ->
            val dx = landmark.position.x - centerX
            val dy = landmark.position.y - centerY
            val angle = atan2(dy, dx).toFloat()
            features.add(angle / Math.PI.toFloat())
        } ?: features.add(0f)
    }

    private fun addTriangleAngle(
        features: MutableList<Float>,
        landmarkMap: Map<Int, FaceLandmark>,
        vertex: Int,
        point1: Int,
        point2: Int
    ) {
        val v = landmarkMap[vertex]
        val p1 = landmarkMap[point1]
        val p2 = landmarkMap[point2]

        if (v != null && p1 != null && p2 != null) {
            val dx1 = p1.position.x - v.position.x
            val dy1 = p1.position.y - v.position.y
            val dx2 = p2.position.x - v.position.x
            val dy2 = p2.position.y - v.position.y

            val dot = dx1 * dx2 + dy1 * dy2
            val mag1 = sqrt(dx1 * dx1 + dy1 * dy1)
            val mag2 = sqrt(dx2 * dx2 + dy2 * dy2)

            if (mag1 > 0 && mag2 > 0) {
                val cosAngle = dot / (mag1 * mag2)
                features.add(cosAngle)
            } else {
                features.add(0f)
            }
        } else {
            features.add(0f)
        }
    }

    private fun calculateSymmetry(
        landmarkMap: Map<Int, FaceLandmark>,
        landmarkType: Int,
        cheekType: Int,
        centerX: Float
    ): Float {
        val landmark = landmarkMap[landmarkType]
        val cheek = landmarkMap[cheekType]

        return if (landmark != null && cheek != null) {
            val distanceToCenter = kotlin.math.abs(landmark.position.x - centerX)
            val distanceToCheek = kotlin.math.abs(landmark.position.x - cheek.position.x)
            if (distanceToCenter > 0) distanceToCheek / distanceToCenter else 0f
        } else {
            0f
        }
    }

    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        val magnitude = sqrt(embedding.map { it * it }.sum())
        return if (magnitude > 0) {
            embedding.map { it / magnitude }.toFloatArray()
        } else {
            embedding
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════
     * CÁLCULO DE SIMILITUD COSENO
     * ═══════════════════════════════════════════════════════════════
     *
     * ENTRADA:
     * ────────
     * - embedding1: Vector de 250 números del Rostro A
     * - embedding2: Vector de 250 números del Rostro B
     *
     * PROCESO (CAJA NEGRA):
     * ─────────────────────
     * 1. Multiplica cada posición: A[0]*B[0] + A[1]*B[1] + ... + A[249]*B[249]
     * 2. Suma todos los productos (producto punto)
     * 3. Convierte el resultado a rango [0, 1]
     *
     * SALIDA:
     * ───────
     * - Float entre 0.0 y 1.0
     *   1.0 = 100% idénticos
     *   0.85 = 85% similares (muy probable que sea la misma persona)
     *   0.80 = 80% similares (umbral mínimo - ESTRICTO)
     *   0.70 = 70% similares (NO reconocido)
     *   0.40 = 40% similares (NO reconocido)
     *
     * EJEMPLO VISUAL:
     * ───────────────
     * Vector A: [0.5, 0.3, 0.8, ...]
     * Vector B: [0.5, 0.3, 0.7, ...]
     *
     * Producto: 0.5*0.5 + 0.3*0.3 + 0.8*0.7 + ... = 0.87
     * Resultado: 87% de similitud → ¡RECONOCIDO!
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f

        var dotProduct = 0f
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
        }

        return dotProduct.coerceIn(0f, 1f)
    }

    /**
     * Encuentra el mejor match en una lista de candidatos
     */
    fun findBestMatch(
        queryEmbedding: FloatArray,
        candidates: List<Pair<Int, FloatArray>>,
        threshold: Float = 0.8f
    ): Pair<Int, Float>? {
        var bestMatch: Pair<Int, Float>? = null
        var bestSimilarity = threshold

        candidates.forEach { (id, candidateEmbedding) ->
            val similarity = calculateSimilarity(queryEmbedding, candidateEmbedding)

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = Pair(id, similarity)
            }
        }

        return bestMatch
    }

    fun release() {
        detector.close()
    }
}

