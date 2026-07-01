package com.kamrenzirger.synctoandroiddata.ui
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kamrenzirger.synctoandroiddata.data.SyncEntry
import com.kamrenzirger.synctoandroiddata.data.SyncEntryWithPairs
import com.kamrenzirger.synctoandroiddata.databinding.ItemSyncEntryBinding
class SyncEntryAdapter(
    private val onSyncToggle: (SyncEntry, Boolean) -> Unit,
    private val onForceSync: (SyncEntry, Boolean) -> Unit,
    private val onClick: (SyncEntry) -> Unit
) : ListAdapter<SyncEntryWithPairs, SyncEntryAdapter.ViewHolder>(SyncEntryDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSyncEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    inner class ViewHolder(private val binding: ItemSyncEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(syncEntryWithPairs: SyncEntryWithPairs) {
            val entry = syncEntryWithPairs.entry
            val pairs = syncEntryWithPairs.pairs
            binding.appName.text = entry.appName
            binding.packageName.text = entry.packageName
            val pathInfo = if (pairs.isEmpty()) {
                "No directory pairs configured"
            } else {
                pairs.joinToString("\n---\n") { 
                    "Ext: ${it.externalPath}\nIn: ${it.internalPath}"
                }
            }
            binding.paths.text = pathInfo
            binding.syncSwitch.isChecked = entry.isEnabled
            try {
                val icon = itemView.context.packageManager.getApplicationIcon(entry.packageName)
                Glide.with(itemView.context).load(icon).into(binding.appIcon)
            } catch (e: Exception) {
                binding.appIcon.setImageDrawable(null)
            }
            binding.syncSwitch.setOnCheckedChangeListener { _, isChecked ->
                onSyncToggle(entry, isChecked)
            }
            binding.forceInToExt.setOnClickListener { onForceSync(entry, true) }
            binding.forceExtToIn.setOnClickListener { onForceSync(entry, false) }
            itemView.setOnClickListener { onClick(entry) }
        }
    }
    class SyncEntryDiffCallback : DiffUtil.ItemCallback<SyncEntryWithPairs>() {
        override fun areItemsTheSame(oldItem: SyncEntryWithPairs, newItem: SyncEntryWithPairs): Boolean {
            return oldItem.entry.id == newItem.entry.id
        }
        override fun areContentsTheSame(oldItem: SyncEntryWithPairs, newItem: SyncEntryWithPairs): Boolean {
            return oldItem == newItem
        }
    }
}
