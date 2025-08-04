package com.reactnativepopupmenu

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.drawable.DrawableCompat
import com.facebook.react.bridge.ColorPropConverter
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UIManager
import com.facebook.react.uimanager.PixelUtil
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.util.ReactFindViewUtil
import java.net.URI

object Helpers {

  fun isDarkMode(activity: Activity, options: ReadableMap): Boolean {
    if (options.hasKey("theme")) {
      return options.getString("theme") == "dark"
    }
    return when (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
      Configuration.UI_MODE_NIGHT_NO -> false
      Configuration.UI_MODE_NIGHT_YES -> true
      else -> false
    }
  }

  fun findView(activity: Activity, params: ReadableMap): View? {
    val viewID = if (params.hasKey("viewID")) params.getInt("viewID") else 0
    //UIManagerHelper.getUIManager(activity as ReactContext, viewID)
    return when {
      viewID != 0 -> activity.findViewById(viewID)
      else -> null
    }
  }

  fun getIcon(activity: Activity, source: String?): Bitmap? {
    source ?: return null
    val resourceId: Int =
      activity.resources.getIdentifier(source, "drawable", activity.packageName)

    return if (resourceId == 0) {
      val uri = URI(source)
      BitmapFactory.decodeStream(uri.toURL().openConnection().getInputStream())
    } else {
      BitmapFactory.decodeResource(activity.resources, resourceId)
    }
  }

  fun toDrawable(activity: Activity, bitmap: Bitmap?): Drawable? {
    bitmap ?: return null
    return BitmapDrawable(activity.resources, bitmap)
  }

  fun getStatusBarHeight(activity: Activity): Int {
    var result = 0
    val resourceId: Int = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
    if (resourceId > 0) {
      result = activity.resources.getDimensionPixelSize(resourceId)
    }
    return result
  }
}


fun Double.toPixel(): Int {
  return PixelUtil.toPixelFromDIP(this).toInt()
}

fun ReadableMap.bool(key: String): Boolean {
  return this.hasKey(key) && getBoolean(key)
}

fun ReadableMap.double(key: String, default: Double): Double {
  if (!hasKey(key)) return default
  return getDouble(key)
}

fun ReadableMap.color(context: Context, key: String, default: Int): Int {
  if (!hasKey(key)) return default
  return ColorPropConverter.getColor(getDouble(key), context) ?: default
}
