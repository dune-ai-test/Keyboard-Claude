package com.example.customkeyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.customkeyboard.R
import com.example.customkeyboard.data.db.ClipboardItem
import java.util.Collections

/**
 * Renders the full clipboard history inside the keyboard's clipboard panel. Each row shows the
 * complete (non-truncated, multi-line) text plus pin, delete, and drag-to-reorder controls.
 */
class ClipboardAdapter(
    private val onTap: (ClipboardItem) -> Unit,
    private val onTogglePin: (ClipboardItem) -> Unit,
    private val onDelete: (ClipboardItem) -> Unit,
    private val onReordered: (List<ClipboardItem>) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    private val items = mutableListOf<ClipboardItem>()

    /** Attaches drag-to-reorder support; call once after the RecyclerView is created. */
    fun attachDragSupport(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                Collections.swap(items, from, to)
                notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Reordering only; swipe-to-dismiss is intentionally not enabled (use the
                // dedicated delete button instead, to avoid accidental data loss).
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                onReordered(items.toList())
            }

            override fun isLongPressDragEnabled(): Boolean = false
        }
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)

        // Expose the touch helper so row drag handles can trigger dragging explicitly.
        dragStarter = { holder -> touchHelper.startDrag(holder) }
    }

    private var dragStarter: ((RecyclerView.ViewHolder) -> Unit)? = null

    fun submitList(newItems: List<ClipboardItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clipboard_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item.text
        holder.pinButton.isSelected = item.isPinned
        holder.pinButton.alpha = if (item.isPinned) 1f else 0.55f
        holder.pinButton.setColorFilter(
            ContextCompat.getColor(
                holder.itemView.context,
                if (item.isPinned) R.color.kb_accent else R.color.kb_key_subtext
            )
        )

        holder.itemView.setOnClickListener { onTap(item) }
        holder.pinButton.setOnClickListener { onTogglePin(item) }
        holder.deleteButton.setOnClickListener { onDelete(item) }

        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                dragStarter?.invoke(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.clipText)
        val pinButton: ImageButton = view.findViewById(R.id.pinButton)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        val dragHandle: android.widget.ImageView = view.findViewById(R.id.dragHandle)
    }
}
