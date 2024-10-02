package com.salesforce.androidsdk.ui

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.salesforce.androidsdk.R.drawable.sf__fix_status_bar

// TODO: Remove this in 13.0 after rewriting screens in compose.
internal fun AppCompatActivity.fixEdgeToEdge(view: View) {
    if (application.applicationInfo.targetSdkVersion > UPSIDE_DOWN_CAKE && SDK_INT > UPSIDE_DOWN_CAKE) {
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(view) { listenerView, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Fix screens being drawn behind status and navigation bars
            listenerView.updatePadding(insets.left, insets.top, insets.right, insets.bottom)

            // Fix transparent status bar not matching action bar
            view.background = ResourcesCompat.getDrawable(resources, sf__fix_status_bar, null)

            WindowInsetsCompat.CONSUMED
        }
    }
}