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
package dev.octoshrimpy.quik.feature.backup

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isVisible
import com.jakewharton.rxbinding2.view.clicks
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.util.QkActivityResultContracts
import dev.octoshrimpy.quik.common.util.extensions.getLabel
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setNegativeButton
import dev.octoshrimpy.quik.common.util.extensions.setPositiveButton
import dev.octoshrimpy.quik.common.util.extensions.setShowing
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.widget.PreferenceView
import dev.octoshrimpy.quik.injection.appComponent
import dev.octoshrimpy.quik.repository.BackupRepository
import dev.octoshrimpy.quik.databinding.BackupControllerBinding
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class BackupController : QkController<BackupControllerBinding, BackupView, BackupState, BackupPresenter>(), BackupView {

    @Inject override lateinit var presenter: BackupPresenter

    private val selectFolderCancelSubject: Subject<Unit> = PublishSubject.create()
    private val selectFolderConfirmSubject: Subject<Unit> = PublishSubject.create()

    private val restoreErrorConfirmSubject: Subject<Unit> = PublishSubject.create()

    private val confirmRestoreCancelSubject: Subject<Unit> = PublishSubject.create()
    private val confirmRestoreConfirmSubject: Subject<Unit> = PublishSubject.create()

    private val stopRestoreConfirmSubject: Subject<Unit> = PublishSubject.create()
    private val stopRestoreCancelSubject: Subject<Unit> = PublishSubject.create()

    private val documentTreeSelectedSubject: Subject<Uri> = PublishSubject.create()
    private val documentSelectedSubject: Subject<Uri> = PublishSubject.create()

    private val stopRestoreDialog by lazy {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_restore_stop_title)
                .setMessage(R.string.backup_restore_stop_message)
                .setPositiveButton(R.string.button_stop, stopRestoreConfirmSubject)
                .setNegativeButton(R.string.button_cancel, stopRestoreCancelSubject)
                .setCancelable(false)
                .create()
    }

    private val selectLocationRationaleDialog by lazy {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_select_location_rationale_title)
                .setMessage(R.string.backup_select_location_rationale_message)
                .setPositiveButton(R.string.button_continue, selectFolderConfirmSubject)
                .setNegativeButton(R.string.button_cancel, selectFolderCancelSubject)
                .setCancelable(false)
                .create()
    }

    private val selectedBackupErrorDialog by lazy {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_selected_backup_error_title)
                .setMessage(R.string.backup_selected_backup_error_message)
                .setPositiveButton(R.string.button_continue, restoreErrorConfirmSubject)
                .setCancelable(false)
                .create()
    }

    private val selectedBackupDetailsDialog by lazy {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.backup_selected_backup_details_title)
                .setPositiveButton(R.string.backup_restore_title, confirmRestoreConfirmSubject)
                .setNegativeButton(R.string.button_cancel, confirmRestoreCancelSubject)
                .setCancelable(false)
                .create()
    }

    private lateinit var openDirectory: ActivityResultLauncher<Uri?>
    private lateinit var openDocument: ActivityResultLauncher<QkActivityResultContracts.OpenDocumentParams>

    init {
        appComponent.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): BackupControllerBinding =
        BackupControllerBinding.inflate(inflater, container, false)

    override fun onContextAvailable(context: Context) {
        // Init activity result contracts
        openDirectory = themedActivity!!
            .registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                uri?.let(documentTreeSelectedSubject::onNext)
            }

        openDocument = themedActivity!!
            .registerForActivityResult(QkActivityResultContracts.OpenDocument()) { uri ->
                uri?.let(documentSelectedSubject::onNext)
            }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.backup_title)
        showBackButton(true)
    }

    override fun onViewCreated() {
        super.onViewCreated()

        themedActivity?.colors?.theme()?.let { theme ->
            binding.progressBar.indeterminateTintList = ColorStateList.valueOf(theme.theme)
            binding.progressBar.progressTintList = ColorStateList.valueOf(theme.theme)
            binding.fab.setBackgroundTint(theme.theme)
            binding.fabIcon.setTint(theme.textPrimary)
            binding.fabLabel.setTextColor(theme.textPrimary)
        }

        // Make the list titles bold
        binding.linearLayout.children
            .mapNotNull { it as? PreferenceView }
            .map { it.titleView }
            .forEach { it.setTypeface(it.typeface, Typeface.BOLD) }
    }

    override fun render(state: BackupState) {
        when {
            state.backupProgress.running -> {
                binding.progressIcon.setImageResource(R.drawable.ic_file_upload_black_24dp)
                binding.progressTitle.setText(R.string.backup_backing_up)
                binding.progressSummary.text = state.backupProgress.getLabel(activity!!)
                binding.progressSummary.isVisible = binding.progressSummary.text.isNotEmpty()
                binding.progressCancel.isVisible = false
                val running = (state.backupProgress as? BackupRepository.Progress.Running)
                binding.progressBar.isVisible =
                    state.backupProgress.indeterminate || (running?.max ?: 0) > 0
                binding.progressBar.isIndeterminate = state.backupProgress.indeterminate
                binding.progressBar.max = running?.max ?: 0
                binding.progressBar.progress = running?.count ?: 0
                binding.progress.isVisible = true
                binding.fab.isVisible = false
            }

            state.restoreProgress.running -> {
                binding.progressIcon.setImageResource(R.drawable.ic_file_download_black_24dp)
                binding.progressTitle.setText(R.string.backup_restoring)
                binding.progressSummary.text = state.restoreProgress.getLabel(activity!!)
                binding.progressSummary.isVisible = binding.progressSummary.text.isNotEmpty()
                binding.progressCancel.isVisible = true
                val running = (state.restoreProgress as? BackupRepository.Progress.Running)
                binding.progressBar.isVisible =
                    state.restoreProgress.indeterminate || (running?.max ?: 0) > 0
                binding.progressBar.isIndeterminate = state.restoreProgress.indeterminate
                binding.progressBar.max = running?.max ?: 0
                binding.progressBar.progress = running?.count ?: 0
                binding.progress.isVisible = true
                binding.fab.isVisible = false
            }

            else -> {
                binding.progress.isVisible = false
                binding.fab.isVisible = true
            }
        }

        selectLocationRationaleDialog.setShowing(state.showLocationRationale)

        selectedBackupErrorDialog.setShowing(state.showSelectedBackupError)

        selectedBackupDetailsDialog.setMessage(state.selectedBackupDetails)
        selectedBackupDetailsDialog.setShowing(state.selectedBackupDetails != null)

        stopRestoreDialog.setShowing(state.showStopRestoreDialog)

        binding.fabIcon.setImageResource(when (state.upgraded) {
            true -> R.drawable.ic_file_upload_black_24dp
            false -> R.drawable.ic_star_black_24dp
        })

        binding.fabLabel.setText(when (state.upgraded) {
            true -> R.string.backup_now
            false -> R.string.title_qksms_plus
        })
    }

    override fun setBackupLocationClicks(): Observable<*> = binding.location.clicks()

    override fun restoreClicks(): Observable<*> = binding.restore.clicks()

    override fun locationRationaleConfirmClicks(): Observable<*> = selectFolderConfirmSubject

    override fun locationRationaleCancelClicks(): Observable<*> = selectFolderCancelSubject

    override fun selectedBackupErrorClicks(): Observable<*> = restoreErrorConfirmSubject

    override fun confirmRestoreBackupConfirmClicks(): Observable<*> = confirmRestoreConfirmSubject

    override fun confirmRestoreBackupCancelClicks(): Observable<*> = confirmRestoreCancelSubject

    override fun stopRestoreClicks(): Observable<*> = binding.progressCancel.clicks()

    override fun stopRestoreConfirmed(): Observable<*> = stopRestoreConfirmSubject

    override fun stopRestoreCancel(): Observable<*> = stopRestoreCancelSubject

    override fun backupClicks(): Observable<*> = binding.fab.clicks()

    override fun documentTreeSelected(): Observable<Uri> = documentTreeSelectedSubject

    override fun documentSelected(): Observable<Uri> = documentSelectedSubject

    override fun selectFolder(initialUri: Uri) {
        openDirectory.launch(initialUri)
    }

    override fun selectFile(initialUri: Uri) {
        openDocument.launch(QkActivityResultContracts.OpenDocumentParams(
                mimeTypes = listOf("application/json", "application/octet-stream"),
                initialUri = initialUri))
    }

}
