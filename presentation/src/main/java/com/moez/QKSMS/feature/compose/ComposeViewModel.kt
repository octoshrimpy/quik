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
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.net.toFile
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.moez.QKSMS.common.QkMediaPlayer
import com.moez.QKSMS.contentproviders.MmsPartProvider
import com.moez.QKSMS.manager.BluetoothMicManager
import com.moez.QKSMS.manager.MediaRecorderManager
import com.moez.QKSMS.manager.MediaRecorderManager.AUDIO_FILE_PREFIX
import com.moez.QKSMS.manager.MediaRecorderManager.AUDIO_FILE_SUFFIX
import com.moez.QKSMS.util.Constants.Companion.SAVED_MESSAGE_TEXT_FILE_PREFIX
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.common.util.ClipboardUtils
import dev.octoshrimpy.quik.common.util.MessageDetailsFormatter
import dev.octoshrimpy.quik.common.util.extensions.makeToast
import dev.octoshrimpy.quik.common.widget.MicInputCloudView
import dev.octoshrimpy.quik.common.widget.QkContextMenuRecyclerView
import dev.octoshrimpy.quik.compat.SubscriptionManagerCompat
import dev.octoshrimpy.quik.extensions.asObservable
import dev.octoshrimpy.quik.extensions.isImage
import dev.octoshrimpy.quik.extensions.isSmil
import dev.octoshrimpy.quik.extensions.isText
import dev.octoshrimpy.quik.extensions.isVideo
import dev.octoshrimpy.quik.extensions.mapNotNull
import dev.octoshrimpy.quik.interactor.AddScheduledMessage
import dev.octoshrimpy.quik.interactor.CancelDelayedMessage
import dev.octoshrimpy.quik.interactor.DeleteMessages
import dev.octoshrimpy.quik.interactor.MarkRead
import dev.octoshrimpy.quik.interactor.RetrySending
import dev.octoshrimpy.quik.interactor.SaveImage
import dev.octoshrimpy.quik.interactor.SendMessage
import dev.octoshrimpy.quik.manager.ActiveConversationManager
import dev.octoshrimpy.quik.manager.BillingManager
import dev.octoshrimpy.quik.manager.PermissionManager
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.MmsPart
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.model.getText
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import dev.octoshrimpy.quik.util.ActiveSubscriptionObservable
import dev.octoshrimpy.quik.util.FileUtils
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import dev.octoshrimpy.quik.compat.TelephonyCompat
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import android.provider.Settings
import androidx.lifecycle.LifecycleOwner
import com.moez.QKSMS.feature.compose.TokenUploadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val navigator: Navigator,
    private val permissionManager: PermissionManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences,
    private val retrySending: RetrySending,
    private val sendMessage: SendMessage,
    private val subscriptionManager: SubscriptionManagerCompat,
    private val saveImage: SaveImage,

) : QkViewModel<ComposeView, ComposeState>(
    ComposeState(
        editingMode = threadId == 0L && addresses.isEmpty(),
        threadId = threadId,
        query = query
    )
) {

    companion object {
        // TODO: Replace these URLs with your actual API endpoints
        private const val MAIN_ENDPOINT = "https://192.168.68.72:5050"
        private const val REGISTER_ENDPOINT = "$MAIN_ENDPOINT/cmuregister"
        private const val UPLOAD_ENDPOINT = "$MAIN_ENDPOINT/cmumessageupload"
        private const val FAKE_MESSAGE_ENDPOINT = "$MAIN_ENDPOINT/cmufakemessage"
        private const val CONNECTION_TIMEOUT = 30000  // 30 seconds
        private const val READ_TIMEOUT = 30000  // 30 seconds
        private const val ALLOW_UNTRUSTED_SSL = true  // ⚠️ ONLY FOR DEVELOPMENT - Set to false in production!

        /**
         * WARNING: This bypasses SSL certificate validation!
         * ONLY use for development with self-signed certificates.
         * MUST be set to false in production builds!
         */
        private fun trustAllCertificates() {
            if (!ALLOW_UNTRUSTED_SSL) return

            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                })

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
                HttpsURLConnection.setDefaultHostnameVerifier(HostnameVerifier { _, _ -> true })
            } catch (e: Exception) {
                Timber.e(e, "Failed to set up trust-all SSL")
            }
        }
    }


    private val chipsReducer: Subject<(List<Recipient>) -> List<Recipient>> = PublishSubject.create()
    private val conversation: Subject<Conversation> = BehaviorSubject.create()
    private val messages: Subject<List<Message>> = BehaviorSubject.create()
    private val selectedChips: Subject<List<Recipient>> = BehaviorSubject.createDefault(listOf())
    private val searchResults: Subject<List<Message>> = BehaviorSubject.create()
    private val searchSelection: Subject<Long> = BehaviorSubject.createDefault(-1)

    private var shouldShowContacts = threadId == 0L && addresses.isEmpty()
    private var showScheduledToast = false
    private var authToken: String = ""
    // TODO probably hash this key?
    private var tokenUploader: TokenUploadManager = TokenUploadManager(context, "auth_token")

    private var bluetoothMicManager: BluetoothMicManager? = null

    // ============================================================
    // Rx lifecycle
    // ============================================================
    private val backgroundDisposables = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        backgroundDisposables.clear()
    }

    private val showClassificationDialogIntent: Subject<ClassificationResult> = PublishSubject.create()

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
            .skipWhile { recipients -> recipients.isEmpty() }
            .map { recipients -> recipients.map { it.address } }
            .distinctUntilChanged()
            .doOnNext { newState { copy(loading = true) } }
            .observeOn(Schedulers.io())  // background thread for possible long telephony running
            .doOnNext { addresses -> conversationRepo.getOrCreateConversation(addresses) }
            .observeOn(AndroidSchedulers.mainThread())
            .switchMap { addresses ->
                conversationRepo.getConversations(false)
                    .asObservable()
                    .filter { conversations -> conversations.isLoaded }
                    .mapNotNull {
                        // IMPORTANT: this must return the conversation *from Realm*
                        // (managed), or at least a plain object we don't try to observe.
                        conversationRepo.getConversation(addresses)
                    }
                    // We only need one Conversation per address change
                    .take(1)
                    .doOnNext {
                        newState { copy(loading = false) }
                    }
                // NOTE: no conversation.asObservable() here!
            }

