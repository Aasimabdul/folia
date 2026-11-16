package com.simbuild.folia.engine

import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Format category grouping according to the universal document reading architecture.
 */
enum class FormatCategory(val displayName: String) {
    WORD_PROCESSING("Word & Text Processing"),
    PDF_FIXED("PDF & Fixed Layout"),
    EBOOK_COMIC("eBooks & Comics"),
    SPREADSHEET("Spreadsheets & Data"),
    PRESENTATION("Presentations & Slides"),
    STRUCTURED_TEXT_CODE("Code & Structured Text"),
    IMAGE_SCANNED("Images & Scanned Docs"),
    ARCHIVE("Archives & Packages"),
    TECHNICAL_DTP("Technical & Publishing")
}

/**
 * Explicit format capability status as mandated by the universal document reader specification.
 * Never pretend that an unsupported or partially supported format is fully natively rendered.
 */
enum class SupportLevel(val label: String, val description: String) {
    NATIVE_RENDERING("Native Rendering", "Direct native engine rendering with full fidelity"),
    LIBRARY_RENDERING("Library Rendering", "Rendered through specialized local native/embedded library"),
    CONVERTED_PREVIEW("Converted Preview", "Parsed and converted locally into responsive styled HTML"),
    TEXT_EXTRACTION("Text Extraction", "Text content extracted from file and displayed cleanly"),
    IMAGE_PREVIEW("Image Preview", "Decoded directly as visual raster or sanitized vector graphic"),
    PARTIAL_SUPPORT("Partial Support", "Container/metadata inspected; partial preview available"),
    UNSUPPORTED("Unsupported", "Proprietary or binary format requiring external/desktop engines")
}

/**
 * Metadata record for every registered document format.
 */
data class FormatInfo(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val category: FormatCategory,
    val supportLevel: SupportLevel,
    val handlerName: String,
    val isOfflineAvailable: Boolean = true,
    val knownLimitations: String = "None"
)

/**
 * Central registry of all document formats supported, converted, or inspected by Folia.
 * Includes explicit capability matrix and magic-byte signature sniffing.
 */
object FormatRegistry {

    private val registry = mutableMapOf<String, FormatInfo>()

