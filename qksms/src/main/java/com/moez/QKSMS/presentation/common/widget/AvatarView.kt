package com.moez.QKSMS.presentation.common.widget

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.jakewharton.rxbinding2.view.clicks
import com.moez.QKSMS.R
import com.moez.QKSMS.common.di.appComponent
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.GlideApp
import com.moez.QKSMS.data.model.Contact
import com.moez.QKSMS.presentation.Navigator
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.avatar_view.view.*
import javax.inject.Inject

class AvatarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    @Inject lateinit var colors: Colors
    @Inject lateinit var navigator: Navigator

    private val disposables = CompositeDisposable()

    var contact: Contact? = null
        set(value) {
            field = value
            updateView()
        }

    init {
        View.inflate(context, R.layout.avatar_view, this)
        appComponent.inject(this)

        setBackgroundResource(R.drawable.circle)
        clipToOutline = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        disposables += colors.theme
                .subscribe { color -> background.setTint(color) }

        disposables += clicks().subscribe {
            contact?.lookupKey?.takeIf { it.isNotEmpty() }?.let { key ->
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, key)
                ContactsContract.QuickContact.showQuickContact(context, this@AvatarView, uri,
                        ContactsContract.QuickContact.MODE_MEDIUM, null)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        disposables.clear()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (!isInEditMode) {
            updateView()
        }
    }

    private fun updateView() {
        if (contact?.name.orEmpty().isNotEmpty()) {
            initial.text = contact?.name?.substring(0, 1)
            icon.visibility = GONE
        } else {
            initial.text = null
            icon.visibility = VISIBLE
        }

        photo.setImageDrawable(null)
        contact?.numbers?.firstOrNull()?.address?.let { address ->
            GlideApp.with(photo).load(PhoneNumberUtils.stripSeparators(address)).into(photo)
        }
    }
}