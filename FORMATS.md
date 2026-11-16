# Folia Universal Format Compatibility Matrix

This document provides the definitive capability status for every supported, converted, and inspected file format in **Folia**.

In strict accordance with open-source engineering standards:
- **No format capability is exaggerated or simulated.**
- **No fake viewers or empty placeholder buttons are present.**
- **Active scripts and macros are unconditionally blocked for security.**
- **100% offline reading operates without network dependencies.**

---

## 🏷️ Capability Level Definitions

| Level | Definition | Behavior in Folia |
|---|---|---|
| **`Native Rendering`** | First-party native C/C++ engine rendering | Highest vector visual fidelity, native zoom, outline TOC, text search. |
| **`Library Rendering`** | Embedded native format library | Rendered with hardware-accelerated raster or vector decoders. |
| **`Converted Preview`** | Local sandboxed parser converting to responsive HTML | Reflowable typographic layout, font size scaling, table & image rendering. |
| **`Text Extraction`** | Text stream & structural extraction | Text content decoded from legacy or binary files into clean paragraphs. |
| **`Image Preview`** | Direct visual bitmap/vector preview | Rendered on canvas with memory-safe downsampling and zoom/pan. |
| **`Partial Support`** | Container & metadata inspected | Safe container browsing or metadata extraction with conversion guidance. |
| **`Unsupported`** | Proprietary binary requiring desktop engine | Honest compatibility disclosure, metadata summary, and export recommendations. |

---

## 📂 Complete Format Support Matrix

### A. Microsoft Word and Word-Processing Formats

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | Word OpenXML Document | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Floating shapes converted to flow; macros disabled. |
| `.doc` | `application/msword` | Word 97-2003 Document | **Text Extraction** | OLE2 binary stream scanner | Yes | Legacy binary stream; text blocks extracted. |
| `.docm` | `application/vnd.ms-word.document.macroEnabled.12` | Word Macro Document | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Macros strictly blocked; document rendered. |
| `.dot` | `application/msword` | Word Template 97-2003 | **Text Extraction** | OLE2 binary stream scanner | Yes | Template text extracted. |
| `.dotx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.template` | Word OpenXML Template | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Template content rendered as document. |
| `.dotm` | `application/vnd.ms-word.template.macroEnabled.12` | Word Macro Template | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Macros blocked; text and layout rendered. |
| `.odt` | `application/vnd.oasis.opendocument.text` | OpenDocument Text | **Converted Preview** | ODT XML parser | Yes | Converted to responsive styled HTML. |
| `.ott` | `application/vnd.oasis.opendocument.text-template` | OpenDocument Template | **Converted Preview** | ODT XML parser | Yes | Template layout converted to styled HTML. |
| `.rtf` | `application/rtf` | Rich Text Format | **Converted Preview** | Native RTF state-machine parser | Yes | Bold, italic, underline, unicode characters parsed. |
| `.txt` | `text/plain` | Plain Text Document | **Converted Preview** | UTF-8 typography wrapper | Yes | Formatted with readable line height and margins. |
| `.text` | `text/plain` | Plain Text File | **Converted Preview** | UTF-8 typography wrapper | Yes | Reflowable with adjustable font size. |
| `.md` | `text/markdown` | Markdown Document | **Converted Preview** | GitHub Flavored Markdown parser | Yes | Headings, lists, code blocks, bold/italics. |
| `.markdown` | `text/markdown` | Markdown Document | **Converted Preview** | Markdown parser | Yes | Rendered to styled HTML. |
| `.rst` | `text/x-rst` | reStructuredText | **Converted Preview** | RST structural parser | Yes | Underline headers, directives, and blocks rendered. |
| `.tex` | `application/x-tex` | LaTeX Source Document | **Converted Preview** | Syntax formatter | Yes | Source displayed with line numbers and syntax. |
| `.wpd` | `application/vnd.wordperfect` | WordPerfect Document | **Text Extraction** | Binary stream extractor | Yes | Plain text streams extracted. |
| `.wps` | `application/vnd.ms-works` | Microsoft Works Document | **Text Extraction** | Binary stream extractor | Yes | Text stream extracted. |
| `.pages` | `application/x-iwork-pages-sffpages` | Apple Pages Document | **Partial Support** | IWork container inspector | Yes | Apple proprietary protobuf; embedded preview shown if present. |
| `.abw` | `application/x-abiword` | AbiWord Document | **Converted Preview** | XML structure parser | Yes | XML content rendered to HTML. |
| `.sxw` | `application/vnd.sun.xml.writer` | StarOffice 1.x Text | **Converted Preview** | OpenOffice XML parser | Yes | Legacy XML parsed to HTML. |

---

