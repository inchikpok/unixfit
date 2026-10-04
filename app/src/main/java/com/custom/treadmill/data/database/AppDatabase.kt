package com.custom.treadmill.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "programs")
data class Program(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val description: String, val segmentsJson: String)

@Dao interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY name") fun observeAll(): Flow<List<Program>>
    @Insert suspend fun insert(program: Program): Long
    @Delete suspend fun delete(program: Program)
}

@Database(entities = [Program::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun programDao(): ProgramDao
    companion object { fun create(context: android.content.Context) = Room.databaseBuilder(context, AppDatabase::class.java, "unixfit.db").build() }
}
