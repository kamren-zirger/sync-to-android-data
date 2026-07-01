package com.kamrenzirger.synctoandroiddata.ui
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kamrenzirger.synctoandroiddata.data.DirectoryPair
import com.kamrenzirger.synctoandroiddata.databinding.ItemDirectoryPairBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
class DirectoryPairAdapter(
    private val onPickInternal: (Int) -> Unit,
    private val onPickExternal: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<DirectoryPairAdapter.ViewHolder>() {
    private val pairs = mutableListOf<DirectoryPair>()
    fun setPairs(newPairs: List<DirectoryPair>) {
        pairs.clear()
        pairs.addAll(newPairs)
        notifyDataSetChanged()
    }
    fun addPair(pair: DirectoryPair) {
        pairs.add(pair)
        notifyItemInserted(pairs.size - 1)
        notifyItemRangeChanged(0, pairs.size)
    }
    fun removePair(position: Int) {
        if (position >= 0 && position < pairs.size) {
            pairs.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(0, pairs.size)
        }
    }
    fun updateInternalPath(position: Int, path: String) {
        if (position >= 0 && position < pairs.size) {
            pairs[position] = pairs[position].copy(internalPath = path)
            notifyItemChanged(position)
        }
    }
    fun updateExternalPath(position: Int, path: String) {
        if (position >= 0 && position < pairs.size) {
            pairs[position] = pairs[position].copy(externalPath = path)
            notifyItemChanged(position)
        }
    }
    fun getPairs(): List<DirectoryPair> = pairs
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDirectoryPairBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(pairs[position], position)
    }
    override fun getItemCount(): Int = pairs.size
    inner class ViewHolder(private val binding: ItemDirectoryPairBinding) : RecyclerView.ViewHolder(binding.root) {
        private var internalTextWatcher: TextWatcher? = null
        private var externalTextWatcher: TextWatcher? = null
        fun bind(pair: DirectoryPair, position: Int) {
            binding.tvPairIndex.text = "Directory Pair ${position + 1}"
            binding.btnRemovePair.visibility = if (itemCount > 1) View.VISIBLE else View.INVISIBLE
            binding.etInternalPath.removeTextChangedListener(internalTextWatcher)
            binding.etExternalPath.removeTextChangedListener(externalTextWatcher)
            binding.etInternalPath.setText(pair.internalPath)
            binding.etExternalPath.setText(pair.externalPath)
            internalTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newPath = s?.toString() ?: ""
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION && pairs[pos].internalPath != newPath) {
                        pairs[pos] = pairs[pos].copy(internalPath = newPath)
                    }
                }
            }
            externalTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newPath = s?.toString() ?: ""
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION && pairs[pos].externalPath != newPath) {
                        pairs[pos] = pairs[pos].copy(externalPath = newPath)
                    }
                }
            }
            binding.etInternalPath.addTextChangedListener(internalTextWatcher)
            binding.etExternalPath.addTextChangedListener(externalTextWatcher)
            val internalLayout = binding.etInternalPath.parent.parent as? TextInputLayout
            internalLayout?.setEndIconOnClickListener { onPickInternal(adapterPosition) }
            val externalLayout = binding.etExternalPath.parent.parent as? TextInputLayout
            externalLayout?.setEndIconOnClickListener { onPickExternal(adapterPosition) }
            binding.btnRemovePair.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    MaterialAlertDialogBuilder(itemView.context)
                        .setTitle("Delete Pair")
                        .setMessage("Are you sure you want to remove Directory Pair ${pos + 1}?")
                        .setPositiveButton("Delete") { _, _ -> onRemove(pos) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }
}
