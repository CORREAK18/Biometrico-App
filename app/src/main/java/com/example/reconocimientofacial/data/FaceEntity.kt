package com.example.reconocimientofacial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de base de datos Room que representa un rostro registrado
 *
 * Almacena:
 * - Información personal (DNI, nombre)
 * - Imagen del rostro comprimida en formato JPEG
 * - Vector de características faciales (embedding) para reconocimiento
 * - Timestamp de registro
 *
 * @property id Identificador único autogenerado
 * @property dni Documento de identidad (debe ser único)
 * @property nombre Nombre completo de la persona
 * @property faceImage Imagen del rostro convertida a ByteArray (JPEG comprimido)
 * @property faceEmbedding Vector de características faciales para comparación
 * @property timestamp Fecha y hora de registro en milisegundos
 */
@Entity(tableName = "faces")
data class FaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val dni: String,
    val nombre: String,
    val faceImage: ByteArray,
    val faceEmbedding: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    // Sobrescribir equals para comparar correctamente los ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceEntity

        if (id != other.id) return false
        if (dni != other.dni) return false
        if (nombre != other.nombre) return false
        if (!faceImage.contentEquals(other.faceImage)) return false
        if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false

        return true
    }

    // Sobrescribir hashCode para consistencia con equals
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + dni.hashCode()
        result = 31 * result + nombre.hashCode()
        result = 31 * result + faceImage.contentHashCode()
        result = 31 * result + faceEmbedding.contentHashCode()
        return result
    }
}
