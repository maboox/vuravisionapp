# VuraVision design system

VuraVision uses Material 3 components as an accessible Android foundation, customized for large interactive classroom displays. It does not copy the appearance of a generic Material sample: product identity comes from VuraVision semantic tokens, typography, iconography, spacing and interaction rules.

## Principles

1. **Canvas first.** Controls should be calm and compact enough to preserve teaching space, but never smaller than a reliable touch target.
2. **Large-display legibility.** Primary controls use 48 dp or larger touch targets, strong contrast and short labels.
3. **One visual language.** Whiteboard, Labs, Games, dialogs and floating tools use the same surfaces, shapes and state colors.
4. **Bilingual by construction.** Components must work with English and Persian text, RTL layout and mirrored directional icons.
5. **State must not depend on color alone.** Selected tools combine fill, contrast, border and elevation. Icons have accessible labels and tooltips where appropriate.
6. **Content remains authoritative.** Decoration must not obscure writing, simulations, diagrams or game state.

## Foundation

The implementation uses `com.google.android.material:material:1.12.0` and `Theme.Material3.Light.NoActionBar`. The custom ink engine remains a native Android `View`; Material is used around it rather than replacing its low-latency Canvas path.

Shared Kotlin components and tokens live in `app/src/main/java/com/vuravision/classroom/Design.kt`. Theme resources live in `app/src/main/res/values/colors.xml` and `styles.xml`.

## Semantic color tokens

| Token | Purpose |
| --- | --- |
| Primary / `NAVY` | Brand, selected controls, app chrome |
| Secondary / `TEAL` | Learning actions, positive emphasis, diagrams |
| Tertiary / `ORANGE` | Attention, game accents, highlights |
| `PAPER` | App and catalog background |
| `SURFACE` | Toolbars, dialogs, cards and controls |
| `SURFACE_VARIANT` | Quiet icon controls and grouped content |
| `PRIMARY_CONTAINER` | Selected or emphasized supporting surfaces |
| `OUTLINE` / `OUTLINE_STRONG` | Component boundaries without heavy shadows |
| `DANGER` | Destructive actions only |
| `SUCCESS` | Confirmed positive status |

Feature code should use semantic tokens rather than introduce arbitrary colors. Simulation colors may differ where they encode scientific or game meaning.

## Shape and elevation

- Buttons: 14 dp corners
- Inputs: 12 dp corners
- Catalog cards: 20 dp corners
- Floating tools and dialogs: 22–24 dp corners
- Ordinary surfaces use borders and 0–2 dp elevation
- Floating and modal surfaces may use 6–8 dp elevation

Elevation communicates interaction hierarchy; it is not decorative.

## Components

- `button(...)` returns a branded `MaterialButton` with filled selected and outlined resting states.
- `field(...)` uses a Material text input with VuraVision surface and outline tokens.
- `card(...)` returns a `MaterialCardView` with standardized radius, border and elevation.
- `ActionIcon` is retained as a lightweight custom line-icon view for dense object actions, with Material-like ripple, surface and destructive states.
- Dialogs use `MaterialAlertDialogBuilder` and the `VuraAlertDialog` theme overlay.

## Responsive behavior

- Under 600 dp, labels may collapse where an accessible icon remains.
- At 720 dp and above, Lab and Games shortcuts may appear in the header.
- Tool rows remain horizontally scrollable rather than shrinking targets.
- Catalogs use generous cards and should move to adaptive column counts only after compact Persian and tablet screenshot tests cover the change.

## Validation

GitHub Actions is the authoritative build environment. It runs JVM/Robolectric tests, release lint, release assembly and Android-test APK assembly. Manual workflow dispatch additionally runs API 35 instrumented tests. Robolectric screenshots under `app/build/qa` provide visual review artifacts but do not replace testing on the target VuraVision panel.
