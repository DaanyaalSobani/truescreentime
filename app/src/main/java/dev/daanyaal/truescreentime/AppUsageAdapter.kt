package dev.daanyaal.truescreentime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppUsageAdapter(
    private val filterStore: FilterStore,
    private val onInclusionChanged: (AppUsage, Boolean) -> Unit,
    private val onAppClicked: (AppUsage) -> Unit,
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    private var items: List<AppUsage> = emptyList()

    fun submitList(newItems: List<AppUsage>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.label
        holder.time.text = TimeFormat.format(holder.itemView.context, item.totalTimeMs)
        if (item.icon != null) {
            holder.icon.setImageDrawable(item.icon)
        } else {
            holder.icon.setImageResource(R.drawable.ic_default_app)
        }

        holder.include.setOnCheckedChangeListener(null)
        holder.include.isChecked = filterStore.isIncluded(item.packageName)
        holder.include.setOnCheckedChangeListener { _, checked ->
            filterStore.setIncluded(item.packageName, checked)
            onInclusionChanged(item, checked)
        }
        // Tapping anywhere on the row toggles inclusion too;
        // tapping the icon opens the per-app weekly detail.
        holder.itemView.setOnClickListener { holder.include.toggle() }
        holder.icon.setOnClickListener { onAppClicked(item) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val time: TextView = view.findViewById(R.id.app_time)
        val include: CheckBox = view.findViewById(R.id.app_include)
    }
}
