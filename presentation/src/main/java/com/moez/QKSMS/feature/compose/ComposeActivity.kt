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

import android.Manifest
import android.animation.LayoutTransition
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.format.DateFormat
import android.view.ContextMenu
import android.view.DragEvent.ACTION_DRAG_ENDED
import android.view.DragEvent.ACTION_DRAG_EXITED
import android.view.DragEvent.ACTION_DROP
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.moez.QKSMS.common.QkMediaPlayer
import com.uber.autodispose.ObservableSubscribeProxy
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.common.util.DateFormatter
import dev.octoshrimpy.quik.common.util.extensions.autoScrollToStart
import dev.octoshrimpy.quik.common.util.extensions.hideKeyboard
import dev.octoshrimpy.quik.common.util.extensions.makeToast
import dev.octoshrimpy.quik.common.util.extensions.scrapViews
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.common.util.extensions.showKeyboard
import dev.octoshrimpy.quik.common.widget.MicInputCloudView
import dev.octoshrimpy.quik.common.widget.QkEditText
import dev.octoshrimpy.quik.extensions.mapNotNull
import dev.octoshrimpy.quik.feature.compose.editing.ChipsAdapter
import dev.octoshrimpy.quik.feature.contacts.ContactsActivity
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.model.Recipient
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.compose_activity.attach
import kotlinx.android.synthetic.main.compose_activity.attachAFileIcon
import kotlinx.android.synthetic.main.compose_activity.attachAFileLabel
import kotlinx.android.synthetic.main.compose_activity.attaching
import kotlinx.android.synthetic.main.compose_activity.shadeBackground
import kotlinx.android.synthetic.main.compose_activity.audioMsgAbort
import kotlinx.android.synthetic.main.compose_activity.audioMsgAttach
import kotlinx.android.synthetic.main.compose_activity.audioMsgBackground
import kotlinx.android.synthetic.main.compose_activity.audioMsgPlayerBackground
import kotlinx.android.synthetic.main.compose_activity.audioMsgPlayerPlayPause
import kotlinx.android.synthetic.main.compose_activity.audioMsgPlayerSeekBar
import kotlinx.android.synthetic.main.compose_activity.audioMsgRecord
import kotlinx.android.synthetic.main.compose_activity.camera
import kotlinx.android.synthetic.main.compose_activity.cameraLabel
import kotlinx.android.synthetic.main.compose_activity.chips
import kotlinx.android.synthetic.main.compose_activity.composeBar
import kotlinx.android.synthetic.main.compose_activity.contact
import kotlinx.android.synthetic.main.compose_activity.contactLabel
import kotlinx.android.synthetic.main.compose_activity.contentView
import kotlinx.android.synthetic.main.compose_activity.counter
import kotlinx.android.synthetic.main.compose_activity.gallery
import kotlinx.android.synthetic.main.compose_activity.galleryLabel
import kotlinx.android.synthetic.main.compose_activity.loading
import kotlinx.android.synthetic.main.compose_activity.message
import kotlinx.android.synthetic.main.compose_activity.messageList
import kotlinx.android.synthetic.main.compose_activity.messagesEmpty
import kotlinx.android.synthetic.main.compose_activity.noValidRecipients
import kotlinx.android.synthetic.main.compose_activity.messageAttachments
import kotlinx.android.synthetic.main.compose_activity.attachAnAudioMessageIcon
import kotlinx.android.synthetic.main.compose_activity.attachAnAudioMessageLabel
import kotlinx.android.synthetic.main.compose_activity.audioMsgBluetooth
import kotlinx.android.synthetic.main.compose_activity.audioMsgDuration
import kotlinx.android.synthetic.main.compose_activity.recordAudioMsg
import kotlinx.android.synthetic.main.compose_activity.schedule
import kotlinx.android.synthetic.main.compose_activity.scheduleLabel
import kotlinx.android.synthetic.main.compose_activity.scheduledCancel
import kotlinx.android.synthetic.main.compose_activity.scheduledGroup
import kotlinx.android.synthetic.main.compose_activity.scheduledTime
import kotlinx.android.synthetic.main.compose_activity.send
import kotlinx.android.synthetic.main.compose_activity.sendAsGroup
import kotlinx.android.synthetic.main.compose_activity.sendAsGroupBackground
import kotlinx.android.synthetic.main.compose_activity.sendAsGroupSwitch
import kotlinx.android.synthetic.main.compose_activity.sim
import kotlinx.android.synthetic.main.compose_activity.simIndex
import kotlinx.android.synthetic.main.compose_activity.speechToTextFrame
import kotlinx.android.synthetic.main.compose_activity.speechToTextIcon
import kotlinx.android.synthetic.main.compose_activity.speechToTextIconBorder
import kotlinx.android.synthetic.main.compose_activity.toolbarSubtitle
import kotlinx.android.synthetic.main.compose_activity.toolbarTitle
import kotlinx.android.synthetic.main.main_activity.toolbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class ComposeActivity : QkThemedActivity(), ComposeView {

    @Inject lateinit var composeAttachmentAdapter: ComposeAttachmentAdapter
    @Inject lateinit var chipsAdapter: ChipsAdapter
    @Inject lateinit var dateFormatter: DateFormatter
    @Inject lateinit var messageAdapter: MessagesAdapter
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val activityVisibleIntent: Subject<Boolean> = PublishSubject.create()
    override val chipsSelectedIntent: Subject<HashMap<String, String?>> = PublishSubject.create()
    override val chipDeletedIntent: Subject<Recipient> by lazy { chipsAdapter.chipDeleted }
    override val menuReadyIntent: Observable<Unit> = menu.map { Unit }
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
    override val contextItemIntent: Subject<MenuItem> = PublishSubject.create()
    override val scheduleAction: Subject<Boolean> = PublishSubject.create()
    override val sendAsGroupIntent by lazy { sendAsGroupBackground.clicks() }
    override val messagePartClickIntent: Subject<Long> by lazy { messageAdapter.partClicks }
    override val messagePartContextMenuRegistrar: Subject<View> by lazy { messageAdapter.partContextMenuRegistrar }
    override val messagesSelectedIntent by lazy { messageAdapter.selectionChanges }
    override val cancelSendingIntent: Subject<Long> by lazy { messageAdapter.cancelSendingClicks }
    override val sendNowIntent: Subject<Long> by lazy { messageAdapter.sendNowClicks }
    override val resendIntent: Subject<Long> by lazy { messageAdapter.resendClicks }
    override val attachmentDeletedIntent: Subject<Attachment> by lazy { composeAttachmentAdapter.attachmentDeleted }
    override val textChangedIntent by lazy { message.textChanges() }
    override val attachIntent by lazy { Observable.merge(attach.clicks(), shadeBackground.clicks()) }
    override val cameraIntent by lazy { Observable.merge(camera.clicks(), cameraLabel.clicks()) }
    override val attachImageFileIntent by lazy { Observable.merge(gallery.clicks(), galleryLabel.clicks()) }
    override val attachAnyFileIntent by lazy { Observable.merge(attachAFileIcon.clicks(), attachAFileLabel.clicks()) }
    override val scheduleIntent by lazy { Observable.merge(schedule.clicks(), scheduleLabel.clicks()) }
    override val attachContactIntent by lazy { Observable.merge(contact.clicks(), contactLabel.clicks()) }
    override val attachAnyFileSelectedIntent: Subject<Uri> = PublishSubject.create()
    override val contactSelectedIntent: Subject<Uri> = PublishSubject.create()
    override val inputContentIntent by lazy { message.inputContentSelected }
    override val scheduleSelectedIntent: Subject<Long> = PublishSubject.create()
    override val changeSimIntent by lazy { sim.clicks() }
    override val scheduleCancelIntent by lazy { scheduledCancel.clicks() }
    override val sendIntent by lazy { send.clicks() }
    override val viewQksmsPlusIntent: Subject<Unit> = PublishSubject.create()
    override val backPressedIntent: Subject<Unit> = PublishSubject.create()
    override val confirmDeleteIntent: Subject<List<Long>> = PublishSubject.create()
    override val clearCurrentMessageIntent: Subject<Boolean> = PublishSubject.create()
    override val messageLinkAskIntent: Subject<Uri> by lazy { messageAdapter.messageLinkClicks }
    override val speechRecogniserIntent by lazy { speechToTextIcon.clicks() }
    override val shadeIntent by lazy { shadeBackground.clicks() }
    override val recordAudioStartStopRecording: Subject<Boolean> = PublishSubject.create()
    override val recordAnAudioMessage by lazy {
        Observable.merge(recordAudioMsg.clicks(),
            attachAnAudioMessageIcon.clicks(),
            attachAnAudioMessageLabel.clicks())
    }
    override val recordAudioAbort by lazy { audioMsgAbort.clicks() }
    override val recordAudioAttach by lazy { audioMsgAttach.clicks() }
    override val recordAudioPlayerPlayPause: Subject<QkMediaPlayer.PlayingState> = PublishSubject.create()
    override val recordAudioPlayerConfigUI: Subject<QkMediaPlayer.PlayingState> = PublishSubject.create()
    override val recordAudioPlayerVisible: Subject<Boolean> = PublishSubject.create()
    override val recordAudioMsgRecordVisible: Subject<Boolean> = PublishSubject.create()
    override val recordAudioChronometer: Subject<Boolean> = PublishSubject.create()
    override val recordAudioRecord: Subject<MicInputCloudView.ViewState> = PublishSubject.create()

    private var seekBarUpdater: Disposable? = null

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[ComposeViewModel::class.java] }

    private var cameraDestination: Uri? = null

    private fun getSeekBarUpdater(): ObservableSubscribeProxy<Long> {
        return Observable.interval(500, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.single())
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(scope())
    }

    private fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(this)
    }

    private val speechResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK)
            return@registerForActivityResult

        // check returned results are good
        val match = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if ((match === null) || (match.size < 1) || (match[0].isNullOrEmpty()))
            return@registerForActivityResult

        // get the edit text view
        val message = findViewById<QkEditText>(R.id.message)
        if (message === null)
            return@registerForActivityResult

        // populate message box with data returned by STT, set cursor to end, and focus
        message.append(match[0])
        message.setSelection(message.text.length)
        message.requestFocus()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.compose_activity)
        showBackButton(true)
        viewModel.bindView(this)

        contentView.layoutTransition = LayoutTransition().apply {
            disableTransitionType(LayoutTransition.CHANGING)
        }
            chipsAdapter.view = chips

            chips.itemAnimator = null
            chips.layoutManager = FlexboxLayoutManager(this)

            messageAdapter.autoScrollToStart(messageList)
            messageAdapter.emptyView = messagesEmpty

            messageList.setHasFixedSize(true)
            messageList.adapter = messageAdapter

            messageAttachments.adapter = composeAttachmentAdapter

            message.supportsInputContent = true

            theme
                .doOnNext { loading.setTint(it.theme) }
                .doOnNext { attach.setBackgroundTint(it.theme) }
                .doOnNext { attach.setTint(it.textPrimary) }
                .doOnNext { speechToTextIconBorder.setBackgroundTint(it.theme) }
                .doOnNext { speechToTextIcon.setBackgroundTint(it.textPrimary) }
                .doOnNext { speechToTextIcon.setTint(it.theme) }
                .doOnNext { audioMsgRecord.setColor(it.theme) }
                .doOnNext { audioMsgPlayerPlayPause.setTint(it.theme) }
                .doOnNext {
                    audioMsgPlayerSeekBar.apply {
                        thumbTintList = ColorStateList.valueOf(it.theme)
                        progressBackgroundTintList = ColorStateList.valueOf(it.theme)
                        progressTintList = ColorStateList.valueOf(it.theme)
                    }
                }
                .doOnNext { messageAdapter.theme = it }
                .autoDisposable(scope())
                .subscribe()

            // context menu registration for message parts
            messagePartContextMenuRegistrar
                .mapNotNull { it }
                .autoDisposable(scope())
                .subscribe { registerForContextMenu(it) }

            // drag drop handlers for speech-to-text icon
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                speechToTextIcon.setOnLongClickListener {
                    it.startDragAndDrop(null, View.DragShadowBuilder(speechToTextFrame), null, 0)
                    speechToTextFrame.isVisible = false

                    contentView.setOnDragListener { _, event ->
                        when (event.action) {
                            ACTION_DROP -> {
                                speechToTextFrame.x = (event.x - (speechToTextFrame.width / 2))
                                speechToTextFrame.y = (event.y - (speechToTextFrame.height / 2))

                                // get offset from root view as a percentage of root view for saving
                                prefs.showSttOffsetX.set((speechToTextFrame.x - contentView.x) / contentView.width)
                                prefs.showSttOffsetY.set((speechToTextFrame.y - contentView.y) / contentView.height)
                            }

                            ACTION_DRAG_ENDED, ACTION_DRAG_EXITED -> {
                                speechToTextFrame.isVisible = true
                            }
                        }
                        true
                    }
                    true
                }
            }

            // start/stop audio message recording
            audioMsgRecord.setOnClickListener {
                recordAudioRecord.onNext(audioMsgRecord.getState())
            }

            recordAudioChronometer
                .subscribeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged()
                .autoDisposable(scope())
                .subscribe {
                    if (it) {
                        audioMsgDuration.base = SystemClock.elapsedRealtime()
                        audioMsgDuration.start()
                    } else {
                        audioMsgDuration.stop()
                    }
                }

            // audio record playback play/pause button
            audioMsgPlayerPlayPause.setOnClickListener {
                recordAudioPlayerPlayPause.onNext(
                    audioMsgPlayerPlayPause.tag as QkMediaPlayer.PlayingState
                )
            }

            recordAudioMsgRecordVisible
                .subscribeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged()
                .autoDisposable(scope())
                .subscribe {
                    audioMsgRecord.isVisible = it
                    audioMsgDuration.isVisible =
                        it   // chronometer follows record button visibility
                    audioMsgBluetooth.isVisible = !it
                }

            recordAudioPlayerVisible
                .subscribeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged()
                .autoDisposable(scope())
                .subscribe {
                    audioMsgPlayerBackground.isVisible = it
                    recordAudioPlayerConfigUI.onNext(QkMediaPlayer.PlayingState.Stopped)
                }

            recordAudioPlayerConfigUI
                .subscribeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged()
                .autoDisposable(scope())
                .subscribe {
                    when (it) {
                        QkMediaPlayer.PlayingState.Playing -> {
                            audioMsgPlayerPlayPause.tag = QkMediaPlayer.PlayingState.Playing
                            QkMediaPlayer.start()
                            audioMsgPlayerPlayPause.setImageResource(R.drawable.exo_icon_pause)
                            seekBarUpdater = getSeekBarUpdater().subscribe {
                                audioMsgPlayerSeekBar.progress = QkMediaPlayer.currentPosition
                                audioMsgPlayerSeekBar.max = QkMediaPlayer.duration
                            }
                            audioMsgPlayerSeekBar.isEnabled = true
                        }

                        QkMediaPlayer.PlayingState.Paused -> {
                            audioMsgPlayerPlayPause.tag = QkMediaPlayer.PlayingState.Paused
                            QkMediaPlayer.pause()
                            audioMsgPlayerPlayPause.setImageResource(R.drawable.exo_icon_play)
                            seekBarUpdater?.dispose()
                        }

                        else -> {
                            audioMsgPlayerPlayPause.tag = QkMediaPlayer.PlayingState.Stopped
                            QkMediaPlayer.reset()
                            audioMsgPlayerPlayPause.setImageResource(R.drawable.exo_icon_play)
                            seekBarUpdater?.dispose()
                            audioMsgPlayerSeekBar.progress = 0
                            audioMsgPlayerSeekBar.isEnabled = false
                        }
                    }
                }
            // audio msg player seek bar handler
            audioMsgPlayerSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                        // if seek was initiated by the user and this part is currently playing
                        if (fromUser)
                            QkMediaPlayer.seekTo(progress)
                    }
                    override fun onStartTrackingTouch(p0: SeekBar?) {}
                    override fun onStopTrackingTouch(p0: SeekBar?) {}
                }
            )

            window.callback = ComposeWindowCallback(window.callback, this)
    }

    override fun onStart() {
        super.onStart()
        activityVisibleIntent.onNext(true)

        // if first time stt icon is shown (since setting reset), pop up an instruction toast
        if (prefs.showStt.get() &&
            (prefs.showSttOffsetX.get() == Float.MIN_VALUE) &&
            (prefs.showSttOffsetX.get() == Float.MIN_VALUE)) {
            makeToast(R.string.compose_toast_drag_stt, Toast.LENGTH_LONG)
            // reset to new flag value that indicates 'not first time through, but not customised'
            prefs.showSttOffsetX.set(Float.MAX_VALUE)
            prefs.showSttOffsetY.set(Float.MAX_VALUE)
        }
    }

    override fun onPause() {
        super.onPause()
        activityVisibleIntent.onNext(false)
    }

    override fun onDestroy() {
        super.onDestroy()

        // stop any playing audio
        QkMediaPlayer.reset()

        seekBarUpdater?.dispose()
    }


    override fun render(state: ComposeState) {
        if (state.hasError) {
            finish()
            return
        }

        threadId.onNext(state.threadId)

        title = when {
            state.selectedMessages > 0 -> getString(R.string.compose_title_selected, state.selectedMessages)
            state.query.isNotEmpty() -> state.query
            else -> state.conversationtitle
        }

        toolbarSubtitle.setVisible(state.query.isNotEmpty())
        toolbarSubtitle.text = getString(R.string.compose_subtitle_results, state.searchSelectionPosition,
            state.searchResults)

        toolbarTitle.setVisible(!state.editingMode)
        chips.setVisible(state.editingMode)
        composeBar.setVisible(!state.loading)

        // Don't set the adapters unless needed
        if (state.editingMode && chips.adapter == null) chips.adapter = chipsAdapter

        toolbar.menu.findItem(R.id.select_all)?.isVisible = !state.editingMode && (messageAdapter.itemCount > 1) && state.selectedMessages != 0
        toolbar.menu.findItem(R.id.add)?.isVisible = state.editingMode
        toolbar.menu.findItem(R.id.call)?.isVisible = !state.editingMode && state.selectedMessages == 0
                && state.query.isEmpty()
        toolbar.menu.findItem(R.id.info)?.isVisible = !state.editingMode && state.selectedMessages == 0
                && state.query.isEmpty()
        toolbar.menu.findItem(R.id.copy)?.isVisible = !state.editingMode && state.selectedMessages > 0
        toolbar.menu.findItem(R.id.details)?.isVisible = !state.editingMode && state.selectedMessages == 1
        toolbar.menu.findItem(R.id.delete)?.isVisible = !state.editingMode && ((state.selectedMessages > 0) || state.canSend)
        toolbar.menu.findItem(R.id.forward)?.isVisible = !state.editingMode && state.selectedMessages == 1
        toolbar.menu.findItem(R.id.show_status)?.isVisible = !state.editingMode && state.selectedMessages > 0
        toolbar.menu.findItem(R.id.previous)?.isVisible = state.selectedMessages == 0 && state.query.isNotEmpty()
        toolbar.menu.findItem(R.id.next)?.isVisible = state.selectedMessages == 0 && state.query.isNotEmpty()
        toolbar.menu.findItem(R.id.clear)?.isVisible = state.selectedMessages == 0 && state.query.isNotEmpty()

        chipsAdapter.data = state.selectedChips

        loading.setVisible(state.loading)

        sendAsGroup.setVisible(state.editingMode && state.selectedChips.size >= 2)
        sendAsGroupSwitch.isChecked = state.sendAsGroup

        messageList.setVisible(!state.editingMode || state.sendAsGroup || state.selectedChips.size == 1)
        messageAdapter.data = state.messages
        messageAdapter.highlight = state.searchSelectionId

        scheduledGroup.isVisible = state.scheduled != 0L
        scheduledTime.text = dateFormatter.getScheduledTimestamp(state.scheduled)

        messageAttachments.setVisible(state.attachments.isNotEmpty())
        composeAttachmentAdapter.data = state.attachments

        attach.animate().rotation(if (state.attaching) 135f else 0f).start()
        attaching.isVisible = state.attaching

        shadeBackground.visibility =
            if (state.attaching || state.audioMsgRecording) View.VISIBLE
            else View.GONE

        // show or hide audio message recording panel and shade background
        audioMsgBackground.isVisible = state.audioMsgRecording

        counter.text = state.remaining
        counter.setVisible(counter.text.isNotBlank())

        sim.setVisible(state.subscription != null)
        sim.contentDescription = getString(R.string.compose_sim_cd, state.subscription?.displayName)
        simIndex.text = state.subscription?.simSlotIndex?.plus(1)?.toString()

        // show either send or audio msg record button
        send.visibility = if (state.canSend && !state.loading) View.VISIBLE else View.INVISIBLE
        recordAudioMsg.visibility = if (state.canSend && !state.loading) View.INVISIBLE else View.VISIBLE

        // if not in editing mode, and there are no non-me participants that can be sent to,
        // hide controls that allow constructing a reply and inform user no valid recipients
        if (!state.editingMode && (state.validRecipientNumbers == 0)) {
            composeBar.visibility = View.GONE
            sim.visibility = View.GONE
            recordAudioMsg.visibility = View.GONE
            noValidRecipients.visibility = View.VISIBLE

            // change constraint of messageList to constrain bottom to top of noValidRecipients
            val constraintLayout = findViewById<ConstraintLayout>(R.id.contentView)
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.connect(
                R.id.messageList,
                ConstraintSet.BOTTOM,
                R.id.noValidRecipients,
                ConstraintSet.TOP,
                0
            )
            constraintSet.applyTo(constraintLayout)
        }

        // if scheduling mode is set, show schedule dialog
        if (state.scheduling)
            scheduleAction.onNext(true)

        if (prefs.showStt.get()) {
            speechToTextFrame.isVisible = true

            var xPercent = prefs.showSttOffsetX.get()
            var yPercent = prefs.showSttOffsetY.get()

            // if the stt icon has a custom position, move it
            if ((xPercent != Float.MAX_VALUE) && (yPercent != Float.MAX_VALUE)) {
                speechToTextFrame.x = (contentView.x + (xPercent * contentView.width))
                speechToTextFrame.y = (contentView.y + (yPercent * contentView.height))
            }
        }
    }

    override fun clearSelection() = messageAdapter.clearSelection()

    override fun toggleSelectAll() {
        messageAdapter.toggleSelectAll()
    }

    override fun expandMessages(messageIds: List<Long>, expand: Boolean) {
        messageAdapter.expandMessages(messageIds, expand)
    }

    override fun showDetails(details: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.compose_details_title)
            .setMessage(details)
            .setCancelable(true)
            .show()
    }

    override fun showMessageLinkAskDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.messageLinkHandling_dialog_title)
            .setMessage(getString(R.string.messageLinkHandling_dialog_body, uri.toString()))
            .setPositiveButton(
                R.string.messageLinkHandling_dialog_positive
            ) { _, _ ->
                ContextCompat.startActivity(
                    this,
                    Intent(Intent.ACTION_VIEW).setData(uri),
                    null
                )
            }
            .setNegativeButton(R.string.messageLinkHandling_dialog_negative) { _, _ -> { } }
            .show()
    }

    override fun requestDefaultSms() {
        navigator.showDefaultSmsDialog(this)
    }

    override fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
    }

    override fun requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
    }

    override fun requestSmsPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS), 0)
    }

    override fun requestDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, DatePickerDialog.OnDateSetListener { _, year, month, day ->
            TimePickerDialog(this, TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                scheduleSelectedIntent.onNext(calendar.timeInMillis)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), DateFormat.is24HourFormat(this))
                .show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()

        // On some devices, the keyboard can cover the date picker
        message.hideKeyboard()
    }

    override fun requestContact() {
        val intent = Intent(Intent.ACTION_PICK)
            .setType(ContactsContract.Contacts.CONTENT_TYPE)

        startActivityForResult(Intent.createChooser(intent, null), ComposeView.AttachContactRequestCode)
    }

    override fun showContacts(sharing: Boolean, chips: List<Recipient>) {
        message.hideKeyboard()
        val serialized = HashMap(chips.associate { chip -> chip.address to chip.contact?.lookupKey })
        val intent = Intent(this, ContactsActivity::class.java)
            .putExtra(ContactsActivity.SharingKey, sharing)
            .putExtra(ContactsActivity.ChipsKey, serialized)
        startActivityForResult(intent, ComposeView.SelectContactRequestCode)
    }

    override fun startSpeechRecognition() {
        if (!isSpeechRecognitionAvailable()) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            try {
                startActivityForResult(intent, ComposeView.SpeechRecognitionRequestCode)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.stt_toast_no_provider), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun themeChanged() {
        messageList.scrapViews()
    }

    override fun showKeyboard() {
        message.postDelayed({
            message.showKeyboard()
        }, 200)
    }

    override fun requestCamera() {
        cameraDestination = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            .let { timestamp -> ContentValues().apply { put(MediaStore.Images.Media.TITLE, timestamp) } }
            .let { cv -> contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, cameraDestination)
        startActivityForResult(Intent.createChooser(intent, null), ComposeView.TakePhotoRequestCode)
    }

    override fun requestGallery(mimeType: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setType(mimeType)
        startActivityForResult(Intent.createChooser(intent, null), requestCode)
    }

    override fun setDraft(draft: String) {
        message.setText(draft)
        message.setSelection(draft.length)
    }

    override fun scrollToMessage(id: Long) {
        messageAdapter.data?.second
            ?.indexOfLast { message -> message.id == id }
            ?.takeIf { position -> position != -1 }
            ?.let(messageList::scrollToPosition)
    }

    override fun showQksmsPlusSnackbar(message: Int) {
        Snackbar.make(contentView, message, Snackbar.LENGTH_LONG).run {
            setAction(R.string.button_more) { viewQksmsPlusIntent.onNext(Unit) }
            setActionTextColor(colors.theme().theme)
            show()
        }
    }

    override fun showDeleteDialog(messages: List<Long>) {
        val count = messages.size
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(resources.getQuantityString(R.plurals.dialog_delete_chat, count, count))
            .setPositiveButton(R.string.button_delete) { _, _ -> confirmDeleteIntent.onNext(messages) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun showClearCurrentMessageDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_clear_compose_title)
            .setMessage(R.string.dialog_clear_compose)
            .setPositiveButton(R.string.button_clear) { _, _ ->
                clearCurrentMessageIntent.onNext(false)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.compose, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        optionsItemIntent.onNext(item.itemId)
        return true
    }

    override fun getColoredMenuItems(): List<Int> {
        return super.getColoredMenuItems() + R.id.call
    }

    override fun onCreateContextMenu(
        menu: ContextMenu?,
        v: View?,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.mms_part_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        super.onContextItemSelected(item)
        contextItemIntent.onNext(item)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK)
            return

        when (requestCode) {
            ComposeView.SelectContactRequestCode -> {
                chipsSelectedIntent.onNext(data?.getSerializableExtra(ContactsActivity.ChipsKey)
                    ?.let { serializable -> serializable as? HashMap<String, String?> }
                    ?: hashMapOf())
            }

            ComposeView.TakePhotoRequestCode -> {
                cameraDestination?.let(attachAnyFileSelectedIntent::onNext)
            }

            ComposeView.AttachAFileRequestCode -> {
                data?.clipData?.itemCount
                    ?.let { count -> 0 until count }
                    ?.mapNotNull { i -> data.clipData?.getItemAt(i)?.uri }
                    ?.forEach(attachAnyFileSelectedIntent::onNext)
                    ?: data?.data?.let(attachAnyFileSelectedIntent::onNext)
            }

            ComposeView.AttachContactRequestCode -> {
                data?.data?.let(contactSelectedIntent::onNext)
            }

            ComposeView.SpeechRecognitionRequestCode -> {
                // check returned results are good
                val match = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if ((match !== null) && (match.size > 0) && (!match[0].isNullOrEmpty())) {
                    // get the edit text view
                    val messageEditBox = findViewById<QkEditText>(R.id.message)
                    if (messageEditBox !== null) {
                        // populate message box with data returned by STT, set cursor to end, and focus
                        messageEditBox.append(match[0])
                        messageEditBox.setSelection(messageEditBox.text.length)
                        messageEditBox.requestFocus()
                    }
                }
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ComposeView.CameraDestinationKey, cameraDestination)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        cameraDestination = savedInstanceState.getParcelable(ComposeView.CameraDestinationKey)
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onBackPressed() = backPressedIntent.onNext(Unit)

}
