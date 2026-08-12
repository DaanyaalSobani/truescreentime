package dev.daanyaal.truescreentime

import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Launches every screen on the JVM. These catch the failures a compiler
 * cannot: a layout that no longer contains a view the activity binds
 * (R.id values resolve across all layouts, so findViewById returns null and
 * the activity dies on start), a missing theme attribute, or a custom view
 * that throws while inflating.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActivitySmokeTest {

    @Test
    fun `main activity inflates and binds its views`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertNotNull(activity.findViewById<View>(R.id.permission_container))
        assertNotNull(activity.findViewById<View>(R.id.content_container))
        assertNotNull(activity.findViewById<View>(R.id.donut_chart))
        assertNotNull(activity.findViewById<View>(R.id.donut_detail))
        assertNotNull(activity.findViewById<View>(R.id.donut_back))
        assertNotNull(activity.findViewById<View>(R.id.week_chart))
        assertNotNull(activity.findViewById<View>(R.id.week_range))
        assertNotNull(activity.findViewById<View>(R.id.show_system_apps))
        assertNotNull(activity.findViewById<View>(R.id.show_excluded_apps))
        assertNotNull(activity.findViewById<View>(R.id.manage_excluded))
        assertNotNull(activity.findViewById<View>(R.id.app_list))
    }

    @Test
    fun `excluded apps activity inflates and binds its views`() {
        val activity = Robolectric.buildActivity(ExcludedAppsActivity::class.java).setup().get()

        assertNotNull(activity.findViewById<View>(R.id.excluded_list))
        assertNotNull(activity.findViewById<View>(R.id.excluded_empty))
        assertNotNull(activity.findViewById<View>(R.id.restore_all))
    }

    @Test
    fun `app detail activity inflates when launched with a package`() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            AppDetailActivity::class.java
        ).putExtra("package_name", "com.example.app")
            .putExtra("label", "Example")

        val activity = Robolectric.buildActivity(AppDetailActivity::class.java, intent)
            .setup().get()

        assertNotNull(activity.findViewById<View>(R.id.detail_week_chart))
        assertNotNull(activity.findViewById<View>(R.id.detail_week_total))
        assertNotNull(activity.findViewById<View>(R.id.prev_week))
        assertNotNull(activity.findViewById<View>(R.id.next_week))
    }
}
