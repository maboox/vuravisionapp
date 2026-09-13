# Dependency research

The repository intentionally minimizes third-party runtime dependencies in the first buildable baseline. This lowers licensing, native ABI, 16-KB page-size and maintenance risk.

| Capability | Candidates researched | License | Maintenance | Offline | Android fit | Selected solution | Reason |
|---|---|---|---|---|---|---|---|
| Drawing/ink | Compose Canvas, Android Canvas/View, Skia/native engines | Android APIs / varies | Android API maintained | Yes | Excellent | Custom Android `View` + `Canvas` | Low-latency direct MotionEvent access, historical samples, no native ABI dependency |
| PDF render | Android `PdfRenderer`, Pdfium forks, open PDF viewers | Android API / varies | Platform maintained | Yes | Excellent | Android `PdfRenderer` | No third-party native binary; Android 14+ safe baseline |
| QR encode | ZXing core, zxing-android-embedded forks | Apache-2.0 | ZXing core remains broadly used; some Android forks are archived | Yes | Excellent | `com.google.zxing:core:3.5.3` | Encoding only; avoids camera/UI dependency |
| Local LAN share | NanoHTTPD, Ktor server, raw `ServerSocket` | BSD/Apache/custom | Mixed | Yes | Good | Small scoped `ServerSocket` HTTP responder | Only serves one explicit exported file/token; avoids server framework weight |
| Persistence | Room, kotlinx.serialization, JSON+ZIP | Apache-2.0 / Apache-2.0 / platform | Good | Yes | Excellent | Versioned ZIP container + JSON | Human-inspectable and migration-friendly `.vura`; asset embedding |
| Math evaluation | SymPy embedding, mXparser, custom restricted parser | BSD / Apache/commercial variants / own | Mixed | Yes | Mixed | Small deterministic parser for V1 baseline | No heavyweight Python runtime; commercial-license safe; backend interface can be replaced |
| Handwriting recognition | ML Kit Digital Ink, ONNX Runtime + handwriting models, PaddleOCR ecosystem | proprietary service terms / MIT / Apache-2.0 + model-specific | Good | Usually | Mixed | **Not bundled in this baseline** | Model/license/quality validation is required, especially Persian; no fake recognition is exposed |
| Math OCR | pix2tex-like models, CROHME models, ONNX ports | model-specific | Mixed | Possible | Needs validation | **Not bundled in this baseline** | Must validate model license, size, Android inference and Persian/mixed-direction behavior |
| 3D | Filament, OpenGL ES | Apache-2.0 / platform | Good | Yes | Excellent | Deferred | Not needed for core whiteboard build gate; avoid superficial 3D feature |
| UI | Compose/Material, classic Views, vendor UI inspiration | Apache-2.0/platform | Good | Yes | Excellent | Classic Views + custom VuraVision theme for baseline | Smaller toolchain surface for first CI build; migration to Compose design-system module remains straightforward |

Notes:
- JourneyApps ZXing Android Embedded is Apache-2.0, but the project needs QR generation rather than scanning, so ZXing Core is enough.
- A 2026 archived ZXing Android fork was explicitly rejected as a primary dependency.
- Native Pdfium forks were not selected because their maintenance/ABI/16-KB-page-size matrix must be audited per fork.
