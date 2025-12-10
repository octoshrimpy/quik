package com.moez.QKSMS.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.format.DateFormat
import dev.octoshrimpy.quik.model.Conversation
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class SpeakManager @Inject constructor (private val context: Context) {
    companion object {
        // system TextToSpeech engine
        private var staticTextToSpeech = AtomicReference<TextToSpeech>(null)

        val lock = Any()

        // currently speaking sessionId
        private var currentSessionId: String? = null

        // audio manager items for audio focus
        private var audioManager: AudioManager? = null
        private var audioFocusRequest: AudioFocusRequest? = null
    }

    private var sessionId: String? = null
    private var sessionStopped = false


    private fun getSystemTtsEngine(): TextToSpeech? {
        // if system TextToSpeech already assigned
        var tts = staticTextToSpeech.get()
        if (tts !== null)
            return tts

        val audioAttributes = AudioAttributes.Builder(). run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                setUsage(AudioAttributes.USAGE_ASSISTANT)
            setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            build()
        }

        var localAudioManager: AudioManager? = null
        var localAudioFocusRequest: AudioFocusRequest? = null

        synchronized(lock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // init audio manager and audio focus request first time through only
                localAudioManager =
                    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (localAudioManager === null)
                    return null

                localAudioFocusRequest =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .run {
                            setAudioAttributes(audioAttributes)
                            setAcceptsDelayedFocusGain(false)
                            build()
                        }
                if (localAudioFocusRequest === null)
                    return null
            }

            // blocking to allow waiting for tts engine response in a synchronous manner
            runBlocking {
                // get system tts engine
                val ttsResult = suspendCoroutine { continuation ->
                    tts = TextToSpeech(context) { status ->
                        if (status == TextToSpeech.ERROR) {
                            tts?.shutdown() // release resources in case of failure to start
                            continuation.resume(false)
                        } else
                            continuation.resume(true)
                    }
                }

                // if error reported in onInitListener.onInit from system TextToSpeech Engine
                if (!ttsResult)
                    tts = null
            }

            // system TextToSpeech not able to be initialised, return failure
            if (tts == null)
                return null

            // old-school setting of audio attribute. > Build.VERSION_CODES.O uses audio focus
            tts.setAudioAttributes(audioAttributes)

            // handlers for start, done and error utterance events
            tts.setOnUtteranceProgressListener(object :
                UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    currentSessionId = utteranceId

                    // request audio focus so other audio can be ducked/paused
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        audioManager?.requestAudioFocus(audioFocusRequest!!)
                }

                override fun onDone(utteranceId: String) {
                    currentSessionId = null

                    // abandon audio focus
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
                }

                override fun onError(utteranceId: String) {
                    currentSessionId = null

                    // abandon audio focus
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
                }
            })

            // assign all these local vars to statics at once so if staticTextToSpeech is set then we know all are set
            staticTextToSpeech.set(tts)
            audioManager = localAudioManager
            audioFocusRequest = localAudioFocusRequest
        }

        return tts
    }

    private fun formatDateForSpeech(date: Long): String {
        fun getFormatter(pattern: String): SimpleDateFormat {
            var formattedPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), pattern)

            if (DateFormat.is24HourFormat(context)) {
                formattedPattern = formattedPattern
                    .replace("h", "HH")
                    .replace("K", "HH")
                    .replace("\\s+a".toRegex(), "")
            }

            return SimpleDateFormat(formattedPattern, Locale.getDefault())
        }

        val now = Calendar.getInstance()
        val then = Calendar.getInstance()
        val yesterdayCheck = Calendar.getInstance()
        val lastWeekCheck = Calendar.getInstance()

        then.timeInMillis = date
        yesterdayCheck.timeInMillis = date
        yesterdayCheck.add(Calendar.DATE, 1)
        lastWeekCheck.timeInMillis = date
        lastWeekCheck.add(Calendar.DATE, 7)

        val timeWithSplitAmPmForSpeech = getFormatter("h:mm a").format(date).let {
            if (it.length < 2) it
            else when (it.substring((it.length - 2)).lowercase()) {
                "am" -> "${it.substring(0, (it.length - 2))}A M"
                "pm" -> "${it.substring(0, (it.length - 2))}P M"
                else -> it  // locales that don't use am/pm
            }
        }

        return when {
            (then.get(Calendar.DATE) == now.get(Calendar.DATE)) -> StringBuilder()      // today
            (yesterdayCheck.get(Calendar.DATE) == now.get(Calendar.DATE)) -> StringBuilder("yesterday")     // yesterday
            (lastWeekCheck.get(Calendar.DATE) > now.get(Calendar.DATE)) -> StringBuilder("on ").append(getFormatter("EEEE").format(date))   // during last week
            (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) -> StringBuilder("on ").append(getFormatter("MMMM d").format(date))     // this year
            else -> StringBuilder("on").append(getFormatter("MMMM d yyyy").format(date))    // otherwise
        }.append(" at ").append(timeWithSplitAmPmForSpeech).toString()
    }

    // toggle is used to stop and not repeat if a sessionId of the same name is currently being read aloud
    fun startSpeakSession(sessionId: String? = null, toggle: Boolean = true) {
        // get or init system TextToSpeech engine
        val tts = getSystemTtsEngine()
        if (tts === null)
            return

        this.sessionId = sessionId

        // do not output any speech in this session
        sessionStopped =
            (this.sessionId !== null) && (toggle) && (currentSessionId == sessionId)

        // stop any current speech immediately
        stopSpeaking()
    }

    fun endSpeakSession() {
        sessionStopped = true
    }

    private fun stopSpeaking() {
        // get or init system TextToSpeech engine
        val tts = getSystemTtsEngine()
        if (tts === null)
            return

        currentSessionId = null     // no utteranceprogresslistener callback so set this explicitly here
        tts.stop()

        // abandon audio focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
    }

    fun speak(utterance: String) {
        // if session stopped, return now
        if (sessionStopped)
            return

        // get or init system TextToSpeech engine
        val tts = getSystemTtsEngine()
        if (tts === null)
            return

        tts.speak(
            utterance,
            TextToSpeech.QUEUE_ADD,
            null,
            sessionId
        )
    }

    fun speakConversationLastSms(conversationAndSender: Pair<Conversation?, String?>): Boolean {
        // if session stopped, return now
        if (sessionStopped)
            return false

        // get or init system TextToSpeech engine
        val tts = getSystemTtsEngine()
        if (tts === null)
            return false

        // no conversation?, fail
        val conversation = conversationAndSender.first
        if (conversation === null)
            return false

        // no text in conversation's last message?, ditch
        val conversationLastSms = conversation.lastMessage
        val conversationLastSmsText = conversationLastSms?.getText()
        if (conversationLastSmsText === null)
            return false

        val utterance = StringBuilder()

        // more than 1 recipient
        if (conversation.recipients.count() > 1)
            utterance.append("Group message ")

        // message sender
        if (conversationLastSms.isMe())
            utterance.append("Sent by you to ")
        else
            utterance.append("from ")

        utterance.append(conversationAndSender.second ?: "an unknown number ")

        // message date
        if (conversation.date > 0)
            utterance.append(formatDateForSpeech(conversationLastSms.date))

        // small delay
        utterance.append(". ").append(conversationLastSmsText)

        speak(utterance.toString())

        return true
    }
}