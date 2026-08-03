package dev.daanyaal.truescreentime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExcludedApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * Manages the exclusion list: every app the user has left out of the
 * total, whether or not it has usage in the current range. Unchecking a
 * row brings the app back into the main list and the total.
 */
class ExcludedAppsActivity : AppCompatActivity() {

    private lateinit var filterStore: FilterStore
    private lateinit var adapter: ExcludedAppsAdapter
    private lateinit var emptyView: TextView
    private lateinit var restoreAllButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_excluded_apps)

        filterStore = FilterStore(this)
        emptyView = findViewById(R.id.excluded_empty)
        restoreAllButton = findViewById(R.id.restore_all)

        adapter = ExcludedAppsAdapter(filterStore)
        val recycler = findViewById<RecyclerView>(R.id.excluded_list)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        restoreAllButton.setOnClickListener {
            filterStore.includeAll()
            reload()
        }

        reload()
    }

    private fun reload() {
        val packages = filterStore.excludedPackages()
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val pm = packageManager
                packages.map { pkg ->
                    // Packages can linger here after being uninstalled;
                    // fall back to the raw name so they stay removable.
                    val info = try {
                        pm.getApplicationInfo(pkg, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                    ExcludedApp(
                        packageName = pkg,
                        label = info?.loadLabel(pm)?.toString() ?: pkg,
                        icon = info?.loadIcon(pm),
                    )
                }.sortedBy { it.label.lowercase() }
            }
            adapter.submitList(items)
            val empty = items.isEmpty()
            emptyView.visibility = if (empty) View.VISIBLE else View.GONE
            restoreAllButton.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ExcludedAppsActivity::class.java))
        }
    }
}
