package com.ersinozdogan.ustalikeserimv.ui.camera

import android.app.Application
import android.graphics.*
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.*

/** Nano model çıktılarını ve orijinal koordinatları tutar */
private data class Detection(
    val preview: BoundingBox,
    val rawLeft: Int,
    val rawTop: Int,
    val rawWidth: Int,
    val rawHeight: Int
)

/** Nano tarafından bulunan kutu (preview koordinatlarında) */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val score: Float = 0f
)

/** YOLO tarafından bulunan kutu + açı (crop içindeki koordinatlar) */
data class BoundingBoxWithAngle(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val angle: Float
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val INPUT_SIZE     = 224
        private const val CONF_THRESHOLD = 0.6f
    }

    private val interpreterNano by lazy { loadInterpreter("nano224.tflite") }
    private val interpreterYolo by lazy { loadInterpreter("yolov_meter.tflite") }

    private val _boxes = MutableStateFlow<List<BoundingBox>>(emptyList())
    val boxes: StateFlow<List<BoundingBox>> = _boxes

    private val _indexBoxes = MutableStateFlow<List<BoundingBoxWithAngle>>(emptyList())
    val indexBoxes: StateFlow<List<BoundingBoxWithAngle>> = _indexBoxes

    /**
     * Kameradan gelen her kare için nano→yolo pipeline’ını çalıştırır
     */
    fun getAnalyzer(pv: PreviewView): ImageAnalysis.Analyzer =
        ImageAnalysis.Analyzer { imageProxy ->
            val rawBmp     = imageProxyToBitmap(imageProxy)
            val rotatedBmp = rawBmp.rotate(imageProxy.imageInfo.rotationDegrees)

            viewModelScope.launch(Dispatchers.Default) {
                // 1) Sayaç gövdesini tespit et (nano)
                val detections = runNano(rotatedBmp, pv.width, pv.height)
                _boxes.value = detections.map { it.preview }

                // 2) Eğer sayaç bulunduysa, kırp & yolo çalıştır & mapToPreview ile orantıla
                val idx = detections.firstOrNull()?.let { det ->
                    val crop = rotatedBmp.cropWithMargin(det, margin = 0.2f)
                    runYolo(crop)?.let { yoloBox ->
                        mapToPreview(yoloBox, det)
                    }
                }
                _indexBoxes.value = idx?.let { listOf(it) } ?: emptyList()
                imageProxy.close()
            }
        }

    /** Nano inference + parse + tek en iyi detection */
    private fun runNano(bmp: Bitmap, viewW: Int, viewH: Int): List<Detection> {
        val square = max(bmp.width, bmp.height)
        val padded = bmp.padToSquare(square)
        val scaled = Bitmap.createScaledBitmap(padded, INPUT_SIZE, INPUT_SIZE, true)
        val input  = scaled.toByteBuffer()
        val output = Array(1) { Array(7) { FloatArray(1029) } }
        interpreterNano.run(input, output)

        return parseNanoOutput(
            preds  = output[0],
            viewW  = viewW, viewH = viewH,
            origW  = bmp.width, origH = bmp.height,
            square = square,
            padX   = (square - bmp.width ) / 2f,
            padY   = (square - bmp.height) / 2f
        )
    }

    /** YOLO inference → en yüksek skorlu kutu + açı */
    private fun runYolo(crop: Bitmap): BoundingBoxWithAngle? {
        val inp  = Bitmap.createScaledBitmap(crop, INPUT_SIZE, INPUT_SIZE, true)
        val buf  = inp.toByteBuffer()
        val outY = Array(1) { Array(6) { FloatArray(1029) } }
        interpreterYolo.run(buf, outY)

        val cxArr    = outY[0][0]
        val cyArr    = outY[0][1]
        val wArr     = outY[0][2]
        val hArr     = outY[0][3]
        val scoreArr = outY[0][4]
        val angleArr = outY[0][5]

        val best = scoreArr.indices
            .filter { scoreArr[it] >= CONF_THRESHOLD }
            .maxByOrNull { scoreArr[it] }
            ?: return null

        val cxPx = cxArr[best] * crop.width
        val cyPx = cyArr[best] * crop.height
        val wPx  = wArr[best]  * crop.width
        val hPx  = hArr[best]  * crop.height
        val ang  = angleArr[best]

        return BoundingBoxWithAngle(
            left   = cxPx - wPx/2f,
            top    = cyPx - hPx/2f,
            width  = wPx,
            height = hPx,
            angle  = ang
        )
    }

    /**
     * YOLO’dan çıkan kutuyu, nano’dan gelen preview (kırmızı kutu)
     * içine ölçekli ve clamp yapılmış şekilde yerleştirir.
     */
    private fun mapToPreview(
        box: BoundingBoxWithAngle,
        det: Detection
    ): BoundingBoxWithAngle {
        val red = det.preview
        val scaleX = red.width  / det.rawWidth
        val scaleY = red.height / det.rawHeight

        // Crop içindeki YOLO koordinatlarını, preview (kırmızı) içine ölçekle
        var left   = red.left + box.left   * scaleX
        var top    = red.top  + box.top    * scaleY
        val width  = box.width  * scaleX
        val height = box.height * scaleY

        // Clamp: taşmayı önle
        left = left.coerceIn(red.left,               red.left + red.width  - width)
        top  = top.coerceIn(red.top,                 red.top  + red.height - height)

        return BoundingBoxWithAngle(left, top, width, height, box.angle)
    }

    /** Nano çıktısını parse edip tek en iyi Detection’ı döner */
    private fun parseNanoOutput(
        preds: Array<FloatArray>,
        viewW: Int, viewH: Int,
        origW: Int, origH: Int,
        square: Int,
        padX: Float, padY: Float
    ): List<Detection> {
        val dets = mutableListOf<Detection>()
        val cxArr = preds[0]; val cyArr = preds[1]
        val wArr  = preds[2]; val hArr  = preds[3]
        val sArr  = preds[4]

        // Görüntüleri ekrana ölçeklemek için
        val ratioX = viewW.toFloat() / origW
        val ratioY = viewH.toFloat() / origH
        val scale  = min(ratioX, ratioY)
        val offX   = (viewW - origW * scale) / 2f
        val offY   = (viewH - origH * scale) / 2f

        for (i in sArr.indices) {
            val score = sArr[i]
            if (score < CONF_THRESHOLD) continue

            val cx = cxArr[i] * square
            val cy = cyArr[i] * square
            val w  = wArr[i]  * square
            val h  = hArr[i]  * square

            val lx = (cx - padX - w/2f).coerceAtLeast(0f)
            val ty = (cy - padY - h/2f).coerceAtLeast(0f)
            val rw = min(w.toInt(), origW  - lx.toInt())
            val rh = min(h.toInt(), origH  - ty.toInt())

            val px = offX + lx * scale
            val py = offY + ty * scale

            dets += Detection(
                preview   = BoundingBox(px, py, rw*scale, rh*scale, score),
                rawLeft   = lx.toInt(),
                rawTop    = ty.toInt(),
                rawWidth  = rw,
                rawHeight = rh
            )
        }

        // NMS → tek en yüksek
        return dets
            .sortedByDescending { it.preview.score }
            .fold(mutableListOf<Detection>()) { kept, d ->
                if (kept.none { iou(it.preview, d.preview) > 0.5f }) kept.add(d)
                kept
            }
            .take(1)
    }

    /** YUV→JPEG→Bitmap */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer
        val ySize  = yPlane.remaining()
        val uSize  = uPlane.remaining()
        val vSize  = vPlane.remaining()
        val nv21   = ByteArray(ySize + uSize + vSize)
        yPlane.get(nv21, 0, ySize)
        vPlane.get(nv21, ySize, vSize)
        uPlane.get(nv21, ySize + vSize, uSize)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0,0,image.width,image.height),100,out)
        return BitmapFactory.decodeByteArray(out.toByteArray(),0,out.size())
    }

    /** Asset içinden mmap → Interpreter */
    private fun loadInterpreter(assetName: String): Interpreter {
        val fd     = getApplication<Application>().assets.openFd(assetName)
        val stream = FileInputStream(fd.fileDescriptor)
        val buf    = stream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset, fd.declaredLength
        )
        return Interpreter(buf)
    }

    /** Bitmap yardımcıları */
    private fun Bitmap.rotate(deg: Int): Bitmap =
        if (deg == 0) this else {
            val m = Matrix().apply { postRotate(deg.toFloat()) }
            Bitmap.createBitmap(this, 0, 0, width, height, m, true)
        }

    private fun Bitmap.padToSquare(size: Int): Bitmap {
        val padded = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        padded.eraseColor(Color.BLACK)
        Canvas(padded).drawBitmap(
            this,
            (size - width)/2f,
            (size - height)/2f,
            null
        )
        return padded
    }

    private fun Bitmap.cropWithMargin(det: Detection, margin: Float): Bitmap {
        val mx    = (det.rawWidth  * margin).toInt()
        val my    = (det.rawHeight * margin).toInt()
        val left  = (det.rawLeft   - mx).coerceAtLeast(0)
        val top   = (det.rawTop    - my).coerceAtLeast(0)
        val right = (det.rawLeft + det.rawWidth  + mx).coerceAtMost(width)
        val bot   = (det.rawTop  + det.rawHeight + my).coerceAtMost(height)
        return Bitmap.createBitmap(this, left, top, right-left, bot-top)
    }

    private fun Bitmap.toByteBuffer(): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        val px  = IntArray(INPUT_SIZE * INPUT_SIZE)
        this.getPixels(px, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        px.forEach { p ->
            buf.putFloat(((p shr 16) and 0xFF) / 255f)
            buf.putFloat(((p shr  8) and 0xFF) / 255f)
            buf.putFloat((p and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }

    /** IOU hesaplama (nano için) */
    private fun iou(a: BoundingBox, b: BoundingBox): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top,  b.top)
        val x2 = min(a.left + a.width,  b.left + b.width)
        val y2 = min(a.top  + a.height, b.top  + b.height)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        return inter / (a.width*a.height + b.width*b.height - inter + 1e-6f)
    }
}