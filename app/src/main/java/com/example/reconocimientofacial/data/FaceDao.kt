package com.example.reconocimientofacial.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) para operaciones de base de datos con rostros
 * Define las consultas SQL para interactuar con la tabla 'faces'
 */
@Dao
interface FaceDao {
    /**
     * Inserta un nuevo rostro en la base de datos
     * @param face Entidad con los datos del rostro
     * @return ID del registro insertado
     */
    @Insert
    suspend fun insertFace(face: FaceEntity): Long

    /**
     * Obtiene todos los rostros registrados como Flow (observable)
     * Se actualiza automáticamente cuando hay cambios en la base de datos
     * @return Flow con lista de todos los rostros
     */
    @Query("SELECT * FROM faces")
    fun getAllFaces(): Flow<List<FaceEntity>>

    /**
     * Busca un rostro por DNI (útil para validar duplicados)
     * @param dni DNI a buscar
     * @return FaceEntity si existe, null si no
     */
    @Query("SELECT * FROM faces WHERE dni = :dni")
    suspend fun getFaceByDni(dni: String): FaceEntity?

    /**
     * Busca un rostro por su ID único
     * @param id ID del rostro
     * @return FaceEntity si existe, null si no
     */
    @Query("SELECT * FROM faces WHERE id = :id")
    suspend fun getFaceById(id: Int): FaceEntity?

    /**
     * Elimina un rostro por su ID
     * @param id ID del rostro a eliminar
     */
    @Query("DELETE FROM faces WHERE id = :id")
    suspend fun deleteFaceById(id: Int)

    /**
     * Cuenta el número total de rostros registrados
     * @return Cantidad de registros en la base de datos
     */
    @Query("SELECT COUNT(*) FROM faces")
    suspend fun getFaceCount(): Int
}
