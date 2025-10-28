package com.example.reconocimientofacial.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room principal de la aplicación
 * Gestiona el almacenamiento local SQLite de rostros registrados
 *
 * Patrón Singleton: Solo existe una instancia en toda la aplicación
 *
 * @property faceDao Proporciona acceso a las operaciones de base de datos
 */
@Database(entities = [FaceEntity::class], version = 1, exportSchema = false)
abstract class FaceDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao

    companion object {
        @Volatile
        private var INSTANCE: FaceDatabase? = null

        /**
         * Obtiene la instancia única de la base de datos (Singleton)
         * Thread-safe: Usa synchronized para evitar múltiples instancias
         *
         * @param context Contexto de la aplicación
         * @return Instancia única de FaceDatabase
         */
        fun getDatabase(context: Context): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    "face_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
