package com.amazon.campusflow.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import org.apache.poi.extractor.ExtractorFactory
import java.io.InputStream

sealed class ExtractedDocument {
    data class Text(val content: String) : ExtractedDocument()
    data class Images(val bitmaps: List<Bitmap>) : ExtractedDocument()
    data class Error(val message: String) : ExtractedDocument()
}

object DocumentExtractor {

    private const val MAX_PDF_PAGES = 5

    fun extract(context: Context, uri: Uri): ExtractedDocument {
        val mimeType = context.contentResolver.getType(uri)
        val extension = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)?.substringAfterLast('.', "")
        }?.lowercase() ?: ""

        Log.d("DocumentExtractor", "Extracting file - MIME: $mimeType, Ext: $extension")

        return try {
            when {
                // Images
                mimeType?.startsWith("image/") == true || extension in listOf("jpg", "jpeg", "png") -> {
                    extractImage(context, uri)
                }
                // PDF
                mimeType == "application/pdf" || extension == "pdf" -> {
                    extractPdf(context, uri)
                }
                // Plain Text / CSV
                mimeType?.startsWith("text/") == true || extension in listOf("txt", "csv") -> {
                    extractText(context, uri)
                }
                // OOXML (Word, PPT, Excel)
                extension in listOf("xlsx", "docx", "pptx", "xls", "doc", "ppt") -> {
                    extractOOXML(context, uri)
                }
                else -> {
                    ExtractedDocument.Error("Unsupported file format: $extension")
                }
            }
        } catch (e: Exception) {
            Log.e("DocumentExtractor", "Extraction failed", e)
            ExtractedDocument.Error("Failed to extract file: ${e.message}")
        }
    }

    private fun extractImage(context: Context, uri: Uri): ExtractedDocument.Images {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        return if (bitmap != null) {
            ExtractedDocument.Images(listOf(bitmap))
        } else {
            throw Exception("Failed to decode image bitmap")
        }
    }

    private fun extractPdf(context: Context, uri: Uri): ExtractedDocument.Images {
        val parcelFileDescriptor: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
        if (parcelFileDescriptor == null) throw Exception("Could not open PDF file descriptor")

        val pdfRenderer = PdfRenderer(parcelFileDescriptor)
        val bitmaps = mutableListOf<Bitmap>()
        
        val pagesToRender = minOf(pdfRenderer.pageCount, MAX_PDF_PAGES)
        
        for (i in 0 until pagesToRender) {
            val page = pdfRenderer.openPage(i)
            // Render at high resolution for OCR (e.g. 2x screen density)
            val bitmap = Bitmap.createBitmap(
                page.width * 2,
                page.height * 2,
                Bitmap.Config.ARGB_8888
            )
            // White background
            bitmap.eraseColor(android.graphics.Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmaps.add(bitmap)
            page.close()
        }
        
        pdfRenderer.close()
        parcelFileDescriptor.close()
        
        return ExtractedDocument.Images(bitmaps)
    }

    private fun extractText(context: Context, uri: Uri): ExtractedDocument.Text {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw Exception("Could not read text stream")
        return ExtractedDocument.Text(text)
    }

    private fun extractOOXML(context: Context, uri: Uri): ExtractedDocument.Text {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream == null) throw Exception("Could not open OOXML file stream")
        
        return try {
            val extractor = ExtractorFactory.createExtractor(inputStream)
            val text = extractor.text
            extractor.close()
            ExtractedDocument.Text(text)
        } catch (e: Exception) {
            Log.e("DocumentExtractor", "POI extraction failed, fallback to raw text reading", e)
            inputStream.close()
            // Fallback for some environments where POI might fail: just read the raw stream if possible, or throw
            throw Exception("Apache POI failed to extract text from OOXML document: ${e.message}")
        }
    }
}
