# Folia 🌿 — Universal Document Reader for Android

A modern, fast, offline, and privacy-first **Universal Document Reading Platform** for Android.  
**Zero ads. Zero tracking. Zero network permissions required.**

---

## 📖 Overview

**Folia** is engineered to open, parse, render, search, and read the widest practical range of document and file formats in a single unified application. It eliminates the need for separate reader apps, cloud converters, or heavy office suites that compromise privacy and battery life.

Built with **Kotlin**, **Jetpack Compose (Material 3)**, and **MuPDF (Fitz JNI core)**, Folia maintains an ultra-low memory footprint and runs smoothly on entry-level Android devices with 4 GB of RAM.

---

## 📂 Format Capabilities at a Glance

Folia adheres to strict capability reporting: every format has an explicit status (**Native Rendering**, **Library Rendering**, **Converted Preview**, **Text Extraction**, **Image Preview**, **Partial Support**, or **Unsupported**).

| Category | Formats Covered | How It Works |
|---|---|---|
| **A. Word Processing** | `.docx`, `.doc`, `.docm`, `.odt`, `.rtf`, `.txt`, `.md`, `.rst`, `.tex`, `.wpd` | Parsed offline to responsive styled HTML with typography and font scaling |
| **B. PDF & Fixed Layout** | `.pdf`, `.xps`, `.oxps`, `.djvu`, `.chm` | High-fidelity MuPDF vector core; TOC outline navigation and in-document search |
| **C. eBooks & Comics** | `.epub`, `.mobi`, `.azw`, `.azw3`, `.fb2`, `.cbz`, `.cbr` | Reflowable eBook reading with dark/sepia themes; streaming comic page viewer |
| **D. Spreadsheets** | `.xlsx`, `.xls`, `.xlsm`, `.ods`, `.csv`, `.tsv` | Multi-worksheet tabs, shared strings pool, responsive HTML tables with zebra striping |
| **E. Presentations** | `.pptx`, `.ppt`, `.ppsx`, `.odp` | Visual slide deck cards with slide numbers, titles, tables, and embedded images |
| **F. Code & Markup** | `.html`, `.xml`, `.json`, `.yaml`, `.py`, `.java`, `.kt`, `.c`, `.cpp`, `.js`, `.ts`, `.sql`, `.sh`, `.log` | Monospace code viewer with syntax line numbers and keyword styling; safe HTML |
| **G. Images & Scanned** | `.jpg`, `.jpeg`, `.png`, `.webp`, `.bmp`, `.gif`, `.tiff`, `.svg`, `.ico`, `.heic` | Memory-safe downsampled bitmap preview; sanitized SVG vector graphics |
| **H. Archives** | `.zip`, `.tar`, `.gz`, `.7z`, `.rar` | Safe archive browser; Zip-slip and Zip-bomb protected; tap files to open |
| **I. Technical & Notes** | `.eml`, `.msg`, `.ics`, `.vcf`, `.odg`, `.dxf`, `.pub`, `.vsd`, `.one` | Email, calendar, and vCard summaries; honest compatibility disclosure for CAD/DTP |