### B. PDF and Fixed-Layout Documents

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.pdf` | `application/pdf` | Portable Document Format | **Native Rendering** | MuPDF Fitz C Core | Yes | High-fidelity vector rendering, TOC, search, password support. |
| `.xps` | `application/vnd.ms-xpsdocument` | XML Paper Specification | **Native Rendering** | MuPDF Fitz C Core | Yes | Native vector fixed-layout rendering. |
| `.oxps` | `application/oxps` | Open XML Paper Specification | **Native Rendering** | MuPDF Fitz C Core | Yes | Native vector fixed-layout rendering. |
| `.djvu` | `image/vnd.djvu` | DjVu Scanned Document | **Partial Support** | DjVu format handler | Yes | Displays document info and export recommendations. |
| `.chm` | `application/vnd.ms-htmlhelp` | Compiled HTML Help | **Partial Support** | ITSF archive handler | Yes | Topic manifest and extracted HTML topics. |

---

### C. eBooks and Comics

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.epub` | `application/epub+zip` | Electronic Publication | **Native Rendering** | MuPDF Fitz C Core | Yes | Reflowable text, adjustable font size, TOC, dark/sepia themes. |
| `.mobi` | `application/x-mobipocket-ebook` | Mobipocket eBook | **Native Rendering** | MuPDF Fitz C Core | Yes | Reflowable reading with theme support. |
| `.azw` | `application/vnd.amazon.ebook` | Amazon Kindle eBook | **Native Rendering** | MuPDF Fitz C Core | Yes | Non-DRM files reflowed smoothly. DRM files require Kindle app. |
| `.azw3` | `application/vnd.amazon.ebook` | Amazon KF8 eBook | **Native Rendering** | MuPDF Fitz C Core | Yes | Non-DRM files rendered with reflow. |
| `.fb2` | `application/x-fictionbook+xml` | FictionBook 2.0 | **Native Rendering** | MuPDF Fitz C Core | Yes | Native reflowable eBook reading. |
| `.cbz` | `application/vnd.comicbook+zip` | Comic Book ZIP | **Native Rendering** | MuPDF Fitz C Core | Yes | Page-by-page streaming comic view. |
| `.cbr` | `application/vnd.comicbook-rar` | Comic Book RAR | **Partial Support** | RAR comic handler | Yes | RAR streaming; CBZ conversion recommended. |

---

### D. Spreadsheets

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | Excel OpenXML Spreadsheet | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Multiple worksheets, shared strings pool, responsive HTML tables. |
| `.xls` | `application/vnd.ms-excel` | Excel 97-2003 Workbook | **Text Extraction** | BIFF binary stream scanner | Yes | Legacy binary stream; cell values extracted to table. |
| `.xlsm` | `application/vnd.ms-excel.sheet.macroEnabled.12` | Excel Macro Workbook | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Macros strictly blocked; sheet data rendered. |
| `.xlsb` | `application/vnd.ms-excel.sheet.binary.macroEnabled.12` | Excel Binary Workbook | **Text Extraction** | Binary stream scanner | Yes | Binary sheet records extracted. |
| `.xlt` | `application/vnd.ms-excel` | Excel Template 97-2003 | **Text Extraction** | BIFF binary stream scanner | Yes | Template data extracted. |
| `.xltx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.template` | Excel OpenXML Template | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Template table rendered. |
| `.xltm` | `application/vnd.ms-excel.template.macroEnabled.12` | Excel Macro Template | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Macros blocked; template data rendered. |
| `.ods` | `application/vnd.oasis.opendocument.spreadsheet` | OpenDocument Spreadsheet | **Converted Preview** | ODS XML table parser | Yes | Parsed from content.xml into tables. |
| `.ots` | `application/vnd.oasis.opendocument.spreadsheet-template` | OpenDocument Template | **Converted Preview** | ODS XML table parser | Yes | Template tables rendered. |
| `.csv` | `text/csv` | Comma-Separated Values | **Converted Preview** | Delimiter auto-detector | Yes | Auto-detects `,`, `;`, `|`, tabs; styled table with zebra striping. |
| `.tsv` | `text/tab-separated-values` | Tab-Separated Values | **Converted Preview** | Delimiter parser | Yes | Formatted as styled data table. |
| `.numbers` | `application/x-iwork-numbers-sffnumbers` | Apple Numbers Spreadsheet | **Partial Support** | IWork container inspector | Yes | Apple proprietary protobuf; embedded preview shown if available. |

---

