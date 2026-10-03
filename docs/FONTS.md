# VuraVision font sources

User-provided families: Kahroba (regular/bold), B Nazanin, B Zar, B Titr, Montserrat, Rubik, Bahnschrift. Appropriate regular/bold faces are selected; thin/black variants are not duplicated as separate families. Static Montserrat/Rubik/Bahnschrift weights are derived from the uploaded variable fonts. Original uploaded files are not modified.

Additional downloaded families and bundled notices:

- vazirmatn:
  - `vazirmatn_regular.ttf`: https://raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/ttf/Vazirmatn-Regular.ttf; SHA-256 `b69fd4c680b8f3f225feabcc655a2c585d97627b8f5f5c0f9985e894069f3a56`
  - `vazirmatn_bold.ttf`: https://raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/ttf/Vazirmatn-Bold.ttf; SHA-256 `f635fdbea28f265de395ba83b4b1570dcf2f58d13c65469e61903b1c2d2ae723`
  - `vazirmatn_OFL.txt`: https://raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/OFL.txt; SHA-256 `17e355067c8284f47743a1ee3b1ef7ff684ff0601eda357f9353b10b3016ab31`
- sahel:
  - `sahel_regular.ttf`: https://raw.githubusercontent.com/rastikerdar/sahel-font/master/dist/Sahel.ttf; SHA-256 `5de2fe8cd1995f10fb5a570b66e3ff40183f16bc8c692519b61e8c0281679675`
  - `sahel_bold.ttf`: https://raw.githubusercontent.com/rastikerdar/sahel-font/master/dist/Sahel-Bold.ttf; SHA-256 `d714fa224c92bc51d0e477337ef69e8818c2eff8f41e6698de35639e3857ae7b`
  - `sahel_LICENSE.txt`: https://raw.githubusercontent.com/rastikerdar/sahel-font/master/LICENSE; SHA-256 `4de56cc5fc458113a5704ec4d850191e640396008a6e45ff2a70d67ea6c5c74f`
- shabnam:
  - `shabnam_regular.ttf`: https://raw.githubusercontent.com/rastikerdar/shabnam-font/master/dist/Shabnam.ttf; SHA-256 `7c14586fe687065babbf04a9815a5fc607fcd01bf356a53c988b644f2f2654ad`
  - `shabnam_bold.ttf`: https://raw.githubusercontent.com/rastikerdar/shabnam-font/master/dist/Shabnam-Bold.ttf; SHA-256 `4e7320e31b4654e4a48ffc52e09487cd4ac026169be49badcbd6093e375b3ac5`
  - `shabnam_LICENSE.txt`: https://raw.githubusercontent.com/rastikerdar/shabnam-font/master/LICENSE; SHA-256 `1db30dc6cadf551c27ad2547ec24a20c8ecd5440e458add9067ae70895151c52`

The bundled manifest records every shipped TTF's hash, size, weight and Persian glyph coverage. 21 static TTF faces total 2,637,118 bytes. System families use Android's installed faces. All runtime font loading is local.

## Verification

FontTools validated tables, static weights and required Persian glyphs/digits for all Persian faces. Offline source/XML/guide checks passed. Android verification for 1.9.4 succeeded with SDK 35, JDK 17 and Gradle 8.11.1: 119 unit tests across 14 suites, zero failures/errors/skips; lintDebug and lintRelease, zero errors (671 warnings in each variant); assembleDebug and assembleDebugAndroidTest; debug APK ZIP alignment. The font settings preview was rendered and visually inspected. Device tests were compiled but not executed on a device/emulator. Physical-panel speed/flicker behavior and production signing are not verified locally. See VERIFICATION_1.9.4.json.
