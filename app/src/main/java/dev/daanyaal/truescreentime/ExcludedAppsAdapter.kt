package dev.daanyaal.truescreentime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Rows in the exclusion list. A checked box means "still excluded";
 * unchecking restores the app to the main list and the total.
 */
class ExcludedAppsAdapter(
    private val filterStore: FilterStore,
) : RecyclerView.Adapter<ExcludedAppsAdapter.ViewHolder>() {

    private var items: List<ExcludedApp> = emptyList()

    fun submitList(newItems: List<ExcludedApp>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_excluded_app, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.label
        holder.packageName.text = item.packageName
        if (item.icon != null) {
            holder.icon.setImageDrawable(item.icon)
        } else {
            holder.icon.setImageResource(R.drawable.ic_default_app)
        }

        holder.excluded.setOnCheckedChangeListener(null)
        holder.excluded.isChecked = !filterStore.isIncluded(item.packageName)
        holder.excluded.setOnCheckedChangeListener { _, checked ->
            filterStore.setIncluded(item.packageName, !checked)
        }
        holder.itemView.setOnClickListener { holder.excluded.toggle() }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.excluded_icon)
        val name: TextView = view.findViewById(R.id.excluded_name)
        val packageName: TextView = view.findViewById(R.id.excluded_package)
        val excluded: CheckBox = view.findViewById(R.id.excluded_checkbox)
    }
}