> 📌 **Complete Matrix:** See [`FORMATS.md`](file:///home/abdulaasim/Downloads/docreader/FORMATS.md) for the exhaustive table of all 100+ extensions, MIME types, and technical notes.

---

## ✨ Features

- **🛡️ 100% Private & Offline**: No `android.permission.INTERNET` requested. Documents never leave your device.
- **🔒 Active Content Protection**: Macros, scripts, and embedded active content are strictly blocked.
- **🔍 Find in Document**: In-document text search with match counts and next/previous match navigation.
- **📑 Table of Contents (TOC)**: Outline drawer to jump instantly between chapters and sections.
- **🎨 Reading Themes**:
  - **Dark**: `#1E1E1E` background, comfortable eye-friendly dark surface.
  - **Light Paper**: `#FFFFFF` crisp reading surface.
  - **Warm Sepia**: `#FBF0D9` amber tone designed to reduce eye strain.
- **🔤 Reflow & Text Resizing**: Increase or decrease font size (`A-` / `A+`) on reflowable documents (Word, EPUB, MOBI, Markdown, Text).
- **🔎 Zoom & Pan**: Pinch-to-zoom or double-tap to inspect small text, diagrams, and high-res comic art.
- **⚡ Ultra-low RAM Footprint**:
  - **Byte-budgeted LRU cache (`PageCache`)**: Limits bitmap memory to 8–32 MB max regardless of whether a document has 10 or 3,000 pages.
  - **Per-ABI APK splits**: Ships only native binaries for your phone's architecture (`arm64-v8a` / `armeabi-v7a`).
- **📚 Library & Recents**:
  - Automatically records recently opened files, reading progress (Page X of Y), and timestamps.
  - Filter by category: *All*, *PDF & Fixed*, *Word & Docs*, *eBooks & Comics*, *Spreadsheets*, *Presentations*, *Text & Code*, *Images*, *Archives*.

---

## 🏗️ Architecture

```
app/src/main/java/com/simbuild/folia/
├── MainActivity.kt                  # Navigation router & SAF OpenDocument intent handler
├── ReaderScreen.kt                  # Reader UI (search, TOC, scrubber, zoom, themes)
├── model/
│   └── RecentDocument.kt            # Document metadata & reading progress model
├── data/
│   └── RecentDocumentsRepository.kt    # SharedPreferences persistence (JSON)
├── engine/
│   ├── DocEngine.kt                 # Format-agnostic interface & SAF URI streaming
│   ├── FormatRegistry.kt            # 100+ format registry & magic-byte sniffer
│   ├── MuPdfEngine.kt               # MuPDF Fitz JNI bindings & page lifecycle management
│   ├── FormatConverter.kt           # Word (.docx, .doc, .rtf, .odt) to HTML converter
│   ├── SpreadsheetConverter.kt      # Excel (.xlsx, .xls), ODS, CSV/TSV table converter
│   ├── PresentationConverter.kt     # PowerPoint (.pptx, .ppt), ODP slide deck converter
│   ├── StructuredTextConverter.kt   # Code, safe HTML, Email, vCard, Calendar converter
│   ├── ArchiveConverter.kt          # Safe archive browser (Zip-slip & Zip-bomb safe)
│   ├── ImageDocEngine.kt            # Memory-safe image decoder & sanitized SVG
│   ├── UnsupportedDocEngine.kt      # Honest compatibility informer for proprietary DTP/CAD
│   └── PageCache.kt                 # Byte-budgeted LRU Bitmap cache
└── ui/
    ├── home/
    │   └── HomeScreen.kt            # Library screen, filter chips, search & cards
    └── theme/
        ├── Color.kt                 # Format-specific badge colors
        └── Theme.kt                 # Material 3 light/dark color schemes
```

---

## 🧪 Testing & Test Fixtures

Automated unit tests and fixtures are located in:
- `app/src/test/java/com/simbuild/folia/FormatRegistryTest.kt`
- `app/src/test/java/com/simbuild/folia/FormatSnifferTest.kt`
- `app/src/test/java/com/simbuild/folia/SecurityAndConvertersTest.kt`
- `app/src/test/java/com/simbuild/folia/FixtureVerificationTest.kt`
- `app/src/test/resources/fixtures/`: Real sample test fixtures for `.docx`, `.xlsx`, `.pptx`, `.csv`, `.tsv`, `.md`, `.json`, `.xml`, `.kt`, `.eml`, `.vcf`, `.ics`, and `.txt`.

To execute the test suite:
```bash
./gradlew testDebugUnitTest
```

---

## 🛠️ Build & Installation

### Requirements
- Android Studio Ladybug / Meerkat or later
- Android SDK 35 (minSdk 24)
- JDK 17+

### Steps
1. Clone this repository:
   ```bash
   git clone https://github.com/Aasimabdul/folia.git
   cd folia
   ```
2. Build debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Release APKs with per-ABI optimization will be generated at:
   `app/build/outputs/apk/release/`

---

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)** in accordance with the MuPDF library licensing terms.
