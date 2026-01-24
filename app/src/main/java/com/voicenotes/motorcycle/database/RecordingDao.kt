package com.voicenotes.motorcycle.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<Recording>>
    
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    suspend fun getAllRecordingsList(): List<Recording>
    
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordingsLiveData(): LiveData<List<Recording>>
    
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): Recording?
    
    @Query("SELECT * FROM recordings WHERE filepath = :filepath LIMIT 1")
    suspend fun getRecordingByFilepath(filepath: String): Recording?
    
    @Query("SELECT * FROM recordings WHERE v2sStatus = :status")
    suspend fun getRecordingsByV2SStatus(status: V2SStatus): List<Recording>
    
    @Query("SELECT * FROM recordings WHERE osmStatus = :status")
    suspend fun getRecordingsByOsmStatus(status: OsmStatus): List<Recording>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: Recording): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordings(recordings: List<Recording>)
    
    @Update
    suspend fun updateRecording(recording: Recording)
    
    @Delete
    suspend fun deleteRecording(recording: Recording)
    
    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
    
    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun getRecordingCount(): Int
    
    @Query("SELECT COUNT(*) FROM recordings WHERE v2sStatus = :status")
    suspend fun getRecordingCountByV2SStatus(status: V2SStatus): Int
    
    @Query("DELETE FROM recordings")
    suspend fun deleteAllRecordings()
}
