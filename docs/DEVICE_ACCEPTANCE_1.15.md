# 1.15 checks on real devices

Placement
- Bottom-right toolbar has no Pie button; Settings → Pie menu still works; five-finger double tap opens it.
- Main menu, Settings and every settings dialog open centred. In nested menus Back is a top-left arrow (pointing right in Persian); the bottom button closes.
- Select an object: the action bar appears above it (below when the object is at the top edge), follows it while dragging and panning, and is hidden in focus mode.
- Labs/games: Close and Add to board remain at the bottom; Back is at the top-left.

Connected displays (two devices on one Wi-Fi, then one device's hotspot)
- Edit profile (name, color, emoji, photo). Create a room on A; B sees it under Join a room. Picking it shows a request on A; Let in / Decline both work.
- Scan A's QR from B (camera permission prompt, then direct entry). Repeat with Enter address + code.
- View: B follows A's page and zoom, cannot draw, sees A's strokes live. Control: B draws on A's board, page changes go both ways, B's Undo undoes on both. Collaborate: independent pages/zoom/tools; Undo on each only reverts own work.
- While B draws, A sees the live stroke and a named cursor; after lifting, a halo in B's color stays and fades within one minute.
- Select an object on A: B sees A's colored outline and name and cannot select/erase it. Release on A; B can now take it.
- Insert an image and a PDF object on B: they appear on A and on a third device.
- Change roles while drawing; remove a person; end the room; leave as guest with and without "Keep a copy". Guest's own lesson returns.
- Turn Wi-Fi off on B: B returns to its own lesson with a message within ~45 s; A drops B from the list.
