package dev.octoshrimpy.quik.feature.scheduled

import android.content.Context
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.common.util.ClipboardUtils
import dev.octoshrimpy.quik.common.util.extensions.makeToast
import dev.octoshrimpy.quik.interactor.SendScheduledMessage
import dev.octoshrimpy.quik.manager.BillingManager
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ScheduledViewModel @Inject constructor(
    billingManager: BillingManager,
    private val context: Context,
    private val navigator: Navigator,
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val sendScheduledMessage: SendScheduledMessage
) : QkViewModel<ScheduledView, ScheduledState>(ScheduledState(
    scheduledMessages = scheduledMessageRepo.getScheduledMessages()
)) {

    init {
        disposables += billingManager.upgradeStatus
            .subscribe { upgraded -> newState { copy(upgraded = upgraded) } }
    }

    override fun bindView(view: ScheduledView) {
        super.bindView(view)

        view.messageClickIntent
            .autoDisposable(view.scope())
            .subscribe { view.showMessageOptions() }

        view.messageMenuIntent
            .withLatestFrom(view.messageClickIntent) { itemId, messageId ->
                when (itemId) {
                    0 -> sendScheduledMessage.execute(messageId)
                    1 -> scheduledMessageRepo.getScheduledMessage(messageId)?.let { message ->
                        ClipboardUtils.copy(context, message.body)
                        context.makeToast(R.string.toast_copied)
                    }
                    2 -> {
                        val deleteSubscription = Observable.fromCallable {
                            scheduledMessageRepo.deleteScheduledMessage(messageId)
                        }
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                // Handle completion, e.g., log success or update UI
                            }, {
                                // Handle error, e.g., log or show error message
                            })

                        disposables += deleteSubscription
                    }
                }
                Unit
            }
            .autoDisposable(view.scope())
            .subscribe()

        view.composeIntent
            .autoDisposable(view.scope())
            .subscribe { navigator.showCompose(mode = "scheduling") }

        view.upgradeIntent
            .autoDisposable(view.scope())
            .subscribe { navigator.showQksmsPlusActivity("schedule_fab") }
    }
}
