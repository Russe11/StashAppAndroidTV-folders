# Stash Android TV Folders

This context describes the folder-browsing language used by the Android TV app's Folders destination.

## Language

**Folder**:
A directory in the user's Stash library tree. A folder may contain direct videos and may also contain subfolders.
_Avoid_: Directory, path, node

**Direct Folder Video**:
A video whose file is immediately inside the selected folder. Videos inside subfolders are not direct folder videos for the selected folder.
_Avoid_: Recursive scene, nested video

**Subfolder Hint**:
Navigation metadata shown for an immediate subfolder, such as its count or representative thumbnail. Subfolder hints may summarize videos nested anywhere under that subfolder.
_Avoid_: Direct count, current folder video

**New Feed**:
A global list of recently updated folders and videos from the local folder cache for the selected server. Video rows use their own update time; folder rows use the newest direct folder video and ignore videos in subfolders.
_Avoid_: Recent folder tree, recursive new folders

## Example Dialogue

Developer: "When the user opens `/Movies/`, should the video grid include `/Movies/Action/clip.mp4`?"

Domain expert: "No. The grid shows direct folder videos only. The `Action` row can still show a count or thumbnail that reflects what is inside it."

Developer: "Should `/Movies/` appear in New because `/Movies/Action/clip.mp4` changed?"

Domain expert: "No. New folder rows are based on direct folder videos only; `/Movies/Action/` can appear, but `/Movies/` should not inherit that update."
