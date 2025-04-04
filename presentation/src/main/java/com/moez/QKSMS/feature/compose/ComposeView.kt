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

import android.net.Uri
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.moez.QKSMS.common.QkMediaPlayer
import dev.octoshrimpy.quik.common.base.QkView
import dev.octoshrimpy.quik.common.widget.MicInputCloudView
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.model.Recipient
import io.reactivex.Observable
import io.reactivex.subjects.Subject

interface ComposeView : QkView<ComposeState> {

    companion object {
        const val SelectContactRequestCode = 0
        const val TakePhotoRequestCode = 1
        const val AttachContactRequestCode = 3
        const val AttachAFileRequestCode = 4
        const val SpeechRecognitionRequestCode = 5

        const val CameraDestinationKey = "camera_destination"
    }

    val activityVisibleIntent: Observable<Boolean>
    val chipsSelectedIntent: Subject<HashMap<String, String?>>
    val chipDeletedIntent: Subject<Recipient>
    val menuReadyIntent: Observable<Unit>
    val optionsItemIntent: Observable<Int>
    val contextItemIntent: Observable<MenuItem>
    val sendAsGroupIntent: Observable<*>
    val messagePartClickIntent: Subject<Long>
    val messagePartContextMenuRegistrar: Subject<View>
    val messagesSelectedIntent: Observable<List<Long>>
    val cancelSendingIntent: Subject<Long>
    val sendNowIntent: Subject<Long>
    val resendIntent: Subject<Long>
    val attachmentDeletedIntent: Subject<Attachment>
    val textChangedIntent: Observable<CharSequence>
    val attachIntent: Observable<Unit>
    val cameraIntent: Observable<*>
    val attachAnyFileIntent: Observable<*>
    val attachImageFileIntent: Observable<*>
    val scheduleIntent: Observable<*>
    val scheduleAction: Observable<*>
    val attachContactIntent: Observable<*>
    val attachAnyFileSelectedIntent: Observable<Uri>
    val contactSelectedIntent: Observable<Uri>
    val inputContentIntent: Observable<InputContentInfoCompat>
    val scheduleSelectedIntent: Observable<Long>
    val scheduleCancelIntent: Observable<*>
    val changeSimIntent: Observable<*>
    val sendIntent: Observable<Unit>
    val viewQksmsPlusIntent: Subject<Unit>
    val backPressedIntent: Observable<Unit>
    val confirmDeleteIntent: Observable<List<Long>>
    val clearCurrentMessageIntent: Subject<Boolean>
    val messageLinkAskIntent: Observable<Uri>
    val speechRecogniserIntent: Observable<*>
    val shadeIntent: Observable<Unit>
    val recordAudioStartStopRecording: Subject<Boolean>
    val recordAnAudioMessage: Observable<Unit>
    val recordAudioAbort: Observable<Unit>
    val recordAudioAttach: Observable<Unit>
    val recordAudioPlayerPlayPause: Observable<QkMediaPlayer.PlayingState>
    val recordAudioPlayerConfigUI: Subject<QkMediaPlayer.PlayingState>
    val recordAudioPlayerVisible: Subject<Boolean>
    val recordAudioMsgRecordVisible: Subject<Boolean>
    val recordAudioRecord: Subject<MicInputCloudView.ViewState>
    val recordAudioChronometer: Subject<Boolean>

    fun clearSelection()
    fun toggleSelectAll()
    fun expandMessages(messageIds: List<Long>, expand: Boolean)
    fun showDetails(details: String)
    fun showMessageLinkAskDialog(uri: Uri)
    fun requestDefaultSms()
    fun requestStoragePermission()
    fun requestRecordAudioPermission()
    fun requestSmsPermission()
    fun showContacts(sharing: Boolean, chips: List<Recipient>)
    fun themeChanged()
    fun showKeyboard()
    fun requestCamera()
    fun requestGallery(mimeType: String, requestCode: Int)
    fun requestDatePicker()
    fun requestContact()
    fun setDraft(draft: String)
    fun scrollToMessage(id: Long)
    fun showQksmsPlusSnackbar(@StringRes message: Int)
    fun showDeleteDialog( messages: List<Long>)
    fun showClearCurrentMessageDialog()
    fun startSpeechRecognition()
    fun focusMessage()
}