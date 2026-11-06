package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FaceSampleDao {
    @Insert
    suspend fun insert(sample: FaceSample)

    @Query("SELECT * FROM face_samples WHERE studentId = :studentId ORDER BY id")
    suspend fun getForStudent(studentId: Int): List<FaceSample>

    @Query("SELECT * FROM face_samples ORDER BY studentId, id")
    suspend fun getAll(): List<FaceSample>
}
