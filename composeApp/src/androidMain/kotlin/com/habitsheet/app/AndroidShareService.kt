package com.habitsheet.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.habitsheet.domain.calculation.DailyShareSummary
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.ShareImageContent
import com.habitsheet.ui.ShareService
import java.io.File
import java.io.FileOutputStream

class AndroidShareService(private val activity: Activity) : ShareService {

    override fun shareDailySummary(summary: DailyShareSummary) {
        // We render the composable to a bitmap using a hidden ComposeView
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            visibility = View.GONE
            setContent {
                HabitSheetTheme {
                    ShareImageContent(summary)
                }
            }
        }
        rootView.addView(composeView)

        // Wait for it to be laid out
        composeView.post {
            try {
                val bitmap = createBitmapFromView(composeView)
                val uri = saveBitmapToCache(bitmap)
                shareImage(uri)
            } finally {
                rootView.removeView(composeView)
            }
        }
    }

    private fun createBitmapFromView(view: View): Bitmap {
        // Measure and layout the view
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val imagesFolder = File(activity.cacheDir, "images")
        imagesFolder.mkdirs()
        val file = File(imagesFolder, "daily_summary_${System.currentTimeMillis()}.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()
        return FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    }

    private fun shareImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "Share Today's Habits"))
    }
}
