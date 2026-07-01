package com.kamrenzirger.synctoandroiddata.ui
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kamrenzirger.synctoandroiddata.databinding.ItemAppPickerBinding
class AppPickerAdapter(
    private val pm: PackageManager,
    private val onAppSelected: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<AppPickerAdapter.ViewHolder>() {
    private var allApps = listOf<ApplicationInfo>()
    private var filteredApps = listOf<ApplicationInfo>()
    fun setApps(apps: List<ApplicationInfo>) {
        allApps = apps
        filteredApps = apps
        notifyDataSetChanged()
    }
    fun filter(query: String) {
        filteredApps = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { 
                pm.getApplicationLabel(it).toString().contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(filteredApps[position])
    }
    override fun getItemCount(): Int = filteredApps.size
    inner class ViewHolder(private val binding: ItemAppPickerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: ApplicationInfo) {
            binding.tvAppName.text = pm.getApplicationLabel(app)
            binding.tvPackageName.text = app.packageName
            try {
                val icon = pm.getApplicationIcon(app)
                Glide.with(itemView.context).load(icon).into(binding.ivAppIcon)
            } catch (e: Exception) {
                binding.ivAppIcon.setImageDrawable(null)
            }
            itemView.setOnClickListener { onAppSelected(app) }
        }
    }
}
