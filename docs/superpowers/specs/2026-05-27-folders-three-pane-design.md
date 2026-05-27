# Folders Three-Pane Design

## Goal

Replace the TV Folders destination's two-pane folder/grid layout with a three-pane browsing surface:

- Folder view: the current folder context and child-folder navigation.
- Video view: a vertically scrollable, single-column list of direct videos for the highlighted folder row.
- Details view: a live embedded scene details surface for the highlighted video.

The design keeps folder browsing fast, spatial, and remote-friendly while avoiding separate scene-details navigation for ordinary browsing.

## Scope

This is a TV-only layout change. Touch/mobile layouts are out of scope until they get a separate design.

The existing Room-backed folder cache and indexing model remain the source of truth. Browsing must not add server queries. The implementation may add local DAO queries and sort support for pane 2, but it should not change sync semantics.

## Approved Interaction

- The full-width Folders top bar remains above all panes, including indexing status, Force resync, and the new video sort control.
- Pane navigation is strict and spatial: `Folder list` <-> `Video list` <-> `Details controls`.
- Up/Down moves within the active pane.
- Right advances one pane.
- Left backs up one pane.
- From the folder pane, Left keeps current Folders behavior: go up a folder; at root, open the main drawer.
- Back opens the main navigation drawer, matching current Folders behavior.
- Enter/Center in the video list moves focus into the details pane controls.
- Enter/Center on `This folder` does nothing because it is a selector, not a navigation target.
- Enter/Center on a child-folder row drills into that folder.
- Details live-preview the highlighted video after a short debounce of about 150 ms.
- The active pane gets a subtle visual accent or border so users can tell which pane owns input.

## Folder Pane

Pane 1 always starts with a pinned `This folder` row. When not at root, it then shows a `..` parent row, followed by child folders.

`This folder` represents direct videos in the current folder and is selected by default when Folders opens. It always appears, even when the current folder has zero direct videos. It displays the current folder's direct video count only, such as `7 direct videos`.

The `..` row represents the parent folder. Highlighting it shows the parent folder's direct videos in pane 2. Activating it goes up to the parent folder.

`This folder` and `..` are utility rows: simpler than child-folder rows, with labels and counts but no thumbnail. Child-folder rows keep thumbnails and show both direct and recursive counts, with direct count primary, for example `3 direct - 42 total`.

Folder-row focus remains remembered per folder path, matching the current Folders page behavior.

## Video Pane

Pane 2 is a single-column vertical list of video thumbnails, not a grid. It shows only direct videos for the folder row highlighted in pane 1. It does not show recursive videos from subfolders.

Highlighting a folder row immediately updates the video list for that row without entering the folder. Enter/Center still controls folder navigation independently.

Each video row uses a static screenshot thumbnail initially. Animated preview playback is out of scope for pane 2 because it would be noisy alongside the live details pane.

Video titles use the same fallback behavior as New: scene title when present, otherwise basename without extension. Rows show both runtime and age; the currently active sort value is emphasized.

The selected video index is remembered per folder path during the session. When entering pane 2 from pane 1, focus lands on the remembered video for that folder, falling back to the first video. If a highlighted video disappears after resync or deletion, selection clamps to the nearest remaining video; if none remain, details clear to an empty state.

## Details Pane

Pane 3 reuses the embedded `SceneDetailsPage` surface, configured like the New page inspector. It loads full details for the highlighted video after the debounce, so Play/Edit/More behavior and metadata stay consistent with the rest of the app.

Moving around pane 2 live-updates pane 3. Right from pane 2 enters the details controls. Left from the first details control returns to pane 2.

When no folder row is highlighted, or the highlighted folder has no direct videos, pane 3 shows a neutral empty state with the relevant folder path and `No direct videos`. It must not show stale details from the last selected video.

Actions belong in pane 3. Pane 2 remains a simple browsing and selection list, with no long-press/context action surface.

## Sorting

Pane 2 supports two video sort modes:

- `Newest`: age newest first.
- `Longest`: runtime longest first.

The sort mode is a global persisted Folders UI preference and defaults to `Newest`. It belongs in the app's proto/DataStore preferences, not server-side `ServerPreferences`, because it is local UI behavior.

Sorting uses cached `folder_scenes` fields only: `updatedAtEpochMs` for `Newest` and `durationSeconds` for `Longest`. Changing sort must be instant and must not trigger network calls.

The sort control lives in the top bar next to Force resync as a compact `Newest` / `Longest` control. It is not part of the normal Left/Right pane path. Up from pane 1 or pane 2 moves focus to the sort control; Down returns focus to the pane that opened it.

## Layout

The initial 16:9 TV pane weights are:

- Folder pane: 25%.
- Video pane: 35%.
- Details pane: 40%.

These weights are starting values. Implementation may make small adjustments if `SceneDetailsPage` needs minimum width to avoid clipping, but the intent remains: folder names need the least space, the video list needs thumbnail/title room, and details needs enough width for embedded controls.

## Architecture

Allow a modest refactor of Folders into smaller pane/state helpers rather than concentrating all behavior in `FoldersPage.kt`.

Suggested units:

- `FoldersPage`: top-level composition, sync top bar, pane orchestration, and high-level D-pad routing.
- `FolderSelectionPane`: pinned utility rows, child folder rows, folder-row focus memory, and visible row count.
- `FolderVideoListPane`: single-column direct-video list, local sort application, video focus memory, and row rendering.
- `FolderDetailsPane`: embedded `SceneDetailsPage`, folder empty state, debounce handling, and details focus handoff.
- Small testable helpers for row targeting, pane transitions, sort ordering, focus clamping, and display-title fallback.

The current `FolderSceneGrid` can either be replaced or reduced to shared lower-level card/thumbnail pieces if useful. The grid interaction model should not remain in TV Folders.

## Data Flow

1. `FoldersViewModel` exposes the current path and child folders as it does today.
2. A derived highlighted folder target is computed from pane 1: current folder for `This folder`, parent for `..`, or a child folder path.
3. Pane 2 queries direct videos for the highlighted folder path from Room.
4. Pane 2 applies the persisted sort locally through DAO ordering or a focused local query.
5. Pane 2 selection emits a selected scene id.
6. Pane 3 debounces selected scene changes by about 150 ms and loads `SceneDetailsPage` for that id.
7. If pane 2 has no rows, pane 3 clears to the neutral empty state.

## Error Handling

- Empty folder: pane 2 and pane 3 show clear neutral empty states.
- Deleted or missing selected video: clamp to the nearest remaining row, otherwise clear details.
- Details load failure: use the existing `SceneDetailsPage` error behavior where possible.
- Empty or missing duration: keep the video row visible; sort unknown durations after known durations in `Longest`.
- Resync while browsing: preserve current folder and focus where identities still exist; clamp otherwise.

## Testing

Unit tests should cover:

- Folder row targeting for `This folder`, `..`, and child rows.
- Pane transition decisions for Left/Right/Up/Down/Enter.
- Direct-video sort order for `Newest` and `Longest`, including null duration handling.
- Video focus memory per folder path.
- Selection clamping when a video list shrinks or becomes empty.
- Display-title fallback from title to basename without extension.

Manual emulator verification should cover:

- Folders opens with `This folder` selected.
- Highlighting child folders updates pane 2 without entering the folder.
- Enter/Center on child folders drills into them.
- Right moves from folder pane to remembered video, then to details controls.
- Left returns from details to video, then to folders.
- Back opens the main drawer.
- Sort toggles between newest and longest, persists after leaving and reopening Folders.
- Empty folders clear stale details.
