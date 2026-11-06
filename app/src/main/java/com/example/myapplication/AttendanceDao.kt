package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class AttendanceStatus(
    val studentId: Int,
    val name: String,
    val className: String,
    val markedAt: Long?
)

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun mark(record: AttendanceRecord): Long

    @Query("SELECT students.id AS studentId, students.name, students.className, attendance_records.markedAt FROM students LEFT JOIN attendance_records ON students.id = attendance_records.studentId AND attendance_records.date = :date ORDER BY students.name")
    suspend fun getForDate(date: String): List<AttendanceStatus>

    @Query("DELETE FROM attendance_records WHERE studentId = :studentId AND date = :date")
    suspend fun remove(studentId: Int, date: String)
}
