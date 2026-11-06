package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun mark(record: AttendanceRecord): Long
}
