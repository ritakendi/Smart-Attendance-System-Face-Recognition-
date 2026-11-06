package com.example.myapplication

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    foreignKeys = [ForeignKey(
        entity = Student::class,
        parentColumns = ["id"],
        childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["studentId", "date"], unique = true)]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val date: String,
    val markedAt: Long
)