    init {
        // =====================================================================
        // Category A: Microsoft Word and Word-Processing Formats
        // =====================================================================
        register("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Microsoft Word Document", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "DocxConverter", knownLimitations = "Complex floating macros/VBA not rendered; formatting reflows to mobile screen.")
        register("doc", "application/msword", "Microsoft Word 97-2003 Document", FormatCategory.WORD_PROCESSING, SupportLevel.TEXT_EXTRACTION, "DocBinaryExtractor", knownLimitations = "Legacy OLE2 binary format; text and paragraphs extracted.")
        register("docm", "application/vnd.ms-word.document.macroEnabled.12", "Word Macro-Enabled Document", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "DocxConverter", knownLimitations = "Macros are deliberately never executed for security; document text and layout are rendered.")
        register("dot", "application/msword", "Word Document Template", FormatCategory.WORD_PROCESSING, SupportLevel.TEXT_EXTRACTION, "DocBinaryExtractor", knownLimitations = "Legacy template text extracted.")
        register("dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template", "Word OpenXML Template", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "DocxConverter", knownLimitations = "Template content rendered as reflowable document.")
        register("dotm", "application/vnd.ms-word.template.macroEnabled.12", "Word Macro Template", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "DocxConverter", knownLimitations = "Macros blocked; template text rendered.")
        register("odt", "application/vnd.oasis.opendocument.text", "OpenDocument Text", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "OdtConverter", knownLimitations = "Content XML parsed to styled HTML.")
        register("ott", "application/vnd.oasis.opendocument.text-template", "OpenDocument Text Template", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "OdtConverter", knownLimitations = "Template layout converted to styled HTML.")
        register("rtf", "application/rtf", "Rich Text Format", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "RtfConverter", knownLimitations = "Basic styles, fonts, and unicode parsed; OLE embedded objects excluded.")
        register("txt", "text/plain", "Plain Text Document", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "TextConverter", knownLimitations = "Reflowed with typographic spacing.")
        register("text", "text/plain", "Plain Text File", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "TextConverter", knownLimitations = "Reflowed with typographic spacing.")
        register("md", "text/markdown", "Markdown Document", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "MarkdownConverter", knownLimitations = "GitHub-Flavored Markdown syntax rendered to styled HTML.")
        register("markdown", "text/markdown", "Markdown Document", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "MarkdownConverter", knownLimitations = "Rendered to styled HTML.")
        register("rst", "text/x-rst", "reStructuredText", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Converted with header and block formatting.")
        register("tex", "application/x-tex", "LaTeX Source Document", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Source formatted with syntax presentation.")
        register("wpd", "application/vnd.wordperfect", "WordPerfect Document", FormatCategory.WORD_PROCESSING, SupportLevel.TEXT_EXTRACTION, "BinaryTextExtractor", knownLimitations = "Proprietary format; plain text strings extracted.")
        register("wps", "application/vnd.ms-works", "Microsoft Works Document", FormatCategory.WORD_PROCESSING, SupportLevel.TEXT_EXTRACTION, "BinaryTextExtractor", knownLimitations = "Legacy format; text stream extracted.")
        register("pages", "application/x-iwork-pages-sffpages", "Apple Pages Document", FormatCategory.WORD_PROCESSING, SupportLevel.PARTIAL_SUPPORT, "AppleIWorkExtractor", knownLimitations = "Proprietary Apple protobuf/archive; embedded preview or text extracted where available.")
        register("abw", "application/x-abiword", "AbiWord Document", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "OdtConverter", knownLimitations = "XML structure parsed to HTML.")
        register("sxw", "application/vnd.sun.xml.writer", "StarOffice / OpenOffice 1.x Text", FormatCategory.WORD_PROCESSING, SupportLevel.CONVERTED_PREVIEW, "OdtConverter", knownLimitations = "Legacy ZIP XML parsed to HTML.")

        // =====================================================================
        // Category B: PDF and Fixed-Layout Documents
        // =====================================================================
        register("pdf", "application/pdf", "Portable Document Format", FormatCategory.PDF_FIXED, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Full vector rendering, TOC, search, and password prompts supported.")
        register("xps", "application/vnd.ms-xpsdocument", "XML Paper Specification", FormatCategory.PDF_FIXED, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Native fixed-layout rendering.")
        register("oxps", "application/oxps", "Open XML Paper Specification", FormatCategory.PDF_FIXED, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Native fixed-layout rendering.")
        register("djvu", "image/vnd.djvu", "DjVu Scanned Document", FormatCategory.PDF_FIXED, SupportLevel.PARTIAL_SUPPORT, "DjvuHandler", knownLimitations = "Requires native DjVuLibre; fallback displays file info and conversion suggestions.")
        register("djv", "image/vnd.djvu", "DjVu Document", FormatCategory.PDF_FIXED, SupportLevel.PARTIAL_SUPPORT, "DjvuHandler", knownLimitations = "Requires native DjVuLibre; fallback displays file info.")
        register("chm", "application/vnd.ms-htmlhelp", "Compiled HTML Help", FormatCategory.PDF_FIXED, SupportLevel.PARTIAL_SUPPORT, "ChmHandler", knownLimitations = "ITSF archive container; topic index extracted.")

        // =====================================================================
        // Category C: eBooks and Comics
        // =====================================================================
        register("epub", "application/epub+zip", "Electronic Publication", FormatCategory.EBOOK_COMIC, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Reflowable text with user-adjustable font size, themes, and TOC.")
        register("mobi", "application/x-mobipocket-ebook", "Mobipocket eBook", FormatCategory.EBOOK_COMIC, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Reflowable reading mode with theme support.")
        register("azw", "application/vnd.amazon.ebook", "Amazon Kindle eBook", FormatCategory.EBOOK_COMIC, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Non-DRM files reflowed smoothly. DRM-protected files require authorized Kindle app.")
        register("azw3", "application/vnd.amazon.ebook", "Amazon KF8 eBook", FormatCategory.EBOOK_COMIC, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Non-DRM files rendered with reflow.")
        register("fb2", "application/x-fictionbook+xml", "FictionBook 2.0", FormatCategory.EBOOK_COMIC, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Native reflowable eBook reading.")
        register("cbz", "application/vnd.comicbook+zip", "Comic Book ZIP Archive", FormatCategory.EBOOK_COMIC, SupportLevel.NATIVE_RENDERING, "MuPdfEngine", knownLimitations = "Page-by-page streaming comic view.")
        register("cbr", "application/vnd.comicbook-rar", "Comic Book RAR Archive", FormatCategory.EBOOK_COMIC, SupportLevel.PARTIAL_SUPPORT, "CbrHandler", knownLimitations = "RAR archive streaming; CBZ format recommended for maximum performance.")

        // =====================================================================
        // Category D: Spreadsheets
        // =====================================================================
        register("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Microsoft Excel Spreadsheet", FormatCategory.SPREADSHEET, SupportLevel.CONVERTED_PREVIEW, "SpreadsheetConverter", knownLimitations = "Parses worksheets, cell values, and styles into responsive HTML tables; macros disabled.")
        register("xls", "application/vnd.ms-excel", "Microsoft Excel 97-2003 Spreadsheet", FormatCategory.SPREADSHEET, SupportLevel.TEXT_EXTRACTION, "SpreadsheetConverter", knownLimitations = "Binary BIFF stream; cell text extracted into tabular format.")
        register("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12", "Excel Macro-Enabled Workbook", FormatCategory.SPREADSHEET, SupportLevel.CONVERTED_PREVIEW, "SpreadsheetConverter", knownLimitations = "Macros strictly blocked; worksheet data rendered.")
        register("xlsb", "application/vnd.ms-excel.sheet.binary.macroEnabled.12", "Excel Binary Workbook", FormatCategory.SPREADSHEET, SupportLevel.TEXT_EXTRACTION, "SpreadsheetConverter", knownLimitations = "Binary format; extracted to text table.")
        register("xlt", "application/vnd.ms-excel", "Excel Template 97-2003", FormatCategory.SPREADSHEET, SupportLevel.TEXT_EXTRACTION, "SpreadsheetConverter", knownLimitations = "Template text extracted.")
        register("xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template", "Excel OpenXML Template", FormatCategory.SPREADSHEET, SupportLevel.CONVERTED_PREVIEW, "SpreadsheetConverter", knownLimitations = "Worksheet preview rendered.")
        register("xltm", "application/vnd.ms-excel.template.macroEnabled.12", "Excel Macro Template", FormatCategory.SPREADSHEET, SupportLevel.CONVERTED_PREVIEW, "SpreadsheetConverter", knownLimitations = "Macros blocked; worksheet data rendered.")
        register("ods", "application/vnd.oasis.opendocument.spreadsheet", "OpenDocument Spreadsheet", FormatCategory.SPREADSHEET, SupportLevel.CONVERTED_PREVIEW, "SpreadsheetConverter", knownLimitations = "Parsed from content.xml into responsive tables.")
        register("ots", "application/vnd.oasis.opendocument.spreadsheet-template", "OpenDocument Spreadsheet Template", FormatCategory.SPREADSHEET, SupportLevel.CONVERTED_PREVIEW, "SpreadsheetConverter", knownLimitations = "Template tables rendered.")
        register("csv", "text/csv", "Comma-Separated Values", FormatCategory.SPREADSHEET, SupportLevel.CONVERTED_PREVIEW, "SpreadsheetConverter", knownLimitations = "Formatted as styled responsive data table with sticky headers.")
        register("tsv", "text/tab-separated-values", "Tab-Separated Values", FormatCategory.SPREADSHEET, SupportLevel.CONVERTED_PREVIEW, "SpreadsheetConverter", knownLimitations = "Formatted as styled responsive data table.")
        register("numbers", "application/x-iwork-numbers-sffnumbers", "Apple Numbers Spreadsheet", FormatCategory.SPREADSHEET, SupportLevel.PARTIAL_SUPPORT, "AppleIWorkExtractor", knownLimitations = "Apple proprietary archive; preview or extracted text displayed where available.")

        // =====================================================================
        // Category E: Presentations
        // =====================================================================
        register("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint Presentation", FormatCategory.PRESENTATION, SupportLevel.CONVERTED_PREVIEW, "PresentationConverter", knownLimitations = "Slide cards rendered with titles, text, tables, and embedded images; macros disabled.")
        register("ppt", "application/vnd.ms-powerpoint", "PowerPoint 97-2003 Presentation", FormatCategory.PRESENTATION, SupportLevel.TEXT_EXTRACTION, "PresentationConverter", knownLimitations = "Legacy OLE2 presentation; slide titles and text extracted.")
        register("pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12", "PowerPoint Macro-Enabled Presentation", FormatCategory.PRESENTATION, SupportLevel.CONVERTED_PREVIEW, "PresentationConverter", knownLimitations = "Macros blocked; slide content rendered.")
        register("pps", "application/vnd.ms-powerpoint", "PowerPoint Slide Show 97-2003", FormatCategory.PRESENTATION, SupportLevel.TEXT_EXTRACTION, "PresentationConverter", knownLimitations = "Slide text extracted.")
        register("ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow", "PowerPoint OpenXML Slide Show", FormatCategory.PRESENTATION, SupportLevel.CONVERTED_PREVIEW, "PresentationConverter", knownLimitations = "Slide cards rendered with images and text.")
        register("ppsm", "application/vnd.ms-powerpoint.slideshow.macroEnabled.12", "PowerPoint Macro Slide Show", FormatCategory.PRESENTATION, SupportLevel.CONVERTED_PREVIEW, "PresentationConverter", knownLimitations = "Macros blocked; slide content rendered.")
        register("pot", "application/vnd.ms-powerpoint", "PowerPoint Template 97-2003", FormatCategory.PRESENTATION, SupportLevel.TEXT_EXTRACTION, "PresentationConverter", knownLimitations = "Slide text extracted.")
        register("potx", "application/vnd.openxmlformats-officedocument.presentationml.template", "PowerPoint OpenXML Template", FormatCategory.PRESENTATION, SupportLevel.CONVERTED_PREVIEW, "PresentationConverter", knownLimitations = "Template slide layout rendered.")
        register("potm", "application/vnd.ms-powerpoint.template.macroEnabled.12", "PowerPoint Macro Template", FormatCategory.PRESENTATION, SupportLevel.CONVERTED_PREVIEW, "PresentationConverter", knownLimitations = "Macros blocked; template text rendered.")
        register("odp", "application/vnd.oasis.opendocument.presentation", "OpenDocument Presentation", FormatCategory.PRESENTATION, SupportLevel.CONVERTED_PREVIEW, "PresentationConverter", knownLimitations = "Parsed from content.xml into slide pages.")
        register("otp", "application/vnd.oasis.opendocument.presentation-template", "OpenDocument Presentation Template", FormatCategory.PRESENTATION, SupportLevel.CONVERTED_PREVIEW, "PresentationConverter", knownLimitations = "Slide template layout rendered.")
        register("key", "application/x-iwork-keynote-sffkey", "Apple Keynote Presentation", FormatCategory.PRESENTATION, SupportLevel.PARTIAL_SUPPORT, "AppleIWorkExtractor", knownLimitations = "Apple proprietary archive; preview images or text extracted where available.")

        // =====================================================================
        // Category F: Web Documents and Structured Text / Code
        // =====================================================================
        register("html", "text/html", "HTML Web Document", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Active scripts blocked; sanitized for safe offline reading.")
        register("htm", "text/html", "HTML Web Page", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Active scripts blocked; sanitized.")
        register("xhtml", "application/xhtml+xml", "XHTML Document", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "XML HTML rendered with typography.")
        register("xml", "application/xml", "XML Data Document", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Formatted with syntax highlighting and line numbers.")
        register("mht", "message/rfc822", "MHTML Web Archive", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Multipart HTML parsed into readable document.")
        register("mhtml", "message/rfc822", "MHTML Archive", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Multipart HTML parsed.")
        register("json", "application/json", "JSON Document", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Indented and syntax formatted with line numbers.")
        register("yaml", "text/yaml", "YAML Document", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Syntax formatted with line numbers.")
        register("yml", "text/yaml", "YAML Document", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Syntax formatted with line numbers.")
        register("log", "text/plain", "Log File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Monospace presentation with line numbers.")
        register("ini", "text/plain", "Configuration File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Monospace syntax formatted.")
        register("conf", "text/plain", "Config File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Monospace syntax formatted.")
        register("properties", "text/plain", "Java Properties File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Key-value syntax formatted.")
        register("sql", "application/sql", "SQL Script File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "SQL syntax highlighted with line numbers.")
        register("js", "application/javascript", "JavaScript Source", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Treated strictly as untrusted text; syntax highlighted; never executed.")
        register("ts", "application/typescript", "TypeScript Source", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Syntax highlighted; never executed.")
        register("css", "text/css", "CSS Stylesheet", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "CSS syntax highlighted with line numbers.")
        register("py", "text/x-python", "Python Source File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Python syntax highlighted with line numbers.")
        register("java", "text/x-java-source", "Java Source File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Java syntax highlighted with line numbers.")
        register("kt", "text/x-kotlin", "Kotlin Source File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Kotlin syntax highlighted with line numbers.")
        register("c", "text/x-c", "C Source File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "C syntax highlighted with line numbers.")
        register("cpp", "text/x-c++", "C++ Source File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "C++ syntax highlighted with line numbers.")
        register("h", "text/x-c-header", "C/C++ Header File", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Header syntax highlighted with line numbers.")
        register("sh", "application/x-sh", "Shell Script", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Displayed as text; never executed.")
        register("bat", "text/plain", "Batch Script", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Displayed as text; never executed.")
        register("ps1", "text/plain", "PowerShell Script", FormatCategory.STRUCTURED_TEXT_CODE, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Displayed as text; never executed.")

        // =====================================================================
        // Category G: Image and Scanned Documents
        // =====================================================================
        register("jpg", "image/jpeg", "JPEG Image Document", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "High-res zoom & pan; OCR text extraction ready.")
        register("jpeg", "image/jpeg", "JPEG Image Document", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "High-res zoom & pan; OCR text extraction ready.")
        register("png", "image/png", "PNG Graphic Document", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "Lossless alpha channel zoom & pan.")
        register("webp", "image/webp", "WebP Image", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "Modern compressed raster preview.")
        register("bmp", "image/bmp", "Bitmap Image", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "Uncompressed bitmap rendering.")
        register("gif", "image/gif", "GIF Graphic Document", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "First frame & animation preview.")
        register("tif", "image/tiff", "TIFF Scanned Image", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "High-resolution scanned document preview.")
        register("tiff", "image/tiff", "TIFF Scanned Document", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "Multi-page or single-page scanned document preview.")
        register("heic", "image/heic", "High Efficiency Image", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "Hardware-accelerated on Android 9+ (API 28).")
        register("heif", "image/heif", "HEIF Image File", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "Decoded via Android platform decoder.")
        register("avif", "image/avif", "AVIF Image File", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "Hardware-accelerated on Android 12+ (API 31).")
        register("ico", "image/x-icon", "Icon Image", FormatCategory.IMAGE_SCANNED, SupportLevel.IMAGE_PREVIEW, "ImageDocEngine", knownLimitations = "Bitmap icon preview.")
        register("svg", "image/svg+xml", "Scalable Vector Graphic", FormatCategory.IMAGE_SCANNED, SupportLevel.CONVERTED_PREVIEW, "ImageDocEngine", knownLimitations = "Sanitized against embedded scripts; vector rendered.")

        // =====================================================================
        // Category H: Archives and Document Packages
        // =====================================================================
        register("zip", "application/zip", "ZIP Archive Package", FormatCategory.ARCHIVE, SupportLevel.CONVERTED_PREVIEW, "ArchiveEngine", knownLimitations = "Safe browsing of archive contents; Zip-slip and zip-bomb protected; tap files to open.")
        register("7z", "application/x-7z-compressed", "7-Zip Archive", FormatCategory.ARCHIVE, SupportLevel.PARTIAL_SUPPORT, "ArchiveEngine", knownLimitations = "Archive signature detected; safe listing provided.")
        register("rar", "application/vnd.rar", "RAR Archive", FormatCategory.ARCHIVE, SupportLevel.PARTIAL_SUPPORT, "ArchiveEngine", knownLimitations = "Archive signature verified; safe container listing.")
        register("tar", "application/x-tar", "Tape Archive", FormatCategory.ARCHIVE, SupportLevel.CONVERTED_PREVIEW, "ArchiveEngine", knownLimitations = "Safe entry browsing without external extraction.")
        register("gz", "application/gzip", "Gzip Compressed Document", FormatCategory.ARCHIVE, SupportLevel.CONVERTED_PREVIEW, "ArchiveEngine", knownLimitations = "Decompresses inner stream up to bounded memory limit.")
        register("bz2", "application/x-bzip2", "Bzip2 Compressed File", FormatCategory.ARCHIVE, SupportLevel.PARTIAL_SUPPORT, "ArchiveEngine", knownLimitations = "Compressed container inspected.")
        register("xz", "application/x-xz", "XZ Compressed Archive", FormatCategory.ARCHIVE, SupportLevel.PARTIAL_SUPPORT, "ArchiveEngine", knownLimitations = "Compressed container inspected.")

        // =====================================================================
        // Category I: Desktop Publishing, Diagrams, Notes, and Technical Docs
        // =====================================================================
        register("pub", "application/x-mspublisher", "Microsoft Publisher Document", FormatCategory.TECHNICAL_DTP, SupportLevel.PARTIAL_SUPPORT, "UnsupportedDocEngine", knownLimitations = "Proprietary Microsoft DTP binary; embedded text extracted where available.")
        register("vsd", "application/vnd.visio", "Microsoft Visio 97-2003 Diagram", FormatCategory.TECHNICAL_DTP, SupportLevel.PARTIAL_SUPPORT, "UnsupportedDocEngine", knownLimitations = "Proprietary OLE2 binary diagram; metadata extracted.")
        register("vsdx", "application/vnd.visio2013", "Microsoft Visio Drawing", FormatCategory.TECHNICAL_DTP, SupportLevel.PARTIAL_SUPPORT, "UnsupportedDocEngine", knownLimitations = "OpenXML diagram; shape text and embedded images extracted.")
        register("one", "application/onenote", "Microsoft OneNote Section", FormatCategory.TECHNICAL_DTP, SupportLevel.PARTIAL_SUPPORT, "UnsupportedDocEngine", knownLimitations = "Proprietary binary note format; text blocks extracted.")
        register("onepkg", "application/onenote", "Microsoft OneNote Package", FormatCategory.TECHNICAL_DTP, SupportLevel.PARTIAL_SUPPORT, "UnsupportedDocEngine", knownLimitations = "CAB/OneNote package inspected.")
        register("qxd", "application/vnd.quark.quarkxpress", "QuarkXPress Project", FormatCategory.TECHNICAL_DTP, SupportLevel.UNSUPPORTED, "UnsupportedDocEngine", knownLimitations = "Proprietary DTP binary; export as PDF is recommended.")
        register("indd", "application/x-indesign", "Adobe InDesign Document", FormatCategory.TECHNICAL_DTP, SupportLevel.UNSUPPORTED, "UnsupportedDocEngine", knownLimitations = "Proprietary Adobe binary; export as PDF is recommended.")
        register("pmd", "application/x-pagemaker", "Adobe PageMaker Publication", FormatCategory.TECHNICAL_DTP, SupportLevel.UNSUPPORTED, "UnsupportedDocEngine", knownLimitations = "Legacy proprietary binary; export as PDF recommended.")
        register("dwg", "image/vnd.dwg", "AutoCAD Drawing", FormatCategory.TECHNICAL_DTP, SupportLevel.UNSUPPORTED, "UnsupportedDocEngine", knownLimitations = "Proprietary binary CAD format; export as PDF/DXF recommended.")
        register("dxf", "image/vnd.dxf", "Drawing Exchange Format", FormatCategory.TECHNICAL_DTP, SupportLevel.PARTIAL_SUPPORT, "UnsupportedDocEngine", knownLimitations = "ASCII text entities and layer names parsed.")
        register("odg", "application/vnd.oasis.opendocument.graphics", "OpenDocument Drawing", FormatCategory.TECHNICAL_DTP, SupportLevel.CONVERTED_PREVIEW, "OdtConverter", knownLimitations = "XML shapes and text rendered.")
        register("fodg", "application/vnd.oasis.opendocument.graphics-flat-xml", "Flat XML OpenDocument Drawing", FormatCategory.TECHNICAL_DTP, SupportLevel.CONVERTED_PREVIEW, "OdtConverter", knownLimitations = "XML text and shapes rendered.")
        register("eml", "message/rfc822", "Email Message File", FormatCategory.TECHNICAL_DTP, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Formatted with Subject, From, To, Date, and message body.")
        register("msg", "application/vnd.ms-outlook", "Outlook Mail Message", FormatCategory.TECHNICAL_DTP, SupportLevel.TEXT_EXTRACTION, "DocBinaryExtractor", knownLimitations = "OLE2 message stream text extracted.")
        register("ics", "text/calendar", "iCalendar Schedule", FormatCategory.TECHNICAL_DTP, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Calendar events, dates, and locations formatted into schedule card.")
        register("vcf", "text/vcard", "vCard Electronic Business Card", FormatCategory.TECHNICAL_DTP, SupportLevel.CONVERTED_PREVIEW, "StructuredTextConverter", knownLimitations = "Contact names, phones, and emails formatted into contact card.")
    }

    private fun register(
        ext: String,
        mime: String,
        name: String,
        cat: FormatCategory,
        level: SupportLevel,
        handler: String,
        offline: Boolean = true,
        knownLimitations: String = "None"
    ) {
        registry[ext.lowercase()] = FormatInfo(
            extension = ext.lowercase(),
            mimeType = mime,
            displayName = name,
            category = cat,
            supportLevel = level,
            handlerName = handler,
            isOfflineAvailable = offline,
            knownLimitations = knownLimitations
        )
    }

    /**
     * Look up format metadata by file extension.
     */
    fun findByExtension(extension: String): FormatInfo? = registry[extension.lowercase()]

    /**
     * Returns all registered formats across all categories.
     */
    fun allFormats(): List<FormatInfo> = registry.values.toList()

    /**
     * Returns formats belonging to a specific category.
     */
    fun formatsByCategory(category: FormatCategory): List<FormatInfo> =
        registry.values.filter { it.category == category }

    /**
     * Robust magic-byte format identification.
     * Never trusts the filename extension alone: inspects initial bytes and container contents.
     */
    fun sniffFormat(file: File, uri: Uri? = null, fallbackName: String = file.name): FormatInfo {
        val ext = fallbackName.substringAfterLast('.', "").lowercase()

        // 1. Read header magic bytes (first 32 bytes)
        val header = ByteArray(32)
        val bytesRead = try {
            FileInputStream(file).use { it.read(header) }
        } catch (_: Exception) {
            0
        }

        if (bytesRead >= 4) {
            // PDF signature: %PDF-
            if (header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
            ) {
                return registry["pdf"]!!
            }

            // ZIP signature: PK\x03\x04
            if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            ) {
                // Disambiguate ZIP containers by checking internal manifest/structure
                val sniffedZip = sniffZipContainer(file)
                if (sniffedZip != null) return sniffedZip
                return registry[ext] ?: registry["zip"]!!
            }

            // OLE2 Compound Document: \xD0\xCF\x11\xE0\xA1\xB1\x1A\xE1
            if (header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
                header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()
            ) {
                return when (ext) {
                    "xls", "xlt" -> registry["xls"]!!
                    "ppt", "pps", "pot" -> registry["ppt"]!!
                    "vsd" -> registry["vsd"]!!
                    "msg" -> registry["msg"]!!
                    "pub" -> registry["pub"]!!
                    else -> registry["doc"]!!
                }
            }

            // Rich Text Format: {\rtf
            if (header[0] == 0x7B.toByte() && header[1] == 0x5C.toByte() &&
                header[2] == 0x72.toByte() && header[3] == 0x74.toByte()
            ) {
                return registry["rtf"]!!
            }

            // 7-Zip: 7z\xBC\xAF\x27\x1C
            if (header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
                header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte()
            ) {
                return registry["7z"]!!
            }

            // RAR archive: Rar!\x1A\x07
            if (header[0] == 0x52.toByte() && header[1] == 0x61.toByte() &&
                header[2] == 0x72.toByte() && header[3] == 0x21.toByte()
            ) {
                return if (ext == "cbr") registry["cbr"]!! else registry["rar"]!!
            }

            // PNG image: \x89PNG
            if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
            ) {
                return registry["png"]!!
            }

            // JPEG image: \xFF\xD8\xFF
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                return registry["jpg"]!!
            }

            // GIF image: GIF8
            if (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
                header[2] == 0x46.toByte() && header[3] == 0x38.toByte()
            ) {
                return registry["gif"]!!
            }

            // BMP image: BM
            if (header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()) {
                return registry["bmp"]!!
            }

            // TIFF: II*\x00 (little-endian) or MM\x00* (big-endian)
            if ((header[0] == 0x49.toByte() && header[1] == 0x49.toByte() && header[2] == 0x2A.toByte() && header[3] == 0x00.toByte()) ||
                (header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() && header[2] == 0x00.toByte() && header[3] == 0x2A.toByte())
            ) {
                return registry["tiff"]!!
            }

            // GZIP: \x1F\x8B
            if (header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()) {
                return registry["gz"]!!
            }
        }

        // 2. Fallback to extension lookup
        return registry[ext] ?: FormatInfo(
            extension = ext,
            mimeType = "application/octet-stream",
            displayName = ext.uppercase().ifEmpty { "UNKNOWN" } + " File",
            category = FormatCategory.TECHNICAL_DTP,
            supportLevel = SupportLevel.UNSUPPORTED,
            handlerName = "UnsupportedDocEngine",
            isOfflineAvailable = false,
            knownLimitations = "Unrecognized document format."
        )
    }

    /**
     * Inspects entry names inside a ZIP file to differentiate DOCX, XLSX, PPTX, EPUB, ODT, ODS, ODP, CBZ, and ZIP.
     */
    private fun sniffZipContainer(file: File): FormatInfo? {
        try {
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entryCount = 0
                var hasWord = false
                var hasExcel = false
                var hasPowerPoint = false
                var hasEpub = false
                var hasContentXml = false
                var hasImages = false

                var entry = zis.nextEntry
                while (entry != null && entryCount < 30) {
                    val name = entry.name
                    if (name.startsWith("word/")) hasWord = true
                    if (name.startsWith("xl/")) hasExcel = true
                    if (name.startsWith("ppt/")) hasPowerPoint = true
                    if (name.contains("mimetype") || name.startsWith("META-INF/container.xml")) hasEpub = true
                    if (name == "content.xml") hasContentXml = true
                    val lower = name.lowercase()
                    if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp")) hasImages = true

                    entryCount++
                    entry = zis.nextEntry
                }

                if (hasWord) return registry["docx"]
                if (hasExcel) return registry["xlsx"]
                if (hasPowerPoint) return registry["pptx"]
                if (hasEpub) return registry["epub"]
                if (hasContentXml) {
                    val ext = file.extension.lowercase()
                    if (ext == "ods" || ext == "ots") return registry["ods"]
                    if (ext == "odp" || ext == "otp") return registry["odp"]
                    return registry["odt"]
                }
                if (hasImages && file.extension.equals("cbz", ignoreCase = true)) return registry["cbz"]
            }
        } catch (_: Exception) {}
        return null
    }
}
