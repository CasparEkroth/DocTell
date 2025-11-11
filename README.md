# DocTell

*Listen to your own PDFs.* A small Android app written in **Java** that lets you **add PDFs and have them read aloud** using Android's built‑in Text‑to‑Speech.

## Features
- Add PDFs from storage or the system share sheet
- Read aloud with Text‑to‑Speech (play/pause/stop)
- Simple library grid with cover previews
- Remembers last opened page per document
- Works offline (no accounts, no tracking)

## Build & Run
1. Open the project in **Android Studio** (latest stable).
2. Run on a device/emulator with a TTS voice installed.
3. On first run, grant file access when prompted.

> Code is Java‑based. PDF parsing and previews are handled in helpers like `PdfPreviewHelper`, while reading happens in `ReaderActivity`. Persistent state is stored via `BookStorage`.

## Privacy
Your files stay on your device. Speech is generated locally by the system TTS engine.

## License
The source code of DocTell is released under the [CC BY-NC 4.0 License](./LICENSE).  
You are free to use or modify the code for personal and educational purposes.  
Commercial use, redistribution on app stores, or selling modified versions is **not permitted**.  

For commercial inquiries, contact: casparekroth.dev@gmail.com
