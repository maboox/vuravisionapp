# VuraVision 1.15.0

Version code 25. Application ID, document format (`.vura`, schema 3), Gradle Wrapper,
dependencies and the four production signing secrets are unchanged.

## Placement fixes

1. The Pie menu button is no longer on the bottom toolbar. Pie menu stays in
   **Settings → Pie menu** and opens with the five-finger double tap.
2. Menus and settings dialogs open in the centre again (1.14 anchored them bottom-right).
3. The selected object's action bar floats beside the object again: above it, or
   below when there is no room, and it follows the object while it moves or the board pans.
4. Back is an icon at the top-left of every nested menu, settings dialog, lab/game screen,
   guide and periodic table (mirrored in Persian). The bottom button now only closes.
5. Kept at the bottom as requested: the main toolbar (menu, files, Undo, Redo, share) and
   the Close/Add-to-board/game controls inside labs and games.

## Connected displays (preview, hidden section)

Reached from **About → tap the logo (engineering) → Connect displays (preview)**.
Details: [COLLABORATION.md](COLLABORATION.md).

- **Profile**: name, color, and an emoji or photo. Stored only on this device; shown only to
  people in the same room.
- **Rooms**: one display creates a room; others join from the list of nearby rooms
  (DNS-SD on Wi-Fi/Ethernet/hotspot), by scanning the room QR code, or by typing the address.
  QR and room code admit directly; picking a room from the list asks the owner to let you in.
- **Roles**, chosen by the room owner per person (and a default for new people):
  - *View*: follows the owner's page and view; cannot edit.
  - *Control*: works on the owner's board as if standing at it — same page, same view, one
    shared Undo/Redo. Page changes go both ways.
  - *Collaborate*: independent tools, page, view and Undo. Each person's Undo only reverts
    their own changes.
- **Presence**: live strokes and a named cursor while someone draws; a soft halo in their
  color around where they last worked (and, for collaborators, where they were looking)
  that fades out over one minute.
- **Selection ownership**: the first person to select an object owns it. Others see a
  colored outline with that person's name and cannot select, move or erase it until it is released.
- A guest works on a copy; the guest's own lesson is restored on leaving and the copy can be
  kept in Recent files.

Limits of the preview: both devices must share a network (same Wi-Fi, or one device's hotspot);
there is no internet relay. The side-by-side PDF workspace is not shared (PDF objects on the
board are). The connection is local and unencrypted; the 6-digit room code or the owner's
approval is required to join.

## Verification

`Release115Test` adds 9 tests (toolbar, centred dialogs, Back icon, floating selection bar,
hidden menu entry, profile round-trip, invite parsing, forwarded Undo, foreign selection locks,
and a host/guest sync and per-person Undo scenario). `Studio114Test` was updated for the Back icon.
See `VERIFICATION_1.15.0.json` for what was and was not checked locally.
