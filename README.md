# DocTell

*Listen to your own PDFs.* A small Android app written in **Java** that lets you **add PDFs and have them read aloud** using Android's built‑in Text‑to‑Speech.

<a href='https://play.google.com/store/apps/details?id=com.doctell.app&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'>
    <img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height="80"/>
</a>

## Features
- Add PDFs from storage or the system share sheet
- Read aloud with Text‑to‑Speech (play/pause/stop)
- Simple library grid with cover previews
- Remembers last opened page per document
- Works offline (no accounts)

## Build & Run
1. Open the project in **Android Studio** (latest stable).
2. Run on a device/emulator with a TTS voice installed.
3. On first run, grant file access when prompted.

> Code is Java‑based. PDF parsing and previews are handled in helpers like `PdfPreviewHelper`, while reading happens in `ReaderActivity`. Persistent state is stored via `BookStorage`.

## Privacy
Your files stay on your device. Speech is generated locally by the system TTS engine.

## License
The source code of DocTell is released under the [AGPL-3.0 License](./LICENSE).
You may view, use, or modify the code for personal and educational purposes.
Commercial use, paid features, subscriptions, ads, or publishing modified versions on app stores is not permitted.
Any redistributed version must remain fully open-source under AGPL-3.0.

For commercial inquiries, contact: [ekrothdev@gmail.com](mailto:ekrothdev@gmail.com)
