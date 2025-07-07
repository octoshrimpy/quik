package dev.octoshrimpy.quik.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class MessageContentFilter(
        @PrimaryKey var id: Long = 0,

        var value: String = "",
        var caseSensitive: Boolean = false,
        var isRegex: Boolean = false,
        var includeContacts: Boolean = false
) : RealmObject()

data class MessageContentFilterData(
        var value: String = "",
        var caseSensitive: Boolean = false,
        var isRegex: Boolean = false,
        var includeContacts: Boolean = false
)
