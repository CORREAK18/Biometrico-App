package com.example.reconocimientofacial.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.sqrt

/**
 * Procesador de reconocimiento facial usando Google ML Kit
 *
 * FUNCIONES PRINCIPALES:
 * 1. Detectar rostros en imágenes usando ML Kit Face Detection
 * 2. Extraer características faciales (embeddings) para comparación
 * 3. Calcular similitud entre rostros usando similitud coseno
 * 4. Encontrar coincidencias en una base de datos de rostros
 *
 * NOTA SOBRE EMBEDDINGS:
 * Esta implementación usa características geométricas (landmarks, ángulos, etc.)
 * Para producción profesional, se recomienda usar modelos de deep learning como:
 * - FaceNet con TensorFlow Lite
 * - MobileFaceNet
 * - ArcFace
 */
class FaceRecognitionProcessor {

    // Configuración del detector de rostros de ML Kit
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Modo preciso
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Detectar todos los landmarks
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Clasificar expresiones
        .setMinFaceSize(0.15f) // Tamaño mínimo del rostro (15% de la imagen)
        .enableTracking() // Rastrear rostros entre frames
        .build()

    private val detector = FaceDetection.getClient(options)

    /**
     * Detecta rostros en una imagen usando ML Kit
     *
     * @param bitmap Imagen donde buscar rostros
     * @return Lista de rostros detectados (vacía si no hay rostros)
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
     * Extrae un vector de características faciales (embedding) de un rostro
     *
     * Este embedding es una representación numérica del rostro que permite:
     * - Comparar rostros de forma cuantitativa
     * - Medir similitud usando álgebra lineal
     *
     * CARACTERÍSTICAS EXTRAÍDAS:
     * - Dimensiones del rectángulo del rostro (ancho, alto, posición)
     * - Posiciones de landmarks faciales (ojos, nariz, boca, orejas, mejillas)
     * - Ángulos de rotación de la cabeza (Euler X, Y, Z)
     * - Probabilidades de expresiones (sonrisa, ojos abiertos)
     *
     * El vector resultante se normaliza para facilitar comparaciones
     *
     * NOTA: Para producción, considera usar modelos pre-entrenados como FaceNet
     * que generan embeddings de 128 o 512 dimensiones con mayor precisión
     *
     * @param face Rostro detectado por ML Kit
     * @param bitmap Imagen original (puede usarse para extracción adicional)
     * @return Vector de características normalizado (FloatArray)
     */
    fun extractFaceEmbedding(face: Face, bitmap: Bitmap): FloatArray {
        val features = mutableListOf<Float>()

        // 1. CARACTERÍSTICAS GEOMÉTRICAS DEL ROSTRO
        // Dimensiones del bounding box
        face.boundingBox.let { box ->
            features.add(box.width().toFloat())
            features.add(box.height().toFloat())
            features.add(box.exactCenterX())
            features.add(box.exactCenterY())
        }

        // 2. LANDMARKS FACIALES (puntos característicos)
        // ML Kit detecta: ojos, nariz, boca, orejas, mejillas
        // Cada landmark tiene coordenadas X, Y
        face.allLandmarks.forEach { landmark ->
            features.add(landmark.position.x)
            features.add(landmark.position.y)
        }

        // 3. ÁNGULOS DE ROTACIÓN DE LA CABEZA (Euler angles)
        // headEulerAngleX: inclinación vertical (arriba/abajo)
        // headEulerAngleY: rotación horizontal (izquierda/derecha)
        // headEulerAngleZ: inclinación lateral
        face.headEulerAngleX.let { features.add(it) }
        face.headEulerAngleY.let { features.add(it) }
        face.headEulerAngleZ.let { features.add(it) }

        // 4. PROBABILIDADES DE EXPRESIONES FACIALES
        // Sonrisa y apertura de ojos (valores entre 0 y 1)
        face.smilingProbability?.let { features.add(it) } ?: features.add(0f)
        face.leftEyeOpenProbability?.let { features.add(it) } ?: features.add(0f)
        face.rightEyeOpenProbability?.let { features.add(it) } ?: features.add(0f)

        // Normalizar el vector para que tenga magnitud 1
        // Esto facilita la comparación usando similitud coseno
        return normalizeEmbedding(features.toFloatArray())
    }

