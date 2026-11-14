package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.R
import com.example.domain.model.Certificate
import com.example.domain.model.Payment
import java.io.File
import java.io.FileOutputStream

object ExportUtils {

    fun generateCertificatePdf(context: Context, certificate: Certificate): Uri {
        val dir = File(context.filesDir, "certificates").apply { mkdirs() }
        val file = File(dir, "JY_Certificate_${certificate.certificateNumber}.pdf")

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val navyPaint = Paint().apply {
            color = Color.parseColor("#0A2240")
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val emeraldPaint = Paint().apply {
            color = Color.parseColor("#00A86B")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val goldPaint = Paint().apply {
            color = Color.parseColor("#F59E0B")
            style = Paint.Style.FILL
        }

        canvas.drawColor(Color.parseColor("#FAFBFD"))
        canvas.drawRect(24f, 24f, 818f, 571f, navyPaint)
        canvas.drawRect(36f, 36f, 806f, 559f, emeraldPaint)

        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_jy_logo)
        if (logoBitmap != null) {
            val destRect = Rect(64, 58, 154, 148)
            canvas.drawBitmap(logoBitmap, null, destRect, null)
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A2240")
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#007A4D")
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A2240")
            textSize = 32f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A2240")
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.CENTER
        }
        val coursePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#007A4D")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 11f
        }

        canvas.drawText(InstitutionConfig.INSTITUTION_NAME, 421f, 88f, headerPaint)
        canvas.drawText(InstitutionConfig.OFFICE_ADDRESS, 421f, 110f, subHeaderPaint)
        canvas.drawRect(220f, 124f, 622f, 127f, goldPaint)

        canvas.drawText("CERTIFICATE OF COMPLETION", 421f, 185f, titlePaint)
        canvas.drawText("This is to certify that", 421f, 225f, bodyPaint)
        canvas.drawText(certificate.studentFullName, 421f, 270f, namePaint)
        canvas.drawText("Student ID: ${certificate.studentCode.ifBlank { certificate.studentId }}", 421f, 298f, bodyPaint)
        canvas.drawText("has successfully completed the prescribed training programme in", 421f, 336f, bodyPaint)
        canvas.drawText(certificate.courseTitle, 421f, 376f, coursePaint)
        val gradeText = if (certificate.grade.isNotBlank()) " • Grade: ${certificate.grade}" else ""
        canvas.drawText("Completed on: ${certificate.completionDate}$gradeText", 421f, 412f, bodyPaint)

        val linePaint = Paint().apply {
            color = Color.parseColor("#0A2240")
            strokeWidth = 1.5f
        }
        canvas.drawLine(80f, 495f, 260f, 495f, linePaint)
        canvas.drawText("Director, JY-COMPUTERS", 80f, 514f, smallPaint)
        canvas.drawText("Certificate ID: ${certificate.certificateNumber}", 80f, 534f, smallPaint)

        drawVerificationMatrix(canvas, certificate.certificateNumber, 685f, 430f, 96f)
        canvas.drawText("Verify ID / QR", 698f, 540f, smallPaint)

        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun drawVerificationMatrix(canvas: Canvas, seedText: String, left: Float, top: Float, size: Float) {
        val grid = 12
        val cell = size / grid
        val paint = Paint().apply { color = Color.parseColor("#0A2240") }
        val hash = seedText.hashCode()
        for (r in 0 until grid) {
            for (c in 0 until grid) {
                val isCorner = (r < 3 && c < 3) || (r < 3 && c >= grid - 3) || (r >= grid - 3 && c < 3)
                val bit = ((hash xor (r * 31 + c * 17)) and 1) == 1
                if (isCorner || bit) {
                    canvas.drawRect(
                        left + c * cell,
                        top + r * cell,
                        left + (c + 1) * cell - 1f,
                        top + (r + 1) * cell - 1f,
                        paint
                    )
                }
            }
        }
    }

    fun generateReceiptPdf(context: Context, payment: Payment): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "JY_Receipt_${payment.reference}.pdf")

        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A2240")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 13f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_jy_logo)
        if (logoBitmap != null) {
            canvas.drawBitmap(logoBitmap, null, Rect(48, 40, 112, 104), null)
        }
        canvas.drawText(InstitutionConfig.INSTITUTION_NAME, 128f, 68f, titlePaint)
        canvas.drawText("Official Payment Receipt", 128f, 92f, labelPaint)
        canvas.drawText(InstitutionConfig.OFFICE_ADDRESS, 48f, 130f, labelPaint)
        canvas.drawText("Tel: ${InstitutionConfig.PRIMARY_PHONE} | ${InstitutionConfig.EMAIL_ADDRESS}", 48f, 150f, labelPaint)

        val rows = listOf(
            "Receipt Number" to payment.receiptNumber.ifBlank { payment.reference },
            "Transaction Reference" to payment.reference,
            "Student Name" to payment.studentName.ifBlank { payment.studentId },
            "Course Programme" to payment.courseTitle.ifBlank { payment.courseId },
            "Amount Paid" to Formatters.formatNgn(payment.amountNgn),
            "Payment Method" to "${payment.provider} (${payment.channel.ifBlank { "Standard" }})",
            "Status" to payment.status,
            "Issued At" to Formatters.formatTimestamp(payment.createdAt?.toDate())
        )
        var y = 210f
        rows.forEach { (k, v) ->
            canvas.drawText(k, 48f, y, labelPaint)
            canvas.drawText(v, 240f, y, valuePaint)
            y += 38f
        }

        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun generateTableReportPdf(
        context: Context,
        fileName: String,
        title: String,
        headers: List<String>,
        rows: List<List<String>>
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$fileName.pdf")
        val pdf = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A2240")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#007A4D")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f
        }

        val colCount = headers.size.coerceAtLeast(1)
        val colWidth = (pageWidth - 72f) / colCount
        val rowsPerPage = 24
        val chunks = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(rowsPerPage)

        chunks.forEachIndexed { pageIndex, chunk ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
            val canvas = page.canvas
            canvas.drawText("${InstitutionConfig.INSTITUTION_NAME} - $title", 36f, 44f, titlePaint)
            canvas.drawText("Generated ${Formatters.todayIsoDate()} | Page ${pageIndex + 1}", 36f, 64f, rowPaint)

            headers.forEachIndexed { i, h ->
                canvas.drawText(h.take(24), 36f + i * colWidth, 94f, headerPaint)
            }
            var y = 118f
            chunk.forEach { row ->
                row.forEachIndexed { i, cell ->
                    canvas.drawText(cell.take(26), 36f + i * colWidth, y, rowPaint)
                }
                y += 19f
            }
            pdf.finishPage(page)
        }
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun generateExcelCsv(
        context: Context,
        fileName: String,
        headers: List<String>,
        rows: List<List<String>>
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$fileName.csv")
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
        rows.forEach { row ->
            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun shareExportUri(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
