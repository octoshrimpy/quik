package dev.octoshrimpy.quik.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.model.MmsPart

open class QkContextMenuRecyclerView<ADAPTER_VALUE_TYPE, VIEW_HOLDER_VALUE_TYPE> : RecyclerView {
    class ViewHolder<VIEW_HOLDER_VALUE_TYPE>(view: View) : QkViewHolder(view) {
        init { itemView.isLongClickable = true }
        var contextMenuValue: VIEW_HOLDER_VALUE_TYPE? = null
    }

    abstract class Adapter<
                ADAPTER_VALUE_TYPE,
                T,
                VHT : RecyclerView.ViewHolder
            > : QkAdapter<T, VHT>() {
        var contextMenuValue: ADAPTER_VALUE_TYPE? = null
    }

    private var contextMenuInfo: ContextMenuInfo<
                ADAPTER_VALUE_TYPE,
                VIEW_HOLDER_VALUE_TYPE
            >? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)

    override fun getContextMenuInfo() = contextMenuInfo

    override fun getChildViewHolder(child: View): ViewHolder<VIEW_HOLDER_VALUE_TYPE>? {
        return super.getChildViewHolder(child) as ViewHolder<VIEW_HOLDER_VALUE_TYPE>
    }

    override fun showContextMenuForChild(originalView: View): Boolean {
        saveContextMenuInfo(originalView)
        return super.showContextMenuForChild(originalView)
    }

    override fun showContextMenuForChild(originalView: View, x: Float, y: Float): Boolean {
        saveContextMenuInfo(originalView)
        return super.showContextMenuForChild(originalView, x, y)
    }

    private fun saveContextMenuInfo(originalView: View) {
        contextMenuInfo = ContextMenuInfo(
            (adapter as Adapter<ADAPTER_VALUE_TYPE, *, *>).contextMenuValue,
            getChildViewHolder(originalView)?.contextMenuValue,
            this,
            originalView,
            getChildAdapterPosition(originalView),
            getChildItemId(originalView)
        )
    }

    class ContextMenuInfo<ADAPTER_VALUE_TYPE, VIEW_HOLDER_VALUE_TYPE>(
        val adapterValue: ADAPTER_VALUE_TYPE?,
        val viewHolderValue: VIEW_HOLDER_VALUE_TYPE?,
        val recyclerView: QkContextMenuRecyclerView<ADAPTER_VALUE_TYPE, VIEW_HOLDER_VALUE_TYPE>,
        val itemView: View,
        val adapterPosition: Int,
        val childItemStableId: Long
    ) : ContextMenu.ContextMenuInfo
}

class QkContextMenuRecyclerViewLongMmsPart : QkContextMenuRecyclerView<Long, MmsPart> {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)
}