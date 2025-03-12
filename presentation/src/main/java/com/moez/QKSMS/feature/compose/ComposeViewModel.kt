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
package dev.octoshrimpy.quik.feature.compose

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Vibrator
import android.telephony.SmsMessage
import androidx.core.content.getSystemService
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.moez.QKSMS.common.QkMediaPlayer
import com.moez.QKSMS.contentproviders.MmsPartProvider
import com.moez.QKSMS.manager.MediaRecorderManager
import com.moez.QKSMS.manager.BluetoothMicManager
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.common.util.ClipboardUtils
import dev.octoshrimpy.quik.common.util.MessageDetailsFormatter
import dev.octoshrimpy.quik.common.util.extensions.makeToast
import dev.octoshrimpy.quik.compat.SubscriptionManagerCompat
import dev.octoshrimpy.quik.compat.TelephonyCompat
import dev.octoshrimpy.quik.extensions.asObservable
import dev.octoshrimpy.quik.extensions.isImage
import dev.octoshrimpy.quik.extensions.isVideo
import dev.octoshrimpy.quik.extensions.mapNotNull
import dev.octoshrimpy.quik.interactor.AddScheduledMessage
import dev.octoshrimpy.quik.interactor.CancelDelayedMessage
import dev.octoshrimpy.quik.interactor.DeleteMessages
import dev.octoshrimpy.quik.interactor.MarkRead
import dev.octoshrimpy.quik.interactor.RetrySending
import dev.octoshrimpy.quik.interactor.SendMessage
import dev.octoshrimpy.quik.manager.ActiveConversationManager
import dev.octoshrimpy.quik.manager.BillingManager
import dev.octoshrimpy.quik.manager.PermissionManager
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.util.ActiveSubscriptionObservable
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.util.tryOrNull
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.common.widget.MicInputCloudView
import dev.octoshrimpy.quik.common.widget.QkContextMenuRecyclerView
import dev.octoshrimpy.quik.extensions.isSmil
import dev.octoshrimpy.quik.interactor.SaveImage
import dev.octoshrimpy.quik.model.MmsPart
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Named

