package com.kamrenzirger.synctoandroiddata.ui
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kamrenzirger.synctoandroiddata.R
import com.kamrenzirger.synctoandroiddata.databinding.ItemDirectoryPickerBinding
class DirectoryPickerAdapter(
    private val onItemSelected: (String, Boolean) -> Unit
) : RecyclerView.Adapter<DirectoryPickerAdapter.ViewHolder>() {
    private var items = listOf<String>()
    fun setItems(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDirectoryPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    override fun getItemCount(): Int = items.size
    inner class ViewHolder(private val binding: ItemDirectoryPickerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: String) {
            val isDirectory = item.endsWith("/") || item == ".."
            val cleanName = item.removeSuffix("/")
            binding.tvFileName.text = cleanName
            binding.ivFileIcon.setImageResource(if (isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
            itemView.isEnabled = isDirectory
            itemView.alpha = if (isDirectory) 1.0f else 0.5f
            itemView.setOnClickListener {
                if (isDirectory) {
                    onItemSelected(cleanName, true)
                }
            }
        }
    }
}
