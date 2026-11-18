# Security Policy

Folia opens files that come from other people, so security is part of the design, not an afterthought.

## Supported versions

Only the **latest stable release** receives security fixes. Please update before reporting.

| Version | Supported |
|---------|-----------|
| 1.x (latest) | Yes |
| < 1.0 | No |

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

1. Open the repository's **Security** tab and choose **Report a vulnerability** (GitHub private vulnerability reporting), or
2. If that is unavailable, open an issue titled "Security contact request" **without details**, and a private channel will be arranged.

Please include: the Folia version, Android version, the file type involved, and steps to reproduce. A sample file helps a lot
(send it privately, never attach exploit files to a public issue).

You can expect an acknowledgement within 7 days and a fix or mitigation plan within 30 days for confirmed issues.
Reporters are credited in the release notes unless they prefer to stay anonymous.

## What Folia does to stay safe

- **No network access.** The app does not request the `INTERNET` permission and sets `usesCleartextTraffic="false"`.
  Documents, annotations and settings never leave the device. No analytics, ads or crash reporters.
- **Scoped storage only.** Folia reads files through Android's Storage Access Framework (the file picker, "Open with", or folders you
  grant in the Library). It does not request broad storage permissions.
- **Hardened parsing of untrusted files.**
  - Zip-bomb protection: every ZIP part is read through a byte limit (48 MB per XML part, 8 MB per image, 48 MB of images per document)
    and packages with more than 20,000 entries are refused.
  - Archive browsing limits entry count and total uncompressed size, and rejects path traversal (Zip-slip).
  - XML parsers are created with secure processing and DOCTYPE/external-entity loading disabled (XXE and "billion laughs" protection).
  - Documents larger than 512 MB are refused before they fill the cache.
- **No active content.** Macros, scripts and embedded objects in Office files are never executed; HTML is converted to static text.
- **Private working copies.** The open document is copied to the app's private cache and deleted when the app closes.
  Sketches are stored in app-private storage, apart from the original file.
- **No app backup** (`allowBackup="false"`), so private data is not copied to cloud backups.
- **Signed releases with checksums.** Each release publishes `SHA256SUMS.txt`; the APKs are signed and CI verifies the signature
  before publishing.

## Signing key

Releases are signed with the public sideload key committed in `signing/` (see `signing/README.md`). This keeps updates installable
without uninstalling, but it means **the signature proves consistency, not publisher identity**. Anyone can build an APK that is
signed with this key. Always download Folia from this repository's Releases page and compare the SHA-256 checksum.
A maintainer who wants a private key can add the `FOLIA_KEYSTORE_*` secrets; CI will then sign with it automatically.
