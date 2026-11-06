package com.example.myapplication

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_samples",
    foreignKeys = [ForeignKey(
        entity = Student::class,
        parentColumns = ["id"],
        childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("studentId")]
)
data class FaceSample(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val embedding: ByteArray
)
