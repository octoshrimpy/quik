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
package dev.octoshrimpy.quik.feature.main

import androidx.recyclerview.widget.ItemTouchHelper
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.extensions.mapNotNull
import dev.octoshrimpy.quik.interactor.DeleteConversations
import dev.octoshrimpy.quik.interactor.MarkAllSeen
import dev.octoshrimpy.quik.interactor.MarkArchived
import dev.octoshrimpy.quik.interactor.MarkPinned
import dev.octoshrimpy.quik.interactor.MarkRead
import dev.octoshrimpy.quik.interactor.MarkUnarchived
import dev.octoshrimpy.quik.interactor.MarkUnpinned
import dev.octoshrimpy.quik.interactor.MarkUnread
import dev.octoshrimpy.quik.interactor.MigratePreferences
import dev.octoshrimpy.quik.interactor.SpeakThreads
import dev.octoshrimpy.quik.interactor.SyncContacts
import dev.octoshrimpy.quik.interactor.SyncMessages
import dev.octoshrimpy.quik.listener.ContactAddedListener
import dev.octoshrimpy.quik.manager.BillingManager
import dev.octoshrimpy.quik.manager.ChangelogManager
import dev.octoshrimpy.quik.manager.PermissionManager
import dev.octoshrimpy.quik.manager.RatingManager
import dev.octoshrimpy.quik.model.EmojiSyncNeeded
import dev.octoshrimpy.quik.model.SyncLog
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.EmojiReactionRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.repository.SyncRepository
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MainViewModel @Inject constructor(
    billingManager: BillingManager,
    contactAddedListener: ContactAddedListener,
    markAllSeen: MarkAllSeen,
    migratePreferences: MigratePreferences,
    syncRepository: SyncRepository,
    private val changelogManager: ChangelogManager,
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val deleteConversations: DeleteConversations,
    private val markArchived: MarkArchived,
    private val markPinned: MarkPinned,
    private val markRead: MarkRead,
    private val markUnarchived: MarkUnarchived,
    private val markUnpinned: MarkUnpinned,
    private val markUnread: MarkUnread,
    private val speakThreads: SpeakThreads,
    private val navigator: Navigator,
    private val permissionManager: PermissionManager,
    private val prefs: Preferences,
    private val ratingManager: RatingManager,
    private val reactions: EmojiReactionRepository,
    private val syncContacts: SyncContacts,
    private val syncMessages: SyncMessages
) : QkViewModel<MainView, MainState>(
    MainState(page = Inbox(data = conversationRepo.getConversations(prefs.unreadAtTop.get())))
) {
    private var lastArchivedThreadIds = listOf<Long>(0)

    init {
        disposables += deleteConversations
        disposables += markAllSeen
        disposables += markArchived
        disposables += markUnarchived
        disposables += migratePreferences
        disposables += syncContacts
        disposables += syncMessages

        // Show the syncing UI
        disposables += syncRepository.syncProgress
                .sample(16, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .subscribe { syncing -> newState { copy(syncing = syncing) } }

        // Update the upgraded status
        disposables += billingManager.upgradeStatus
                .subscribe { upgraded -> newState { copy(upgraded = upgraded) } }

        // Show the rating UI
        disposables += ratingManager.shouldShowRating
                .subscribe { show -> newState { copy(showRating = show) } }


        // Migrate the preferences from 2.7.3
        migratePreferences.execute(Unit)


        // If we have all permissions and we've never run a sync, run a sync. This will be the case
        // when upgrading from 2.7.3, or if the app's data was cleared
        val lastSync = Realm.getDefaultInstance().use { realm -> realm.where(SyncLog::class.java)?.max("date") ?: 0 }
        if (lastSync == 0 && permissionManager.isDefaultSms() && permissionManager.hasReadSms() && permissionManager.hasContacts()) {
            syncMessages.execute(Unit)
        }

        // This is only used when we update to a version that newly supports emoji reactions
        Realm.getDefaultInstance().executeTransactionAsync { realm ->
            val emojiSyncNeeded = realm.where(EmojiSyncNeeded::class.java).findFirst()
            if (emojiSyncNeeded != null) {
                reactions.deleteAndReparseAllEmojiReactions(realm) { /* No progress ui needed here */ }
                emojiSyncNeeded.deleteFromRealm()
            }
        }

        // Sync contacts when we detect a change
        if (permissionManager.hasContacts()) {
            disposables += contactAddedListener.listen()
                    .debounce(1, TimeUnit.SECONDS)
                    .subscribeOn(Schedulers.io())
                    .subscribe { syncContacts.execute(Unit) }
        }

        ratingManager.addSession()
        markAllSeen.execute(Unit)
    }

    override fun bindView(view: MainView) {
        super.bindView(view)

        when {
            !permissionManager.isDefaultSms() -> view.requestDefaultSms()
            !permissionManager.hasReadSms() || !permissionManager.hasContacts() -> view.requestPermissions()
        }


        // when unreadAtTop preference changes, reload the model view data to refresh view
        prefs.unreadAtTop.asObservable()
            .skip(1)
            .debounce(400, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .withLatestFrom(state) { _, state ->
                if (state.page is Inbox)
                    newState {
                        copy(page = Inbox(data = conversationRepo.getConversations(prefs.unreadAtTop.get())))
                    }
                else if (state.page is Archived)
                    newState {
                        copy(page = Inbox(data = conversationRepo.getConversations(prefs.unreadAtTop.get(), true)))
                    }
            }
            .autoDisposable(view.scope())
            .subscribe()

        // If the default SMS state changes, reflect it in the State
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.isDefaultSms() }
            .distinctUntilChanged()
            .doOnNext { defaultSms -> newState { copy(defaultSms = defaultSms) } }
            .autoDisposable(view.scope())
            .subscribe()

        // If the SMS Permission state changes, reflect it in the State
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.hasReadSms() }
            .distinctUntilChanged()
            .doOnNext { smsPermission -> newState { copy(smsPermission = smsPermission) } }
            .autoDisposable(view.scope())
            .subscribe()

        // If the Contacts Permission state changes, reflect it in the State
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.hasContacts() }
            .distinctUntilChanged()
            .doOnNext { contactPermission -> newState { copy(contactPermission = contactPermission) } }
            .autoDisposable(view.scope())
            .subscribe()

        // If the Notifications Permission state changes, reflect it in the State
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.hasNotifications() }
            .distinctUntilChanged()
            .doOnNext { notificationPermission -> newState { copy(notificationPermission = notificationPermission) } }
            .autoDisposable(view.scope())
            .subscribe()

        // If we go from not having all SMS permissions to having them, sync messages
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.isDefaultSms() && permissionManager.hasReadSms() && permissionManager.hasContacts() }
            .distinctUntilChanged()
            .skip(1)
            .filter { hasAllPermissions -> hasAllPermissions }
            .autoDisposable(view.scope())
            .subscribe { syncMessages.execute(Unit) }

        // Launch screen from intent
        view.onNewIntentIntent
                .autoDisposable(view.scope())
                .subscribe { intent ->
                    when (intent.getStringExtra("screen")) {
                        "compose" -> navigator.showConversation(intent.getLongExtra("threadId", 0))
                        "blocking" -> navigator.showBlockedConversations()
                    }
                }

        // Show changelog
        if (changelogManager.didUpdate()) {
            if (Locale.getDefault().language.startsWith("en")) {
                GlobalScope.launch(Dispatchers.Main) {
                    val changelog = changelogManager.getChangelog()
                    changelogManager.markChangelogSeen()
                    view.showChangelog(changelog)
                }
            } else {
                changelogManager.markChangelogSeen()
            }
        } else {
            changelogManager.markChangelogSeen()
        }

        view.changelogMoreIntent
                .autoDisposable(view.scope())
                .subscribe { navigator.showChangelog() }

        view.queryChangedIntent
                .debounce(200, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .map { query -> query.trim() }
                .withLatestFrom(state) { query, state ->
                    if (query.isEmpty() && state.page is Searching) {
                        newState { copy(page = Inbox(data = conversationRepo.getConversations(prefs.unreadAtTop.get()))) }
                    }
                    query
                }
                .filter { query -> query.length >= 2 }
                .distinctUntilChanged()
                .doOnNext {
                    newState {
                        val page = (page as? Searching) ?: Searching()
                        copy(page = page.copy(loading = true))
                    }
                }
                .observeOn(Schedulers.io())
                .map(conversationRepo::searchConversations)
                .autoDisposable(view.scope())
                .subscribe { data -> newState { copy(page = Searching(loading = false, data = data)) } }

        view.activityResumedIntent
                .filter { resumed -> !resumed }
                .switchMap {
                    // Take until the activity is resumed
                    prefs.keyChanges
                            .filter { key -> key.contains("theme") }
                            .map { true }
                            .mergeWith(prefs.autoColor.asObservable().skip(1))
                            .doOnNext { view.themeChanged() }
                            .takeUntil(view.activityResumedIntent.filter { resumed -> resumed })
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.composeIntent
                .autoDisposable(view.scope())
                .subscribe { navigator.showCompose() }

        view.homeIntent
                .withLatestFrom(state) { _, state ->
                    when {
                        state.page is Searching -> view.clearSearch()
                        state.page is Inbox && state.page.selected > 0 -> view.clearSelection()
                        state.page is Archived && state.page.selected > 0 -> view.clearSelection()

                        else -> newState { copy(drawerOpen = true) }
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.drawerToggledIntent
            .doOnNext {
                newState { copy(drawerOpen = it) }
                view.drawerToggled(it)
            }
            .autoDisposable(view.scope())
            .subscribe { open -> newState { copy(drawerOpen = open) } }

        view.navigationIntent
                .withLatestFrom(state) { drawerItem, state ->
                    newState { copy(drawerOpen = false) }
                    when (drawerItem) {
                        NavItem.BACK -> when {
                            state.drawerOpen -> Unit
                            state.page is Searching -> view.clearSearch()
                            state.page is Inbox && state.page.selected > 0 -> view.clearSelection()
                            state.page is Archived && state.page.selected > 0 -> view.clearSelection()
                            state.page !is Inbox -> {
                                newState { copy(page = Inbox(data = conversationRepo.getConversations(prefs.unreadAtTop.get()))) }
                            }
                            else -> newState { copy(hasError = true) }
                        }
                        NavItem.BACKUP -> navigator.showBackup()
                        NavItem.SCHEDULED -> navigator.showScheduled(null)
                        NavItem.BLOCKING -> navigator.showBlockedConversations()
                        NavItem.MESSAGE_UTILS -> navigator.showMessageUtils()
                        NavItem.SETTINGS -> navigator.showSettings()
//                        NavItem.PLUS -> navigator.showQksmsPlusActivity("main_menu")
//                        NavItem.HELP -> navigator.showSupport()
                        NavItem.INVITE -> navigator.showInvite()
                        else -> Unit
                    }
                    drawerItem
                }
                .distinctUntilChanged()
                .doOnNext { drawerItem ->
                    when (drawerItem) {
                        NavItem.INBOX -> newState { copy(page = Inbox(data = conversationRepo.getConversations(prefs.unreadAtTop.get()))) }
                        NavItem.ARCHIVED -> newState { copy(page = Archived(data = conversationRepo.getConversations(prefs.unreadAtTop.get(), true))) }
                        else -> Unit
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
            .filter { itemId -> itemId == R.id.select_all }
            .autoDisposable(view.scope())
            .subscribe { view.toggleSelectAll() }

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.archive }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markArchived.execute(conversations)
                    lastArchivedThreadIds = conversations.toList()
                    view.showArchivedSnackbar(lastArchivedThreadIds.count(), true)
                    view.clearSelection()
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.unarchive }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markUnarchived.execute(conversations.toList())
                    view.showArchivedSnackbar(conversations.count(), false)
                    view.clearSelection()
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.delete }
                .filter { permissionManager.isDefaultSms().also { if (!it) view.requestDefaultSms() } }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    view.showDeleteDialog(conversations)
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.add }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations -> conversations }
                .doOnNext { view.clearSelection() }
                .filter { conversations -> conversations.size == 1 }
                .map { conversations -> conversations.first() }
                .mapNotNull(conversationRepo::getConversation)
                .map { conversation -> conversation.recipients }
                .mapNotNull { recipients -> recipients[0]?.address?.takeIf { recipients.size == 1 } }
                .doOnNext(navigator::addContact)
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.pin }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markPinned.execute(conversations.toList())
                    view.clearSelection()
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.unpin }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markUnpinned.execute(conversations.toList())
                    view.clearSelection()
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.read }
                .filter { permissionManager.isDefaultSms().also { if (!it) view.requestDefaultSms() } }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markRead.execute(conversations.toList())
                    view.clearSelection()
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.unread }
                .filter { permissionManager.isDefaultSms().also { if (!it) view.requestDefaultSms() } }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markUnread.execute(conversations.toList())
                    view.clearSelection()
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.block }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    view.showBlockingDialog(conversations.toList(), true)
                    view.clearSelection()
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.optionsItemIntent
            .filter { itemId -> itemId == R.id.rename }
            .withLatestFrom(view.conversationsSelectedIntent) { _, conversationIds -> conversationIds.first() }
            .mapNotNull { conversationId -> conversationRepo.getConversation(conversationId) }
            .autoDisposable(view.scope())
            .subscribe { conversation -> view.showRenameDialog(conversation.name) }

