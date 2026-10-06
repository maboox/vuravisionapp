# Connected displays — design notes (1.15 preview)

## Files

| File | Purpose |
| --- | --- |
| `CollabProfile.kt` | Local profile, avatar view, profile editor |
| `CollabNet.kt` | Length-prefixed JSON over TCP, server, DNS-SD discovery, invite QR/URI |
| `CollabSync.kt` | Field-level diff/apply of lesson pages, per-view fields excluded |
| `CollabSession.kt` | Room logic: admission, roles, relay, acks, presence, locks, assets |
| `CollabUi.kt` | Hub, room, join and guest screens; board overlay; room chip |
| `QrScanner.kt` | Camera preview + ZXing QR decoding (no new dependency) |

## Model

The room owner (host) is the sequencer. Every device keeps a *shadow* of what it last shared;
after a local change it sends only the changed fields (`ops`). Guests apply their own change
immediately; until the host acknowledges it, incoming values for those same fields are ignored
locally (pending set). The host applies messages in arrival order and relays them, so all copies
converge on the host's order.

Not shared: current page, pane zoom/scroll, active layer (personal), and the PDF side workspace.

Per-person Undo uses the existing snapshot history: a received change is also applied to every
Undo/Redo snapshot (`Store.rebase`), so Undo restores only the local person's work. For *Control*
guests, `Store.remoteHistory` forwards Undo/Redo to the host, whose history is shared.

Images and PDFs referenced by shared objects are sent in 192 KB chunks; the host serves missing
files to later guests.

## Messages

`hello`, `wait`, `welcome`, `denied`, `ops`, `ack`, `presence`, `peers`, `locks`, `role`, `page`,
`view`, `undo`/`redo`, `need`/`asset`, `sync`/`lesson`, `profile`, `ping`, `bye`, `end`.
Protocol version: `COLLAB_PROTOCOL = 1`; DNS-SD type `_vuravision._tcp`.
