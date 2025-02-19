package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.ScheduledMessage
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import javax.inject.Inject

class ScheduledMessageRepositoryImpl @Inject constructor() : ScheduledMessageRepository {

    private val disposables = CompositeDisposable()

    override fun saveScheduledMessage(
        date: Long,
        subId: Int,
        recipients: List<String>,
        sendAsGroup: Boolean,
        body: String,
        attachments: List<String>
    ) {
        Realm.getDefaultInstance().use { realm ->
            val id = (realm.where(ScheduledMessage::class.java).max("id")?.toLong() ?: -1) + 1
            val recipientsRealmList = RealmList(*recipients.toTypedArray())
            val attachmentsRealmList = RealmList(*attachments.toTypedArray())

            val message = ScheduledMessage(id, date, subId, recipientsRealmList, sendAsGroup, body,
                attachmentsRealmList)

            realm.executeTransaction { realm.insertOrUpdate(message) }
        }
    }

    override fun getScheduledMessages(): RealmResults<ScheduledMessage> {
        return Realm.getDefaultInstance()
            .where(ScheduledMessage::class.java)
            .sort("date")
            .findAll()
    }

    override fun getScheduledMessage(id: Long): ScheduledMessage? {
        return Realm.getDefaultInstance()
            .apply { refresh() }
            .where(ScheduledMessage::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun deleteScheduledMessage(id: Long) {
        val subscription = Completable.fromAction {
            Realm.getDefaultInstance().use { realm ->
                val message = realm.where(ScheduledMessage::class.java)
                    .equalTo("id", id)
                    .findFirst()

                realm.executeTransaction { message?.deleteFromRealm() }
            }
        }.subscribeOn(Schedulers.io()) // Perform the operation in a background thread
            .observeOn(AndroidSchedulers.mainThread()) // Switch back to the main thread if needed
            .subscribe({
                // Handle completion, e.g., log success or update UI
            }, {
                // Handle error, e.g., log or show error message
            })

        disposables.add(subscription)
    }

    override fun deleteScheduledMessages(ids: List<Long>) {
        ids.forEach { deleteScheduledMessage(it) }
    }

    // Ensure to clear disposables when the repository is no longer needed
    fun clearDisposables() {
        disposables.clear()
    }
}
