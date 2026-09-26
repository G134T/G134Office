package org.example.document

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.color.PDColor
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquare
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationStrikeout
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationUnderline
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

class PdfSession {
    private val lock = Any()
    var file: File? = null
        private set
    var dirty: Boolean = false
        private set
    private var document: PDDocument? = null
    private var renderer: PDFRenderer? = null

    val isOpen: Boolean get() = synchronized(lock) { document != null }
    val pageCount: Int get() = synchronized(lock) { document?.numberOfPages ?: 0 }

    fun open(target: File, password: String? = null) {
        close()
        val doc = if (password.isNullOrEmpty()) Loader.loadPDF(target) else Loader.loadPDF(target, password)
        synchronized(lock) {
            document = doc
            renderer = PDFRenderer(doc)
            file = target
            dirty = false
        }
    }

    fun close() {
        synchronized(lock) {
            try {
                document?.close()
            } catch (_: Exception) {
            }
            document = null
            renderer = null
            file = null
            dirty = false
        }
    }

    fun save(target: File) {
        synchronized(lock) {
            val doc = document ?: error("PDF не открыт")
            AtomicFileSave.write(target) { tmp -> doc.save(tmp) }
            file = target
            dirty = false
        }
    }

    fun <T> withDocument(block: (PDDocument, PDFRenderer) -> T): T = synchronized(lock) {
        val doc = document ?: error("PDF не открыт")
        val rend = renderer ?: error("PDF не открыт")
        block(doc, rend)
    }

    fun pageSize(index: Int): Pair<Float, Float> = synchronized(lock) {
        val page = document?.getPage(index) ?: return 595f to 842f
        val box = page.cropBox
        val rot = page.rotation
        if (rot == 90 || rot == 270) box.height to box.width else box.width to box.height
    }

    fun rotatePage(index: Int, degrees: Int) {
        withDocument { doc, _ ->
            val page = doc.getPage(index)
            page.rotation = ((page.rotation + degrees) % 360 + 360) % 360
            rebuildRenderer(doc)
            dirty = true
        }
    }

    fun deletePage(index: Int) {
        withDocument { doc, _ ->
            if (doc.numberOfPages <= 1) error("Нельзя удалить последнюю страницу")
            doc.removePage(index)
            rebuildRenderer(doc)
            dirty = true
        }
    }

    fun insertBlank(index: Int) {
        withDocument { doc, _ ->
            val proto = doc.getPage(index.coerceIn(0, doc.numberOfPages - 1)).mediaBox
            val page = PDPage(PDRectangle(proto.width, proto.height))
            if (index >= doc.numberOfPages) doc.addPage(page)
            else doc.pages.insertBefore(page, doc.getPage(index))
            rebuildRenderer(doc)
            dirty = true
        }
    }

    fun movePage(from: Int, to: Int) {
        withDocument { doc, _ ->
            if (from == to) return@withDocument
            val page = doc.getPage(from)
            doc.removePage(from)
            val adj = if (to > from) to - 1 else to
            if (adj >= doc.numberOfPages) doc.addPage(page)
            else doc.pages.insertBefore(page, doc.getPage(adj))
            rebuildRenderer(doc)
            dirty = true
        }
    }

    fun mergeFrom(other: File) {
        withDocument { doc, _ ->
            Loader.loadPDF(other).use { src ->
                for (page in src.pages) {
                    doc.importPage(page)
                }
            }
            rebuildRenderer(doc)
            dirty = true
        }
    }

    fun extractPages(from: Int, to: Int, target: File) {
        withDocument { doc, _ ->
            PDDocument().use { out ->
                for (i in from..to) {
                    out.importPage(doc.getPage(i))
                }
                AtomicFileSave.write(target) { tmp -> out.save(tmp) }
            }
        }
    }

    fun addMarkup(
        pageIndex: Int,
        subtype: String,
        quads: FloatArray,
        rect: PDRectangle,
        color: FloatArray,
        contents: String
    ) {
        withDocument { doc, _ ->
            val page = doc.getPage(pageIndex)
            val ann: PDAnnotationTextMarkup = when (subtype) {
                "Underline" -> PDAnnotationUnderline()
                "StrikeOut" -> PDAnnotationStrikeout()
                else -> PDAnnotationHighlight()
            }
            ann.rectangle = rect
            ann.quadPoints = quads
            ann.color = PDColor(color, PDDeviceRGB.INSTANCE)
            ann.contents = contents
            ann.titlePopup = "G134Office"
            ann.isPrinted = true
            try {
                ann.constructAppearances(doc)
            } catch (_: Exception) {
            }
            page.annotations.add(ann)
            dirty = true
        }
    }

    fun addNote(pageIndex: Int, x: Float, y: Float, text: String) {
        withDocument { doc, _ ->
            val page = doc.getPage(pageIndex)
            val ann = PDAnnotationText()
            ann.rectangle = PDRectangle(x, y, 24f, 24f)
            ann.contents = text
            ann.titlePopup = "G134Office"
            ann.color = PDColor(floatArrayOf(1f, 0.85f, 0.2f), PDDeviceRGB.INSTANCE)
            try {
                ann.constructAppearances(doc)
            } catch (_: Exception) {
            }
            page.annotations.add(ann)
            dirty = true
        }
    }

    fun addSquare(pageIndex: Int, rect: PDRectangle) {
        withDocument { doc, _ ->
            val page = doc.getPage(pageIndex)
            val ann = PDAnnotationSquare()
            ann.rectangle = rect
            ann.color = PDColor(floatArrayOf(0.85f, 0.12f, 0.12f), PDDeviceRGB.INSTANCE)
            try {
                ann.constructAppearances(doc)
            } catch (_: Exception) {
            }
            page.annotations.add(ann)
            dirty = true
        }
    }

    fun info(): PdfInfo {
        val doc = document ?: return PdfInfo()
        val i = doc.documentInformation
        return PdfInfo(
            title = i.title.orEmpty(),
            author = i.author.orEmpty(),
            subject = i.subject.orEmpty(),
            keywords = i.keywords.orEmpty(),
            creator = i.creator.orEmpty(),
            pages = doc.numberOfPages,
            encrypted = doc.isEncrypted,
            version = doc.version.toString(),
            fileName = file?.name.orEmpty(),
            fileSize = file?.length() ?: 0L
        )
    }

    fun setInfo(title: String, author: String, subject: String, keywords: String) {
        withDocument { doc, _ ->
            val i = doc.documentInformation
            i.title = title
            i.author = author
            i.subject = subject
            i.keywords = keywords
            dirty = true
        }
    }

    private fun rebuildRenderer(doc: PDDocument) {
        renderer = PDFRenderer(doc)
    }
}

data class PdfInfo(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val pages: Int = 0,
    val encrypted: Boolean = false,
    val version: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L
)
