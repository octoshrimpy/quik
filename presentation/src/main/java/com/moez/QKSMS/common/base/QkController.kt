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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.bluelinelabs.conductor.archlifecycle.LifecycleController
import dev.octoshrimpy.quik.R

abstract class QkController<VB : ViewBinding, ViewContract : QkViewContract<State>, State, Presenter : QkPresenter<ViewContract, State>> : LifecycleController() {

    abstract var presenter: Presenter

    private val appCompatActivity: AppCompatActivity?
        get() = activity as? AppCompatActivity

    protected val themedActivity: QkThemedActivity?
        get() = activity as? QkThemedActivity

    private var _binding: VB? = null
    protected val binding: VB
        get() = requireNotNull(_binding)

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): VB

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        val vb = inflateBinding(inflater, container)
        _binding = vb
        return vb.root.also { onViewCreated() }
    }

    open fun onViewCreated() {
    }

    fun setTitle(@StringRes titleId: Int) {
        setTitle(activity?.getString(titleId))
    }

    fun setTitle(title: CharSequence?) {
        activity?.title = title
        view?.findViewById<TextView>(R.id.toolbarTitle)?.text = title
    }

    fun showBackButton(show: Boolean) {
        appCompatActivity?.supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onCleared()
    }

}
