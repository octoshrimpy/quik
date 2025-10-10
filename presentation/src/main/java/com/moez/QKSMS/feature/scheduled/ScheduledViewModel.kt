package dev.octoshrimpy.quik.feature.scheduled

import android.content.Context
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.common.util.ClipboardUtils
import dev.octoshrimpy.quik.interactor.DeleteScheduledMessages
import dev.octoshrimpy.quik.interactor.SendScheduledMessage
import dev.octoshrimpy.quik.manager.BillingManager
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Named

class ScheduledViewModel @Inject constructor(
    @Named("conversationId") private val conversationId: Long?,
    billingManager: BillingManager,
    private val context: Context,
    private val navigator: Navigator,
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val sendScheduledMessageInteractor: SendScheduledMessage,
    private val deleteScheduledMessagesInteractor: DeleteScheduledMessages,
) : QkViewModel<ScheduledView, ScheduledState>(ScheduledState(
    scheduledMessages = scheduledMessageRepo.getScheduledMessages(),
    conversationId = conversationId
)) {

    init {
        loadMessages(conversationId)
        disposables += billingManager.upgradeStatus
            .subscribe { upgraded -> newState { copy(upgraded = upgraded) } }
    }

    override fun bindView(view: ScheduledView) {
        super.bindView(view)

        // update the state when the message selected count changes
        view.messagesSelectedIntent
            .map { selection -> selection.size }
            .autoDisposable(view.scope())
            .subscribe { newState { copy(selectedMessages = it) } }

        // toggle select all / select none
        view.optionsItemIntent
            .filter { it == R.id.select_all }
            .autoDisposable(view.scope())
            .subscribe { view.toggleSelectAll() }

        // show the delete message dialog if one or more messages selected
        view.optionsItemIntent
            .filter { it == R.id.delete }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessages ->
                selectedMessages }
            .autoDisposable(view.scope())
            .subscribe { it ->
                val ids = it.mapNotNull(scheduledMessageRepo::getScheduledMessage)
                    .map { it.id }
                view.showDeleteDialog(ids)
            }


        // copy the selected message text to the clipboard
        view.optionsItemIntent
            .filter { it == R.id.copy }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessageIds ->
                selectedMessageIds  // same order as messages on screen
                    .mapNotNull(scheduledMessageRepo::getScheduledMessage)
                    .sortedBy { it.date }
                    .let { scheduledMessages ->
                        ClipboardUtils.copy(
                            context,
                            when (scheduledMessages.size) {
                                1 -> scheduledMessages.first().body
                                else -> scheduledMessages.fold(StringBuilder()) { acc, message ->
                                    if (acc.isNotEmpty() && message.body.isNotEmpty())
                                        acc.append("\n\n")
                                    acc.append(message.body)
                                }
                            }.toString()
                        )
                    }
            }
            .autoDisposable(view.scope())
            .subscribe()

        // send the messages now menu item selected
        view.optionsItemIntent
            .filter { it == R.id.send_now }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessages ->
                view.showSendNowDialog(selectedMessages)
            }
            .autoDisposable(view.scope())
            .subscribe()

        // edit message menu item selected
        view.optionsItemIntent
            .filter { it == R.id.edit_message }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessage ->
                view.showEditMessageDialog(selectedMessage.first())
            }
            .autoDisposable(view.scope())
            .subscribe()

        // delete message(s) (fired after the confirmation dialog has been shown)
        view.deleteScheduledMessages
            .autoDisposable(view.scope())
            .subscribe { selectedMessagesIds ->
                deleteScheduledMessagesInteractor.execute(selectedMessagesIds.toList())
                view.clearSelection()
            }

        // send message(s) now (fired after the confirmation dialog has been shown)
        view.sendScheduledMessages
            .autoDisposable(view.scope())
            .subscribe { selectedMessagesIds ->
                selectedMessagesIds.forEach { selectedMessagesId ->
                    sendScheduledMessageInteractor.execute(selectedMessagesId)
                }
                view.clearSelection()
            }


        // edit message (fired after the confirmation dialog has been shown)
        view.editScheduledMessage
            .observeOn(Schedulers.io())
            .doOnNext { selectedMessageId ->
                scheduledMessageRepo.getScheduledMessage(selectedMessageId)
                    ?.let { scheduledMessage ->
                        navigator.showCompose(scheduledMessage)
                        scheduledMessageRepo.deleteScheduledMessage(scheduledMessage.id)
                    }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(view.scope())
            .subscribe { view.clearSelection() }

        // navigate back or unselect
        view.optionsItemIntent
            .filter { it == android.R.id.home }
            .map { }
            .mergeWith(view.backPressedIntent)
            .withLatestFrom(state) { _, state -> state }
            .autoDisposable(view.scope())
            .subscribe {
                when {
                    (it.selectedMessages > 0) -> view.clearSelection()
                    else -> view.finishActivity()
                }
            }

        view.composeIntent
            .autoDisposable(view.scope())
            .subscribe {
                navigator.showCompose(mode = "scheduling")
                view.clearSelection()
            }

        view.upgradeIntent
            .autoDisposable(view.scope())
            .subscribe { navigator.showQksmsPlusActivity("schedule_fab") }
    }

    private fun loadMessages(conversationId: Long?) {
        val results = if (conversationId != null)
            scheduledMessageRepo.getScheduledMessagesForConversation(conversationId)
        else
            scheduledMessageRepo.getScheduledMessages()
        newState { copy(scheduledMessages = results) }
    }
}
