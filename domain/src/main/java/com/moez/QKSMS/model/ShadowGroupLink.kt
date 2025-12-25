package dev.octoshrimpy.quik.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/**
 * Links an "unreplyable" RCS thread to the SMS thread that we created
 * as a shadow / fallback conversation.
 *
 * One RCS thread -> one SMS thread.
 */
open class ShadowGroupLink(
    @PrimaryKey
    var rcsThreadId: Long = 0L,   // original RCS thread id
    var smsThreadId: Long = 0L    // shadow SMS thread id
) : RealmObject()
