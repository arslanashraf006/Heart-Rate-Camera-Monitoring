package com.example.heartratepoc

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Created by jcs on 13/2/18.
 */
class HeartbeatView : View {
    constructor(context: Context, attr: AttributeSet?) : super(context, attr) {
        greenBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.green_icon)
        redBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.red_icon)
    }

    constructor(context: Context) : super(context) {
        greenBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.green_icon)
        redBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.red_icon)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(parentWidth, parentHeight)
    }

    override fun onDraw(canvas: Canvas) {
        if (canvas == null) throw NullPointerException()
        var bitmap: Bitmap? = null
        bitmap =
            if (MainActivity().getCurrent() === MainActivity.TYPE.GREEN) greenBitmap else redBitmap
        val bitmapX = bitmap!!.width / 2
        val bitmapY = bitmap.height / 2
        val parentX = parentWidth / 2
        val parentY = parentHeight / 2
        val centerX = parentX - bitmapX
        val centerY = parentY - bitmapY
        Companion.matrix.reset()
        Companion.matrix.postTranslate(centerX.toFloat(), centerY.toFloat())
        canvas.drawBitmap(bitmap, Companion.matrix, paint)
    }

    companion object {
        private val matrix = Matrix()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var greenBitmap: Bitmap? = null
        private var redBitmap: Bitmap? = null
        private var parentWidth = 0
        private var parentHeight = 0
    }
}