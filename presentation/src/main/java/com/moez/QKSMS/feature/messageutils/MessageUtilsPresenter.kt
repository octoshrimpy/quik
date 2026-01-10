package dev.octoshrimpy.quik.feature.messageutils

import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkPresenter
import dev.octoshrimpy.quik.interactor.DeduplicateMessages
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MessageUtilsPresenter @Inject constructor(
    private val deduplicateMessages: DeduplicateMessages,
    private val prefs: Preferences,
    messageRepo: MessageRepository
) : QkPresenter<MessageUtilsView, MessageUtilsState>(MessageUtilsState()) {
    init {
        disposables += messageRepo.deduplicationProgress
            .sample(16, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .subscribe { deduplicateProgress -> newState { copy(deduplicationProgress = deduplicateProgress)}}

        disposables += prefs.autoDeduplicate.asObservable()
            .subscribe { enabled -> newState { copy(autoDeduplicateMessages = enabled) } }
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
                                view.handleResult(R.string.deduplicating_messages_finished)
                            }

                            is MessageRepository.DeduplicationResult.NoDuplicates -> {
                                view.handleResult(R.string.deduplicating_messages_no_messages)
                            }

                            is MessageRepository.DeduplicationResult.Failure -> {
                                view.handleResult(R.string.deduplicating_messages_failed)
                                Timber.e("Error: ${result.error.localizedMessage}")
                            }
                        }
                    }
            }

        view.autoDeduplicateClickIntent
            .autoDisposable(view.scope())
            .subscribe { prefs.autoDeduplicate.set(!prefs.autoDeduplicate.get()) }
    }
}