### E. Presentations

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.pptx` | `application/vnd.openxmlformats-officedocument.presentationml.presentation` | PowerPoint OpenXML Presentation | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Slide cards rendered with titles, text, tables, and embedded images. |
| `.ppt` | `application/vnd.ms-powerpoint` | PowerPoint 97-2003 Presentation | **Text Extraction** | OLE2 binary stream scanner | Yes | Slide titles and text extracted into slide cards. |
| `.pptm` | `application/vnd.ms-powerpoint.presentation.macroEnabled.12` | PowerPoint Macro Presentation | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Macros blocked; slide content rendered. |
| `.pps` | `application/vnd.ms-powerpoint` | PowerPoint Slide Show 97-2003 | **Text Extraction** | OLE2 binary stream scanner | Yes | Slide text extracted. |
| `.ppsx` | `application/vnd.openxmlformats-officedocument.presentationml.slideshow` | PowerPoint OpenXML Slide Show | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Slide cards rendered with images and text. |
| `.ppsm` | `application/vnd.ms-powerpoint.slideshow.macroEnabled.12` | PowerPoint Macro Slide Show | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Macros blocked; slides rendered. |
| `.pot` | `application/vnd.ms-powerpoint` | PowerPoint Template 97-2003 | **Text Extraction** | OLE2 binary stream scanner | Yes | Template text extracted. |
| `.potx` | `application/vnd.openxmlformats-officedocument.presentationml.template` | PowerPoint OpenXML Template | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Template slide layout rendered. |
| `.potm` | `application/vnd.ms-powerpoint.template.macroEnabled.12` | PowerPoint Macro Template | **Converted Preview** | Built-in OpenXML DOM parser | Yes | Macros blocked; template rendered. |
| `.odp` | `application/vnd.oasis.opendocument.presentation` | OpenDocument Presentation | **Converted Preview** | ODP XML slide parser | Yes | Parsed from content.xml into slide pages. |
| `.otp` | `application/vnd.oasis.opendocument.presentation-template` | OpenDocument Template | **Converted Preview** | ODP XML slide parser | Yes | Slide template layout rendered. |
| `.key` | `application/x-iwork-keynote-sffkey` | Apple Keynote Presentation | **Partial Support** | IWork container inspector | Yes | Apple proprietary archive; preview images shown if available. |

---

### F. Web Documents and Structured Text / Code

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.html`, `.htm` | `text/html` | HTML Web Page | **Converted Preview** | Sanitized typography renderer | Yes | Scripts, iframes, and active content stripped for security. |
| `.xhtml` | `application/xhtml+xml` | XHTML Document | **Converted Preview** | Sanitized typography renderer | Yes | XML HTML rendered with typography. |
| `.xml` | `application/xml` | XML Data Document | **Converted Preview** | Code syntax formatter | Yes | Formatted with syntax highlighting and line numbers. |
| `.mht`, `.mhtml` | `message/rfc822` | MHTML Web Archive | **Converted Preview** | Multipart parser | Yes | HTML body extracted and sanitized. |
| `.json` | `application/json` | JSON Document | **Converted Preview** | Syntax formatter | Yes | Indented with line numbers. |
| `.yaml`, `.yml` | `text/yaml` | YAML Document | **Converted Preview** | Syntax formatter | Yes | Formatted with line numbers. |
| `.log` | `text/plain` | Log File | **Converted Preview** | Monospace formatter | Yes | Monospace presentation with line numbers. |
| `.ini`, `.conf` | `text/plain` | Configuration Files | **Converted Preview** | Monospace formatter | Yes | Monospace syntax formatted. |
| `.properties` | `text/plain` | Java Properties | **Converted Preview** | Monospace formatter | Yes | Key-value syntax formatted. |
| `.sql` | `application/sql` | SQL Script | **Converted Preview** | Code syntax formatter | Yes | SQL keywords highlighted with line numbers. |
| `.js`, `.ts` | `application/javascript` | JavaScript / TypeScript | **Converted Preview** | Code syntax formatter | Yes | Treated strictly as untrusted text; never executed. |
| `.py`, `.java`, `.kt` | `text/x-source` | Python / Java / Kotlin | **Converted Preview** | Code syntax formatter | Yes | Keywords highlighted with line numbers. |
| `.c`, `.cpp`, `.h` | `text/x-c` | C / C++ Source & Header | **Converted Preview** | Code syntax formatter | Yes | Keywords highlighted with line numbers. |
| `.sh`, `.bat`, `.ps1` | `text/plain` | Shell / Batch / PowerShell | **Converted Preview** | Code syntax formatter | Yes | Displayed as text; never executed. |

---

