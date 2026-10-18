# CloudBox — Screenshot-Informed UI/UX Improvement Plan

Reviewed on 2026-10-19 from six portrait screenshots (1080 × 2400):

- `Screenshot_20261019_005629.jpg` — Home, no saved links
- `Screenshot_20261019_005631.jpg` — Explorer, empty folder
- `Screenshot_20261019_005633.jpg` — Downloads, empty state
- `Screenshot_20261019_005637.jpg` — Settings
- `Screenshot_20261019_005651.jpg` — Explorer, grid view with video files
- `Screenshot_20261019_005657.jpg` — Explorer, list view with video files

Source: [Screenshot share on Filebin](https://filebin.net/19set94flvw101ua). This is a visual review of static captures; interactions and other device configurations still need runtime testing.

The shared spacing/motion tokens and code-level safe-inset/IME handling are already implemented, so they are not listed as new implementation work. Keep them in the regression checks below.

## Priority 0 — Remaining verification

- Run a visual pass on an emulator or device at 320 dp and 360 dp widths with system text at 1.3× and 2×. Confirm that prompts truncate deliberately, filter and theme choices all appear, link actions retain readable labels, and no content is clipped. Check the compact icon-only navigation with TalkBack.
- Automated Compose coverage now checks that filter chips stay within the viewport at 320 dp / 2× and 360 dp / 1.3×. Repeat the visual checks on-device because this sandbox has no Android SDK or emulator.

## Priority 1 — Refine Home and Explorer composition

The Home capture has a prominent link-entry card followed by a large open area before the empty-state message. The Explorer’s empty-state copy is clear, while its search field and filters consume a tall band above the content. Refine these screens without removing the clear primary action or making empty states feel crowded.

- Rebalance Home’s vertical hierarchy so link entry, categories, and the empty state feel connected. Keep “Open” as the primary action and “Paste” as the explicit secondary action; never auto-open clipboard content.
- Extract the link-entry area into a reusable component only if doing so gives its Empty, Editing, Invalid, and Loading states a consistent layout. Preserve the typed link across recoverable network/API errors.
- On Explorer, keep search and filters compact at ordinary text sizes, but let them reflow when text is enlarged. Make active filters apparent and provide a quick reset even when results are present.
- Add a sort control for the available file metadata. Keep the existing grid/list toggle easy to locate in both compact and wide layouts.

**Acceptance:** The primary link action remains reachable with the keyboard open, empty states explain the next step, and changing a filter never hides which choices are active.

## Priority 2 — Clarify file cards and media actions

The grid capture shows a video-type glyph beneath a central play icon, plus a separate download control over the same tile. These competing marks make the thumbnail area visually busy. The list capture presents file size and date clearly; retain that useful information hierarchy.

- Give video tiles one unmistakable play treatment. Use a consistent thumbnail or fallback, then place a single play affordance with adequate contrast.
- Keep download and selection actions from competing with the thumbnail or obscuring file content. Preserve a generous touch target and clear accessible label for each action.
- Keep filenames, size, and available dates legible in both grid and list modes, including long filenames and larger text. Make selected state visually distinct without relying on color alone.
- Move multi-selection actions into a clear contextual surface with a selected-item count and only valid group actions.

**Acceptance:** Grid and list communicate the same essential file information; play, download, and selection actions are visually distinct and independently operable.

## Priority 2 — Finish Settings and Downloads at constrained heights

The Settings screenshot shows the theme choices extending beyond the visible row, and the final “saved on this device” note is only partly visible near the lower edge. The Downloads empty state is clear, but its status-filter row also shows only part of the next choice.

- Ensure the Settings screen can scroll fully to its last item with bottom content spacing that remains clear of the navigation bar and system navigation area.
- Make the theme choices usable at enlarged text sizes without hiding the selected mode.
- Preserve the Downloads empty-state explanation and “Show all” recovery action for filtered-empty results. Make all status filters discoverable at narrow widths.
- When real downloads are present, review filename, status, progress, speed/size, and available actions together. Add a completion action such as “Open” or “Show file” if the current flow does not already provide one.

**Acceptance:** No Settings content is stranded behind navigation; all download filters and card actions remain accessible when the list is empty or populated.

## Priority 3 — Validate color, accessibility, and unfinished states

The captures show a dark appearance with both blue brand accents and pink selected/primary accents. Review the actual combinations rather than assuming that one palette works for every theme or wallpaper.

- Check contrast in light and dark themes with dynamic color both enabled and disabled, including secondary text, selected chips, error states, and icons over thumbnails.
- Verify TalkBack descriptions and state announcements for search, filters, selection, downloads, and progress. Maintain accessible touch targets.
- Complete the Explorer loading, offline, empty-search, and error/retry states; add skeleton loading and pull-to-refresh where they improve perceived responsiveness.
- Exercise download pause, resume, cancel, network loss, restart recovery, and grouped-download behavior. Check system notifications against the in-app status and actions.

## Validation sequence

1. Re-capture Home, empty Explorer, populated Explorer in grid/list, Downloads, and Settings at the same device size and text scale as the supplied images; compare clipping, hierarchy, and action visibility.
2. Repeat at narrow-phone width, enlarged text, light/dark themes, and dynamic-color on/off. Scroll Settings to the bottom and each filter row to its final option.
3. Test gesture and three-button navigation, the IME, display cutouts, landscape, and a wide window. Verify that the recently implemented inset handling does not double-pad navigation areas.
4. Run targeted Compose UI tests for link validation/IME submission, filter visibility and reset, file-card actions, selection, empty/error/loading states, and download progress. Finish with a short usability pass on opening a link, finding a file, and recovering from an error.

## Sources

[1]: https://filebin.net/19set94flvw101ua "User-provided CloudBox screenshots"
[2]: https://m3.material.io/blog/building-with-m3-expressive "Start building with Material 3 Expressive"
[3]: https://developer.android.com/develop/ui/compose/designsystems/material3 "Material Design 3 in Compose"
[4]: https://developer.apple.com/design/human-interface-guidelines/ "Apple Human Interface Guidelines"
