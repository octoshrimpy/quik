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
package dev.octoshrimpy.quik.feature.qkreply

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.common.util.extensions.autoScrollToStart
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.common.util.extensions.showKeyboard
import dev.octoshrimpy.quik.common.widget.QkEditText
import dev.octoshrimpy.quik.databinding.QkreplyActivityBinding
import dev.octoshrimpy.quik.feature.compose.MessagesAdapter
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class QkReplyActivity : QkThemedActivity(), QkReplyView {

    private lateinit var binding: QkreplyActivityBinding

    @Inject lateinit var adapter: MessagesAdapter
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val menuItemIntent: Subject<Int> = PublishSubject.create()
    override val textChangedIntent by lazy { binding.message.textChanges() }
    override val changeSimIntent by lazy { binding.sim.clicks() }
    override val sendIntent by lazy { binding.send.clicks() }

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[QkReplyViewModel::class.java] }

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
        message.setText(match[0])
        message.setSelection(message.text?.length ?: 0)
        message.requestFocus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        setFinishOnTouchOutside(prefs.qkreplyTapDismiss.get())
        binding = QkreplyActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setBackgroundDrawable(null)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        viewModel.bindView(this)

        binding.toolbar.clipToOutline = true

        binding.messages.adapter = adapter
        binding.messages.adapter?.autoScrollToStart(binding.messages)
        binding.messages.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = binding.messages.scrollToPosition(adapter.itemCount - 1)
        })

        binding.message.setOnTouchListener(object : OnTouchListener {
            private val gestureDetector =
                GestureDetector(this@QkReplyActivity, object : SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            // include if want a custom message that the STT can (optionally) display   .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
                        speechResultLauncher.launch(speechRecognizerIntent)
                        return true
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        binding.message.showKeyboard()
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        return true     // don't show soft keyboard on this event
                    }
                })

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                return gestureDetector.onTouchEvent(e)
            }
        })
    }

    override fun render(state: QkReplyState) {
        if (state.hasError) {
            finish()
        }

        threadId.onNext(state.threadId)

        title = state.title

        binding.toolbar.menu.findItem(R.id.expand)?.isVisible = !state.expanded
        binding.toolbar.menu.findItem(R.id.collapse)?.isVisible = state.expanded

        adapter.data = state.data

        binding.counter.text = state.remaining
        binding.counter.setVisible(binding.counter.text.isNotBlank())

        binding.sim.setVisible(state.subscription != null)
        binding.sim.contentDescription = getString(R.string.compose_sim_cd, state.subscription?.displayName)
        binding.simIndex.text = "${state.subscription?.simSlotIndex?.plus(1)}"

        binding.send.isEnabled = state.canSend
        binding.send.imageAlpha = if (state.canSend) 255 else 128
    }

    override fun setDraft(draft: String) {
        binding.message.setText(draft)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.qkreply, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        menuItemIntent.onNext(item.itemId)
        return true
    }

    override fun getActivityThemeRes(black: Boolean) = when {
        black -> R.style.AppThemeDialog_Black
        else -> R.style.AppThemeDialog
    }

}