class ComposeViewModel @Inject constructor(
    @Named("query") private val query: String,
    @Named("threadId") private val threadId: Long,
    @Named("addresses") private val addresses: List<String>,
    @Named("text") private val sharedText: String,
    @Named("attachments") val sharedAttachments: List<Attachment>,
    @Named("mode") private val mode: String,
    @Named("subscriptionId") val sharedSubscriptionId: Int,
    @Named("sendAsGroup") val sharedSendAsGroup: Boolean?,
    @Named("scheduleDateTime") val sharedScheduledDateTime: Long,
    private val contactRepo: ContactRepository,
    private val context: Context,
    private val activeConversationManager: ActiveConversationManager,
    private val addScheduledMessage: AddScheduledMessage,
    private val billingManager: BillingManager,
    private val cancelMessage: CancelDelayedMessage,
    private val conversationRepo: ConversationRepository,
    private val deleteMessages: DeleteMessages,
    private val markRead: MarkRead,
    private val messageDetailsFormatter: MessageDetailsFormatter,
    private val messageRepo: MessageRepository,
    private val navigator: Navigator,
    private val permissionManager: PermissionManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences,
    private val retrySending: RetrySending,
    private val sendMessage: SendMessage,
    private val subscriptionManager: SubscriptionManagerCompat,
    private val saveImage: SaveImage,
) : QkViewModel<ComposeView, ComposeState>(ComposeState(
        editingMode = threadId == 0L && addresses.isEmpty(),
        threadId = threadId,
        query = query)
) {

    companion object {
        private const val AUDIO_FILE_PREFIX = "recorded-"
    }

    private val chipsReducer: Subject<(List<Recipient>) -> List<Recipient>> = PublishSubject.create()
    private val conversation: Subject<Conversation> = BehaviorSubject.create()
    private val messages: Subject<List<Message>> = BehaviorSubject.create()
    private val selectedChips: Subject<List<Recipient>> = BehaviorSubject.createDefault(listOf())
    private val searchResults: Subject<List<Message>> = BehaviorSubject.create()
    private val searchSelection: Subject<Long> = BehaviorSubject.createDefault(-1)

    private var shouldShowContacts = threadId == 0L && addresses.isEmpty()

    private var bluetoothMicManager: BluetoothMicManager? = null

    init {
        // set shared subscription into state if set
        subscriptionManager.activeSubscriptionInfoList.firstOrNull {
            it.subscriptionId == sharedSubscriptionId
        }?.let { newState { copy(subscription = it)} }

        // set shared scheduled datetime into state if set
        if (sharedScheduledDateTime != 0L)
            newState { copy (scheduled = sharedScheduledDateTime) }

        // set shared sendAsGroup into state if set
        if (sharedSendAsGroup != null)
            newState { copy(sendAsGroup = sharedSendAsGroup) }

        // set shared attachments into state
        newState { copy(attachments = sharedAttachments) }

        val initialConversation = threadId.takeIf { it != 0L }
                ?.let(conversationRepo::getConversationAsync)
                ?.asObservable()
                ?: Observable.empty()

        val selectedConversation = selectedChips
                .skipWhile { it.isEmpty() }
                .map { chips -> chips.map { it.address } }
                .distinctUntilChanged()
                .doOnNext { newState { copy(loading = true) } }
                .observeOn(Schedulers.io())
                .map { addresses -> Pair(conversationRepo.getOrCreateConversation(addresses)?.id ?: 0, addresses) }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { newState { copy(loading = false) } }
                .switchMap { (threadId, addresses) ->
                    // If we already have this thread in realm, or we're able to obtain it from the
                    // system, just return that.
                    threadId.takeIf { it > 0 }?.let {
                        return@switchMap conversationRepo.getConversationAsync(threadId).asObservable()
                    }

                    // Otherwise, we'll monitor the conversations until our expected conversation is created
                    conversationRepo.getConversations(prefs.unreadAtTop.get()).asObservable()
                            .filter { it.isLoaded }
                            .observeOn(Schedulers.io())
                            .map { conversationRepo.getOrCreateConversation(addresses)?.id ?: 0 }
                            .observeOn(AndroidSchedulers.mainThread())
                            .switchMap { actualThreadId ->
                                when (actualThreadId) {
                                    0L -> Observable.just(Conversation(0))
                                    else -> conversationRepo.getConversationAsync(actualThreadId).asObservable()
                                }
                            }
                }

        // Merges two potential conversation sources (threadId from constructor and contact selection) into a single
        // stream of conversations. If the conversation was deleted, notify the activity to shut down
        disposables += selectedConversation
                .mergeWith(initialConversation)
                .filter { conversation -> conversation.isLoaded }
                .doOnNext { conversation ->
                    if (!conversation.isValid) {
                        newState { copy(hasError = true) }
                    }
                }
                .filter { conversation -> conversation.isValid }
                .subscribe(conversation::onNext)

        if (addresses.isNotEmpty()) {
            selectedChips.onNext(addresses.map { address -> Recipient(address = address) })
        }

        disposables += chipsReducer
                .scan(listOf<Recipient>()) { previousState, reducer -> reducer(previousState) }
                .doOnNext { chips -> newState { copy(selectedChips = chips) } }
                .skipUntil(state.filter { state -> state.editingMode })
                .takeUntil(state.filter { state -> !state.editingMode })
                .subscribe(selectedChips::onNext)

        // When the conversation changes, mark read, and update the recipientId and the messages for the adapter
        disposables += conversation
                .distinctUntilChanged { conversation -> conversation.id }
                .observeOn(AndroidSchedulers.mainThread())
                .map { conversation ->
                    val messages = messageRepo.getMessages(conversation.id)
                    newState { copy(threadId = conversation.id, messages = Pair(conversation, messages)) }
                    messages
                }
                .switchMap { messages -> messages.asObservable() }
                .subscribe(messages::onNext)

        disposables += conversation
                .map { conversation -> conversation.getTitle() }
                .distinctUntilChanged()
                .subscribe { title -> newState { copy(conversationtitle = title) } }

        disposables += prefs.sendAsGroup.asObservable()
                .distinctUntilChanged()
                .subscribe { enabled -> newState { copy(sendAsGroup = enabled) } }

        disposables += conversation
                .map { conversation -> conversation.id }
                .distinctUntilChanged()
                .withLatestFrom(state) { id, state -> messageRepo.getMessages(id, state.query) }
                .switchMap { messages -> messages.asObservable() }
                .takeUntil(state.map { it.query }.filter { it.isEmpty() })
                .filter { messages -> messages.isLoaded }
                .filter { messages -> messages.isValid }
                .subscribe(searchResults::onNext)

        // on conversation change/init, work out how many non-me participants of the conversation
        // have a valid address (subscriber number) for replying/sending to
        disposables += conversation
            .distinctUntilChanged { conversation -> conversation.id }
            .observeOn(AndroidSchedulers.mainThread())
            .map { conversation ->
                var possibleNumbers = 0
                conversation.recipients.forEach { recipient ->
                    if (phoneNumberUtils.isPossibleNumber(recipient.address))
                        ++possibleNumbers
                }
                possibleNumbers
            }
            .subscribe { validRecipientNumbers ->
                newState { copy(validRecipientNumbers = validRecipientNumbers) }
            }

        disposables += Observables.combineLatest(searchSelection, searchResults) { selected, messages ->
            if (selected == -1L) {
                messages.lastOrNull()?.let { message -> searchSelection.onNext(message.id) }
            } else {
                val position = messages.indexOfFirst { it.id == selected } + 1
                newState { copy(searchSelectionPosition = position, searchResults = messages.size) }
            }
        }.subscribe()

        val latestSubId = messages
                .map { messages -> messages.lastOrNull()?.subId ?: -1 }
                .distinctUntilChanged()

        val subscriptions = ActiveSubscriptionObservable(subscriptionManager)
        disposables += Observables.combineLatest(latestSubId, subscriptions) { subId, subs ->
            val sub = if (subs.size > 1) subs.firstOrNull { it.subscriptionId == subId } ?: subs[0] else null
            newState { copy(subscription = sub) }
        }.subscribe()

        // actions
        if (mode == "scheduling")
            newState { copy(scheduling = true) }
    }

    @SuppressLint("StringFormatInvalid")
    override fun bindView(view: ComposeView) {
        super.bindView(view)

        val sharing = (sharedText.isNotEmpty() || sharedAttachments.isNotEmpty())
        if (shouldShowContacts) {
            shouldShowContacts = false
            view.showContacts(sharing, selectedChips.blockingFirst())
        }

        view.chipsSelectedIntent
                .withLatestFrom(selectedChips) { hashmap, chips ->
                    // If there's no contacts already selected, and the user cancelled the contact
                    // selection, close the activity
                    if (hashmap.isEmpty() && chips.isEmpty()) {
                        newState { copy(hasError = true) }
                    }
                    // Filter out any numbers that are already selected
                    hashmap.filter { (address) ->
                        chips.none { recipient -> phoneNumberUtils.compare(address, recipient.address) }
                    }
                }
                .filter { hashmap -> hashmap.isNotEmpty() }
                .map { hashmap ->
                    hashmap.map { (address, lookupKey) ->
                        conversationRepo.getRecipients()
                                .asSequence()
                                .filter { recipient -> recipient.contact?.lookupKey == lookupKey }
                                .firstOrNull { recipient -> phoneNumberUtils.compare(recipient.address, address) }
                                ?: Recipient(
                                        address = address,
                                        contact = lookupKey?.let(contactRepo::getUnmanagedContact))
                    }
                }
                .autoDisposable(view.scope())
                .subscribe { chips ->
                    chipsReducer.onNext { list -> list + chips }
                    view.showKeyboard()
                }

        // Set the contact suggestions list to visible when the add button is pressed
        view.optionsItemIntent
                .filter { it == R.id.add }
                .withLatestFrom(selectedChips) { _, chips ->
                    view.showContacts(sharing, chips)
                }
                .autoDisposable(view.scope())
                .subscribe()

        // Update the list of selected contacts when a new contact is selected or an existing one is deselected
        view.chipDeletedIntent
                .autoDisposable(view.scope())
                .subscribe { contact ->
                    chipsReducer.onNext { contacts ->
                        val result = contacts.filterNot { it == contact }
                        if (result.isEmpty()) {
                            view.showContacts(sharing, result)
                        }
                        result
                    }
                }

        // When the menu is loaded, trigger a new state so that the menu options can be rendered correctly
        view.menuReadyIntent
                .autoDisposable(view.scope())
                .subscribe { newState { copy() } }

        // toggle select all / select none
        view.optionsItemIntent
            .filter { it == R.id.select_all }
            .autoDisposable(view.scope())
            .subscribe { view.toggleSelectAll() }

        // Open the phone dialer if the call button is clicked
        view.optionsItemIntent
                .filter { it == R.id.call }
                .withLatestFrom(conversation) { _, conversation -> conversation }
                .mapNotNull { conversation -> conversation.recipients.firstOrNull() }
                .map { recipient -> recipient.address }
                .autoDisposable(view.scope())
                .subscribe { address -> navigator.makePhoneCall(address) }

        // Open the conversation settings if info button is clicked
        view.optionsItemIntent
                .filter { it == R.id.info }
                .withLatestFrom(conversation) { _, conversation -> conversation }
                .autoDisposable(view.scope())
                .subscribe { conversation -> navigator.showConversationInfo(conversation.id) }

        // Copy the message contents
        view.optionsItemIntent
                .filter { it == R.id.copy }
                .withLatestFrom(view.messagesSelectedIntent) { _, messageIds ->
                    val messages = messageIds.mapNotNull(messageRepo::getMessage).sortedBy { it.date }
                    val text = when (messages.size) {
                        1 -> messages.first().getText()
                        else -> messages.foldIndexed("") { index, acc, message ->
                            when {
                                index == 0 -> message.getText()
                                messages[index - 1].compareSender(message) -> "$acc\n${message.getText()}"
                                else -> "$acc\n\n${message.getText()}"
                            }
                        }
                    }

                    ClipboardUtils.copy(context, text)
                }
                .autoDisposable(view.scope())
                .subscribe { view.clearSelection() }

        // Show the message details
        view.optionsItemIntent
                .filter { it == R.id.details }
                .withLatestFrom(view.messagesSelectedIntent) { _, messages -> messages }
                .mapNotNull { messages -> messages.firstOrNull().also { view.clearSelection() } }
                .mapNotNull(messageRepo::getMessage)
                .map(messageDetailsFormatter::format)
                .autoDisposable(view.scope())
                .subscribe { view.showDetails(it) }

        // Show the delete message dialog if one or more messages selected
        view.optionsItemIntent
            .filter { it == R.id.delete }
            .withLatestFrom(view.messagesSelectedIntent) { _, selectedMessages -> selectedMessages }
            .filter { permissionManager.isDefaultSms().also { if (!it) view.requestDefaultSms() } }
            .autoDisposable(view.scope())
            .subscribe { view.showDeleteDialog(it) }

        // show the clear current message dialog if no messages selected
        view.optionsItemIntent
            .filter { it == R.id.delete }
            .withLatestFrom(state) { _, state -> state }
            .filter { it.selectedMessages == 0 }
            .autoDisposable(view.scope())
            .subscribe { view.showClearCurrentMessageDialog() }

        // Forward the message
        view.optionsItemIntent
            .filter { it == R.id.forward }
            .withLatestFrom(view.messagesSelectedIntent) { _, messages ->
                messages?.firstOrNull()?.let { messageRepo.getMessage(it) }?.let { message ->
                    navigator.showCompose(
                        message.getText(),
                        message.parts.filter { !it.isSmil() }.mapNotNull { it.getUri() }
                    )
                }
            }
            .autoDisposable(view.scope())
            .subscribe { view.clearSelection() }

        // expand message to show additional info
        view.optionsItemIntent
            .filter { it == R.id.show_status }
            .withLatestFrom(view.messagesSelectedIntent) { _, messages -> messages }
            .autoDisposable(view.scope())
            .subscribe { messageIds ->
                view.expandMessages(messageIds, true)
                view.clearSelection()
            }

        // Show the previous search result
        view.optionsItemIntent
                .filter { it == R.id.previous }
                .withLatestFrom(searchSelection, searchResults) { _, selection, messages ->
                    val currentPosition = messages.indexOfFirst { it.id == selection }
                    if (currentPosition <= 0L) messages.lastOrNull()?.id ?: -1
                    else messages.getOrNull(currentPosition - 1)?.id ?: -1
                }
                .filter { id -> id != -1L }
                .autoDisposable(view.scope())
                .subscribe(searchSelection)

        // Show the next search result
        view.optionsItemIntent
                .filter { it == R.id.next }
                .withLatestFrom(searchSelection, searchResults) { _, selection, messages ->
                    val currentPosition = messages.indexOfFirst { it.id == selection }
                    if (currentPosition >= messages.size - 1) messages.firstOrNull()?.id ?: -1
                    else messages.getOrNull(currentPosition + 1)?.id ?: -1
                }
                .filter { id -> id != -1L }
                .autoDisposable(view.scope())
                .subscribe(searchSelection)

        // Clear the search
        view.optionsItemIntent
                .filter { it == R.id.clear }
                .autoDisposable(view.scope())
                .subscribe { newState { copy(query = "", searchSelectionId = -1) } }

        // message part context menu item selected - save
        view.contextItemIntent
            .filter { it.itemId == R.id.save }
            .filter { permissionManager.hasStorage().also { if (!it) view.requestStoragePermission() } }
            .autoDisposable(view.scope())
            .subscribe {
                val menuInfo = it.menuInfo as QkContextMenuRecyclerView.ContextMenuInfo<Long, MmsPart>
                if (menuInfo.viewHolderValue != null)
                    saveImage.execute(menuInfo.viewHolderValue.id) {
                        context.makeToast(R.string.gallery_toast_saved)
                    }
            }

        // message part context menu item selected - share
        view.contextItemIntent
            .filter { it.itemId == R.id.share }
            .autoDisposable(view.scope())
            .subscribe {
                val menuInfo = it.menuInfo as QkContextMenuRecyclerView.ContextMenuInfo<Long, MmsPart>
                if (menuInfo.viewHolderValue != null)
                    navigator.shareFile(
                        MmsPartProvider.getUriForMmsPartId(
                            menuInfo.viewHolderValue.id,
                            menuInfo.viewHolderValue.getBestFilename()
                        ),
                        menuInfo.viewHolderValue.type
                    )
            }

        // message part context menu item selected - forward
        view.contextItemIntent
            .filter { it.itemId == R.id.forward }
            .autoDisposable(view.scope())
            .subscribe {
                val menuInfo = it.menuInfo as QkContextMenuRecyclerView.ContextMenuInfo<Long, MmsPart>
                if (menuInfo.viewHolderValue != null)
                    navigator.showCompose("", listOf(menuInfo.viewHolderValue.getUri()))
            }

        // message part context menu item selected - open externally
        view.contextItemIntent
            .filter { it.itemId == R.id.openExternally }
            .autoDisposable(view.scope())
            .subscribe {
                val menuInfo = it.menuInfo as QkContextMenuRecyclerView.ContextMenuInfo<Long, MmsPart>
                if (menuInfo.viewHolderValue != null)
                    navigator.viewFile(
                        MmsPartProvider.getUriForMmsPartId(
                            menuInfo.viewHolderValue.id,
                            menuInfo.viewHolderValue.getBestFilename()
                        ),
                        menuInfo.viewHolderValue.type
                    )
            }

        // Toggle the group sending mode
        view.sendAsGroupIntent
                .autoDisposable(view.scope())
                .subscribe { prefs.sendAsGroup.set(!prefs.sendAsGroup.get()) }

        // Scroll to search position
        searchSelection
                .filter { id -> id != -1L }
                .doOnNext { id -> newState { copy(searchSelectionId = id) } }
                .autoDisposable(view.scope())
                .subscribe(view::scrollToMessage)

        // Theme changes
        prefs.keyChanges
                .filter { key -> key.contains("theme") }
                .doOnNext { view.themeChanged() }
                .autoDisposable(view.scope())
                .subscribe()

        // Media attachment clicks
        view.messagePartClickIntent
                .mapNotNull(messageRepo::getPart)
                .filter { part -> part.isImage() || part.isVideo() }
                .autoDisposable(view.scope())
                .subscribe { part -> navigator.showMedia(part.id) }

        // Non-media attachment clicks
        view.messagePartClickIntent
                .mapNotNull(messageRepo::getPart)
                .filter { part -> !part.isImage() && !part.isVideo() }
                .autoDisposable(view.scope())
                .subscribe {
                    navigator.viewFile(
                        MmsPartProvider.getUriForMmsPartId(it.id, it.getBestFilename()),
                        it.type
                    )
                }

        // Update the State when the message selected count changes
        view.messagesSelectedIntent
                .map { selection -> selection.size }
                .autoDisposable(view.scope())
                .subscribe { messages -> newState { copy(selectedMessages = messages, editingMode = false) } }

        // Cancel sending a message
        view.cancelSendingIntent
                .mapNotNull(messageRepo::getMessage)
                .doOnNext { message -> view.setDraft(message.getText()) }
                .autoDisposable(view.scope())
                .subscribe { message ->
                    cancelMessage.execute(CancelDelayedMessage.Params(message.id, message.threadId))
                }

        // send a delayed message now
        view.sendNowIntent
            .mapNotNull(messageRepo::getMessage)
            .autoDisposable(view.scope())
            .subscribe { message ->
                cancelMessage.execute(CancelDelayedMessage.Params(message.id, message.threadId))
                val address = listOf(conversationRepo
                    .getConversation(threadId)?.recipients?.firstOrNull()?.address ?: message.address)
                sendMessage.execute(
                    SendMessage.Params(
                        message.subId,
                        message.threadId,
                        address,
                        message.body,
                        listOf(),       // sms with attachments (mms) can't be delayed so we can know attachments are empty for a 'send now' delayed sms
                        0
                    )
                )
            }

        // resend a failed message
        view.resendIntent
            .mapNotNull(messageRepo::getMessage)
            .filter { message -> message.isFailedMessage() }
            .doOnNext { message -> retrySending.execute(message.id) }
            .autoDisposable(view.scope())
            .subscribe()

        // Show the message details
        view.messageLinkAskIntent
            .autoDisposable(view.scope())
            .subscribe { view.showMessageLinkAskDialog(it) }

        // Set the current conversation
        Observables
                .combineLatest(
                        view.activityVisibleIntent.distinctUntilChanged(),
                        conversation.mapNotNull { conversation ->
                            conversation.takeIf { it.isValid }?.id
                        }.distinctUntilChanged())
                { visible, threadId ->
                    when (visible) {
                        true -> {
                            activeConversationManager.setActiveConversation(threadId)
                            markRead.execute(listOf(threadId))
                        }

                        false -> activeConversationManager.setActiveConversation(null)
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()

        // Save draft when the activity goes into the background
        view.activityVisibleIntent
                .filter { visible -> !visible }
                .withLatestFrom(conversation) { _, conversation -> conversation }
                .mapNotNull { conversation -> conversation.takeIf { it.isValid }?.id }
                .observeOn(Schedulers.io())
                .withLatestFrom(view.textChangedIntent) { threadId, draft ->
                    conversationRepo.saveDraft(threadId, draft.toString())
                }
                .autoDisposable(view.scope())
                .subscribe()

        // Open the attachment options
        view.attachIntent
                .autoDisposable(view.scope())
                .subscribe { newState { copy(attaching = !attaching) } }

        // Attach a photo from camera
        view.cameraIntent
                .autoDisposable(view.scope())
                .subscribe {
                    newState { copy(attaching = false) }
                    view.requestCamera()
                }

        // pick a photo (specifically) from image provider apps
        view.attachImageFileIntent
            .doOnNext { newState { copy(attaching = false) } }
            .autoDisposable(view.scope())
            .subscribe { view.requestGallery("image/*", ComposeView.AttachAFileRequestCode) }

        // pick any file from any provider apps
        view.attachAnyFileIntent
            .doOnNext { newState { copy(attaching = false) } }
            .autoDisposable(view.scope())
            .subscribe { view.requestGallery("*/*", ComposeView.AttachAFileRequestCode) }

        // Choose a time to schedule the message
        view.scheduleIntent
                .doOnNext { newState { copy(attaching = false) } }
                .withLatestFrom(billingManager.upgradeStatus) { _, upgraded -> upgraded }
                .filter { upgraded ->
                    upgraded.also { if (!upgraded) view.showQksmsPlusSnackbar(R.string.compose_scheduled_plus) }
                }
                .autoDisposable(view.scope())
                .subscribe { view.requestDatePicker() }

        view.scheduleAction
            .take(1)
            .doOnNext{ newState { copy(scheduling = false) } }
            .autoDisposable(view.scope())
            .subscribe { view.requestDatePicker() }

        // an attachment was picked by the user
        Observable.merge(
            view.attachAnyFileSelectedIntent.map { uri -> Attachment(context, uri) },
            view.inputContentIntent.map { inputContent -> Attachment(context, inputContent = inputContent) }
        )
            .autoDisposable(view.scope())
            .subscribe {
                newState { copy(attachments = attachments + it, attaching = false) }
            }

        // Set the scheduled time
        view.scheduleSelectedIntent
                .filter { scheduled ->
                    (scheduled > System.currentTimeMillis()).also { future ->
                        if (!future) context.makeToast(R.string.compose_scheduled_future)
                    }
                }
                .autoDisposable(view.scope())
                .subscribe { scheduled -> newState { copy(scheduled = scheduled) } }

        // Attach a contact
        view.attachContactIntent
                .doOnNext { newState { copy(attaching = false) } }
                .autoDisposable(view.scope())
                .subscribe { view.requestContact() }

        // Contact was selected for attachment
        view.contactSelectedIntent
                .subscribeOn(Schedulers.io())
                .autoDisposable(view.scope())
                .subscribe(
                    {
                        newState {
                            copy(attachments = attachments + Attachment(context, uri = it))
                        }
                    }
                ) { error ->
                    context.makeToast(R.string.compose_contact_error)
                    Timber.w(error)
                }

        // Detach an attachment
        view.attachmentDeletedIntent
                .autoDisposable(view.scope())
                .subscribe {
                    newState { copy(attachments = attachments - it) }

                    // if the attachment is backed by a local file, delete the file
                    it.removeCacheFile()
                }

        conversation
                .map { conversation -> conversation.draft }
                .distinctUntilChanged()
                .autoDisposable(view.scope())
                .subscribe { draft ->

                    // If text was shared into the conversation, it should take priority over the
                    // existing draft
                    //
                    // TODO: Show dialog warning user about overwriting draft
                    if (sharedText.isNotBlank()) {
                        view.setDraft(sharedText)
                    } else {
                        view.setDraft(draft)
                    }
                }

        // set canSend state depending on if there is text input, an attachment or a schedule set
        Observables.combineLatest(
            view.textChangedIntent,     // input message text changed
            state
                .distinctUntilChanged { state -> state.attachments }    // attachments changed
                .map { it.attachments.size },   // number of attachments
            state.distinctUntilChanged { state -> state.scheduled }    // schedule set or not
                .map { it.scheduled }
        )
            .autoDisposable(view.scope())
            .subscribe {
                newState {
                    copy(
                        canSend = (it.first.isNotBlank() || (it.second > 0)) || (it.third > 0)
                    )
                }
            }

        // Show the remaining character counter when necessary
        view.textChangedIntent
                .observeOn(Schedulers.computation())
                .mapNotNull { draft -> tryOrNull { SmsMessage.calculateLength(draft, prefs.unicode.get()) } }
                .map { array ->
                    val messages = array[0]
                    val remaining = array[2]

                    when {
                        messages <= 1 && remaining > 10 -> ""
                        messages <= 1 && remaining <= 10 -> "$remaining"
                        else -> "$remaining / $messages"
                    }
                }
                .distinctUntilChanged()
                .autoDisposable(view.scope())
                .subscribe { remaining -> newState { copy(remaining = remaining) } }

        // Cancel the scheduled time
        view.scheduleCancelIntent
                .autoDisposable(view.scope())
                .subscribe { newState { copy(scheduled = 0) } }

        // Toggle to the next sim slot
        view.changeSimIntent
                .withLatestFrom(state) { _, state ->
                    val subs = subscriptionManager.activeSubscriptionInfoList
                    val subIndex = subs.indexOfFirst { it.subscriptionId == state.subscription?.subscriptionId }
                    val subscription = when {
                        subIndex == -1 -> null
                        subIndex < subs.size - 1 -> subs[subIndex + 1]
                        else -> subs[0]
                    }

                    if (subscription != null) {
                        context.getSystemService<Vibrator>()?.vibrate(40)
                        context.makeToast(context.getString(R.string.compose_sim_changed_toast,
                                subscription.simSlotIndex + 1, subscription.displayName))
                    }

                    newState { copy(subscription = subscription) }
                }
                .autoDisposable(view.scope())
                .subscribe()

        // speech recognition button clicked
        view.speechRecogniserIntent
            .autoDisposable(view.scope())
            .subscribe { view.startSpeechRecognition() }

        // shade clicked
        view.shadeIntent
            .autoDisposable(view.scope())
            .subscribe { newState { copy(attaching = false) } }

        // starting or stopping (change state) of audio message ui
        state
            .distinctUntilChanged { state -> state.audioMsgRecording }
            .skip(1)    // skip initial value
            .autoDisposable(view.scope())
            .subscribe {
                // stop any audio playback (ie from mms attachment or audio recorder)
                QkMediaPlayer.reset()

                // if leaving audio recording mode
                if (!it.audioMsgRecording) {
                    // ensure recording stopped and delete any recording file
                    safeDeleteLocalFile(MediaRecorderManager.stopRecording())
                    view.recordAudioStartStopRecording.onNext(false)
                }
            }

        // starting or stopping the recording of audio
        view.recordAudioStartStopRecording
            .autoDisposable(view.scope())
            .subscribe {
                // if start recording
                if (it == true) {
                    view.recordAudioPlayerVisible.onNext(false)  // hide audio player

                    // check have permissions to record audio
                    if (permissionManager.hasRecordAudio().also {
                        if (!it) view.requestRecordAudioPermission()
                    }) {
                        // create bluetooth mic device manager
                        bluetoothMicManager?.close()
                        bluetoothMicManager = BluetoothMicManager(
                            context,
                            object : BluetoothMicManager.Callbacks {
                                override fun onNoDeviceFound() {
                                    // no bluetooth sco device found, use built-in mic
                                    this.onConnected(null)
                                }
                                override fun onDeviceFound(device: AudioDeviceInfo?) {
                                    // show bluetooth placeholder until bluetooth connected
                                    view.recordAudioMsgRecordVisible.onNext(false)
                                }
                                override fun onConnecting(device: AudioDeviceInfo?) { /* nothing */ }
                                override fun onConnected(device: AudioDeviceInfo?) {
                                    // show record button and chronometer, hide bluetooth placeholder
                                    view.recordAudioMsgRecordVisible.onNext(true)
                                    view.recordAudioChronometer.onNext(true)  // start chronometer
                                    MediaRecorderManager.startRecording(context, device)
                                }
                                override fun onDisconnected(device: AudioDeviceInfo?) {
                                    // if bluetooth disconnects, stop recording
                                    if (device != null) {
                                        view.recordAudioRecord.onNext(
                                            MicInputCloudView.ViewState.PAUSED_STATE
                                        )
                                    }
                                }
                            }
                        )
                        bluetoothMicManager?.startBluetoothDevice()
                    }
                } else {
                    // stop recording
                    bluetoothMicManager?.close()
                    view.recordAudioChronometer.onNext(false)  // stop chronometer
                    MediaRecorderManager.stopRecording()
                }
            }

        // record an audio message menu item or main mic icon
        view.recordAnAudioMessage
            .autoDisposable(view.scope())
            .subscribe {
                view.recordAudioStartStopRecording.onNext(true)  // start recording
                newState { copy( attaching = false, audioMsgRecording = true) }
            }

        // abort recording audio message button
        view.recordAudioAbort
            .observeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe { newState { copy( audioMsgRecording = false) } }

        // main record/stop recording audio message button
        view.recordAudioRecord
            .autoDisposable(view.scope())
            .subscribe {
                if (it == MicInputCloudView.ViewState.PAUSED_STATE) {
                    view.recordAudioStartStopRecording.onNext(false)  // stop recording
                    view.recordAudioPlayerVisible.onNext(true)  // show audio player
                } else {  // state = start recording
                    safeDeleteLocalFile(MediaRecorderManager.uri)  // delete old recording file
                    view.recordAudioStartStopRecording.onNext(true)  // start new recording
                }
            }

        // attach recorded audio message button
        view.recordAudioAttach
            .autoDisposable(view.scope())
            .subscribe {
                MediaRecorderManager.stopRecording()

                try {
                    // create new filename for recorded file (so cache clean doesn't accidentally
                    // delete the attachment file)
                    val newFile = File(
                        context.cacheDir,
                        "${AUDIO_FILE_PREFIX}${UUID.randomUUID()}${MediaRecorderManager.AUDIO_FILE_SUFFIX}"
                    )

                    // rename recorded file to new name
                    MediaRecorderManager.uri.toFile().renameTo(newFile)

                    // attach newly named file to message
                    newState {
                        copy(
                            audioMsgRecording = false,
                            attachments = attachments + Attachment(context, newFile.toUri())
                        )
                    }
                }
                catch (e: Exception) { /* nothing */ }
            }

        // audio recording player play/pause button
        view.recordAudioPlayerPlayPause
            .autoDisposable(view.scope())
            .subscribe {
                when (it) {
                    QkMediaPlayer.PlayingState.Paused ->
                        view.recordAudioPlayerConfigUI.onNext(
                            QkMediaPlayer.PlayingState.Playing
                        )
                    QkMediaPlayer.PlayingState.Playing ->
                        view.recordAudioPlayerConfigUI.onNext(
                            QkMediaPlayer.PlayingState.Paused
                        )
                    else -> {
                        if (MediaRecorderManager.uri != Uri.EMPTY) {
                            QkMediaPlayer.setOnPreparedListener {
                                view.recordAudioPlayerConfigUI.onNext(
                                    QkMediaPlayer.PlayingState.Playing
                                )
                            }
                            QkMediaPlayer.setOnCompletionListener {
                                view.recordAudioPlayerConfigUI.onNext(
                                    QkMediaPlayer.PlayingState.Stopped
                                )
                            }

                            // start the media player play sequence
                            QkMediaPlayer.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )

                            QkMediaPlayer.reset()

                            QkMediaPlayer.setDataSource(context, MediaRecorderManager.uri)

                            QkMediaPlayer.prepareAsync()
                        }
                    }
                }
            }

        // Send a message when the send button is clicked, and disable editing mode if it's enabled
        view.sendIntent
                .withLatestFrom(view.textChangedIntent) { _, body -> body.toString() }
                .withLatestFrom(state, conversation, selectedChips) { body, state, conversation, chips ->
                    if (!permissionManager.isDefaultSms()) {
                        view.requestDefaultSms()
                        return@withLatestFrom
                    }

                    if (!permissionManager.hasSendSms()) {
                        view.requestSmsPermission()
                        return@withLatestFrom
                    }

                    val delay = when (prefs.sendDelay.get()) {
                        Preferences.SEND_DELAY_SHORT -> 3000
                        Preferences.SEND_DELAY_MEDIUM -> 5000
                        Preferences.SEND_DELAY_LONG -> 10000
                        else -> 0
                    }

                    if ((delay != 0 || state.scheduled != 0L) && !permissionManager.hasExactAlarms()) {
                        navigator.showExactAlarmsSettings()
                        return@withLatestFrom
                    }

                    val subId = state.subscription?.subscriptionId ?: -1
                    val addresses = when (conversation.recipients.isNotEmpty()) {
                        true -> conversation.recipients.map { it.address }
                        false -> chips.map { chip -> chip.address }
                    }
                    val sendAsGroup = ((addresses.size > 1) &&  // if more than one address to send to
                            (!state.editingMode ||    // and is not a new convo
                            state.sendAsGroup))  // or (is a new convo and) send as group is selected

                    when {
                        // Scheduling a message
                        state.scheduled != 0L -> {
                            newState { copy(scheduled = 0) }
                            val uris = state.attachments.map { it.uri.toString() }
                            val params = AddScheduledMessage
                                    .Params(state.scheduled, subId, addresses, sendAsGroup, body, uris)
                            addScheduledMessage.execute(params)
                            context.makeToast(R.string.compose_scheduled_toast)
                        }

                        // Sending a group message
                        sendAsGroup -> {
                            sendMessage.execute(SendMessage
                                    .Params(subId, conversation.id, addresses, body, state.attachments, delay))
                        }

                        // Sending a message to an existing conversation with one recipient
                        conversation.recipients.size == 1 -> {
                            val address = conversation.recipients.map { it.address }
                            sendMessage.execute(SendMessage.Params(subId, threadId, address, body, state.attachments, delay))
                        }

                        // Create a new conversation with one address
                        addresses.size == 1 -> {
                            sendMessage.execute(SendMessage
                                    .Params(subId, threadId, addresses, body, state.attachments, delay))
                        }

                        // Send a message to multiple addresses
                        else -> {
                            addresses.forEach { addr ->
                                val threadId = tryOrNull(false) {
                                    TelephonyCompat.getOrCreateThreadId(context, addr)
                                } ?: 0
                                val address = listOf(conversationRepo
                                        .getConversation(threadId)?.recipients?.firstOrNull()?.address ?: addr)
                                sendMessage.execute(SendMessage
                                        .Params(subId, threadId, address, body, state.attachments, delay))
                            }
                        }
                    }

                    // clear the current message ready for new message composition (or finish()
                    // compose activity)
                    view.clearCurrentMessageIntent.onNext(
                        ((addresses.size > 1) &&  // if more than one address to send to
                            state.editingMode &&    // and is a new convo
                            !state.sendAsGroup)     // and is *not* sent as a group
                    )
                }
                .autoDisposable(view.scope())
                .subscribe()

        // View QKSMS+
        view.viewQksmsPlusIntent
                .autoDisposable(view.scope())
                .subscribe { navigator.showQksmsPlusActivity("compose_schedule") }

        // Navigate back
        view.optionsItemIntent
                .filter { it == android.R.id.home }
                .map { Unit }
                .mergeWith(view.backPressedIntent)
                .withLatestFrom(state) { _, state ->
                    when {
                        state.selectedMessages > 0 -> view.clearSelection()
                        else -> newState { copy(hasError = true) }
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()

        // Delete the message
        view.confirmDeleteIntent
                .withLatestFrom(view.messagesSelectedIntent, conversation) { _, messages, conversation ->
                    deleteMessages.execute(DeleteMessages.Params(messages, conversation.id))
                }
                .autoDisposable(view.scope())
                .subscribe { view.clearSelection() }

        // clear the current message schedule, text and attachments
        view.clearCurrentMessageIntent
            .autoDisposable(view.scope())
            .subscribe {
                view.setDraft("")
                newState {
                    copy(
                        editingMode = false,
                        hasError = it,  // hasError being kinda misused to finish() compose activity
                        attachments = listOf(),
                        scheduled = 0,
                    )
                }
            }
    }

    private fun safeDeleteLocalFile(uri: Uri): Boolean {
        return try {
            if (uri == Uri.EMPTY)
                false
            uri.toFile().delete()
        }
        catch (e: Exception) { false }
    }

}
