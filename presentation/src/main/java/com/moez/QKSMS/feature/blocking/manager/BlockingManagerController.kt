package dev.octoshrimpy.quik.feature.blocking.manager

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import com.jakewharton.rxbinding2.view.clicks
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import dev.octoshrimpy.quik.databinding.BlockingManagerControllerBinding
import dev.octoshrimpy.quik.injection.appComponent
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import javax.inject.Inject

class BlockingManagerController : QkController<BlockingManagerControllerBinding, BlockingManagerView, BlockingManagerState, BlockingManagerPresenter>(),
    BlockingManagerView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): BlockingManagerControllerBinding =
        BlockingManagerControllerBinding.inflate(inflater, container, false)

    @Inject lateinit var colors: Colors
    @Inject override lateinit var presenter: BlockingManagerPresenter

    private val activityResumedSubject: PublishSubject<Unit> = PublishSubject.create()

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.blocking_manager_title)
        showBackButton(true)

        val states = arrayOf(
                intArrayOf(android.R.attr.state_activated),
                intArrayOf(-android.R.attr.state_activated))

        val textTertiary = view.context.resolveThemeColor(android.R.attr.textColorTertiary)
        val imageTintList = ColorStateList(states, intArrayOf(colors.theme().theme, textTertiary))

        binding.qksms.actionView.imageTintList = imageTintList
        binding.callBlocker.actionView.imageTintList = imageTintList
        binding.callControl.actionView.imageTintList = imageTintList
        binding.shouldIAnswer.actionView.imageTintList = imageTintList
    }

    override fun onActivityResumed(activity: Activity) {
        activityResumedSubject.onNext(Unit)
    }

    override fun render(state: BlockingManagerState) {
        binding.qksms.actionView.setImageResource(getActionIcon(true))
        binding.qksms.actionView.isActivated = true
        binding.qksms.actionView.isInvisible = state.blockingManager != Preferences.BLOCKING_MANAGER_QKSMS

        binding.callBlocker.actionView.setImageResource(getActionIcon(state.callBlockerInstalled))
        binding.callBlocker.actionView.isActivated = state.callBlockerInstalled
        binding.callBlocker.actionView.isInvisible = state.blockingManager != Preferences.BLOCKING_MANAGER_CB
                && state.callBlockerInstalled

        binding.callControl.actionView.setImageResource(getActionIcon(state.callControlInstalled))
        binding.callControl.actionView.isActivated = state.callControlInstalled
        binding.callControl.actionView.isInvisible = state.blockingManager != Preferences.BLOCKING_MANAGER_CC
                && state.callControlInstalled

        binding.shouldIAnswer.actionView.setImageResource(getActionIcon(state.siaInstalled))
        binding.shouldIAnswer.actionView.isActivated = state.siaInstalled
        binding.shouldIAnswer.actionView.isInvisible = state.blockingManager != Preferences.BLOCKING_MANAGER_SIA
                && state.siaInstalled
    }

    private fun getActionIcon(installed: Boolean): Int = when {
        !installed -> R.drawable.ic_chevron_right_black_24dp
        else -> R.drawable.ic_check_white_24dp
    }

    override fun activityResumed(): Observable<*> = activityResumedSubject
    override fun qksmsClicked(): Observable<*> = binding.qksms.clicks()
    override fun callBlockerClicked(): Observable<*> = binding.callBlocker.clicks()
    override fun callControlClicked(): Observable<*> = binding.callControl.clicks()
    override fun siaClicked(): Observable<*> = binding.shouldIAnswer.clicks()

    override fun showCopyDialog(manager: String): Single<Boolean> = Single.create { emitter ->
        AlertDialog.Builder(activity)
                .setTitle(R.string.blocking_manager_copy_title)
                .setMessage(resources?.getString(R.string.blocking_manager_copy_summary, manager))
                .setPositiveButton(R.string.button_continue) { _, _ -> emitter.onSuccess(true) }
                .setNegativeButton(R.string.button_cancel) { _, _ -> emitter.onSuccess(false) }
                .setCancelable(false)
                .show()
    }

}
