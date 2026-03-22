package com.piscine.timer.presentation.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Génère un QR code Bitmap à partir d'un texte.
 *
 * @param content  Texte à encoder
 * @param sizePx   Taille en pixels du QR code
 * @return Bitmap noir/blanc du QR code, ou null si erreur
 */
fun generateQrBitmap(content: String, sizePx: Int = 300): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,           // marges minimales pour maximiser la taille
            EncodeHintType.ERROR_CORRECTION to "M" // correction d'erreur niveau M (~15%)
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * Composable affichant un QR code avec fond blanc (nécessaire pour la lisibilité).
 */
@Composable
fun QrCodeImage(
    content: String,
    size: Dp = 180.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(content) { generateQrBitmap(content, sizePx = 400) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code résultats",
            modifier = modifier
                .size(size)
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(4.dp)
        )
    }
}
