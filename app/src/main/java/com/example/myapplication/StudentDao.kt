package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StudentDao {
    @Insert
    suspend fun insert(student: Student): Long

    @Query("SELECT * FROM students ORDER BY name")
    suspend fun getAll(): List<Student>
}