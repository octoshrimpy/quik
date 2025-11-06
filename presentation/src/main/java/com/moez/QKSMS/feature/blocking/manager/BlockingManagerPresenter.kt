package dev.octoshrimpy.quik.feature.blocking.manager

import android.content.Context
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.blocking.BlockingClient
import dev.octoshrimpy.quik.blocking.CallBlockerBlockingClient
import dev.octoshrimpy.quik.blocking.CallControlBlockingClient
import dev.octoshrimpy.quik.blocking.QksmsBlockingClient
import dev.octoshrimpy.quik.blocking.ShouldIAnswerBlockingClient
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkPresenter
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class BlockingManagerPresenter @Inject constructor(
    private val callBlocker: CallBlockerBlockingClient,
    private val callControl: CallControlBlockingClient,
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val navigator: Navigator,
    private val prefs: Preferences,
    private val qksms: QksmsBlockingClient,
    private val shouldIAnswer: ShouldIAnswerBlockingClient
) : QkPresenter<BlockingManagerView, BlockingManagerState>(BlockingManagerState(
        blockingManager = prefs.blockingManager.get(),
        callBlockerInstalled = callBlocker.isAvailable(),
        callControlInstalled = callControl.isAvailable(),
        siaInstalled = shouldIAnswer.isAvailable()
)) {

    init {
        disposables += prefs.blockingManager.asObservable()
                .subscribe { manager -> newState { copy(blockingManager = manager) } }
    }

    override fun bindIntents(view: BlockingManagerView) {
        super.bindIntents(view)

        view.activityResumed()
                .map { callBlocker.isAvailable() }
                .distinctUntilChanged()
                .autoDisposable(view.scope())
                .subscribe { available -> newState { copy(callBlockerInstalled = available) } }

        view.activityResumed()
                .map { callControl.isAvailable() }
                .distinctUntilChanged()
                .autoDisposable(view.scope())
                .subscribe { available -> newState { copy(callControlInstalled = available) } }

        view.activityResumed()
                .map { shouldIAnswer.isAvailable() }
                .distinctUntilChanged()
                .autoDisposable(view.scope())
                .subscribe { available -> newState { copy(siaInstalled = available) } }

        view.qksmsClicked()
                .observeOn(Schedulers.io())
                .map { getAddressesToBlock(qksms) }
                .switchMap { numbers -> qksms.block(numbers).andThen(Observable.just(Unit)) } // Hack
                .autoDisposable(view.scope())
                .subscribe {
                    prefs.blockingManager.set(Preferences.BLOCKING_MANAGER_QKSMS)
                }

        view.callBlockerClicked()
                .filter {
                    val installed = callBlocker.isAvailable()
                    if (!installed) {
                        navigator.installCallBlocker()
                    }

                    val enabled = prefs.blockingManager.get() == Preferences.BLOCKING_MANAGER_CB
                    installed && !enabled
                }
                .autoDisposable(view.scope())
                .subscribe {
                    prefs.blockingManager.set(Preferences.BLOCKING_MANAGER_CB)
                }

        view.callControlClicked()
                .filter {
                    val installed = callControl.isAvailable()
                    if (!installed) {
                        navigator.installCallControl()
                    }

                    val enabled = prefs.blockingManager.get() == Preferences.BLOCKING_MANAGER_CC
                    installed && !enabled
                }
                .observeOn(Schedulers.io())
                .map { getAddressesToBlock(callControl) }
                .observeOn(AndroidSchedulers.mainThread())
                .switchMap { numbers ->
                    when (numbers.size) {
                        0 -> Observable.just(true)
                        else -> view.showCopyDialog(context.getString(R.string.blocking_manager_call_control_title))
                                .toObservable()
                    }
                }
                .doOnNext { newState { copy() } } // Radio button may have been selected when it shouldn't, fix it
                .filter { it }
                .observeOn(Schedulers.io())
                .map { getAddressesToBlock(callControl) } // This sucks. Can't wait to use coroutines
                .switchMap { numbers -> callControl.block(numbers).andThen(Observable.just(Unit)) } // Hack
                .autoDisposable(view.scope())
                .subscribe {
                    callControl.shouldBlock("callcontrol").blockingGet()
                    prefs.blockingManager.set(Preferences.BLOCKING_MANAGER_CC)
                }

        view.siaClicked()
                .filter {
                    val installed = shouldIAnswer.isAvailable()
                    if (!installed) {
                        navigator.installSia()
                    }

                    val enabled = prefs.blockingManager.get() == Preferences.BLOCKING_MANAGER_SIA
                    installed && !enabled
                }
                .autoDisposable(view.scope())
                .subscribe {
                    prefs.blockingManager.set(Preferences.BLOCKING_MANAGER_SIA)
                }
    }

    private fun getAddressesToBlock(client: BlockingClient) = conversationRepo.getBlockedConversations()
        .fold(listOf<String>()) { numbers, conversation -> numbers + conversation.recipients.map { it.address } }
        .filter { number -> client.isBlacklisted(number).blockingGet() !is BlockingClient.Action.Block }

}
