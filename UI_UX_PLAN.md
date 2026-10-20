# CloudBox — Remaining UI/UX Work

The earlier design and implementation work has delivered adaptive navigation, Material 3 Expressive theming, settings persistence, responsive filter layouts, file sorting, media previews, a reusable link-intake card, and media metadata controls. Those completed items are intentionally omitted from this active roadmap.

The original screenshot review covered Home, Explorer, Downloads, Settings, and populated grid/list views at 1080 × 2400. Because it used static captures, interactions and other device configurations still need runtime verification. Source screenshots: [Filebin share](https://filebin.net/19set94flvw101ua).

## Priority 0 — Device and accessibility verification

- On a real device or emulator, inspect Home, Explorer, Downloads, Settings, and the media viewer at 320 dp and 360 dp widths with system text at 1.3× and 2×. Confirm labels and content remain readable, controls reflow, and nothing is clipped.
- Verify the IME does not cover the link submission action. Test gesture and three-button navigation, display cutouts, landscape, and a wide window. Confirm system bars restore correctly when leaving the media viewer.
- Use TalkBack to check navigation, filter selection, saved-link actions, file selection, media metadata toggle, and download progress. Confirm selected and error states are announced and icon-only controls have useful labels.
- Compare light and dark themes with dynamic color both enabled and disabled. Check contrast for secondary text, selected filters, errors, and controls over media.

## Priority 1 — File cards and media actions

- Review grid and list cards for consistent filename, type, size, and date information, including long names and enlarged text.
- Ensure video tiles use one clear play affordance and that download and selection actions do not obscure previews or compete for touch focus.
- Verify multi-selection presents the selected count and only valid group actions, with a selected state that does not rely on color alone.

## Priority 1 — Settings and Downloads at constrained heights

- Scroll Settings to its final item at small window heights and enlarged text. Check that the theme choices remain usable and the final note clears the navigation area.
- Check all download status filters at narrow widths and larger text. For populated downloads, review filename, status, progress, speed/size, and available actions together.
- Confirm completed downloads offer a clear open/show-file action where supported. Test filtered-empty recovery and the “Show all” action.

## Priority 2 — Loading, error, and download lifecycle

- Exercise Explorer loading, offline, empty-search, and error/retry states. Add skeleton loading or pull-to-refresh only where they improve feedback without obscuring recovery actions.
- Test download pause, resume, cancel, network loss, process restart recovery, and grouped-download behavior. Verify system notifications match in-app status and actions.

## Validation sequence

1. Re-capture Home, empty Explorer, populated Explorer in grid/list, Downloads, Settings, and media viewer on the same device configuration as the supplied screenshots.
2. Repeat at narrow widths, larger text, light/dark themes, and dynamic color on/off. Scroll screens and filter rows to their ends.
3. Test navigation modes, the IME, display cutouts, landscape, and a wide window. Verify edge-to-edge insets and viewer system-bar transitions.
4. Run Compose tests for link validation and error recovery, filter visibility/reset, file-card actions, selection, viewer metadata visibility, and download progress. Finish with a short end-to-end usability pass.

## Design references

- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)
