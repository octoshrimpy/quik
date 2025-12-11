package dev.octoshrimpy.quik.repository.migrations

import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration

class AppRealmMigration : RealmMigration {

    companion object {
        const val SCHEMA_VERSION: Long = 15
    }

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var version = oldVersion

        if (version < 11L) {
            realm.schema.get("Conversation")
                ?.addField("sendAsGroup", Boolean::class.java, FieldAttribute.REQUIRED)
                ?.transform { obj ->
                    val list = obj.getList("recipients")
                    obj.setBoolean("sendAsGroup", list != null && list.size > 1)
                }

            realm.schema.get("Message")
                ?.addField("sendAsGroup", Boolean::class.java, FieldAttribute.REQUIRED)
                ?.transform { obj ->
                    obj.setBoolean("sendAsGroup", false)
                }

            version = 11L
        }

        if (version < SCHEMA_VERSION) {
            version = SCHEMA_VERSION
        }
    }
}
