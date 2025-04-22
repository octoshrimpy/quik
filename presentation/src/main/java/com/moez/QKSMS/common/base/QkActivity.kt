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
package dev.octoshrimpy.quik.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.toolbar.*
import javax.inject.Inject

abstract class QkActivity : AppCompatActivity() {
    @Inject lateinit var prefs: Preferences

    protected val menu: Subject<Menu> = BehaviorSubject.create()

    private var scaleGestureDetector: ScaleGestureDetector? = null

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onNewIntent(intent)
        disableScreenshots(prefs.disableScreenshots.get())

        scaleGestureDetector = ScaleGestureDetector(
            this.applicationContext,
            object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    var currentTextSize = prefs.textSize.get()

                    // large enough pinch changes text size
                    if (detector.scaleFactor < 0.8) currentTextSize--
                    if (detector.scaleFactor > 1.2) currentTextSize++

                    // bounds check
                    val maxSize = this@QkActivity.resources.getStringArray(R.array.text_sizes).size
                    if (currentTextSize < 0) currentTextSize = 0
                    if (currentTextSize > maxSize) currentTextSize = maxSize

                    prefs.textSize.set(currentTextSize)
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        disableScreenshots(prefs.disableScreenshots.get())
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null)
            scaleGestureDetector?.onTouchEvent(ev)

        return super.dispatchTouchEvent(ev)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setSupportActionBar(toolbar)
        title = title // The title may have been set before layout inflation
    }

    override fun setTitle(titleId: Int) {
        title = getString(titleId)
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        toolbarTitle?.text = title
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        if (menu != null) {
            this.menu.onNext(menu)
        }
        return result
    }

    protected open fun showBackButton(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    private fun disableScreenshots(disableScreenshots: Boolean) {
        if (disableScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

}