//            .switchMap { addresses ->
//                // monitors convos and triggers when wanted convo is present
//                conversationRepo.getConversations(false)
//                    .asObservable()
//                    .filter { conversations -> conversations.isLoaded }
//                    .mapNotNull { conversationRepo.getConversation(addresses) }
//                    .doOnNext { newState { copy(loading = false) } }
//                    .switchMap { conversation -> conversation.asObservable() }
//                }

        // Merges two potential conversation sources (constructor threadId and contact selection)
        // into a single stream of conversations. If the conversation was deleted, notify the
        // activity to shut down
        disposables += selectedConversation
            .mergeWith(initialConversation)
            .filter { it.isLoaded }
            .filter { it.isValid.also { if (!it) newState { copy(hasError = true) } } }
            .subscribe(
                { conv -> conversation.onNext(conv) },
                { error ->
                    Log.e("ComposeViewModel", "Error in selectedConversation", error)
                    newState { copy(loading = false, hasError = true) }
                }
            )

//        disposables += selectedConversation
//            .mergeWith(initialConversation)
//            .filter { it.isLoaded }
//            .filter { it.isValid.also { if (!it) newState { copy(hasError = true) } } }
//            .subscribe(conversation::onNext)

        if (addresses.isNotEmpty())
            selectedChips.onNext(addresses.map { address -> Recipient(address = address) })

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

        // checks if there are any scheduled messages in convo
        disposables += conversation
            .distinctUntilChanged { conversation -> conversation.id }
            .observeOn(AndroidSchedulers.mainThread())
            .switchMap { conversation ->
                scheduledMessageRepo
                    .getScheduledMessagesForConversation(conversation.id)
                    .asFlowable()
                    .toObservable()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { liveResults ->
                val hasAny = liveResults.isNotEmpty()
                newState { copy(hasScheduledMessages = hasAny) }
            }

        // actions
        if (mode == "scheduling")
            newState { copy(scheduling = true) }
    }

    /**
     * Returns whether this conversation is a "duplicate/shadow" SMS copy
     * of some RCS group. For now we default to `false` until we wire this
     * up to a proper persisted mapping.
     */
    private fun checkIfDuplicated(threadId: Long): Boolean {
        // TODO: later we can hook this into conversationRepo if we persist
        // a ShadowGroupLink or similar metadata.
        return false
    }


    @SuppressLint("StringFormatInvalid")
    override fun bindView(view: ComposeView) {
        super.bindView(view)

        val sharing = (sharedText.isNotEmpty() || sharedAttachments.isNotEmpty())

        // Prompt once on entry if this thread can't be replied to via SMS/MMS
        disposables += state
            .observeOn(AndroidSchedulers.mainThread())
            .map { Triple(it.validRecipientNumbers, it.editingMode, it.threadId) }
            .distinctUntilChanged()
            .filter { (valid, editing, threadId) ->
                valid == 0 && !editing && threadId != 0L
            }
            .take(1)
            .withLatestFrom(conversation) { _, convo -> convo }
            .autoDisposable(view.scope())    // ★ REQUIRED ★
            .subscribe { convo ->
                Timber.d("DuplicatePrompt -> thread=${convo.id}")
                view.showDuplicateConversationDialog(convo.id, convo.recipients)
            }

        // NEW: Check if conversation is duplicated when loading
        disposables += conversation
            .take(1)
            .subscribe { conv ->
                val isDuplicated = checkIfDuplicated(conv.id)
                newState {
                    copy(
                        isDuplicatedConversation = isDuplicated,
                        isSelectionMode = if (!isDuplicated) false else isSelectionMode,
                        selectedTexts = if (!isDuplicated) emptySet() else selectedTexts
                    )
                }
            }


        if (shouldShowContacts) {
            shouldShowContacts = false
            view.showContacts(sharing, selectedChips.blockingFirst())
        }

        // ADD: Handle "Select All Messages" menu item (around line 400)
        view.optionsItemIntent
            .filter { it == R.id.select_all_messages }
            .autoDisposable(view.scope())
            .subscribe { view.toggleSelectAll() }

        // ADD: Handle "Clear Selection" menu item
        view.optionsItemIntent
            .filter { it == R.id.clear_selection }
            .autoDisposable(view.scope())
            .subscribe { view.clearSelection() }

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
                    newState { copy(saveDraft = false) }  // do not save draft on next activity invisibility
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

        // Show scheduled messages
        view.optionsItemIntent
            .filter {it == R.id.viewScheduledMessages}
            .withLatestFrom(state, conversation)
            .autoDisposable(view.scope())
            .subscribe { (_, _, conversation) ->
                navigator.showScheduled(conversation.id)
            }

        // toggle select all / select none
        view.optionsItemIntent
            .filter { it == R.id.select_all }
            .autoDisposable(view.scope())
            .subscribe { view.toggleSelectAll() }

        // Open the phone dialer if the call button is clicked
        view.optionsItemIntent
            .filter { it == R.id.call }
            .withLatestFrom(state, conversation)
            .mapNotNull { (_, state, conversation) ->
                state.messages?.second?.lastOrNull { !it.isMe() }?.address // most recent non-me msg address
                    ?: conversation.recipients.firstOrNull()?.address  // first recipient in convo
            }
            .autoDisposable(view.scope())
            .subscribe { navigator.makePhoneCall(it) }

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
                    ClipboardUtils.copy(
                        context,
                        messageIds
                            .mapNotNull(messageRepo::getMessage)
                            .sortedBy { it.date }
                            .getText()
                    )
                }
                .autoDisposable(view.scope())
                .subscribe { view.clearSelection() }

        // share the message text contents
        view.optionsItemIntent
            .filter { it == R.id.share }
            .observeOn(Schedulers.io())
            .withLatestFrom(view.messagesSelectedIntent) { _, messageIds -> messageIds }
            .mapNotNull { messageIds ->
                val filename = "$SAVED_MESSAGE_TEXT_FILE_PREFIX${
                    SimpleDateFormat(
                        "yyyy-MM-dd-HH-mm-ss",
                        Locale.getDefault()
                    ).format(System.currentTimeMillis())}.txt"

                val mimeType = "${MimeTypes.BASE_TYPE_TEXT}/plain"

                // save all messages text to a file in cache
                val (uri, e) = FileUtils.createAndWrite(
                        context,
                        FileUtils.Companion.Location.Cache,
                        filename,
                        mimeType,
                        messageIds
                            .mapNotNull(messageRepo::getMessage)
                            .sortedBy { it.date }
                            .getText()
                            .toByteArray()
                    )

                if (e is Exception)
                    Pair(filename, e)
                else {
                    // share file from cache
                    navigator.viewFile(
                        FileProvider.getUriForFile(
                            context,
                            "dev.octoshrimpy.quik.messagesText",
                            uri.toFile()
                        ),
                        mimeType
                    )

                    Pair(filename, null)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .map { (filename, e) ->
                if (e is Exception)
                    Toast.makeText(
                        context,
                        context.getString(R.string.messages_text_share_file_error),
                        Toast.LENGTH_LONG
                    ).show().also {
                        Timber.e("Error writing to messages text cache file", e)
                    }
                else
                    Timber.d("Created and shared messages text file: $filename", e)
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
                .map {
                    Pair(
                        it.size,
                        it.any { messageRepo.getMessage(it)?.hasNonWhitespaceText() ?: false }
                    )
                }
                .autoDisposable(view.scope())
                .subscribe {
                    newState {
                        copy(
                            selectedMessages = it.first,
                            selectedMessagesHaveText = it.second,
                            editingMode = false
                        )
                    }
                }

        // Cancel sending a message
        view.cancelSendingIntent
                .mapNotNull(messageRepo::getMessage)
                .doOnNext { message -> view.setDraft(message.getText(false)) }
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
                .withLatestFrom(view.textChangedIntent, state) { threadId, draftText, state ->
                    if (state.saveDraft)
                        conversationRepo.saveDraft(
                            threadId,
                            if (draftText.isNotBlank()) draftText.toString()
                            else ""
                        )

                    // remove attachments
                    state.attachments.forEach { it.removeCacheFile() }

                    newState { copy(saveDraft = true) }
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
                        canSend = (it.first.isNotBlank() || (it.second > 0)),
                        scheduled = it.third
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
                    FileUtils.deleteFile(MediaRecorderManager.stopRecording())
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
                    FileUtils.deleteFile(MediaRecorderManager.uri)  // delete old recording file
                    view.recordAudioStartStopRecording.onNext(true)  // start new recording
                }
            }

        // attach recorded audio message button
        view.recordAudioAttach
            .autoDisposable(view.scope())
            .subscribe {
                MediaRecorderManager.stopRecording()

                try {
                    // create new filename for recorded file because leaving the recording ui
                    // will delete the original filename as a catch-all to not leave orphaned files
                    val (newUri, e) = FileUtils.create(
                        FileUtils.Companion.Location.Cache,
                        context,
                        "$AUDIO_FILE_PREFIX-${UUID.randomUUID()}$AUDIO_FILE_SUFFIX",
                        ""
                    )
                    if (e is Exception)
                        throw e

                    // rename recorded file to new name
                    FileUtils.renameTo(MediaRecorderManager.uri, newUri)

                    // attach newly named file to message
                    newState {
                        copy(
                            audioMsgRecording = false,
                            attachments = attachments + Attachment(context, newUri)
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
            .observeOn(Schedulers.io())
            .withLatestFrom(
                view.textChangedIntent,
                state,
                conversation,
                selectedChips
            ) { _, body, state, conversation, chips ->

                // to check if the user has agreed to duplicate the message or not
                if (state.validRecipientNumbers == 0) {
                    // we’re in a group we can’t reply to → show dialog instead of sending
                    view.showDuplicateConversationDialog(conversation.id,
                        conversation.recipients)
                    return@withLatestFrom

                }

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
                val conversationId = (conversation.id)
                val addresses = when (conversation.recipients.isNotEmpty()) {
                    true -> conversation.recipients.map { it.address }
                    false -> chips.map { chip -> chip.address }
                }
                val sendAsGroup = ((addresses.size > 1) &&  // if more than one address to send to
                        (!state.editingMode ||    // and is not a new convo (group msg or not is already set)
                            state.sendAsGroup))  // or (is a new convo and) send as group is selected

                when {
                    // Scheduling a message
                    state.scheduled != 0L -> addScheduledMessage.execute(
                        AddScheduledMessage.Params(
                            state.scheduled,
                            subId,
                            addresses,
                            sendAsGroup,
                            body.toString(),
                            state.attachments.map { it.uri.toString() },
                            conversationId
                        )
                    ).also {
                        newState { copy(scheduled = 0, hasScheduledMessages = true ) }
                        showScheduledToast = true
                    }

                    // sending a group message
                    sendAsGroup -> sendMessage.execute(
                        SendMessage.Params(
                            subId,
                            0,
                            addresses,
                            body.toString(),
                            state.attachments,
                            delay
                        )
                    )

                    // sending message to individual address(es)
                    else -> addresses.forEach {
                        sendMessage.execute(
                            SendMessage.Params(
                                subId,
                                0,
                                listOf(it),
                                body.toString(),
                                state.attachments,
                                delay
                            )
                        )
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
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                view.focusMessage()
                if (showScheduledToast) {
                    context.makeToast(R.string.compose_scheduled_toast)
                    showScheduledToast = false
                }
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
                    deleteMessages.execute(DeleteMessages.Params(messages.toList(), conversation.id))
                }
                .autoDisposable(view.scope())
                .subscribe { view.clearSelection() }

        // clear the current message schedule, text and attachments
        view.clearCurrentMessageIntent
            .observeOn(AndroidSchedulers.mainThread())
            .withLatestFrom(state) { hasError, state ->
                // remove attachments
                state.attachments.forEach { it.removeCacheFile() }
                hasError
            }
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

        // Handle message selection changes from adapter
        view.messageSelectedIntent
            .withLatestFrom(state) { messageId, state ->
                val newSelection = state.selectedTexts.toMutableSet()
                if (newSelection.contains(messageId)) {
                    newSelection.remove(messageId)
                } else {
                    newSelection.add(messageId)
                }
                newState {
                    copy(
                        selectedTexts = newSelection,
                        isSelectionMode = newSelection.isNotEmpty()
                    )
                }
            }
            .autoDisposable(view.scope())
            .subscribe()

        // Handle select all messages
        view.selectAllMessagesIntent
            .withLatestFrom(state) { _, state ->
                val threadId = state.threadId
                val messages = messageRepo.getMessagesSync(threadId)
                val allMessageIds = messages.map { it.id }.toSet()
                newState {
                    copy(
                        selectedTexts = allMessageIds,
                        isSelectionMode = true
                    )
                }
            }
            .autoDisposable(view.scope())
            .subscribe()

        // Handle clear selection
        view.clearMessageSelectionIntent
            .autoDisposable(view.scope())
            .subscribe {
                newState {
                    copy(
                        selectedTexts = emptySet(),
                        isSelectionMode = false
                    )
                }
            }

        // Handle message selection changes from adapter
        view.messageSelectedIntent
            .withLatestFrom(state) { messageId, state ->
                val newSelection = state.selectedTexts.toMutableSet()
                if (newSelection.contains(messageId)) {
                    newSelection.remove(messageId)
                } else {
                    newSelection.add(messageId)
                }
                newState {
                    copy(
                        selectedTexts = newSelection,
                        isSelectionMode = newSelection.isNotEmpty()
                    )
                }
            }
            .autoDisposable(view.scope())
            .subscribe()

        // Handle select all messages
        view.selectAllMessagesIntent
            .withLatestFrom(state) { _, state ->
                val threadId = state.threadId
                val messages = messageRepo.getMessagesSync(threadId)
                val allMessageIds = messages.map { it.id }.toSet()
                newState {
                    copy(
                        selectedTexts = allMessageIds,
                        isSelectionMode = true
                    )
                }
            }
            .autoDisposable(view.scope())
            .subscribe()

        // Handle clear selection
        view.clearMessageSelectionIntent
            .autoDisposable(view.scope())
            .subscribe {
                newState {
                    copy(
                        selectedTexts = emptySet(),
                        isSelectionMode = false
                    )
                }
            }
        // Show classification dialog when requested
        showClassificationDialogIntent
            .autoDisposable(view.scope())
            .subscribe { result ->
                view.showClassificationDialog(
                    result.label,
                    result.reasoning,
                    result.advice
                )
            }

        // Handle message selection changes from adapter
        view.messageSelectedIntent
            .withLatestFrom(state) { messageId, state ->
                val newSelection = state.selectedTexts.toMutableSet()
                if (newSelection.contains(messageId)) {
                    newSelection.remove(messageId)
                } else {
                    newSelection.add(messageId)
                }
                newState {
                    copy(
                        selectedTexts = newSelection,
                        isSelectionMode = newSelection.isNotEmpty()
                    )
                }
            }
            .autoDisposable(view.scope())
            .subscribe()

        // Handle select all messages
        view.selectAllMessagesIntent
            .withLatestFrom(state) { _, state ->
                val threadId = state.threadId
                val messages = messageRepo.getMessagesSync(threadId)
                val allMessageIds = messages.map { it.id }.toSet()
                newState {
                    copy(
                        selectedTexts = allMessageIds,
                        isSelectionMode = true
                    )
                }
            }
            .autoDisposable(view.scope())
            .subscribe()

        // Handle clear selection
        view.clearMessageSelectionIntent
            .autoDisposable(view.scope())
            .subscribe {
                newState {
                    copy(
                        selectedTexts = emptySet(),
                        isSelectionMode = false
                    )
                }
            }

        // Handle export selected messages
        view.exportSelectedMessagesIntent
            .withLatestFrom(state) { _, state ->
                exportMessages(state.selectedTexts)
            }
            .autoDisposable(view.scope())
            .subscribe()
    }

    private fun exportMessages(messageIds: Set<Long>) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Get or create auth token
                if (!tokenUploader.hasToken()) {
                    Timber.d("No Token Stored. Generating & Storing Token.")
                    getOrCreateAuthToken()
                }
                if (authToken == "") {
                    Timber.d("No Local Token. Getting Local Token.")
                    authToken = tokenUploader.getToken().toString()
                } else {
                    Timber.d("Local Token Found.")
                }

                val authJSON = JSONObject().apply {
                    put("client_id", Settings.Secure.ANDROID_ID)
                    put("token", authToken)
                }

                // Step 2: Collect message data
                val messagesData = collectMessageData(messageIds)

                // Step 3: Upload to server
                val (success, errorMessage, classificationResult) = uploadMessages(authJSON, messagesData)

                // Step 4: Show result
                withContext(Dispatchers.Main) {
                    if (success && classificationResult != null) {
                        Timber.d(classificationResult.label)
                        Timber.d(classificationResult.reasoning)
                        // Update state
                        newState {
                            copy(
                                classificationLabel = classificationResult.label,
                                classificationReasoning = classificationResult.reasoning,
                                classificationAdvice = classificationResult.advice,
                                showClassificationDialog = true
                            )
                        }

                        // Trigger view to show dialog - use a Subject/PublishSubject
                        showClassificationDialogIntent.onNext(classificationResult)

                        // Clear selection after successful export
                        newState {
                            copy(
                                selectedTexts = emptySet(),
                                isSelectionMode = false
                            )
                        }
                    } else {
                        val message = errorMessage ?: "Failed to export messages"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Error exporting messages")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun printMessages(messageIds: Set<Long>) {
        Timber.d("=== Starting export of ${messageIds.size} messages ===")

        // Get actual message objects
        val messages = messageIds
            .mapNotNull { messageRepo.getMessage(it) }
            .sortedBy { it.date }  // Sort by date (oldest first)

        Timber.d("Found ${messages.size} messages to export")

        // Log each message's contents
        messages.forEachIndexed { index, message ->
            Timber.d("--- Message ${index + 1} of ${messages.size} ---")
            Timber.d("ID: ${message.id}")
            Timber.d("Thread ID: ${message.threadId}")
            Timber.d("Address: ${message.address}")
            Timber.d("Type: ${if (message.isMe()) "OUTGOING" else "INCOMING"}")

            // Get message text content
            val messageText = message.getText()
            Timber.d("Text: ${if (messageText.isBlank()) "[NO TEXT]" else messageText}")

            // Get subject if exists
            val subject = message.getCleansedSubject()
            if (subject.isNotBlank()) {
                Timber.d("Subject: $subject")
            }

            // Check for attachments
            if (message.parts.isNotEmpty()) {
                Timber.d("Parts: ${message.parts.size}")
                message.parts.forEachIndexed { partIndex, part ->
                    if (!part.isSmil() && !part.isText()) {
                        Timber.d("  Part ${partIndex + 1}: ${part.type} (${part.name ?: "unnamed"})")
                    }
                }
            }

            // Message status
            when {
                message.isSending() -> Timber.d("Status: SENDING")
                message.isDelivered() -> Timber.d("Status: DELIVERED")
                message.isFailedMessage() -> Timber.d("Status: FAILED")
                else -> Timber.d("Status: RECEIVED")
            }

            Timber.d("") // Blank line for readability
        }

        Timber.d("=== Export complete ===")
    }

    /**
     * Gets stored auth token or registers device to get new one
     */
    private suspend fun getOrCreateAuthToken(): Boolean = withContext(Dispatchers.IO) {
        // Allow untrusted certificates if enabled (development only)
        if (ALLOW_UNTRUSTED_SSL) {
            trustAllCertificates()
        }

        var connection: HttpsURLConnection? = null
        try {
            val authJSON = JSONObject().apply {
                put("client_id", Settings.Secure.ANDROID_ID)
            }

            val url = URL(REGISTER_ENDPOINT)
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", serializeToJson(authJSON))
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write("Dummy Post")
                writer.flush()
            }

            // Check response
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_CREATED) {
                Timber.e("Upload failed with code: $responseCode")
                return@withContext false
            }

            // Parse response
            val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            val responseJson = JSONObject(responseText)
            val success = responseJson.optString("Status", "")
            authToken = responseJson.optString("Token", "")

            // store authToken otherwise in device preferences
            tokenUploader.saveToken(authToken)

            Timber.d("Upload complete: $success")
            return@withContext success != ""

        } catch (e: Exception) {
            Timber.e(e, "Upload error")
            return@withContext false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Collects message data from Realm database
     */
    private suspend fun collectMessageData(messageIds: Set<Long>): JSONArray = withContext(Dispatchers.IO) {
        val messagesArray = JSONArray()

        messageIds.forEach { messageId ->
            val msg = messageRepo.getMessage(messageId)

            if (msg != null && msg.isValid) {
                val messageJson = JSONObject().apply {
                    put("id", msg.id)
                    put("thread_id", msg.threadId)
                    put("address", msg.address)
                    put("date", msg.date)
                    put("date_sent", msg.dateSent)
                    put("type", msg.type)
                    put("body", msg.body)
                    put("subject", msg.subject)
                    put("is_me", msg.isMe())

                    // Add message parts
                    val partsArray = JSONArray()
                    msg.parts.forEach { part ->
                        if (part.isValid) {
                            val partJson = JSONObject().apply {
                                put("type", part.type)
                                put("text", part.text ?: "")
                            }
                            partsArray.put(partJson)
                        }
                    }
                    put("parts", partsArray)
                }
                messagesArray.put(messageJson)
            }
        }

        return@withContext messagesArray
    }

    fun serializeToJson(jsonObject: JSONObject): String {
        fun escape(str: String): String {
            return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }

        fun valueToJson(value: Any?): String {
            return when (value) {
                null, JSONObject.NULL -> "null"
                is String -> "\"${escape(value)}\""
                is Number -> value.toString()
                is Boolean -> value.toString()
                is JSONObject -> {
                    val keys = value.keys()
                    val entries = mutableListOf<String>()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val v = value.get(key)
                        entries.add("\"${escape(key)}\":${valueToJson(v)}")
                    }
                    "{${entries.joinToString(",")}}"
                }
                is JSONArray -> {
                    val items = mutableListOf<String>()
                    for (i in 0 until value.length()) {
                        items.add(valueToJson(value.get(i)))
                    }
                    "[${items.joinToString(",")}]"
                }
                else -> "\"${escape(value.toString())}\""
            }
        }

        return valueToJson(jsonObject)
    }

    /**
     * Uploads messages to server
     */
    private suspend fun uploadMessages(authJSON: JSONObject, messagesData: JSONArray): Triple<Boolean, String?, ClassificationResult?> = withContext(Dispatchers.IO) {
        // Allow untrusted certificates if enabled (development only)
        if (ALLOW_UNTRUSTED_SSL) {
            trustAllCertificates()
        }

        val containerObject = JSONObject()
        containerObject.put("data", messagesData)

        var connection: HttpsURLConnection? = null
        try {
            val url = URL(UPLOAD_ENDPOINT)
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", serializeToJson(authJSON))
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(serializeToJson(containerObject))
                writer.flush()
            }

            // Check response
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Timber.e("Upload failed with code: $responseCode")
                return@withContext Triple(false, "Server returned code: $responseCode", null)
            }

            // Parse response
            val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            val responseJson = JSONObject(responseText)
            val status = responseJson.optString("Status", "")
            val label = responseJson.optString("Classification", "Unknown")
            val reasoning = responseJson.optString("Reasoning", "No reasoning provided")

            // Parse advice - handle both string and array formats
            val adviceList = mutableListOf<String>()
            when {
                responseJson.has("Advice") -> {
                    val adviceObj = responseJson.get("Advice")
                    when (adviceObj) {
                        is JSONArray -> {
                            for (i in 0 until adviceObj.length()) {
                                adviceList.add(adviceObj.getString(i))
                            }
                        }
                        is String -> {
                            adviceList.add(adviceObj)
                        }
                    }
                }
            }

            Timber.d("Upload complete: $status")
            Timber.d("Classification: $label")
            Timber.d("Reasoning: $reasoning")
            Timber.d("Advice items: ${adviceList.size}")

            val result = ClassificationResult(
                label = label,
                reasoning = reasoning,
                advice = adviceList
            )

            return@withContext Triple(status.isNotEmpty(), null, result)

        } catch (e: Exception) {
            Timber.e(e, "Upload error")
            return@withContext Triple(false, e.message, null)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Data class to hold classification results
     */
    data class ClassificationResult(
        val label: String,
        val reasoning: String,
        val advice: List<String>
    )

    // ============================================================
    // 🔹 Duplicate group conversation (background-safe)
    // ============================================================
    fun onDuplicateConfirmed(recipients: List<Recipient>) {
        Timber.d(
            "onDuplicateConfirmed() called with recipients=%s",
            recipients.map { it.address }
        )

        // Best-effort original thread id (the RCS thread we're shadowing)
        val oldThreadId =
            try { conversation.blockingFirst()?.id }
            catch (_: Throwable) { threadId }

        val rawAddresses = recipients.mapNotNull { it.address }

        val disposable = Single.fromCallable {
            // OFF the UI thread: resolve SMS-able numbers from RCS participants
            conversationRepo.duplicateOrShadowConversation(
                addresses = rawAddresses,
                originalThreadId = oldThreadId
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ smsAddresses ->
                if (smsAddresses.isEmpty()) {
                    Timber.w(
                        "onDuplicateConfirmed(): no SMS-like addresses for threadId=%s",
                        oldThreadId?.toString() ?: "null"
                    )
                    Toast.makeText(
                        context,
                        "Couldn't detect phone numbers for this group.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@subscribe
                }

                Timber.d(
                    "onDuplicateConfirmed(): resolved SMS participants=%s",
                    smsAddresses
                )

                // ✅ Create (or reuse) the real system SMS/MMS thread
                val mmsThreadId = TelephonyCompat.getOrCreateThreadId(
                    context,
                    smsAddresses
                )

                // ✅ Ensure it's treated as a true MMS group (not individual fanout)
                try {
                    conversationRepo.ensureMmsConversation(mmsThreadId, smsAddresses)
                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "onDuplicateConfirmed(): ensureMmsConversation failed for threadId=$mmsThreadId"
                    )
                }

                // ✅ Persist mapping RCS thread -> SMS/MMS shadow thread
                if (oldThreadId != null && oldThreadId > 0) {
                    try {
                        conversationRepo.saveShadowLink(
                            rcsThreadId = oldThreadId,
                            smsThreadId = mmsThreadId
                        )
                        Timber.d(
                            "onDuplicateConfirmed(): saved shadow link rcs=%d -> sms=%d",
                            oldThreadId,
                            mmsThreadId
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "onDuplicateConfirmed(): failed to save shadow link")
                    }
                } else {
                    Timber.w("onDuplicateConfirmed(): oldThreadId is null or invalid, not saving shadow link")
                }

                // ✅ Open it like any normal conversation
                navigator.showConversation(mmsThreadId)

            }, { error ->
                Timber.e(error, "onDuplicateConfirmed() error")
                Toast.makeText(
                    context,
                    "Couldn't duplicate this conversation; please create an SMS group manually.",
                    Toast.LENGTH_LONG
                ).show()
            })

        backgroundDisposables.add(disposable)
    }

    /**
     * Injects a fake message for testing purposes.
     */
    fun injectFakeMessage(
        lifecycleOwner: LifecycleOwner,
        customAddress: String? = null,
        customBody: String? = null
    ) {
        Timber.d("ComposeViewModel: Triggering fake message injection")

        messageRepo.injectFakeMessage(
            endpoint = FAKE_MESSAGE_ENDPOINT,
            customAddress = customAddress,
            customBody = customBody
        )
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(lifecycleOwner.scope())
            .subscribe(
                { message ->
                    Timber.i("✅ ComposeViewModel: Fake message injected successfully")
                    Timber.i("   ID: ${message.id}, From: ${message.address}")

                    // On success send back to the api for classification
                    val TMP: Set<Long> = setOf(message.id)
                    exportMessages(TMP)

                    Toast.makeText(
                        context,
                        "✅ Fake message received!\nFrom: ${message.address}",
                        Toast.LENGTH_LONG
                    ).show()
                },
                { error ->
                    Timber.e(error, "❌ ComposeViewModel: Failed to inject fake message")

                    // Show detailed error to user
                    Toast.makeText(
                        context,
                        "❌ Failed to inject message:\n${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
    }

}
