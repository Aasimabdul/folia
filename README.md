# Folia 🌿

A modern, fast, and privacy-first document and eBook reader for Android.  
**Zero ads. Zero tracking. Zero internet permissions required.**

---

## 📖 Overview

**Folia** (Latin for *leaves / pages*) is built to open virtually any document format smoothly on mobile devices without relying on heavy cloud converters or battery-draining office suites.

By leveraging a single native C core ([MuPDF](https://mupdf.com)) combined with Jetpack Compose (Material 3), Folia provides snappy reading, reflowable eBook formatting, and low RAM consumption even on 4 GB entry-level Android devices.

---

## 📂 Supported Formats

| Category | Formats | Engine / Technique |
|---|---|---|
| **Documents & Print** | PDF, XPS, OpenXPS | MuPDF native engine |
| **eBooks & Literature** | EPUB, MOBI, FB2, AZW | MuPDF native reflow engine |
| **Office & Productivity** | DOCX, XLSX, PPTX | MuPDF text & structure reflow |
| **Comics & Manga** | CBZ, CBR | Archive stream renderer |
| **Notes & Formats** | Markdown (`.md`), Plain Text (`.txt`), HTML | Styled HTML typography wrapper |
| **Data & Code** | CSV, JSON, XML, Code (`.kt`, `.py`, `.java`, `.cpp`, `.js`) | Monospace & tabular reflow |

---

## ✨ Features

- **🛡️ 100% Private & Offline**: No `android.permission.INTERNET` in `AndroidManifest.xml`. Your documents never leave your phone.
- **📚 Home Library & Recents**:
  - Automatically records recently opened files, reading progress (Page X of Y), and timestamps.
  - Filter by category: *All*, *PDF*, *eBooks*, *Office*, *Comics*, *Text & Code*.
  - Instant title and format search.
  - Pin favorite documents to the top.
- **👓 Immersive Reader Experience**:
  - Distraction-free full-screen reading mode (tap anywhere to toggle toolbars).
  - Smooth vertical scrolling with zero frame drops.
  - Interactive page scrubber and slider to jump instantly to any page.
  - Remembers your exact reading position when you reopen any file.
- **🎨 Reading Themes**:
  - **AMOLED Dark**: Deep `#121212` background, battery-friendly for OLED screens.
  - **Light Paper**: Crisp `#FFFFFF` reading surface with high contrast.
  - **Warm Sepia**: Amber `#FBF0D9` tone designed to reduce eye strain during long reading sessions.
- **🔤 Reflow & Text Resizing**:
  - Increase or decrease font size (`A-` / `A+`) on reflowable documents (EPUB, MOBI, DOCX, Markdown, Text).
- **🔍 Zoom & Pan**:
  - Double-tap or pinch-to-zoom to inspect small text, diagrams, or high-res comic art.
- **🚀 Ultra-low RAM & Storage footprint**:
  - **Byte-budgeted LRU cache (`PageCache`)**: Limits bitmap memory to 8 MB - 32 MB max, regardless of whether a document has 10 or 3,000 pages.
  - **Per-ABI APK splits**: Downloads only native binaries for your phone's architecture (`arm64-v8a` / `armeabi-v7a`).

---

## 🏗️ Architecture

```
app/src/main/java/com/simbuild/folia/
├── MainActivity.kt               # Navigation router & SAF OpenDocument intent handler
├── ReaderScreen.kt               # Reader UI (controls, slider, themes, zoom, cache)
├── model/
│   └── RecentDocument.kt         # Document metadata & reading progress model
├── data/
│   └── RecentDocumentsRepository.kt # SharedPreferences persistence (JSON)
├── engine/
│   ├── DocEngine.kt              # Format-agnostic interface & SAF URI streaming
│   ├── MuPdfEngine.kt            # MuPDF JNI bindings & page lifecycle management
│   ├── FormatConverter.kt        # Markdown / CSV / Code to HTML typography converter
│   └── PageCache.kt              # Byte-budgeted LRU Bitmap cache
└── ui/
    ├── home/
    │   └── HomeScreen.kt         # Library screen, filter chips, search & cards
    └── theme/
        ├── Color.kt              # Folia brand & format-specific badge colors
        └── Theme.kt              # Material 3 light/dark color schemes
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
   git clone https://github.com/<your-username>/folia.git
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

This project is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](https://www.gnu.org/licenses/agpl-3.0.html) in accordance with the MuPDF library licensing terms.
