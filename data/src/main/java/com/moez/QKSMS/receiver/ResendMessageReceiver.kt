package dev.octoshrimpy.quik.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.interactor.SendExistingMessage
import javax.inject.Inject

class ResendMessageReceiver : BroadcastReceiver() {
    @Inject
    lateinit var sendExistingMessage: SendExistingMessage

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        val messageId = intent.getLongExtra("id", -1L)

        val pendingRepository = goAsync()
        sendExistingMessage.execute(messageId) { pendingRepository.finish() }
    }
}