    /**
     * Normaliza un vector de características a magnitud 1
     *
     * Fórmula: cada elemento se divide por la magnitud del vector
     * Magnitud = sqrt(suma de cuadrados de todos los elementos)
     *
     * Beneficio: Los vectores normalizados facilitan el cálculo de similitud coseno
     *
     * @param embedding Vector de características sin normalizar
     * @return Vector normalizado con magnitud 1
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        val magnitude = sqrt(embedding.map { it * it }.sum())
        return if (magnitude > 0) {
            embedding.map { it / magnitude }.toFloatArray()
        } else {
            embedding
        }
    }

    /**
     * Calcula la similitud coseno entre dos vectores de características
     *
     * SIMILITUD COSENO:
     * Mide el ángulo entre dos vectores en el espacio multidimensional
     * - Valor 1.0: Vectores idénticos (ángulo 0°)
     * - Valor 0.5: Vectores moderadamente similares (ángulo 60°)
     * - Valor 0.0: Vectores completamente diferentes (ángulo 90°)
     *
     * Fórmula: cos(θ) = (A · B) / (|A| × |B|)
     * Como nuestros vectores están normalizados (|A| = |B| = 1):
     * cos(θ) = A · B (producto punto)
     *
     * Rango de salida: [0, 1] donde 1 es similitud perfecta
     *
     * @param embedding1 Primer vector de características
     * @param embedding2 Segundo vector de características
     * @return Similitud entre 0.0 y 1.0 (1.0 = idéntico)
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f

        // Producto punto (dot product)
        var dotProduct = 0f
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
        }

        // Convertir de rango [-1, 1] a [0, 1]
        // (Aunque nuestros vectores normalizados deberían dar valores positivos)
        return (dotProduct + 1) / 2
    }

    /**
     * Encuentra el rostro más similar en una lista de candidatos
     *
     * PROCESO:
     * 1. Compara el embedding de consulta con cada candidato
     * 2. Calcula la similitud coseno para cada uno
     * 3. Retorna el candidato con mayor similitud
     * 4. Solo retorna si supera el umbral mínimo
     *
     * UMBRAL (threshold):
     * - Valor por defecto: 0.7 (70% de similitud mínima)
     * - En la app se usa 0.6 (60%) para ser más permisivo
     * - Valores más altos = más estricto (menos falsos positivos)
     * - Valores más bajos = más permisivo (más falsos positivos)
     *
     * @param queryEmbedding Vector de características del rostro a buscar
     * @param candidates Lista de pares (ID, embedding) de candidatos
     * @param threshold Umbral mínimo de similitud (por defecto 0.7)
     * @return Par (ID, similitud) del mejor match, o null si ninguno supera el umbral
     */
    fun findBestMatch(
        queryEmbedding: FloatArray,
        candidates: List<Pair<Int, FloatArray>>,
        threshold: Float = 0.7f
    ): Pair<Int, Float>? {
        var bestMatch: Pair<Int, Float>? = null
        var bestSimilarity = threshold // Iniciar con el umbral mínimo

        // Comparar con cada candidato
        candidates.forEach { (id, candidateEmbedding) ->
            val similarity = calculateSimilarity(queryEmbedding, candidateEmbedding)

            // Si es más similar que el mejor actual, actualizarlo
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = Pair(id, similarity)
            }
        }

        return bestMatch
    }

    /**
     * Libera los recursos del detector cuando ya no se necesita
     * Llamar en onCleared() del ViewModel
     */
    fun release() {
        detector.close()
    }
}
