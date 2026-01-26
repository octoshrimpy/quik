package dev.octoshrimpy.quik.feature.messageutils

import dev.octoshrimpy.quik.repository.MessageRepository

data class MessageUtilsState(
    val autoDeduplicateMessages: Boolean = false,
    val deduplicationProgress: MessageRepository.DeduplicationProgress = MessageRepository.DeduplicationProgress.Idle,

    val autoDelete: Int = 0,
)
