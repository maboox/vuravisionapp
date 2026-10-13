# VuraVision 1.15.0 · source release

Version code 25. The application ID, production signing secrets, Gradle/SDK versions and existing dependencies remain unchanged. This release preserves the 1.14.1 build-verification repair and the prior whiteboard features.

## Interface corrections

Only the main Menu / Files / Undo / Redo / Share toolbar sits at the bottom-right, beside Hide toolbars. Object actions are anchored above or beside the selection and reposition with the viewport. Ordinary dialogs are centered. Nested navigation uses a Back arrow in the title row, including labs, games, guide and settings. Lab/game Close, save-to-board and gameplay actions remain at the bottom. The pie-menu toolbar button is removed: enable/configure it in normal Settings and open it with the five-finger double tap.

## Experimental local rooms

Normal Settings contains Device profile: a local name, opaque color and emoji or optional photo. There is no account. The profile is exchanged only while in a room. Rooms remain in hidden Engineering: About VuraVision → double tap logo → wait 1–3 seconds → double tap again → Shared room.

A host shares the current whiteboard. Guests find the service through Android NSD, scan a QR using the optional camera, read a QR image, or enter an invitation address. All devices need the same local network (Wi-Fi, wired LAN or hotspot); QR transfers the address and invitation token. This implementation has no Internet relay or automatic Wi-Fi provisioning.

Every guest initially waits for explicit host approval and receives no lesson, selection leases or drawing previews. The host can grant:

| Role | Page, camera and tools | Undo / Redo |
| --- | --- | --- |
| View | Follow the host; cannot edit | No editing history |
| Control | Share the host page, camera and tool settings | Host and controller share the host history |
| Collaborate | Independent current page, camera and tools; shared board content | Separate history for each session |

Live ink/shape previews, names and selection outlines use profile colors. Selection leases are first-come, renew while selected and expire after interruption. Remote selection prevents local object edits. Last activity fades within 60 seconds; identical idle heartbeats do not revive it. Host time is projected onto the local monotonic clock so clock differences between devices do not distort fading.

The host serializes conditional object edits. Conflicts return receipts and a canonical snapshot; Undo is rejected when its inverse would overwrite a subsequent edit by another participant. Concurrent appends keep host arrival order; deletion Undo restores the original stacking position. Structural edits require the current revision. There is a bounded incremental journal with snapshot fallback.

Guest outboxes are idempotent and reconnect after short interruptions. Private guest credentials and outstanding operations are saved separately from lessons in the app-private recovery file. Reconnect to previous room resumes that session after reopening. The first approved shared board becomes a new local project after saving the prior board. Leaving keeps the latest local board; leaving as host closes the room. Switching projects or restarting the UI is blocked while in a room.

Images and PDF objects on the whiteboard transfer with the document. The separate PDF reading workspace, microphone, recognition models and device touch calibration remain local. Profile photos are resized; QR image input, JSON and asset sizes are bounded. Room invitations accept literal local IPv4 addresses only; the existing HTTPS policy for Internet services stays unchanged. LAN room transport uses local sockets and is intended for a trusted classroom network.

Current bounds: 16 sessions including host; 12 MiB document JSON; 64 MiB per asset; 30 history entries per author; 128 queued local operations; 32 operations per transport request; 256 journal entries.

## Verification status

31 new unit tests cover the room engine, actual localhost HTTP transport and UI integration. All previous tests remain enabled (244 total unit-test annotations). Offline source/XML/data/font and workflow checks are recorded in VERIFICATION_1.15.json. Android compilation, unit-test execution, lint, signed APK and emulator/physical-device acceptance are still pending: local Gradle download fails with `Network is unreachable`. This source ZIP is not a claim of a passed Android build.
