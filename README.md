# TileDeck v4.0.0

Direct-execution Android Quick Settings utility hub with automatic SU (KernelSU/Magisk) detection and auto-hiding of root-only tiles.

## SU Auto-Hide Behavior
- On launch (and via **Request SU**), TileDeck runs `su -c id` on a background thread.
- If **SU is granted**: all 65 direct-action tiles (including all 18 root/Moto/system tiles) are unlocked in the Tile Hub, Quick Settings panel, Apps & Lock Hide Tray, and App Hub ContentProvider.
- If **SU is denied or absent**: all 18 root tiles are automatically disabled via `PackageManager.setComponentEnabledSetting()` and hidden from the UI and App Hub API, leaving 47 clean non-root tiles.
