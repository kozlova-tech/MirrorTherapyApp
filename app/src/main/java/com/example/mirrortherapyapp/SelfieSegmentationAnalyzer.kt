package com.example.mirrortherapyapp

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.SegmentationMask
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SelfieSegmentationAnalyzer(
    private val listener: (Bitmap?) -> Unit  // Callback to deliver the segmentation mask bitmap.
) : ImageAnalysis.Analyzer {

    // Configure segmentation options. STREAM_MODE is used for real-time processing.
    private val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        .build()

    // Get the segmentation client.
    private val segmenter = Segmentation.getClient(options)

    override fun analyze(imageProxy: ImageProxy) {
        val startTime = System.currentTimeMillis()
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            segmenter.process(image)
                .addOnSuccessListener { mask: SegmentationMask ->
                    val analysisTime = System.currentTimeMillis() - startTime
                    //Log.d("SegmentationTiming", "Segmentation analysis took $analysisTime ms")
                    // Now convert the segmentation mask to a Bitmap.
                    val conversionStartTime = System.currentTimeMillis()
                    val maskBitmap = convertSegmentationMaskToBitmap(mask)
                    val conversionTime = System.currentTimeMillis() - conversionStartTime
                    //Log.d("SegmentationTiming", "Conversion took $conversionTime ms")
                    listener(maskBitmap)
                }
                .addOnFailureListener { e ->
                    //Log.e("SegmentationTiming", "Segmentation failed", e)
                    listener(null)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }


    /**
     * Converts a SegmentationMask into a Bitmap.
     *
     * The mask’s buffer contains float values (0.0 to 1.0) for each pixel.
     * This function converts each confidence value to an alpha value in a white ARGB pixel.
     */
    private fun convertSegmentationMaskToBitmap(mask: SegmentationMask, threshold: Float = 0.99f): Bitmap {
        val width = mask.width
        val height = mask.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Get the confidence buffer from the mask.
        val buffer: ByteBuffer = mask.buffer
        buffer.order(ByteOrder.nativeOrder())
        val floatBuffer = buffer.asFloatBuffer()

        val numPixels = width * height
        val pixels = IntArray(numPixels)
        for (i in 0 until numPixels) {
            val confidence = floatBuffer.get(i)
            // If the confidence is below the threshold, treat it as background (alpha = 0).
            // Otherwise, set the pixel fully opaque (alpha = 255).
            val alpha = if (confidence < threshold) 0 else 255
            // Create a white pixel with the computed alpha.
            pixels[i] = (alpha shl 24) or 0x00FFFFFF
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }


}
