package dev.octoshrimpy.quik.feature.conversationinfo

import dev.octoshrimpy.quik.model.MmsPart
import dev.octoshrimpy.quik.model.Recipient
import io.realm.RealmList

sealed class ConversationInfoItem {

    data class ConversationInfoRecipient(val value: Recipient) : ConversationInfoItem()

    data class ConversationInfoSettings(
        val name: String,
        val recipients: RealmList<Recipient>,
        val archived: Boolean,
        val blocked: Boolean
    ) : ConversationInfoItem()

    data class ConversationInfoMedia(val value: MmsPart) : ConversationInfoItem()

}
