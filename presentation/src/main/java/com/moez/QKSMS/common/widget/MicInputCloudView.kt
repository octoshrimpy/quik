/*
 * Copyright (C) - see below
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
/*
 * Original from:  Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.octoshrimpy.quik.common.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import dev.octoshrimpy.quik.R
import kotlin.math.roundToInt

class MicInputCloudView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    /**
     * State Descriptions:
     * - PAUSED_STATE: Animation speed is set to zero.
     * - PLAY_STATE: Animation speed is set to default, but can be overridden.
     */
    enum class ViewState {
        /**
         * Animation speed is set to zero.
         */
        PAUSED_STATE,

        /**
         * Animation speed is set to default, but can be overridden.
         */
        PLAY_STATE
    }

    @ColorInt
    private var primaryColor: Int = Color.WHITE

    private var pauseIcon: Drawable? = null

    private var playIcon: Drawable? = null

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.MicInputCloudView,
            0,
            0
        ).apply {

            try {
                pauseIcon = getDrawable(R.styleable.MicInputCloudView_pauseIcon)
                playIcon = getDrawable(R.styleable.MicInputCloudView_playIcon)
            } finally {
                recycle()
            }
        }
    }

    private var state: ViewState = ViewState.PLAY_STATE
    private var ovalOneAnimator: ValueAnimator? = null
    private var ovalTwoAnimator: ValueAnimator? = null
    private var ovalThreeAnimator: ValueAnimator? = null
    private var r1 = OVAL_ONE_DEFAULT_ROTATION
    private var r2 = OVAL_TWO_DEFAULT_ROTATION
    private var r3 = OVAL_THREE_DEFAULT_ROTATION
    private var o1h = OVAL_ONE_DEFAULT_HEIGHT
    private var o1w = OVAL_ONE_DEFAULT_WIDTH
    private var o2h = OVAL_TWO_DEFAULT_HEIGHT
    private var o2w = OVAL_TWO_DEFAULT_WIDTH
    private var o3h = OVAL_THREE_DEFAULT_HEIGHT
    private var o3w = OVAL_THREE_DEFAULT_WIDTH
    private var rotationSpeedMultiplier: Float = DEFAULT_ROTATION_SPEED_MULTIPLIER
    private var radius: Float = DEFAULT_RADIUS
    private var centerX: Float = 0f
    private var centerY: Float = 0f

    private val bottomCirclePaint = Paint(ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
        alpha = DEFAULT_OPACITY
    }

    private val topCircleBounds = Rect(0, 0, 0, 0)
    private val iconBounds = topCircleBounds

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            createAnimators()
        } else {
            state = ViewState.PLAY_STATE
            destroyAnimators()
        }
    }

    private fun createAnimators() {
        ovalOneAnimator = ValueAnimator.ofInt(
            o1h,
            OVAL_ONE_DEFAULT_HEIGHT + ANIMATION_CAP,
            o1h
        ).apply {
            duration = OVAL_ONE_ANIMATION_LENGTH
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { valueAnimator ->
                o1h = valueAnimator.animatedValue as Int
            }
        }

        ovalTwoAnimator = ValueAnimator.ofInt(
            o2h,
            OVAL_TWO_DEFAULT_HEIGHT + ANIMATION_CAP,
            o2h
        ).apply {
            duration = OVAL_TWO_ANIMATION_LENGTH
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { valueAnimator ->
                o2h = valueAnimator.animatedValue as Int
            }
        }

        ovalThreeAnimator = ValueAnimator.ofInt(
            o3h,
            OVAL_THREE_DEFAULT_HEIGHT + ANIMATION_CAP,
            o3h
        ).apply {
            duration = OVAL_THREE_ANIMATION_LENGTH
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { valueAnimator ->
                o3h = valueAnimator.animatedValue as Int
                invalidate() // needed to animate the other listeners as well
            }
        }
    }

    private fun destroyAnimators() {
        ovalOneAnimator?.cancel()
        ovalOneAnimator?.removeAllUpdateListeners()
        ovalTwoAnimator?.cancel()
        ovalTwoAnimator?.removeAllUpdateListeners()
        ovalThreeAnimator?.cancel()
        ovalThreeAnimator?.removeAllUpdateListeners()
    }

    private val circlePath: Path = Path()
    private val ovalOnePath: Path = Path()
    private val ovalTwoPath: Path = Path()
    private val ovalThreePath: Path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        circlePath.apply {
            addCircle(centerX, centerY, DEFAULT_RADIUS, Path.Direction.CCW)
        }
        ovalOnePath.apply {
            addOval(
                centerX - (radius + o1w),
                centerY - o1h,
                centerX + (radius + o1w),
                centerY + o1h,
                Path.Direction.CCW
            )
            op(this, circlePath, Path.Op.DIFFERENCE)
        }
        ovalTwoPath.apply {
            addOval(
                centerX - (radius + o2w),
                centerY - o2h,
                centerX + (radius + o2w),
                centerY + o2h,
                Path.Direction.CCW
            )
            op(this, circlePath, Path.Op.DIFFERENCE)
        }
        ovalThreePath.apply {
            addOval(
                centerX - (radius + o3w),
                centerY - o3h,
                centerX + (radius + o3w),
                centerY + o3h,
                Path.Direction.CCW
            )
            op(this, circlePath, Path.Op.DIFFERENCE)
        }
        drawMicInputCloud(canvas)
        if (state == ViewState.PLAY_STATE) {
            r1 += OVAL_ONE_ANIMATION_SPEED * rotationSpeedMultiplier
            r2 -= OVAL_TWO_ANIMATION_SPEED * rotationSpeedMultiplier
            r3 += OVAL_THREE_ANIMATION_SPEED * rotationSpeedMultiplier
            invalidate()
        }
    }

    private fun drawMicInputCloud(canvas: Canvas?) {
        canvas?.apply {
            save()
            rotate(r1, centerX, centerY)
            drawPath(ovalOnePath, bottomCirclePaint)
            restore()
            save()
            rotate(r2, centerX, centerY)
            drawPath(ovalTwoPath, bottomCirclePaint)
            restore()
            save()
            rotate(r3, centerX, centerY)
            drawPath(ovalThreePath, bottomCirclePaint)
            restore()
            circlePath.reset()
            ovalOnePath.reset()
            ovalTwoPath.reset()
            ovalThreePath.reset()
            if (state == ViewState.PLAY_STATE) {
                pauseIcon?.apply {
                    bounds = topCircleBounds
                    setTint(primaryColor)
                    draw(canvas)
                }
            } else {
                playIcon?.apply {
                    bounds = topCircleBounds
                    setTint(primaryColor)
                    draw(canvas)
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = DEFAULT_SIZE.dp
        val desiredHeight = DEFAULT_SIZE.dp

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width: Int = when (widthMode) {
            MeasureSpec.EXACTLY -> {
                widthSize
            }

            MeasureSpec.AT_MOST -> {
                desiredWidth.coerceAtMost(widthSize)
            }

            else -> {
                desiredWidth
            }
        }

        val height: Int = when (heightMode) {
            MeasureSpec.EXACTLY -> {
                heightSize
            }

            MeasureSpec.AT_MOST -> {
                desiredHeight.coerceAtMost(heightSize)
            }

            else -> {
                desiredHeight
            }
        }

        centerX = (width / 2).toFloat()
        centerY = (height / 2).toFloat()
        topCircleBounds.apply {
            left = (centerX - DEFAULT_RADIUS).toInt()
            top = (centerY - DEFAULT_RADIUS).toInt()
            right = (centerX + DEFAULT_RADIUS).toInt()
            bottom = (centerY + DEFAULT_RADIUS).toInt()
        }

        /**
         * Drawables are drawn the same way as the canvas is drawn, as both originate from the top-left corner.
         * Because of this, the icon's width = (right - left) and height = (bottom - top).
         */
        iconBounds.apply {
            left = (centerX - DEFAULT_RADIUS + ICON_SIZE.dp).toInt()
            top = (centerY - DEFAULT_RADIUS + ICON_SIZE.dp).toInt()
            right = (centerX + DEFAULT_RADIUS - ICON_SIZE.dp).toInt()
            bottom = (centerY + DEFAULT_RADIUS - ICON_SIZE.dp).toInt()
        }

        setMeasuredDimension(width, height)
    }

    override fun performClick(): Boolean {
        state = if (state == ViewState.PAUSED_STATE) {
            ovalOneAnimator?.resume()
            ovalTwoAnimator?.resume()
            ovalThreeAnimator?.resume()
            ViewState.PLAY_STATE
        } else {
            ovalOneAnimator?.pause()
            ovalTwoAnimator?.pause()
            ovalThreeAnimator?.pause()
            ViewState.PAUSED_STATE
        }
        invalidate()
        return super.performClick()
    }

    /**
     *  Sets the color of the cloud to the parameter, opacity is still set to 50%.
     */
    fun setColor(primary: Int) {
        primaryColor = primary
        bottomCirclePaint.apply {
            color = primary
            style = Paint.Style.FILL
            alpha = DEFAULT_OPACITY
        }
        invalidate()
    }

    /**
     * Sets state of the component to the parameter, must be of type MicInputCloud.ViewState.
     */
    fun setState(s: ViewState) {
        state = s
        invalidate()
    }

    fun getState() : ViewState {
        return state
    }

    /**
     * Sets the rotation speed and radius to the parameters, defaults are left unchanged.
     */
    fun setRotationSpeed(speed: Float, r: Float) {
        rotationSpeedMultiplier = speed
        radius = r
        invalidate()
    }

    /**
     * Starts the growing and shrinking animation
     */
    fun startAnimators() {
        ovalOneAnimator?.start()
        ovalTwoAnimator?.start()
        ovalThreeAnimator?.start()
    }

    companion object {
        val TAG: String? = MicInputCloudView::class.simpleName
        const val DEFAULT_RADIUS: Float = 70f
        const val EXTENDED_RADIUS: Float = 75f
        const val MAXIMUM_RADIUS: Float = 80f
        const val ICON_SIZE: Int = 9 // Converted to dp this equals about 24dp
        private const val DEFAULT_SIZE: Int = 110
        private const val DEFAULT_OPACITY: Int = 108
        private const val DEFAULT_ROTATION_SPEED_MULTIPLIER: Float = 0.5f
        private const val OVAL_ONE_DEFAULT_ROTATION: Float = 105f
        private const val OVAL_ONE_DEFAULT_HEIGHT: Int = 85
        private const val OVAL_ONE_DEFAULT_WIDTH: Int = 30
        private const val OVAL_ONE_ANIMATION_LENGTH: Long = 2000
        private const val OVAL_ONE_ANIMATION_SPEED: Float = 2.3f
        private const val OVAL_TWO_DEFAULT_ROTATION: Float = 138f
        private const val OVAL_TWO_DEFAULT_HEIGHT: Int = 70
        private const val OVAL_TWO_DEFAULT_WIDTH: Int = 25
        private const val OVAL_TWO_ANIMATION_LENGTH: Long = 1000
        private const val OVAL_TWO_ANIMATION_SPEED: Float = 1.75f
        private const val OVAL_THREE_DEFAULT_ROTATION: Float = 63f
        private const val OVAL_THREE_DEFAULT_HEIGHT: Int = 80
        private const val OVAL_THREE_DEFAULT_WIDTH: Int = 40
        private const val OVAL_THREE_ANIMATION_LENGTH: Long = 1500
        private const val OVAL_THREE_ANIMATION_SPEED: Float = 1f
        private const val ANIMATION_CAP: Int = 15
        private val Int.dp: Int
            get() = (this * Resources.getSystem().displayMetrics.density).roundToInt()
    }
}
