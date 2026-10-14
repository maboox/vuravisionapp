# Third-party notices

Third-party licenses apply independently of the license chosen for this application's source.

Kotlin standard library (JetBrains), Material Components for Android (Google), Gson (Google), ZXing (ZXing authors), exp4j (Frank Asseg), AndroidX and applicable Google supporting open-source libraries are licensed under Apache License 2.0. NanoHTTPD is licensed under BSD 3-Clause. Google ML Kit / Google Play services binaries and language models are governed by their own SDK/model terms, not relicensed as open-source app code.

The complete Apache 2.0 text and NanoHTTPD license are in `docs/licenses`. Build-only tools and test libraries are not packaged into the application runtime. The Gradle wrapper is distributed under Apache 2.0.

[ML Kit terms and privacy](https://developers.google.com/ml-kit/terms)

The native labs and games were derived from two HTML reference documents supplied by the user for this project; the HTML files themselves are no longer packaged. Bundled Latin ML Kit OCR has Google SDK/model terms, not an open-source model license.

Application icons are generated from Phosphor Icons 2.1.1 (Bold and Fill weights) by `scripts/generate-icons.py`, under the MIT license (`docs/licenses/Phosphor-MIT.txt`). The protractor glyph (`vura_protractor`) is a project-specific addition in the same style.

## PDFBox-Android

PDF modification uses TomRoush PDFBox-Android 2.0.27.0, a port of Apache PDFBox 2.0.27, under the Apache License 2.0. Source and license: https://github.com/TomRoush/PdfBox-Android . Library fonts/assets retain their bundled notices. Original PDF page content is retained; application annotations are appended as a fixed overlay.

## Natural Earth

The map of Iran and its provinces (`app/src/main/assets/maps/iran.json`) is simplified from Natural Earth 1:10m Admin 1 – States, Provinces. Natural Earth data is in the public domain (https://www.naturalearthdata.com/about/terms-of-use/).
