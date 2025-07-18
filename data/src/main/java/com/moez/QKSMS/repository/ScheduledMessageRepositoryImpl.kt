package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.ScheduledMessage
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import timber.log.Timber
import javax.inject.Inject

class ScheduledMessageRepositoryImpl @Inject constructor() : ScheduledMessageRepository {

    private val disposables = CompositeDisposable()

    override fun saveScheduledMessage(
        date: Long,
        subId: Int,
        recipients: List<String>,
        sendAsGroup: Boolean,
        body: String,
        attachments: List<String>,
        conversationId: Long
    ): ScheduledMessage {
        Realm.getDefaultInstance().use { realm ->
            val id = (realm
                .where(ScheduledMessage::class.java)
                .max("id")
                ?.toLong() ?: -1
                    ) + 1

            val recipientsRealmList = RealmList(*recipients.toTypedArray())
            val attachmentsRealmList = RealmList(*attachments.toTypedArray())

            val message = ScheduledMessage(id, date, subId, recipientsRealmList, sendAsGroup, body,
                attachmentsRealmList, conversationId)

            realm.executeTransaction { realm.insertOrUpdate(message) }

            return message
        }
    }

    override fun updateScheduledMessage(scheduledMessage: ScheduledMessage) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { realm.insertOrUpdate(scheduledMessage) }
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

    override fun getScheduledMessagesForConversation(conversationId: Long): RealmResults<ScheduledMessage> {
        return Realm.getDefaultInstance()
            .where(ScheduledMessage::class.java)
            .equalTo("conversationId", conversationId)
            .findAllAsync()
    }

    override fun deleteScheduledMessage(id: Long) {
        val subscription = Completable.fromAction {
            Realm.getDefaultInstance().use { realm ->
                val message = realm.where(ScheduledMessage::class.java)
                    .equalTo("id", id)
                    .findFirst()

                realm.executeTransaction { message?.deleteFromRealm() }
            }
        }.subscribeOn(Schedulers.io()) // Run on a background thread and switch to main if needed
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Timber.v("Successfully deleted scheduled messages.")
            }, {
                Timber.e("Deleting scheduled messages failed.")
            })

        disposables.add(subscription)
    }

    override fun deleteScheduledMessages(ids: List<Long>) {
        ids.forEach { deleteScheduledMessage(it) }
    }

    override fun getAllScheduledMessageIdsSnapshot(): List<Long> {
        Realm.getDefaultInstance().use { realm ->
            return realm
                .where(ScheduledMessage::class.java)
                .sort("date")
                .findAll()
                .createSnapshot()
                .map { it.id }
        }
    }
}