### G. Image and Scanned Documents

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.jpg`, `.jpeg` | `image/jpeg` | JPEG Image Document | **Image Preview** | Memory-safe bitmap decoder | Yes | Subsampled zoom & pan; OCR framework ready. |
| `.png` | `image/png` | PNG Graphic Document | **Image Preview** | Memory-safe bitmap decoder | Yes | Lossless alpha channel rendering with zoom. |
| `.webp` | `image/webp` | WebP Image Document | **Image Preview** | Platform decoder | Yes | Modern compressed raster preview. |
| `.bmp` | `image/bmp` | Bitmap Graphic | **Image Preview** | Platform decoder | Yes | Direct bitmap canvas rendering. |
| `.gif` | `image/gif` | GIF Graphic Document | **Image Preview** | Platform decoder | Yes | First frame raster preview. |
| `.tif`, `.tiff` | `image/tiff` | TIFF Scanned Document | **Image Preview** | Platform TIFF decoder | Yes | High-resolution scanned document preview. |
| `.heic`, `.heif` | `image/heic` | High Efficiency Image | **Image Preview** | Hardware decoder (Android 9+) | Yes | Decoded via platform hardware decoder. |
| `.avif` | `image/avif` | AVIF Image File | **Image Preview** | Hardware decoder (Android 12+) | Yes | Modern AV1 image decode. |
| `.ico` | `image/x-icon` | Windows Icon | **Image Preview** | Platform decoder | Yes | Icon bitmap preview. |
| `.svg` | `image/svg+xml` | Scalable Vector Graphic | **Converted Preview** | Vector sanitizer | Yes | Sanitized against embedded scripts; vector rendered. |

---

### H. Archives and Document Packages

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.zip` | `application/zip` | ZIP Archive Package | **Converted Preview** | Safe Archive Engine | Yes | Safe browsing; Zip-slip and zip-bomb protected; tap files to open. |
| `.tar` | `application/x-tar` | Tape Archive | **Converted Preview** | Safe Archive Engine | Yes | Safe entry browsing without extracting to disk. |
| `.gz` | `application/gzip` | Gzip Archive | **Converted Preview** | Stream decoder | Yes | Decompresses inner stream up to bounded memory limit. |
| `.7z` | `application/x-7z-compressed` | 7-Zip Archive | **Partial Support** | Archive container sniffer | Yes | Archive signature detected; safe listing provided. |
| `.rar` | `application/vnd.rar` | RAR Archive | **Partial Support** | Archive container sniffer | Yes | Archive signature verified; safe container listing. |
| `.bz2`, `.xz` | `application/x-bzip2` | Bzip2 / XZ Compressed | **Partial Support** | Archive container sniffer | Yes | Container metadata verified. |

---

### I. Desktop Publishing, Diagrams, Notes, and Technical Documents

| Extension | MIME Type | Format Name | Support Level | Engine / Technique | Offline? | Limitations / Notes |
|---|---|---|---|---|---|---|
| `.pub` | `application/x-mspublisher` | Microsoft Publisher | **Partial Support** | Container text extractor | Yes | Proprietary Microsoft binary; text blocks extracted. |
| `.vsd`, `.vsdx` | `application/vnd.visio` | Microsoft Visio Diagram | **Partial Support** | OpenXML / OLE2 extractor | Yes | Diagram shapes, text, and embedded images extracted. |
| `.one`, `.onepkg` | `application/onenote` | Microsoft OneNote | **Partial Support** | Container text extractor | Yes | Proprietary binary notes; text sections extracted. |
| `.qxd` | `application/vnd.quark.quarkxpress` | QuarkXPress | **Unsupported** | Compatibility informer | Yes | Proprietary DTP binary; export as PDF recommended. |
| `.indd`, `.pmd` | `application/x-indesign` | Adobe InDesign / PageMaker | **Unsupported** | Compatibility informer | Yes | Proprietary Adobe binary; export as PDF recommended. |
| `.dwg` | `image/vnd.dwg` | AutoCAD Drawing | **Unsupported** | Compatibility informer | Yes | Proprietary CAD binary; export as PDF/DXF recommended. |
| `.dxf` | `image/vnd.dxf` | Drawing Exchange Format | **Partial Support** | ASCII entity parser | Yes | ASCII text entities and layer names parsed. |
| `.odg`, `.fodg` | `application/vnd.oasis.opendocument.graphics` | OpenDocument Drawing | **Converted Preview** | ODG XML parser | Yes | XML shapes and text rendered. |
| `.eml`, `.mht` | `message/rfc822` | Email Message | **Converted Preview** | RFC 822 parser | Yes | Formatted with Subject, From, To, Date, and message body. |
| `.msg` | `application/vnd.ms-outlook` | Outlook Message | **Text Extraction** | OLE2 message extractor | Yes | Message stream text extracted. |
| `.ics` | `text/calendar` | iCalendar Schedule | **Converted Preview** | iCal parser | Yes | Event summary, dates, location formatted into event card. |
| `.vcf` | `text/vcard` | vCard Contact | **Converted Preview** | vCard parser | Yes | Contact names, phones, emails formatted into contact card. |
