package com.example.reconocimientofacial.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reconocimientofacial.data.FaceDatabase
import com.example.reconocimientofacial.data.FaceEntity
import com.example.reconocimientofacial.data.FaceRepository
import com.example.reconocimientofacial.ml.FaceRecognitionProcessorMejorado
import com.example.reconocimientofacial.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resultado del proceso de reconocimiento facial
 * @param found Indica si se encontró un rostro coincidente
 * @param name Nombre de la persona reconocida
 * @param dni DNI de la persona reconocida
 * @param similarity Nivel de similitud (0.0 a 1.0), donde 1.0 es idéntico
 * @param message Mensaje descriptivo del resultado
 */
data class RecognitionResult(
    val found: Boolean,
    val name: String = "",
    val dni: String = "",
    val similarity: Float = 0f,
    val message: String = ""
)

/**
 * ViewModel principal para gestionar el reconocimiento y registro facial
 * Maneja la lógica de negocio entre la UI y la capa de datos
 */
class FaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FaceRepository
    private val faceProcessor = FaceRecognitionProcessorMejorado() // ← CAMBIADO: Usar procesador mejorado

    // Umbral mínimo de confianza para considerar un rostro como reconocido
    // Valores: 0.0 a 1.0, donde 1.0 es una coincidencia perfecta
    // 0.80 = 80% de confianza mínima para aceptar el reconocimiento (MÁS ESTRICTO)
    companion object {
        const val RECOGNITION_THRESHOLD = 0.80f // ← CAMBIADO: Aumentado a 80% para ser más estricto
        const val DUPLICATE_THRESHOLD = 0.90f   // ← CAMBIADO: Aumentado a 90% para duplicados
    }

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _recognitionResult = MutableStateFlow<RecognitionResult?>(null)
    val recognitionResult: StateFlow<RecognitionResult?> = _recognitionResult.asStateFlow()

    private val _allFaces = MutableStateFlow<List<FaceEntity>>(emptyList())
    val allFaces: StateFlow<List<FaceEntity>> = _allFaces.asStateFlow()

    init {
        val faceDao = FaceDatabase.getDatabase(application).faceDao()
        repository = FaceRepository(faceDao)

        viewModelScope.launch {
            repository.allFaces.collect { faces ->
                _allFaces.value = faces
            }
        }
    }

    /**
     * Registra un nuevo rostro en la base de datos
     * Valida que:
     * - Solo haya un rostro en la imagen
     *
     * @param bitmap Imagen capturada del rostro
     * @param dni Documento de identidad
     * @param nombre Nombre completo de la persona
     */
    fun registerFace(bitmap: Bitmap, dni: String, nombre: String) {
        viewModelScope.launch {
            try {
                _registrationState.value = RegistrationState.Processing

                val result = withContext(Dispatchers.Default) {
                    // VALIDACIÓN: Detectar rostro en la imagen
                    val faces = faceProcessor.detectFaces(bitmap)

                    if (faces.isEmpty()) {
                        return@withContext RegistrationState.Error("No se detectó ningún rostro en la imagen")
                    }

                    if (faces.size > 1) {
                        return@withContext RegistrationState.Error(
                            "Se detectaron múltiples rostros. Por favor, asegúrate de que solo haya una persona"
                        )
                    }

                    val face = faces.first()

                    // Extraer características faciales (embedding)
                    val embedding = faceProcessor.extractFaceEmbedding(face, bitmap)

                    // Convertir imagen a bytes para almacenamiento
                    val scaledBitmap = ImageUtils.scaleBitmap(bitmap)
                    val imageBytes = ImageUtils.bitmapToByteArray(scaledBitmap)
                    val embeddingBytes = ImageUtils.floatArrayToByteArray(embedding)

                    // Guardar en base de datos SQLite
                    val faceEntity = FaceEntity(
                        dni = dni,
                        nombre = nombre,
                        faceImage = imageBytes,
                        faceEmbedding = embeddingBytes
                    )

                    repository.insertFace(faceEntity)

                    RegistrationState.Success("✓ Rostro registrado exitosamente para $nombre")
                }

                _registrationState.value = result

            } catch (e: Exception) {
                _registrationState.value = RegistrationState.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * Reconoce un rostro comparándolo con los registrados en la base de datos
     *
     * Proceso:
     * 1. Detecta el rostro en la imagen
     * 2. Extrae las características faciales (embedding)
     * 3. Compara con todos los rostros registrados usando similitud coseno
     * 4. Si la similitud supera el umbral (60%), se considera reconocido
     *
     * UMBRAL DE CONFIANZA:
     * - Mínimo: 60% (0.6) - definido en RECOGNITION_THRESHOLD
     * - Si la similitud es menor a 60%, el rostro no se reconoce
     * - La similitud se calcula usando similitud coseno entre vectores de características
     * - Rango: 0% (completamente diferente) a 100% (idéntico)
     *
     * @param bitmap Imagen capturada del rostro a reconocer
     */
    fun recognizeFace(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    // Detectar rostro en la imagen
                    val faces = faceProcessor.detectFaces(bitmap)

                    if (faces.isEmpty()) {
                        return@withContext RecognitionResult(
                            found = false,
                            message = "No se detectó ningún rostro en la imagen"
                        )
                    }

                    if (faces.size > 1) {
                        return@withContext RecognitionResult(
                            found = false,
                            message = "Se detectaron múltiples rostros. Por favor, capture solo una persona"
                        )
                    }

                    val face = faces.first()

                    // Extraer embedding (características faciales)
                    val queryEmbedding = faceProcessor.extractFaceEmbedding(face, bitmap)

                    // Obtener todos los rostros registrados
                    val allFacesData = _allFaces.value

                    if (allFacesData.isEmpty()) {
                        return@withContext RecognitionResult(
                            found = false,
                            message = "No hay rostros registrados en la base de datos. Registre al menos uno."
                        )
                    }

                    // Crear lista de candidatos con sus embeddings
                    val candidates = allFacesData.map { faceEntity ->
                        val embedding = ImageUtils.byteArrayToFloatArray(faceEntity.faceEmbedding)
                        Pair(faceEntity.id, embedding)
                    }

                    // Buscar mejor coincidencia usando el umbral de 85%
                    val bestMatch = faceProcessor.findBestMatch(
                        queryEmbedding,
                        candidates,
                        threshold = RECOGNITION_THRESHOLD
                    )

                    if (bestMatch != null) {
                        val matchedFace = allFacesData.find { it.id == bestMatch.first }
                        if (matchedFace != null) {
                            val confidencePercent = (bestMatch.second * 100).toInt()
                            RecognitionResult(
                                found = true,
                                name = matchedFace.nombre,
                                dni = matchedFace.dni,
                                similarity = bestMatch.second,
                                message = "✓ Rostro reconocido con $confidencePercent% de confianza"
                            )
                        } else {
                            RecognitionResult(found = false, message = "✗ Rostro no reconocido")
                        }
                    } else {
                        RecognitionResult(
                            found = false,
                            message = "✗ Rostro no reconocido. La similitud es inferior al 80% requerido"
                        )
                    }
                }

                _recognitionResult.value = result

            } catch (e: Exception) {
                _recognitionResult.value = RecognitionResult(
                    found = false,
                    message = "Error al procesar: ${e.message}"
                )
            }
        }
    }

    /**
     * Elimina un rostro de la base de datos
     * @param id ID del rostro a eliminar
     */
    fun deleteFace(id: Int) {
        viewModelScope.launch {
            repository.deleteFaceById(id)
        }
    }

    /**
     * Reinicia el estado de registro a Idle
     */
    fun resetRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    /**
     * Limpia el resultado del reconocimiento
     */
    fun resetRecognitionResult() {
        _recognitionResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        faceProcessor.release()
    }
}

/**
 * Estados posibles del proceso de registro facial
 */
sealed class RegistrationState {
    object Idle : RegistrationState()
    object Processing : RegistrationState()
    data class Success(val message: String) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
    data class AlreadyExists(val message: String) : RegistrationState()
}
