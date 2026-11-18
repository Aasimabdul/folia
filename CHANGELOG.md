# Changelog

All notable changes to Folia are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-19

First stable release.

### Added
- **Page style settings**: pages keep their original colours in dark mode (new default). Choose *Original colours*, *Match app theme*,
  *Always dark pages* or *Sepia*, plus a switch to soften bright white pages at night.
- **Page thumbnails** at the bottom of the reader; tap one to jump to that page.
- **Full-screen reading**: the status and navigation bars are hidden while reading and return with an edge swipe; handles display cutouts.
- **Library file manager**: add folders with the system picker, browse nested folders, breadcrumb, search, sort by name/date/size/type,
  list or grid view, rename, share and delete, with loading, empty and permission-lost states.
- **Sketch / Annotate** on PDFs and images: pen, highlighter, eraser, 5 colours, 3 thicknesses, undo/redo, clear, stylus-only option.
  Sketches autosave separately from the file and are restored when you reopen it.
- **Save annotated copy** as a new PDF through the system file picker, with a re-open check and an honest description of where it was saved.
- **Text editor** for TXT, Markdown, code and other plain-text files, with undo/redo, unsaved-changes guard, overwrite confirmation,
  Save as, and read-back verification after saving.
- Security and project files: `SECURITY.md`, `PRIVACY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `THIRD_PARTY_NOTICES.md`,
  issue and pull request templates, Dependabot and CodeQL.
- Release assets now include `SHA256SUMS.txt`.

### Changed
- The bottom page slider was removed; navigation uses thumbnails, previous/next buttons and the page counter.
- Pages are laid out with exact bounds so sketches align at any zoom.
- Opened documents are copied to private cache with a 512 MB limit, and the copy is deleted when the app closes.

### Security
- Zip-bomb limits on every ZIP part, embedded-image budget, entry-count limit.
- XML parsing with DOCTYPE and external entities disabled.
- `usesCleartextTraffic="false"` and removal of a leftover setup script.

## [0.3.3] - 2026-11-17
- All releases signed with one fixed key so updates install normally.

## [0.3.2] - 2026-11-17
- "Open with" support, theme switch (System/Light/Dark) and a new dark palette.

## [0.3.1] - 2026-11-16
- Signed release APKs and the Folia launcher icon.
