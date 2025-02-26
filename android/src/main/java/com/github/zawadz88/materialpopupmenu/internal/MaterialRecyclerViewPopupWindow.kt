package com.github.zawadz88.materialpopupmenu.internal

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.*
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.createAppCompatPopupWindow
import androidx.core.widget.PopupWindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.zawadz88.materialpopupmenu.appearance
import com.reactnativepopupmenu.R
import java.lang.reflect.Method
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private val dp16 = (Resources.getSystem().displayMetrics.density * 16).toInt()
private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
private val screenHeight = Resources.getSystem().displayMetrics.heightPixels

/**
 * A more Material version of [androidx.appcompat.widget.ListPopupWindow] based on [RecyclerView].
 *
 * Its width is a multiple of 56dp units with a minimum of 112dp and a maximum of 280dp
 * as stated in the <a href="https://material.io/guidelines/components/menus.html#menus-simple-menus">Material documentation</a>
 *
 * @see androidx.appcompat.widget.ListPopupWindow
 */
@SuppressLint("PrivateResource,RestrictedApi")
internal class MaterialRecyclerViewPopupWindow(
    private val context: Context,
    private val fixedContentWidthInPx: Int,
    private var popupMinWidth: Int,
    private var popupMaxWidth: Int,
    dropDownVerticalOffset: Int?,
    dropDownHorizontalOffset: Int?
) {

    @SuppressLint("DiscouragedPrivateApi")
    companion object {

        private const val TAG = "MaterialRVPopupWindow"
        private const val DEFAULT_BACKGROUND_DIM_AMOUNT = 0.3f

        private var clipToWindowEnabledMethod: Method? = null
        private var getMaxAvailableHeightMethod: Method? = null

        init {
            try {
                clipToWindowEnabledMethod = PopupWindow::class.java.getDeclaredMethod(
                    "setClipToScreenEnabled", Boolean::class.javaPrimitiveType
                )
            } catch (e: NoSuchMethodException) {
                Log.i(
                    TAG,
                    "Could not find method setClipToScreenEnabled() on PopupWindow. Oh well."
                )
            }

            try {
                getMaxAvailableHeightMethod = PopupWindow::class.java.getDeclaredMethod(
                    "getMaxAvailableHeight",
                    View::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            } catch (e: NoSuchMethodException) {
                Log.i(
                    TAG,
                    "Could not find method getMaxAvailableHeight(View, int, boolean)" + " on PopupWindow. Oh well."
                )
            }
        }
    }

    /**
     * The view that will be used to anchor this popup.
     */
    internal var anchorView: View? = null

    internal var adapter: PopupMenuAdapter? = null
        set(value) {
            val menuWidth = measureMenuSizeAndGetWidth(checkNotNull(value))
            if (fixedContentWidthInPx == 0) {
                updateContentWidth(menuWidth)
            }
            field = value
        }

    private var dropDownWidth = ViewGroup.LayoutParams.WRAP_CONTENT

    private var dropDownVerticalOffset: Int = 0

    private var dropDownHorizontalOffset: Int = 0

    private val tempRect = Rect()

    private val popup: PopupWindow

    private val popupWidthUnit: Int

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private val backgroundDimEnabled: Boolean

    private val backgroundDimAmount: Float

//  private val popupPaddingBottom: Int

//  private val popupPaddingTop: Int

    init {
        popup = createAppCompatPopupWindow(context)
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        popup.isFocusable = true

        if (popupMaxWidth == 0) {
          popupMaxWidth = appearance.maxWidth ?: context.resources.getDimensionPixelSize(R.dimen.mpm_popup_menu_max_width)
        }
        if (popupMinWidth == 0) {
            popupMinWidth = appearance.minWidth ?: context.resources.getDimensionPixelSize(R.dimen.mpm_popup_menu_min_width)
        }
        popupWidthUnit = context.resources.getDimensionPixelSize(R.dimen.mpm_popup_menu_width_unit)

        val a = context.obtainStyledAttributes(null, R.styleable.MaterialRecyclerViewPopupWindow)

        this.dropDownHorizontalOffset = dropDownHorizontalOffset ?: a.getDimensionPixelOffset(
            R.styleable.MaterialRecyclerViewPopupWindow_android_dropDownHorizontalOffset,
            0
        )
        this.dropDownVerticalOffset = dropDownVerticalOffset ?: a.getDimensionPixelOffset(
            R.styleable.MaterialRecyclerViewPopupWindow_android_dropDownVerticalOffset,
            0
        )
        backgroundDimEnabled =
            a.getBoolean(
                R.styleable.MaterialRecyclerViewPopupWindow_android_backgroundDimEnabled,
                false
            )
        backgroundDimAmount = a.getFloat(
            R.styleable.MaterialRecyclerViewPopupWindow_android_backgroundDimAmount,
            DEFAULT_BACKGROUND_DIM_AMOUNT
        )
//    popupPaddingBottom =
//      a.getDimensionPixelSize(R.styleable.MaterialRecyclerViewPopupWindow_mpm_paddingBottom, 0)
//    popupPaddingTop =
//      a.getDimensionPixelSize(R.styleable.MaterialRecyclerViewPopupWindow_mpm_paddingTop, 0)

        a.recycle()

        if (fixedContentWidthInPx != 0) {
            updateContentWidth(fixedContentWidthInPx)
        }
    }

    /**
     * Sets the width of the popupMenu window by the size of its content. The final width may be
     * larger to accommodate styled window dressing.

     * @param width Desired width of content in pixels.
     */
    private fun updateContentWidth(width: Int) {
        val popupBackground = popup.background
        dropDownWidth = if (popupBackground != null) {
            popupBackground.getPadding(tempRect)
            tempRect.left + tempRect.right + width
        } else {
            width
        }
    }

    /**
     * Show the popupMenu list. If the list is already showing, this method
     * will recalculate the popupMenu's size and position.
     */
    internal fun show(location: Rect?, centered: Boolean, @ColorInt overrideBackgroundColor: Int? = null) {
        checkNotNull(anchorView) { "Anchor view must be set!" }
        val height = buildDropDown(overrideBackgroundColor = overrideBackgroundColor)

        PopupWindowCompat.setWindowLayoutType(
            popup,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
        )

        val widthSpec = dropDownWidth
        when {
            popup.isShowing -> {
                popup.isOutsideTouchable = true
                popup.update(
                    anchorView,
                    dropDownHorizontalOffset,
                    dropDownVerticalOffset,
                    widthSpec,
                    if (height < 0) -1 else height
                )
            }
            location == null -> {
                popup.width = widthSpec
                popup.height = height
                setPopupClipToScreenEnabled(true)
                // use outside touchable to dismiss drop down when touching outside of it, so
                // only set this if the dropdown is not always visible
                popup.isOutsideTouchable = true
                PopupWindowCompat.showAsDropDown(
                    popup,
                    anchorView!!,
                    dropDownHorizontalOffset,
                    dropDownVerticalOffset,
                    appearance.popMenuGravity
                )
            }
            else -> {
                if (popup.contentView.measuredWidth == 0) {
                    popup.contentView.measure(
                        MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    )
                    popup.width = min(popup.contentView.measuredWidth, dropDownWidth)
                }
                val contentWidth = popup.width
                val contentHeight = popup.contentView.measuredHeight
                var left = max(dp16, min(location.left + dp16, screenWidth - contentWidth - dp16))
                if (centered) {
                  val x = location.left + location.width() / 2
                  left = x - popup.width / 2
                }
                val top = max(dp16, min(location.top + dp16, screenHeight - contentHeight - dp16))
                popup.showAtLocation(anchorView, appearance.popMenuGravity, left, top)
            }
        }

        if (backgroundDimEnabled) {
            addBackgroundDimming()
        }
    }

    /**
     * Dismiss the popupMenu window.
     */
    internal fun dismiss() {
        popup.dismiss()
        popup.contentView = null
    }

    /**
     * Sets a listener that is called when this popup window is dismissed.
     *
     * @param listener Listener that is called when this popup window is dismissed.
     */
    internal fun setOnDismissListener(listener: (() -> Unit)?) {
        if (listener != null) {
            popup.setOnDismissListener { listener.invoke() }
        } else {
            popup.setOnDismissListener(null)
        }
    }

    /**
     *
     * Builds the popupMenu window's content and returns the height the popupMenu
     * should have.

     * @return the content's height
     */
    private fun buildDropDown(@ColorInt overrideBackgroundColor: Int? = null): Int {
        var otherHeights = 0

        val dropDownList = View.inflate(context, R.layout.mpm_popup_menu, null) as RecyclerView
        dropDownList.also {
            it.adapter = adapter
            it.layoutManager = LinearLayoutManager(context)
            it.isFocusable = true
            it.isFocusableInTouchMode = true
        }

        val background = popup.background

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dropDownList.clipToOutline = true
            // Move the background from popup to RecyclerView for clipToOutline to take effect.
            dropDownList.background = background
            dropDownList.setBackgroundColor(overrideBackgroundColor ?: appearance.backgroundColor)
            // Remove background from popup itself to avoid overdraw.
            // This causes issues on Lollipop so we do it on M+ only (see issue #66 on GitHub).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                popup.setBackgroundDrawable(null)
            }
        }

        popup.contentView = dropDownList
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        popup.contentView.clipToOutline = true
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        popup.contentView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, appearance.cornerRadius)
            }
        }

        // getMaxAvailableHeight() subtracts the padding, so we put it back
        // to get the available height for the whole window.
        val padding: Int
        if (background != null) {
            background.getPadding(tempRect)
            padding = tempRect.top + tempRect.bottom

            // If we don't have an explicit vertical offset, determine one from
            // the window background so that content will line up.
            dropDownVerticalOffset -= tempRect.top
        } else {
            tempRect.setEmpty()
            padding = 0
        }

        if ((appearance.popMenuGravity and Gravity.BOTTOM) == Gravity.BOTTOM) {
            dropDownVerticalOffset += anchorView!!.height
        }

        // Max height available on the screen for a popupMenu.
        val ignoreBottomDecorations = popup.inputMethodMode == PopupWindow.INPUT_METHOD_NOT_NEEDED
        val maxHeight = getMaxAvailableHeight(
            anchorView!!, dropDownVerticalOffset,
            ignoreBottomDecorations
        )

        val listContent = measureHeightOfChildrenCompat(maxHeight - otherHeights)
        if (listContent > 0) {
            val listPadding = dropDownList.paddingTop + dropDownList.paddingBottom
            otherHeights += padding + listPadding
        }

        return listContent + otherHeights
    }

    /**
     * Measures the height of the given range of children (inclusive) and returns the height
     * with this ListView's padding and divider heights included. If maxHeight is provided, the
     * measuring will stop when the current height reaches maxHeight.

     * @param maxHeight                    The maximum height that will be returned (if all the
     * *                                     children don't fit in this value, this value will be
     * *                                     returned).
     * *
     * @return The height of this ListView with the given children.
     *
     * @see androidx.appcompat.widget.DropDownListView.measureHeightOfChildrenCompat
     */
    private fun measureHeightOfChildrenCompat(maxHeight: Int): Int {

        val parent = FrameLayout(context)
        val widthMeasureSpec =
            MeasureSpec.makeMeasureSpec(dropDownWidth, MeasureSpec.EXACTLY)

        // Include the padding of the list
        var returnedHeight = 0

        val count = adapter?.itemCount ?: 0
        for (i in 0 until count) {
            val positionType = adapter!!.getItemViewType(i)

            val vh = adapter!!.createViewHolder(parent, positionType)
            adapter!!.bindViewHolder(vh, i)
            val itemView = vh.itemView

            // Compute child height spec
            val heightMeasureSpec: Int
            var childLp: ViewGroup.LayoutParams? = itemView.layoutParams

            if (childLp == null) {
                childLp = generateDefaultLayoutParams()
                itemView.layoutParams = childLp
            }

            heightMeasureSpec = if (childLp.height > 0) {
                MeasureSpec.makeMeasureSpec(
                    childLp.height,
                    MeasureSpec.EXACTLY
                )
            } else {
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }
            itemView.measure(widthMeasureSpec, heightMeasureSpec)
            // Since this view was measured directly against the parent measure
            // spec, we must measure it again before reuse.
            itemView.forceLayout()

            val marginLayoutParams = childLp as? ViewGroup.MarginLayoutParams
            val topMargin = marginLayoutParams?.topMargin ?: 0
            val bottomMargin = marginLayoutParams?.bottomMargin ?: 0
            val verticalMargin = topMargin + bottomMargin

            returnedHeight += itemView.measuredHeight + verticalMargin

            if (returnedHeight >= maxHeight) {
                // We went over, figure out which height to return.  If returnedHeight >
                // maxHeight, then the i'th position did not fit completely.
                return maxHeight
            }
        }

        // At this point, we went through the range of children, and they each
        // completely fit, so return the returnedHeight
        return returnedHeight
    }

    private fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setPopupClipToScreenEnabled(clip: Boolean) {
        clipToWindowEnabledMethod?.let {
            try {
                it.invoke(popup, clip)
            } catch (e: Exception) {
                Log.i(TAG, "Could not call setClipToScreenEnabled() on PopupWindow. Oh well.")
            }
        }
    }

    private fun getMaxAvailableHeight(
        anchor: View,
        yOffset: Int,
        ignoreBottomDecorations: Boolean
    ): Int {
        getMaxAvailableHeightMethod?.let {
            try {
                return it.invoke(
                    popup, anchor, yOffset,
                    ignoreBottomDecorations
                ) as Int
            } catch (e: Exception) {
                Log.i(
                    TAG,
                    "Could not call getMaxAvailableHeightMethod(View, int, boolean)" + " on PopupWindow. Using the public version."
                )
            }
        }
        return popup.getMaxAvailableHeight(anchor, yOffset)
    }

    /**
     * @see androidx.appcompat.view.menu.MenuPopup.measureIndividualMenuWidth
     */
    private fun measureMenuSizeAndGetWidth(adapter: PopupMenuAdapter): Int {
        adapter.setupIndices()
        val parent = FrameLayout(context)
        var menuWidth = popupMinWidth

        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val count = adapter.itemCount
        for (i in 0 until count) {
            val positionType = adapter.getItemViewType(i)

            val vh = adapter.createViewHolder(parent, positionType)
            adapter.bindViewHolder(vh, i)
            val itemView = vh.itemView
            itemView.measure(widthMeasureSpec, heightMeasureSpec)

            val itemWidth = itemView.measuredWidth
            if (itemWidth >= popupMaxWidth) {
                return popupMaxWidth
            } else if (itemWidth > menuWidth) {
                menuWidth = itemWidth
            }
        }

        menuWidth = ceil(menuWidth.toDouble() / popupWidthUnit).toInt() * popupWidthUnit

        return menuWidth
    }

    private fun addBackgroundDimming() {
        val decorView = popup.contentView.rootView
        val layoutParams = decorView.layoutParams as WindowManager.LayoutParams
        layoutParams.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
        layoutParams.dimAmount = backgroundDimAmount
        windowManager.updateViewLayout(decorView, layoutParams)
    }
}
