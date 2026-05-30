package com.github.damontecres.stashapp.util.realtime

/**
 * Pure mapper from a decoded [DeviceCommand] (the wire payload — IDs + numbers, never titles) to a
 * concrete, player-ready [PlayerAction] (R-C — this Android-TV obeying remote commands as a TARGET).
 *
 * Returns `null` when the command can't be satisfied as given — e.g. a `PLAY` with no `sceneId`, a
 * `SEEK` with no `seekSeconds`, or an `ENQUEUE` with no `sceneIds`. A null is *ignored* (the
 * dispatcher does nothing) rather than acted on incorrectly. This keeps every malformed/partial
 * command from becoming a surprise player action.
 *
 * Kept free of Apollo/Media3 so the full command→action surface is exhaustively unit-testable.
 */
object DeviceCommandMapper {
    /**
     * Translate [command] into a [PlayerAction], or null if it lacks the fields needed for its type
     * (those are silently ignored). The mapping matches the server `DeviceCommandType` 1:1:
     *  - PLAY    → [PlayerAction.Play] (requires sceneId; startSeconds optional)
     *  - PAUSE   → [PlayerAction.Pause]
     *  - RESUME  → [PlayerAction.Resume]
     *  - SEEK    → [PlayerAction.Seek] (requires seekSeconds)
     *  - STOP    → [PlayerAction.Stop]
     *  - ENQUEUE → [PlayerAction.Enqueue] (requires a non-empty sceneIds)
     *  - NEXT    → [PlayerAction.Next]
     *  - PREV    → [PlayerAction.Previous]
     */
    fun toAction(command: DeviceCommand): PlayerAction? =
        when (command.type) {
            DeviceCommandType.PLAY -> {
                val sceneId = command.sceneId?.takeIf { it.isNotBlank() } ?: return null
                PlayerAction.Play(sceneId = sceneId, startSeconds = command.startSeconds)
            }

            DeviceCommandType.PAUSE -> PlayerAction.Pause

            DeviceCommandType.RESUME -> PlayerAction.Resume

            DeviceCommandType.SEEK -> {
                val seek = command.seekSeconds ?: return null
                PlayerAction.Seek(seekSeconds = seek)
            }

            DeviceCommandType.STOP -> PlayerAction.Stop

            DeviceCommandType.ENQUEUE -> {
                val ids = command.sceneIds.filter { it.isNotBlank() }
                if (ids.isEmpty()) return null
                PlayerAction.Enqueue(sceneIds = ids)
            }

            DeviceCommandType.NEXT -> PlayerAction.Next

            DeviceCommandType.PREV -> PlayerAction.Previous
        }
}
