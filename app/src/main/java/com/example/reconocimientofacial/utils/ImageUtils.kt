package com.example.reconocimientofacial.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

/**
 * Utilidades para conversión y manipulación de imágenes
 *
 * FUNCIONES PRINCIPALES:
 * 1. Convertir entre Bitmap y ByteArray para almacenamiento en base de datos
 * 2. Rotar imágenes (útil para corregir orientación de cámara)
 * 3. Escalar imágenes para reducir tamaño de almacenamiento
 * 4. Convertir entre FloatArray y ByteArray para embeddings faciales
 */
object ImageUtils {

    /**
     * Convierte un Bitmap a ByteArray para almacenar en la base de datos
     *
     * Comprime la imagen en formato JPEG con calidad ajustable
     * - Calidad 100 = sin pérdida (archivo más grande)
     * - Calidad 80 = buena calidad con compresión moderada (recomendado)
     * - Calidad 50 = compresión alta (archivo pequeño, pérdida visible)
     *
     * @param bitmap Imagen a convertir
     * @param quality Calidad JPEG (0-100, por defecto 80)
     * @return ByteArray con la imagen comprimida
     */
    fun bitmapToByteArray(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Convierte un ByteArray a Bitmap para mostrar en la UI
     *
     * @param byteArray Datos de imagen almacenados
     * @return Bitmap decodificado
     */
    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    /**
     * Rota un Bitmap por un número específico de grados
     *
     * Útil para corregir la orientación de imágenes de cámara
     * que a veces capturan rotadas 90, 180 o 270 grados
     *
     * @param bitmap Imagen a rotar
     * @param degrees Grados de rotación (ej: 90, 180, 270)
     * @return Nuevo Bitmap rotado
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Escala un Bitmap a un tamaño máximo especificado
     *
     * Reduce el tamaño de la imagen manteniendo la proporción (aspect ratio)
     * Beneficios:
     * - Reduce espacio en base de datos
     * - Acelera el procesamiento de ML Kit
     * - Reduce uso de memoria
     *
     * Si la imagen ya es más pequeña que el máximo, se retorna sin cambios
     *
     * @param bitmap Imagen a escalar
     * @param maxWidth Ancho máximo en píxeles (por defecto 800)
     * @param maxHeight Alto máximo en píxeles (por defecto 800)
     * @return Bitmap escalado o el original si ya es pequeño
     */
    fun scaleBitmap(bitmap: Bitmap, maxWidth: Int = 800, maxHeight: Int = 800): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Calcular factor de escala (el menor entre ancho y alto)
        val scale = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )

        // Si scale >= 1, la imagen ya es pequeña, no escalar
        if (scale >= 1) return bitmap

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Convierte un FloatArray a ByteArray para almacenamiento
     *
     * Cada Float (4 bytes) se convierte a 4 bytes individuales
     * Usado para guardar embeddings faciales en la base de datos
     *
     * Proceso:
     * 1. Convertir Float a bits (32 bits / 4 bytes)
     * 2. Separar en 4 bytes individuales usando operaciones bit a bit
     *
     * @param floatArray Vector de características faciales
     * @return ByteArray con los datos serializados
     */
    fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val byteArray = ByteArray(floatArray.size * 4)
        var index = 0
        for (value in floatArray) {
            val bits = value.toBits() // Convertir Float a Int (bits)
            // Extraer los 4 bytes del Int
            byteArray[index++] = (bits shr 24).toByte() // Byte más significativo
            byteArray[index++] = (bits shr 16).toByte()
            byteArray[index++] = (bits shr 8).toByte()
            byteArray[index++] = bits.toByte()         // Byte menos significativo
        }
        return byteArray
    }

    /**
     * Convierte un ByteArray a FloatArray para uso en reconocimiento
     *
     * Proceso inverso de floatArrayToByteArray
     * Cada 4 bytes se reconstruyen en un Float
     *
     * Usado para recuperar embeddings faciales de la base de datos
     * y realizar comparaciones de similitud
     *
     * @param byteArray Datos almacenados del embedding
     * @return FloatArray con el vector de características
     */
    fun byteArrayToFloatArray(byteArray: ByteArray): FloatArray {
        val floatArray = FloatArray(byteArray.size / 4)
        var index = 0
        for (i in floatArray.indices) {
            // Reconstruir el Int de 32 bits desde 4 bytes
            val bits = ((byteArray[index++].toInt() and 0xFF) shl 24) or
                      ((byteArray[index++].toInt() and 0xFF) shl 16) or
                      ((byteArray[index++].toInt() and 0xFF) shl 8) or
                      (byteArray[index++].toInt() and 0xFF)
            floatArray[i] = Float.fromBits(bits) // Convertir bits a Float
        }
        return floatArray
    }
}
