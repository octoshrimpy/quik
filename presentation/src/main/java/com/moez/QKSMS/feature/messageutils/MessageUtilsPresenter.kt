package dev.octoshrimpy.quik.feature.messageutils

import android.content.Context
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkPresenter
import dev.octoshrimpy.quik.interactor.DeduplicateMessages
import dev.octoshrimpy.quik.interactor.DeleteOldMessages
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.service.AutoDeleteService
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MessageUtilsPresenter @Inject constructor(
    private val context: Context,
    private val deduplicateMessages: DeduplicateMessages,
    private val prefs: Preferences,
    private val deleteOldMessages: DeleteOldMessages,
    private val messageRepo: MessageRepository
) : QkPresenter<MessageUtilsView, MessageUtilsState>(MessageUtilsState()) {
    init {
        disposables += messageRepo.deduplicationProgress
            .sample(16, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .subscribe { deduplicateProgress -> newState { copy(deduplicationProgress = deduplicateProgress)}}

        disposables += prefs.autoDeduplicate.asObservable()
            .subscribe { enabled -> newState { copy(autoDeduplicateMessages = enabled) } }

        disposables += prefs.autoDelete.asObservable()
            .subscribe { autoDelete -> newState { copy(autoDelete = autoDelete) } }
    }

    override fun bindIntents(view: MessageUtilsView) {
        super.bindIntents(view)

        view.deduplicateClickIntent
            .flatMapSingle { view.showDeduplicationConfirmationDialog() }
            .filter { it }
            .observeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe {
                deduplicateMessages.buildObservable(Unit)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { result ->
                        when (result) {
                            is MessageRepository.DeduplicationResult.Success -> {
                                view.handleDeduplicationResult(R.string.deduplicating_messages_finished)
                            }

                            is MessageRepository.DeduplicationResult.NoDuplicates -> {
                                view.handleDeduplicationResult(R.string.deduplicating_messages_no_messages)
                            }

                            is MessageRepository.DeduplicationResult.Failure -> {
                                view.handleDeduplicationResult(R.string.deduplicating_messages_failed)
                                Timber.e("Error while deduplicating messages: ${result.error}")
                            }
                        }
                    }
            }

        view.autoDeduplicateClickIntent
            .autoDisposable(view.scope())
            .subscribe { prefs.autoDeduplicate.set(!prefs.autoDeduplicate.get()) }

        view.autoDeleteClickIntent
            .autoDisposable(view.scope())
            .subscribe { view.showAutoDeleteDialog(prefs.autoDelete.get()) }

        view.autoDeleteChanged()
            .observeOn(Schedulers.io())
            .filter { maxAge ->
                if (maxAge == 0) {
                    return@filter true
                }

                val counts = messageRepo.getOldMessageCounts(maxAge)
                if (counts.values.sum() == 0) {
                    return@filter true
                }

                runBlocking { view.showAutoDeleteWarningDialog(counts.values.sum()) }
            }
            .doOnNext { maxAge ->
                when (maxAge == 0) {
                    true -> AutoDeleteService.cancelJob(context)
                    false -> {
                        AutoDeleteService.scheduleJob(context)
                        deleteOldMessages.execute(Unit)
                    }
                }
            }
            .doOnNext(prefs.autoDelete::set)
            .autoDisposable(view.scope())
            .subscribe()
    }
}
