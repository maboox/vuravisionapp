# Dependencies and privacy

| Component | Version / decision | Consideration |
| --- | --- | --- |
| Kotlin / AGP / Gradle | 2.1.20 / 8.9.2 / 8.11.1, Java 17 | Pinned, tested build toolchain |
| Android Canvas / PdfRenderer / PdfDocument | Platform APIs, app minimum 26 | Native drawing and PDF rendering, not a PDF editing SDK |
| ML Kit digital ink | 19.0.0 | Google SDK/model terms, not an open-source recognizer; actual model downloads |
| Gson | 2.12.1 | Apache 2.0; schema validation is independent of JSON parsing |
| ZXing core | 3.5.3 | Apache 2.0; offline QR generation |
| NanoHTTPD | 2.3.1 | BSD 3-Clause; old release, restricted to one read-only tokenized file |
| exp4j | 0.4.8 | Apache 2.0; old/archived upstream, bounded expression input, no scripting or CAS |
| JUnit / Robolectric | 4.13.2 / 4.14.1 | Test-only; native view/image tests on host, not actual panel tests |
| AndroidX test runner / ext JUnit | 1.6.2 / 1.2.1 | Instrumented device tests |

The archived/old numeric and HTTP libraries are beta maintenance risks to review before production expansion. Static ELF/package alignment checks do not replace testing on a 16 KB page-size device.

Lessons and imported media remain in app-private storage. There is no account, ad SDK or custom analytics backend. The user chooses export destinations; Android backup is disabled, so uninstall removes private lessons.

ML Kit downloads the language models you select. Recognition then runs on-device. The Google SDK may send operational diagnostics under its applicable terms. Model storage and checksums are handled by that SDK; the app does not claim an independently audited model package or an offline first model installation.

LAN QR sharing serves the exported PDF over HTTP. Anyone with the token URL and local-network access can download it while active. Use a trusted classroom network. Client isolation can block transfers. Closing the dialog stops sharing; expiry is ten minutes. It does not publish a public Internet link.

Primary references: [ML Kit integration](https://developers.google.com/ml-kit/vision/digital-ink-recognition/android), [ML Kit terms](https://developers.google.com/ml-kit/terms), [Gson](https://github.com/google/gson), [ZXing](https://github.com/zxing/zxing), [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd), [exp4j](https://github.com/fasseg/exp4j), [Android command-line builds](https://developer.android.com/build/building-cmdline), [apksigner](https://developer.android.com/tools/apksigner), [Gradle action](https://github.com/gradle/actions), [artifact action](https://github.com/actions/upload-artifact).
