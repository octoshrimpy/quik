package dev.octoshrimpy.quik.realm

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration
import dev.octoshrimpy.quik.repository.migrations.AppRealmMigration

object RealmConfig {

    @Volatile
    private var config: RealmConfiguration? = null

    private fun getConfig(): RealmConfiguration {
        return config ?: synchronized(this) {
            config ?: RealmConfiguration.Builder()
                .compactOnLaunch()
                .schemaVersion(AppRealmMigration.SCHEMA_VERSION)
                .migration(AppRealmMigration())
                .build().also { config = it }
        }
    }

    fun init(context: Context) {
        Realm.init(context)
        Realm.setDefaultConfiguration(getConfig())
    }
}
