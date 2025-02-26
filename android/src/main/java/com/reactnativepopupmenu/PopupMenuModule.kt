package com.reactnativepopupmenu

import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import androidx.annotation.ColorInt
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.PixelUtil
import com.github.zawadz88.materialpopupmenu.popupMenu

import com.facebook.react.bridge.ReactApplicationContext
import com.github.zawadz88.materialpopupmenu.appearance


class PopupMenuModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return "PopupMenu"
  }

  fun ReadableMap.gravity(): Int {
    return if (hasKey("gravity") && getString("gravity") == "top") {
      Gravity.TOP
    } else {
      Gravity.BOTTOM
    }
  }

  @ReactMethod
  fun configurePopup(options: ReadableMap) {
    if (options.hasKey("isIconsFromRight")) {
      val rightIcon = options.getBoolean("isIconsFromRight")
      appearance.rightIcon = rightIcon
    }

    if (options.hasKey("cornerRadius")) {
      appearance.cornerRadius = PixelUtil.toPixelFromDIP(options.getDouble("cornerRadius"))
    }

    if (options.hasKey("minWidth")) {
      appearance.minWidth = PixelUtil.toPixelFromDIP(options.getDouble("minWidth")).toInt()
    }

    if (options.hasKey("maxWidth")) {
      appearance.maxWidth = PixelUtil.toPixelFromDIP(options.getDouble("maxWidth")).toInt()
    }

    if (options.hasKey("allowFontScaling")) {
      appearance.allowFontScaling = options.bool("allowFontScaling")
    }

    if (options.hasKey("item")) {
      val item = options.getMap("item")!!
      if (item.hasKey("height")) {
        appearance.itemHeight = PixelUtil.toPixelFromDIP(item.getDouble("height"))
      }

      if (item.hasKey("fontSize")) {
        appearance.itemFontSize = item.getDouble("fontSize").toFloat()
      }

      if (item.hasKey("iconSize")) {
        appearance.popMenuActionIconSize = PixelUtil.toPixelFromDIP(item.getDouble("iconSize"))
      }

      if (item.hasKey("paddingHorizontal")) {
        appearance.popMenuActionPaddingHorizontal = PixelUtil.toPixelFromDIP(item.getDouble("paddingHorizontal"))
      }

      if (item.hasKey("separatorHeight")) {
        appearance.separatorHeight = PixelUtil.toPixelFromDIP(item.getDouble("separatorHeight"))
      }

      if (item.hasKey("separatorColor") && reactApplicationContext != null) {
        appearance.separatorColor =
          item.color(reactApplicationContext!!, "separatorColor", Color.parseColor("#1F000000"))
      }

      if (item.hasKey("iconTint") && reactApplicationContext != null) {
        appearance.iconTint = item.color(reactApplicationContext!!, "iconTint", Color.parseColor("#1F000000"))
      }

      if (item.hasKey("textColor") && reactApplicationContext != null) {
        appearance.textColor = item.color(reactApplicationContext!!, "textColor", Color.parseColor("#1F000000"))
      }
    }


    if (options.hasKey("backgroundColor") && reactApplicationContext != null) {
      appearance.backgroundColor =
        options.color(reactApplicationContext!!, "backgroundColor", Color.parseColor("#1F000000"))
    }
  }

  @ReactMethod
  fun showPopup(params: ReadableMap, actionCallback: Callback) {
    val originalAppearance = appearance.copy()
    val activity = reactApplicationContext?.currentActivity ?: return

    var s = R.style.Widget_MPM_Menu
    if (Helpers.isDarkMode(activity, params)) {
      s = R.style.Widget_MPM_Menu_Dark
    }

    var gravity = Gravity.NO_GRAVITY
    if (!params.hasKey("frame")) gravity = params.gravity()
    appearance.popMenuGravity = gravity

    var minWidth = 0
    if (params.hasKey("minWidth")) {
      minWidth = PixelUtil.toPixelFromDIP(params.getDouble("minWidth")).toInt()
    }

    var maxWidth = 0
    if (params.hasKey("maxWidth")) {
      maxWidth = PixelUtil.toPixelFromDIP(params.getDouble("maxWidth")).toInt()
    }

    var didDismissBySelectItem = false
    val popupMenu = popupMenu {
      popupMinWidth = minWidth
      popupMaxWidth = maxWidth
      style = s
      val buttons = params.getArray("buttons") ?: return@popupMenu
      section {
        for (i in 0 until buttons.size()) {
          val button = buttons.getMap(i) ?: continue
          val icon = Helpers.getIcon(activity, button.getString("icon"))
          val drawable = Helpers.toDrawable(activity, icon)

          var iconTint = appearance.iconTint
          if (button.hasKey("iconTint")) {
            iconTint = button.color(activity, "iconTint", Color.BLACK)
          }

          var textColor = appearance.textColor
          if (button.hasKey("textColor")) {
            textColor = button.color(activity, "textColor", Color.BLACK)
          }

          var sh = appearance.separatorHeight
          if (button.hasKey("separatorHeight")) {
            sh = PixelUtil.toPixelFromDIP(button.getDouble("separatorHeight"))
          }

          var sc = appearance.separatorColor
          if (button.hasKey("separatorColor")) {
            sc = button.color(activity, "separatorColor", appearance.separatorColor)
          }

          var fontSize = appearance.itemFontSize
          if (button.hasKey("fontSize")) {
            fontSize = button.getDouble("fontSize").toFloat()
          }

          var iconSize = appearance.popMenuActionIconSize
          if (button.hasKey("iconSize")) {
            iconSize = PixelUtil.toPixelFromDIP(button.getDouble("iconSize"))
          }

          item {
            rightIconDrawable = if (appearance.rightIcon) drawable else null
            iconDrawable = if (!appearance.rightIcon) drawable else null
            iconColor = iconTint
            labelColor = textColor
            textAlign = if (params.hasKey("textAlign")) params.getString("textAlign") else null
            label = button.getString("text")
            showSeparator = sh > 0
            separatorHeight = sh.toInt()
            separatorColor = sc
            this.fontSize = fontSize
            this.iconSize = iconSize
            callback = {
              didDismissBySelectItem = true
              actionCallback.invoke(i)
            }
          }
        }
      }
    }

    activity.runOnUiThread {
      fun create(anchor: View) {
        var rect: Rect? = null
        if (params.hasKey("frame")) {
          val frame = params.getMap("frame")!!
          var y = frame.getDouble("y").toPixel()
          if (params.gravity() == Gravity.BOTTOM) {
            y += frame.getDouble("height").toPixel()
          }
          y += Helpers.getStatusBarHeight(activity)
          val r = frame.getDouble("x").toPixel() + frame.getDouble("width").toPixel()
          val b = frame.getDouble("y").toPixel() + frame.getDouble("height").toPixel()
          rect = Rect(
            frame.getDouble("x").toPixel(), y, r, b
          )
        }
        @ColorInt
        val overrideBackgroundColor: Int? = if (params.hasKey("backgroundColor")) params.color(
          activity, "backgroundColor", Color.parseColor("#1F000000")
        ) else null
        popupMenu.setOnDismissListener {
          appearance = originalAppearance.copy()
          if (!didDismissBySelectItem) actionCallback.invoke(null)
        }
        popupMenu.show(
          context = activity,
          anchor = anchor,
          location = rect,
          centered = params.bool("centered"),
          overrideBackgroundColor = overrideBackgroundColor,
        )
      }
      if (params.hasKey("frame")) {
        create(activity.findViewById(android.R.id.content))
      } else {
        Helpers.findView(activity, params)?.let(::create)
      }
    }
  }


}
