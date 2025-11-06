/*
 * Copyright (C) 2025
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QUIK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QUIK.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.GET_DEVICES_INPUTS


// this class is, by design, as simplistic it can be to support easy and fast connection
// for recording from first bluetooth mic found, if any

class BluetoothMicManager(
    private val context: Context,
    private val callbacks: Callbacks
)
    : BroadcastReceiver() {
    interface Callbacks {
        fun onNoDeviceFound()
        fun onDeviceFound(device: AudioDeviceInfo?)
        fun onConnecting(device: AudioDeviceInfo?)
        fun onConnected(device: AudioDeviceInfo?)
        fun onDisconnected(device: AudioDeviceInfo?)
    }

    init {
        // register for bluetooth sco broadcast intents
        context.registerReceiver(this, IntentFilter().apply {
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        })
    }

    var device: AudioDeviceInfo? = null
        private set

    private var hadFirstStickyIntent: Boolean = false
    private var closed: Boolean = false

    fun close() {
        if (closed)
            return
        closed = true

        device = null
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).stopBluetoothSco()
        context.unregisterReceiver(this)
    }

    // note: caller may get connected() callback before this method returns if this method is
    // called from a non-UI thread and the UI thread processes a sticky sco_audio_state_connected
    fun startBluetoothDevice() {
        device = null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // get all audio input devices
        val allAudioInputDevices = audioManager.getDevices(GET_DEVICES_INPUTS)

// @TODO: below code can be used for BLE headset devices if and when one can be tested
//      : also need to register for BLE headset intent broadcasts and other things later
//        // firstly (highest priority), check for a connected BLE headset input device
//        for (btInputDevice in allAudioInputDevices) {
//            if (btInputDevice.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
//                device = btInputDevice
//                break
//            }
//        }

        // if no ble device found, check if platform supports non-phone-call sco connections
        if ((device == null) &&
            (audioManager.isBluetoothScoAvailableOffCall)) {
            // find first SCO input device
            for (btInputDevice in allAudioInputDevices) {
                if (btInputDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    device = btInputDevice
                    break
                }
            }
        }

        // if no usable device found, callback to caller and return
        if (device == null) {
            callbacks.onNoDeviceFound()
            return
        }

        // callback that a sco capable bluetooth device was found
        callbacks.onDeviceFound(device)

        // start sco connection on bluetooth device (even if already started by other app)
        try {    // see https://stackoverflow.com/a/26929741 for try-catch reason
            audioManager.startBluetoothSco()
        }
        catch (e: Exception) { /* nothing */ }

        // if sco device is already on (maybe by another app?) callback now
        if (audioManager.isBluetoothScoOn)
            callbacks.onConnected(device)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!hadFirstStickyIntent) {
            hadFirstStickyIntent = true
            return
        }

        // if sco connection state has changed
        val action = intent.action ?: return
        if (action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
            when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> callbacks.onConnected(device)
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> callbacks.onConnecting(device)
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                    close()
                    callbacks.onDisconnected(device)
                }
            }
        }
    }
}
