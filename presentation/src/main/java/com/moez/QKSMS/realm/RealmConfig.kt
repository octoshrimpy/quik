package dev.octoshrimpy.quik.realm

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration
import dev.octoshrimpy.quik.repository.migrations.AppRealmMigration

object RealmConfig {

    private val config: RealmConfiguration by lazy {
        RealmConfiguration.Builder()
            .schemaVersion(AppRealmMigration.SCHEMA_VERSION)
            .migration(AppRealmMigration())
            .build()
    }

    fun init(context: Context) {
        Realm.init(context)
        Realm.setDefaultConfiguration(config)
    }
}
