package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

internal class FaceEmbeddingModel(context: Context) : AutoCloseable {
    private val interpreter = Interpreter(context.assets.open("mobile_facenet.tflite").use {
        val model = it.readBytes()
        ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder()).apply {
            put(model)
            rewind()
        }
    })

    fun embed(face: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(face, 112, 112, true)
        val input = Array(1) { Array(3) { Array(112) { FloatArray(112) } } }
        for (y in 0 until 112) for (x in 0 until 112) {
            val pixel = resized.getPixel(x, y)
            input[0][0][y][x] = Color.red(pixel) / 255f
            input[0][1][y][x] = Color.green(pixel) / 255f
            input[0][2][y][x] = Color.blue(pixel) / 255f
        }

        val output = Array(2) { FloatArray(128) }
        interpreter.runForMultipleInputsOutputs(arrayOf(input, input), mapOf(0 to output))
        val norm = sqrt(output[0].sumOf { (it * it).toDouble() }).toFloat()
        return if (norm == 0f) output[0] else FloatArray(128) { output[0][it] / norm }
    }

    override fun close() = interpreter.close()
}
