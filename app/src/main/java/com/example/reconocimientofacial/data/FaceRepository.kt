package com.example.reconocimientofacial.data

import kotlinx.coroutines.flow.Flow

/**
 * Repositorio que actúa como intermediario entre el ViewModel y la base de datos
 * Abstrae el acceso a datos y proporciona una API limpia para el ViewModel
 *
 * Patrón Repository: Separa la lógica de negocio de la lógica de acceso a datos
 */
class FaceRepository(private val faceDao: FaceDao) {

    /**
     * Flow observable con todos los rostros registrados
     * Se actualiza automáticamente cuando hay cambios en la base de datos
     */
    val allFaces: Flow<List<FaceEntity>> = faceDao.getAllFaces()

    /**
     * Inserta un nuevo rostro en la base de datos
     * @param face Entidad del rostro a insertar
     * @return ID del registro insertado
     */
    suspend fun insertFace(face: FaceEntity): Long {
        return faceDao.insertFace(face)
    }

    /**
     * Busca un rostro por DNI
     * Utilizado para validar que no existan DNIs duplicados
     * @param dni DNI a buscar
     * @return FaceEntity si existe, null si no existe
     */
    suspend fun getFaceByDni(dni: String): FaceEntity? {
        return faceDao.getFaceByDni(dni)
    }

    /**
     * Busca un rostro por su ID único
     * @param id ID del rostro
     * @return FaceEntity si existe, null si no existe
     */
    suspend fun getFaceById(id: Int): FaceEntity? {
        return faceDao.getFaceById(id)
    }

    /**
     * Elimina un rostro de la base de datos
     * @param id ID del rostro a eliminar
     */
    suspend fun deleteFaceById(id: Int) {
        faceDao.deleteFaceById(id)
    }

    /**
     * Obtiene el número total de rostros registrados
     * @return Cantidad de registros
     */
    suspend fun getFaceCount(): Int {
        return faceDao.getFaceCount()
    }
}
