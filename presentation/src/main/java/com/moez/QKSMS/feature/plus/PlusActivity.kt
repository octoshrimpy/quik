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
package dev.octoshrimpy.quik.feature.plus

import android.graphics.Typeface
import android.os.Bundle
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding2.view.clicks
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.common.util.FontProvider
import dev.octoshrimpy.quik.common.util.extensions.makeToast
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.common.widget.PreferenceView
import dev.octoshrimpy.quik.feature.plus.experiment.UpgradeButtonExperiment
import dev.octoshrimpy.quik.manager.BillingManager
import dev.octoshrimpy.quik.databinding.QksmsPlusActivityBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class PlusActivity : QkThemedActivity(), PlusView {

    @Inject lateinit var fontProvider: FontProvider
    @Inject lateinit var upgradeButtonExperiment: UpgradeButtonExperiment
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[PlusViewModel::class.java] }
    private lateinit var binding: QksmsPlusActivityBinding

    override val upgradeIntent get() = binding.upgrade.clicks()
    override val upgradeDonateIntent get() = binding.upgradeDonate.clicks()
    override val donateIntent get() = binding.donate.clicks()
    override val themeClicks get() = binding.themes.clicks()
    override val scheduleClicks get() = binding.schedule.clicks()
    override val backupClicks get() = binding.backup.clicks()
    override val delayedClicks get() = binding.delayed.clicks()
    override val nightClicks get() = binding.night.clicks()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = QksmsPlusActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.title_qksms_plus)
        showBackButton(true)
        viewModel.bindView(this)

        binding.free.setVisible(false)

        if (!prefs.systemFont.get()) {
            fontProvider.getLato { lato ->
                val typeface = Typeface.create(lato, Typeface.BOLD)
                binding.toolbarLayout.collapsingToolbar.setCollapsedTitleTypeface(typeface)
                binding.toolbarLayout.collapsingToolbar.setExpandedTitleTypeface(typeface)
            }
        }

        // Make the list titles bold
        binding.linearLayout.children
            .mapNotNull { it as? PreferenceView }
            .map { it.titleTextView }
            .forEach { it.setTypeface(it.typeface, Typeface.BOLD) }

        val textPrimary = resolveThemeColor(android.R.attr.textColorPrimary)
        binding.toolbarLayout.collapsingToolbar.setCollapsedTitleTextColor(textPrimary)
        binding.toolbarLayout.collapsingToolbar.setExpandedTitleColor(textPrimary)

        val theme = colors.theme().theme
        binding.donate.setBackgroundTint(theme)
        binding.upgrade.setBackgroundTint(theme)
        binding.thanksIcon.setTint(theme)
    }

    override fun render(state: PlusState) {
        binding.description.text = getString(R.string.qksms_plus_description_summary, state.upgradePrice)
        binding.upgrade.text = getString(upgradeButtonExperiment.variant, state.upgradePrice, state.currency)
        binding.upgradeDonate.text = getString(R.string.qksms_plus_upgrade_donate, state.upgradeDonatePrice, state.currency)

        val fdroid = true

        binding.free.setVisible(fdroid)
        binding.toUpgrade.setVisible(!fdroid && !state.upgraded)
        binding.upgraded.setVisible(!fdroid && state.upgraded)

        binding.themes.isEnabled = state.upgraded
        binding.schedule.isEnabled = state.upgraded
        binding.backup.isEnabled = state.upgraded
        binding.delayed.isEnabled = state.upgraded
        binding.night.isEnabled = state.upgraded
    }

    override fun initiatePurchaseFlow(billingManager: BillingManager, sku: String) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                billingManager.initiatePurchaseFlow(this@PlusActivity, sku)
            } catch (e: Exception) {
                Timber.w(e)
                makeToast(R.string.qksms_plus_error)
            }
        }
    }

}
