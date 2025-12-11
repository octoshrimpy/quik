/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
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

            version = 11L
        }

        if (version < SCHEMA_VERSION) {
            version = SCHEMA_VERSION
        }
    }
}