//        view.plusBannerIntent
//                .autoDisposable(view.scope())
//                .subscribe {
//                    newState { copy(drawerOpen = false) }
//                    navigator.showQksmsPlusActivity("main_banner")
//                }

        view.rateIntent
                .autoDisposable(view.scope())
                .subscribe {
                    navigator.showRating()
                    ratingManager.rate()
                }

        view.dismissRatingIntent
                .autoDisposable(view.scope())
                .subscribe { ratingManager.dismiss() }

        view.conversationsSelectedIntent
                .withLatestFrom(state) { selection, state ->
                    val conversations = selection.mapNotNull(conversationRepo::getConversation)
                    val add = conversations.firstOrNull()
                            ?.takeIf { conversations.size == 1 }
                            ?.takeIf { conversation -> conversation.recipients.size == 1 }
                            ?.recipients?.first()
                            ?.takeIf { recipient -> recipient.contact == null } != null
                    val pin = conversations.sumBy { if (it.pinned) -1 else 1 } >= 0
                    val read = when (conversations.size) {
                        0    -> false
                        1    -> conversations[0].unread
                        else -> true
                    }
                    val selected = selection.size

                    when (state.page) {
                        is Inbox -> {
                            val page = state.page.copy(addContact = add, markPinned = pin, markRead = read, selected = selected)
                            newState { copy(page = page) }
                        }

                        is Archived -> {
                            val page = state.page.copy(addContact = add, markPinned = pin, markRead = read, selected = selected)
                            newState { copy(page = page) }
                        }

                        is Searching -> {} // Ignore
                        else -> {}
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()

        // Delete the conversation
        view.confirmDeleteIntent
                .autoDisposable(view.scope())
                .subscribe { conversations ->
                    deleteConversations.execute(conversations.toList())
                    view.clearSelection()
                }

        view.renameConversationIntent
            .withLatestFrom(view.conversationsSelectedIntent) { newConversationName, selectedConversationIds ->
                Pair(newConversationName, selectedConversationIds.first())
            }
            .doOnNext { view.clearSelection() }
            .map { newNameAndConversationId ->
                conversationRepo.setConversationName(
                    newNameAndConversationId.second,
                    newNameAndConversationId.first
                )
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
            }
            .flatMapCompletable { it }
            .autoDisposable(view.scope())
            .subscribe()

        view.swipeConversationIntent
                .autoDisposable(view.scope())
                .subscribe { (threadId, direction) ->
                    val action =
                        if (direction == ItemTouchHelper.RIGHT) prefs.swipeRight.get()
                        else prefs.swipeLeft.get()
                    when (action) {
                        Preferences.SWIPE_ACTION_ARCHIVE ->
                            markArchived.execute(listOf(threadId)) {
                                lastArchivedThreadIds = listOf(threadId)
                                view.showArchivedSnackbar(1, true)
                            }
                        Preferences.SWIPE_ACTION_DELETE ->
                            view.showDeleteDialog(listOf(threadId))
                        Preferences.SWIPE_ACTION_BLOCK ->
                            view.showBlockingDialog(listOf(threadId), true)
                        Preferences.SWIPE_ACTION_CALL -> {
                            (
                                messageRepo.getMessagesSync(threadId).lastOrNull { !it.isMe() }
                                    ?.address // most recent non-me msg address
                                ?: conversationRepo.getConversation(threadId)
                                    ?.recipients?.firstOrNull()?.address  // first recipient in convo
                            )?.let(navigator::makePhoneCall)
                        }
                        Preferences.SWIPE_ACTION_READ -> markRead.execute(listOf(threadId))
                        Preferences.SWIPE_ACTION_UNREAD -> markUnread.execute(listOf(threadId))
                        Preferences.SWIPE_ACTION_SPEAK -> speakThreads.execute(listOf(threadId))
                    }
                }

        view.undoArchiveIntent
                .autoDisposable(view.scope())
                .subscribe {
                    markUnarchived.execute(lastArchivedThreadIds.toList())
                    lastArchivedThreadIds = listOf()
                }

        view.snackbarButtonIntent
                .withLatestFrom(state) { _, state ->
                    when {
                        !state.defaultSms -> view.requestDefaultSms()
                        !state.smsPermission -> view.requestPermissions()
                        !state.contactPermission -> view.requestPermissions()
                        !state.notificationPermission -> {
                            if (prefs.hasAskedForNotificationPermission.get()) {
                                navigator.showPermissions()
                            } else {
                                prefs.hasAskedForNotificationPermission.set(true)
                                view.requestPermissions()
                            }
                        }
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()
    }

}