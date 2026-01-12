package dev.octoshrimpy.quik.feature.messageutils

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.jakewharton.rxbinding2.view.clicks
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.util.extensions.animateLayoutChanges
import dev.octoshrimpy.quik.common.widget.QkSwitch
import dev.octoshrimpy.quik.databinding.MessageUtilsControllerBinding
import dev.octoshrimpy.quik.injection.appComponent
import dev.octoshrimpy.quik.repository.MessageRepository
import io.reactivex.Single
import javax.inject.Inject
class MessageUtilsController : QkController<MessageUtilsView, MessageUtilsState, MessageUtilsPresenter>(), MessageUtilsView {
    @Inject override lateinit var presenter: MessageUtilsPresenter
    private var binding: MessageUtilsControllerBinding? = null
    override val autoDeduplicateClickIntent by lazy { binding!!.autoDeduplicate.clicks() }
    override val deduplicateClickIntent by lazy { binding!!.deduplicateMessages.clicks() }

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = MessageUtilsControllerBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated() {
        super.onViewCreated()
        (binding?.root as? ViewGroup)?.postDelayed({
            binding?.parent?.animateLayoutChanges = true
        }, 100)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        setTitle(R.string.message_management_title)
        showBackButton(true)
        presenter.bindIntents(this)
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        binding = null
    }

    override fun render(state: MessageUtilsState) {
        binding?.autoDeduplicate
            ?.findViewById<QkSwitch>(R.id.checkbox)
            ?.isChecked = state.autoDeduplicateMessages

        val deduplicationProgress = binding!!.deduplicationProgress
        val progressAnimator = ObjectAnimator.ofInt(deduplicationProgress, "progress", 0, 0)

        when (state.deduplicationProgress) {
            is MessageRepository.DeduplicationProgress.Idle -> {
                deduplicationProgress.isVisible = false
            }

            is MessageRepository.DeduplicationProgress.Running -> {
                deduplicationProgress.isVisible = true
                deduplicationProgress.max = state.deduplicationProgress.max
                progressAnimator
                    .apply {
                        setIntValues(
                            deduplicationProgress.progress,
                            state.deduplicationProgress.progress
                        )
                    }
                    .start()
                deduplicationProgress.isIndeterminate =
                    state.deduplicationProgress.indeterminate
            }
        }
    }

    override fun showDeduplicationConfirmationDialog(): Single<Boolean> = Single.create { emitter ->
        AlertDialog.Builder(activity)
            .setTitle(R.string.deduplicate_messages_title)
            .setMessage(R.string.deduplicate_message_confirmation_dialog_message)
            .setPositiveButton(R.string.button_continue) { _, _ -> emitter.onSuccess(true) }
            .setNegativeButton(R.string.button_cancel) { _, _ -> emitter.onSuccess(false) }
            .setCancelable(false)
            .show()
    }

    override fun handleResult(resIdString: Int) {
        binding?.deduplicationProgressText?.isVisible = true
        binding?.deduplicationProgressText?.setText(resIdString)
    }
}
