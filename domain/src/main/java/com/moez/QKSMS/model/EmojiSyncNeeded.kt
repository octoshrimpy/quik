package dev.octoshrimpy.quik.model

import io.realm.RealmObject

open class EmojiSyncNeeded : RealmObject() {
    var createdAt: Long = System.currentTimeMillis()
}