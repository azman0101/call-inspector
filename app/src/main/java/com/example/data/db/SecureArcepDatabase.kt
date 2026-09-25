package com.example.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "operators")
data class OperatorEntity(
    @PrimaryKey val code: String,
    val name: String,
    val siret: String?,
    val rcs: String?,
    val address: String?,
    @ColumnInfo(name = "declaration_date") val declarationDate: String?
)

@Entity(
    tableName = "number_ranges",
    indices = [Index(value = ["tranche_debut", "tranche_fin"]), Index(value = ["ezabpqm"]), Index(value = ["operator_code"])]
)
data class NumberRangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ezabpqm: String,
    @ColumnInfo(name = "tranche_debut") val trancheDebut: String,
    @ColumnInfo(name = "tranche_fin") val trancheFin: String,
    @ColumnInfo(name = "operator_code") val operatorCode: String,
    @ColumnInfo(name = "operator_name") val operatorName: String,
    val territory: String?,
    @ColumnInfo(name = "attribution_date") val attributionDate: String?
)

@Entity(tableName = "call_notes")
data class CallNoteEntity(
    @PrimaryKey @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_spam", defaultValue = "0") val isSpam: Boolean = false,
    @ColumnInfo(name = "user_tag") val userTag: String?,
    @ColumnInfo(name = "user_note") val userNote: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?
)

@Entity(tableName = "arcep_metadata")
data class ArcepMetadataEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface CallNoteDao {
    @Query("SELECT * FROM call_notes WHERE phone_number = :phoneNumber LIMIT 1")
    fun find(phoneNumber: String): CallNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(note: CallNoteEntity)
}

@Database(
    entities = [OperatorEntity::class, NumberRangeEntity::class, CallNoteEntity::class, ArcepMetadataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SecureArcepDatabase : RoomDatabase() {
    abstract fun callNoteDao(): CallNoteDao
}
