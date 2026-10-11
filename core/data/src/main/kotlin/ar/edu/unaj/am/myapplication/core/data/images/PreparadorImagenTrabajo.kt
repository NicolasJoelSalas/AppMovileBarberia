package ar.edu.unaj.am.myapplication.core.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Limita memoria, corrige orientación y exporta todas las fotos como JPEG cuadrado. */
class PreparadorImagenTrabajo(private val context: Context) {
    fun preparar(local: String, x: Float, y: Float): ByteArray {
        val uri = Uri.parse(local)
        require(uri.scheme == "content") { "Elegí una imagen desde la cámara o la galería." }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La imagen no se pudo leer." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val original = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("La foto ya no está disponible. Elegila nuevamente.")
        try {
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val oriented = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            try {
                val side = min(oriented.width, oriented.height)
                val left = (((x + 1f) / 2f) * (oriented.width - side)).toInt().coerceIn(0, oriented.width - side)
                val top = (((y + 1f) / 2f) * (oriented.height - side)).toInt().coerceIn(0, oriented.height - side)
                val square = Bitmap.createBitmap(oriented, left, top, side, side)
                try {
                    val output = Bitmap.createScaledBitmap(square, 1080, 1080, true)
                    try {
                        return ByteArrayOutputStream().use {
                            check(output.compress(Bitmap.CompressFormat.JPEG, 85, it))
                            it.toByteArray()
                        }
                    } finally { if (output !== square) output.recycle() }
                } finally { if (square !== oriented) square.recycle() }
            } finally { if (oriented !== original) oriented.recycle() }
        } finally { original.recycle() }
    }
}
