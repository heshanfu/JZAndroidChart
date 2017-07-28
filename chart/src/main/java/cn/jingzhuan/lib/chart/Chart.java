package cn.jingzhuan.lib.chart;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;

import cn.jingzhuan.chart.R;
import cn.jingzhuan.lib.chart.component.Axis;
import cn.jingzhuan.lib.chart.component.AxisX;
import cn.jingzhuan.lib.chart.component.AxisY;
import cn.jingzhuan.lib.chart.event.OnViewportChangeListener;

/**
 * Created by Donglua on 17/7/17.
 */

public abstract class Chart extends View {

    protected AxisY mAxisLeft = new AxisY(AxisY.LEFT_INSIDE);
    protected AxisY mAxisRight = new AxisY(AxisY.RIGHT_INSIDE);
    protected AxisX mAxisTop = new AxisX(AxisX.TOP);
    protected AxisX mAxisBottom = new AxisX(AxisX.BOTTOM);

    // State objects and values related to gesture tracking.
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetectorCompat mGestureDetector;
    private OverScroller mScroller;
    private Zoomer mZoomer;
    private PointF mZoomFocalPoint = new PointF();
    private RectF mScrollerStartViewport = new RectF(); // Used only for zooms and flings.

    private OnViewportChangeListener mOnViewportChangeListener;
    private boolean mScaleXEnable = true;

    /**
     * The current viewport. This rectangle represents the currently visible lib domain
     * and range. The currently visible lib X values are from this rectangle's left to its right.
     * The currently visible lib Y values are from this rectangle's top to its bottom.
     * <p>
     * Note that this rectangle's top is actually the smaller Y value, and its bottom is the larger
     * Y value. Since the lib is drawn onscreen in such a way that lib Y values increase
     * towards the top of the screen (decreasing pixel Y positions), this rectangle's "top" is drawn
     * above this rectangle's "bottom" value.
     *
     * @see #mContentRect
     */
    private Viewport mCurrentViewport = new Viewport();

    /**
     * The current destination rectangle (in pixel coordinates) into which the lib data should
     * be drawn. Chart labels are drawn outside this area.
     *
     * @see #mCurrentViewport
     */
    private Rect mContentRect = new Rect();

    /**
     * The scaling factor for a single zoom 'step'.
     *
     * @see #zoomIn()
     * @see #zoomOut()
     */
    private static final float ZOOM_AMOUNT = 0.25f;

    private Point mSurfaceSizeBuffer = new Point();


    // Edge effect / overscroll tracking objects.
    private EdgeEffectCompat mEdgeEffectTop;
    private EdgeEffectCompat mEdgeEffectBottom;
    private EdgeEffectCompat mEdgeEffectLeft;
    private EdgeEffectCompat mEdgeEffectRight;

    private boolean mEdgeEffectTopActive;
    private boolean mEdgeEffectBottomActive;
    private boolean mEdgeEffectLeftActive;
    private boolean mEdgeEffectRightActive;


    public Chart(Context context) {
        this(context, null, 0);
    }

    public Chart(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Chart(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Chart(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr);
    }


    private void init(Context context, AttributeSet attrs, int defStyleAttr) {

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.Chart, defStyleAttr, defStyleAttr);

        try {
            List<Axis> axisList = new ArrayList<>(4);
            axisList.add(mAxisLeft);
            axisList.add(mAxisRight);
            axisList.add(mAxisTop);
            axisList.add(mAxisBottom);

            float labelTextSize = a.getDimension(R.styleable.Chart_labelTextSize, 28);
            float labelSeparation = a.getDimensionPixelSize(R.styleable.Chart_labelSeparation, 10);
            float gridThickness = a.getDimension(R.styleable.Chart_gridThickness, 2);
            float axisThickness = a.getDimension(R.styleable.Chart_axisThickness, 2);
            int gridColor = a.getColor(R.styleable.Chart_gridColor, Color.GRAY);
            int axisColor = a.getColor(R.styleable.Chart_axisColor, Color.GRAY);
            int labelTextColor = a.getColor(R.styleable.Chart_labelTextColor, Color.GRAY);

            for (Axis axis : axisList) {
                axis.setLabelTextSize(labelTextSize);
                axis.setLabelTextColor(labelTextColor);
                axis.setLabelSeparation(labelSeparation);
                axis.setGridColor(gridColor);
                axis.setGridThickness(gridThickness);
                axis.setAxisColor(axisColor);
                axis.setAxisThickness(axisThickness);
            }

        } finally {
            a.recycle();
        }

        initChart();

        setupInteractions(context);

        setupEdgeEffect(context);
    }

    public abstract void initChart();

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mContentRect.set(
                getPaddingLeft() + mAxisLeft.getMaxLabelWidth() + (mAxisLeft.isInside() ? 0 : mAxisLeft.getLabelSeparation()),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom() - mAxisBottom.getLabelHeight() - mAxisBottom.getLabelSeparation());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minChartSize = getResources().getDimensionPixelSize(R.dimen.jz_chart_min_size);
        setMeasuredDimension(
                Math.max(getSuggestedMinimumWidth(),
                        resolveSize(minChartSize + getPaddingLeft()
                                        + (mAxisLeft.isInside() ? 0 : mAxisLeft.getMaxLabelWidth())
                                        + (mAxisLeft.isInside() ? 0 : mAxisLeft.getLabelSeparation())
                                        + getPaddingRight(),
                                widthMeasureSpec)),
                Math.max(getSuggestedMinimumHeight(),
                        resolveSize(minChartSize + getPaddingTop()
                                        + (mAxisBottom.isInside() ? 0 : mAxisBottom.getLabelHeight())
                                        + (mAxisBottom.isInside() ? 0 : mAxisBottom.getLabelSeparation())
                                        + getPaddingBottom(),
                                heightMeasureSpec)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawAxis(canvas);

        // Clips the next few drawing operations to the content area
        int clipRestoreCount = canvas.save();
        canvas.clipRect(mContentRect);

        render(canvas);

        drawEdgeEffectsUnclipped(canvas);

        // Removes clipping rectangle
        canvas.restoreToCount(clipRestoreCount);
    }

    protected abstract void drawAxis(Canvas canvas);

    protected abstract void render(Canvas canvas);

    public Viewport getCurrentViewport() {
        return mCurrentViewport;
    }

    public Rect getContentRect() {
        return mContentRect;
    }


    //    -------- -------- --------


    private void setupInteractions(Context context) {

        mScaleGestureDetector = new ScaleGestureDetector(context, mScaleGestureListener);
        mGestureDetector = new GestureDetectorCompat(context, mGestureListener);

        mScroller = new OverScroller(context);
        mZoomer = new Zoomer(context);
    }

    /**
     * Finds the lib point (i.e. within the lib's domain and range) represented by the
     * given pixel coordinates, if that pixel is within the lib region described by
     * {@link #mContentRect}. If the point is found, the "dest" argument is set to the point and
     * this function returns true. Otherwise, this function returns false and "dest" is unchanged.
     */
    private boolean hitTest(float x, float y, PointF dest) {
        if (!mContentRect.contains((int) x, (int) y)) {
            return false;
        }

        dest.set(mCurrentViewport.left
                        + mCurrentViewport.width()
                        * (x - mContentRect.left) / mContentRect.width(),
                mCurrentViewport.top
                        + mCurrentViewport.height()
                        * (y - mContentRect.bottom) / -mContentRect.height());
        return true;
    }


    /**
     * The scale listener, used for handling multi-finger scale gestures.
     */
    private final ScaleGestureDetector.OnScaleGestureListener mScaleGestureListener
            = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        /**
         * This is the active focal point in terms of the viewport. Could be a local
         * variable but kept here to minimize per-frame allocations.
         */
        private PointF viewportFocus = new PointF();
        private float lastSpanX;
//        private float lastSpanY;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            lastSpanX = ScaleGestureDetectorCompat.getCurrentSpanX(scaleGestureDetector);
//            lastSpanY = ScaleGestureDetectorCompat.getCurrentSpanY(scaleGestureDetector);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            super.onScaleEnd(detector);

            if (mScaleXEnable) {
                notifyViewportChange();
            }
        }

        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {

            if (!mScaleXEnable) return false;

            float spanX = ScaleGestureDetectorCompat.getCurrentSpanX(scaleGestureDetector);
            float spanY = ScaleGestureDetectorCompat.getCurrentSpanY(scaleGestureDetector);

            float newWidth = lastSpanX / spanX * mCurrentViewport.width();
//            float newHeight = lastSpanY / spanY * mCurrentViewport.height();

            if (newWidth < mCurrentViewport.width() && mCurrentViewport.width() < 0.2) {
                return true;
            }

            float focusX = scaleGestureDetector.getFocusX();
            float focusY = scaleGestureDetector.getFocusY();
            hitTest(focusX, focusY, viewportFocus);

            mCurrentViewport.left = viewportFocus.x
                    - newWidth * (focusX - mContentRect.left)
                    / mContentRect.width();
//            mCurrentViewport.set(
//                    viewportFocus.x
//                            - newWidth * (focusX - mContentRect.left)
//                            / mContentRect.width(),
//                    viewportFocus.y
//                            - newHeight * (mContentRect.bottom - focusY)
//                            / mContentRect.height(),
//                    0,
//                    0);
            mCurrentViewport.right = mCurrentViewport.left + newWidth;
//            mCurrentViewport.bottom = mCurrentViewport.top + newHeight;
            mCurrentViewport.constrainViewport();
            ViewCompat.postInvalidateOnAnimation(Chart.this);

            lastSpanX = spanX;
//            lastSpanY = spanY;

            Log.d("Scale", "viewport width = " + getCurrentViewport().width());

            return true;
        }
    };


    /**
     * The gesture listener, used for handling simple gestures such as double touches, scrolls,
     * and flings.
     */
    private final GestureDetector.SimpleOnGestureListener mGestureListener
            = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            releaseEdgeEffects();
            mScrollerStartViewport.set(mCurrentViewport);
            mScroller.forceFinished(true);
            ViewCompat.postInvalidateOnAnimation(Chart.this);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            mZoomer.forceFinished(true);
            if (hitTest(e.getX(), e.getY(), mZoomFocalPoint)) {
                mZoomer.startZoom(ZOOM_AMOUNT);
            }
            ViewCompat.postInvalidateOnAnimation(Chart.this);
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // Scrolling uses math based on the viewport (as opposed to math using pixels).
            /**
             * Pixel offset is the offset in screen pixels, while viewport offset is the
             * offset within the current viewport. For additional information on surface sizes
             * and pixel offsets, see the docs for {@link computeScrollSurfaceSize()}. For
             * additional information about the viewport, see the comments for
             * {@link mCurrentViewport}.
             */
            float viewportOffsetX = distanceX * mCurrentViewport.width() / mContentRect.width();
            float viewportOffsetY = -distanceY * mCurrentViewport.height() / mContentRect.height();
            computeScrollSurfaceSize(mSurfaceSizeBuffer);
            int scrolledX = (int) (mSurfaceSizeBuffer.x
                    * (mCurrentViewport.left + viewportOffsetX - Viewport.AXIS_X_MIN)
                    / (Viewport.AXIS_X_MAX - Viewport.AXIS_X_MIN));
            int scrolledY = (int) (mSurfaceSizeBuffer.y
                    * (Viewport.AXIS_Y_MAX - mCurrentViewport.bottom - viewportOffsetY)
                    / (Viewport.AXIS_Y_MAX - Viewport.AXIS_Y_MIN));
            boolean canScrollX = mCurrentViewport.left > Viewport.AXIS_X_MIN
                    || mCurrentViewport.right < Viewport.AXIS_X_MAX;
            boolean canScrollY = mCurrentViewport.top > Viewport.AXIS_Y_MIN
                    || mCurrentViewport.bottom < Viewport.AXIS_Y_MAX;
            setViewportBottomLeft(
                    mCurrentViewport.left + viewportOffsetX,
                    mCurrentViewport.bottom + viewportOffsetY);

            if (canScrollX && scrolledX < 0) {
                mEdgeEffectLeft.onPull(scrolledX / (float) mContentRect.width());
                mEdgeEffectLeftActive = true;
            }
            if (canScrollY && scrolledY < 0) {
                mEdgeEffectTop.onPull(scrolledY / (float) mContentRect.height());
                mEdgeEffectTopActive = true;
            }
            if (canScrollX && scrolledX > mSurfaceSizeBuffer.x - mContentRect.width()) {
                mEdgeEffectRight.onPull((scrolledX - mSurfaceSizeBuffer.x + mContentRect.width())
                        / (float) mContentRect.width());
                mEdgeEffectRightActive = true;
            }
            if (canScrollY && scrolledY > mSurfaceSizeBuffer.y - mContentRect.height()) {
                mEdgeEffectBottom.onPull((scrolledY - mSurfaceSizeBuffer.y + mContentRect.height())
                        / (float) mContentRect.height());
                mEdgeEffectBottomActive = true;
            }

            Log.d("mGestureListener", "onScroll");

            notifyViewportChange();

            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            fling((int) -velocityX, (int) -velocityY);
            notifyViewportChange();

            return true;
        }

    };

    private void notifyViewportChange() {
        if (mOnViewportChangeListener != null) {
            mOnViewportChangeListener.onViewportChange(mCurrentViewport);
        }
    }

    private void fling(int velocityX, int velocityY) {
        releaseEdgeEffects();
        // Flings use math in pixels (as opposed to math based on the viewport).
        computeScrollSurfaceSize(mSurfaceSizeBuffer);
        mScrollerStartViewport.set(mCurrentViewport);
        int startX = (int) (mSurfaceSizeBuffer.x * (mScrollerStartViewport.left - Viewport.AXIS_X_MIN) / (
                Viewport.AXIS_X_MAX - Viewport.AXIS_X_MIN));
        int startY = (int) (mSurfaceSizeBuffer.y * (Viewport.AXIS_Y_MAX - mScrollerStartViewport.bottom) / (
                Viewport.AXIS_Y_MAX - Viewport.AXIS_Y_MIN));
        mScroller.forceFinished(true);
        mScroller.fling(
                startX,
                startY,
                velocityX,
                velocityY,
                0, mSurfaceSizeBuffer.x - mContentRect.width(),
                0, mSurfaceSizeBuffer.y - mContentRect.height(),
                mContentRect.width() / 2,
                mContentRect.height() / 2);
        ViewCompat.postInvalidateOnAnimation(this);

        Log.d("mGestureListener", "onFling");
    }


    /**
     * Computes the current scrollable surface size, in pixels. For example, if the entire lib
     * area is visible, this is simply the current size of {@link #mContentRect}. If the lib
     * is zoomed in 200% in both directions, the returned size will be twice as large horizontally
     * and vertically.
     */
    private void computeScrollSurfaceSize(Point out) {
        out.set((int) (mContentRect.width() * (Viewport.AXIS_X_MAX - Viewport.AXIS_X_MIN)
                        / mCurrentViewport.width()),
                (int) (mContentRect.height() * (Viewport.AXIS_Y_MAX - Viewport.AXIS_Y_MIN)
                        / mCurrentViewport.height()));
    }

    /**
     * Smoothly zooms the lib in one step.
     */
    public void zoomIn() {
        mScrollerStartViewport.set(mCurrentViewport);
        mZoomer.forceFinished(true);
        mZoomer.startZoom(ZOOM_AMOUNT);
        mZoomFocalPoint.set(
                (mCurrentViewport.right + mCurrentViewport.left) / 2,
                (mCurrentViewport.bottom + mCurrentViewport.top) / 2);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly zooms the lib out one step.
     */
    public void zoomOut() {
        mScrollerStartViewport.set(mCurrentViewport);
        mZoomer.forceFinished(true);
        mZoomer.startZoom(-ZOOM_AMOUNT);
        mZoomFocalPoint.set(
                (mCurrentViewport.right + mCurrentViewport.left) / 2,
                (mCurrentViewport.bottom + mCurrentViewport.top) / 2);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();

        boolean needsInvalidate = false;

        if (mScroller.computeScrollOffset()) {
            // The scroller isn't finished, meaning a fling or programmatic pan operation is
            // currently active.

            computeScrollSurfaceSize(mSurfaceSizeBuffer);
            int currX = mScroller.getCurrX();
            int currY = mScroller.getCurrY();

            boolean canScrollX = (mCurrentViewport.left > Viewport.AXIS_X_MIN
                    || mCurrentViewport.right < Viewport.AXIS_X_MAX);
            boolean canScrollY = (mCurrentViewport.top > Viewport.AXIS_Y_MIN
                    || mCurrentViewport.bottom < Viewport.AXIS_Y_MAX);

            if (canScrollX
                    && currX < 0
                    && mEdgeEffectLeft.isFinished()
                    && !mEdgeEffectLeftActive) {
                mEdgeEffectLeft.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectLeftActive = true;
                needsInvalidate = true;
            } else if (canScrollX
                    && currX > (mSurfaceSizeBuffer.x - mContentRect.width())
                    && mEdgeEffectRight.isFinished()
                    && !mEdgeEffectRightActive) {
                mEdgeEffectRight.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectRightActive = true;
                needsInvalidate = true;
            }

            if (canScrollY
                    && currY < 0
                    && mEdgeEffectTop.isFinished()
                    && !mEdgeEffectTopActive) {
                mEdgeEffectTop.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectTopActive = true;
                needsInvalidate = true;
            } else if (canScrollY
                    && currY > (mSurfaceSizeBuffer.y - mContentRect.height())
                    && mEdgeEffectBottom.isFinished()
                    && !mEdgeEffectBottomActive) {
                mEdgeEffectBottom.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectBottomActive = true;
                needsInvalidate = true;
            }

            float currXRange = Viewport.AXIS_X_MIN + (Viewport.AXIS_X_MAX - Viewport.AXIS_X_MIN)
                    * currX / mSurfaceSizeBuffer.x;
            float currYRange = Viewport.AXIS_Y_MAX - (Viewport.AXIS_Y_MAX - Viewport.AXIS_Y_MIN)
                    * currY / mSurfaceSizeBuffer.y;
            setViewportBottomLeft(currXRange, currYRange);
        }

        if (mZoomer.computeZoom()) {
            // Performs the zoom since a zoom is in progress (either programmatically or via
            // double-touch).
            float newWidth = (1f - mZoomer.getCurrZoom()) * mScrollerStartViewport.width();
            float newHeight = (1f - mZoomer.getCurrZoom()) * mScrollerStartViewport.height();
            float pointWithinViewportX = (mZoomFocalPoint.x - mScrollerStartViewport.left)
                    / mScrollerStartViewport.width();
            float pointWithinViewportY = (mZoomFocalPoint.y - mScrollerStartViewport.top)
                    / mScrollerStartViewport.height();
            mCurrentViewport.set(
                    mZoomFocalPoint.x - newWidth * pointWithinViewportX,
                    mZoomFocalPoint.y - newHeight * pointWithinViewportY,
                    mZoomFocalPoint.x + newWidth * (1 - pointWithinViewportX),
                    mZoomFocalPoint.y + newHeight * (1 - pointWithinViewportY));
            mCurrentViewport.constrainViewport();
            needsInvalidate = true;
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }


    /**
     * Sets the current viewport (defined by {@link #mCurrentViewport}) to the given
     * X and Y positions. Note that the Y value represents the topmost pixel position, and thus
     * the bottom of the {@link #mCurrentViewport} rectangle. For more details on why top and
     * bottom are flipped, see {@link #mCurrentViewport}.
     */
    private void setViewportBottomLeft(float x, float y) {
        /**
         * Constrains within the scroll range. The scroll range is simply the viewport extremes
         * (AXIS_X_MAX, etc.) minus the viewport size. For example, if the extrema were 0 and 10,
         * and the viewport size was 2, the scroll range would be 0 to 8.
         */

        float curWidth = mCurrentViewport.width();
        float curHeight = mCurrentViewport.height();
        x = Math.max(Viewport.AXIS_X_MIN, Math.min(x, Viewport.AXIS_X_MAX - curWidth));
        y = Math.max(Viewport.AXIS_Y_MIN + curHeight, Math.min(y, Viewport.AXIS_Y_MAX));

        mCurrentViewport.set(x, y - curHeight, x + curWidth, y);
        ViewCompat.postInvalidateOnAnimation(this);
    }


    /**
     * Sets the lib's current viewport.
     *
     * @see #getCurrentViewport()
     */
    public void setCurrentViewport(RectF viewport) {
        mCurrentViewport = new Viewport(viewport);
        mCurrentViewport.constrainViewport();
        ViewCompat.postInvalidateOnAnimation(this);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retVal = mScaleGestureDetector.onTouchEvent(event);
        retVal = mGestureDetector.onTouchEvent(event) || retVal;
        return retVal || super.onTouchEvent(event);
    }


    protected void setupEdgeEffect(Context context) {

        // Sets up edge effects
        mEdgeEffectLeft = new EdgeEffectCompat(context);
        mEdgeEffectTop = new EdgeEffectCompat(context);
        mEdgeEffectRight = new EdgeEffectCompat(context);
        mEdgeEffectBottom = new EdgeEffectCompat(context);
    }


    /**
     * Draws the overscroll "glow" at the four edges of the lib region, if necessary. The edges
     * of the lib region are stored in {@link #mContentRect}.
     *
     * @see EdgeEffectCompat
     */
    private void drawEdgeEffectsUnclipped(Canvas canvas) {
        // The methods below rotate and translate the canvas as needed before drawing the glow,
        // since EdgeEffectCompat always draws a top-glow at 0,0.

        boolean needsInvalidate = false;

        if (!mEdgeEffectTop.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.left, mContentRect.top);
            mEdgeEffectTop.setSize(mContentRect.width(), mContentRect.height());
            if (mEdgeEffectTop.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectBottom.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(2 * mContentRect.left - mContentRect.right, mContentRect.bottom);
            canvas.rotate(180, mContentRect.width(), 0);
            mEdgeEffectBottom.setSize(mContentRect.width(), mContentRect.height());
            if (mEdgeEffectBottom.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectLeft.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.left, mContentRect.bottom);
            canvas.rotate(-90, 0, 0);
            mEdgeEffectLeft.setSize(mContentRect.height(), mContentRect.width());
            if (mEdgeEffectLeft.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectRight.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.right, mContentRect.top);
            canvas.rotate(90, 0, 0);
            mEdgeEffectRight.setSize(mContentRect.height(), mContentRect.width());
            if (mEdgeEffectRight.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }


    private void releaseEdgeEffects() {
        mEdgeEffectLeftActive
                = mEdgeEffectTopActive
                = mEdgeEffectRightActive
                = mEdgeEffectBottomActive
                = false;
        mEdgeEffectLeft.onRelease();
        mEdgeEffectTop.onRelease();
        mEdgeEffectRight.onRelease();
        mEdgeEffectBottom.onRelease();
    }


    public AxisY getAxisLeft() {
        return mAxisLeft;
    }

    public AxisY getAxisRight() {
        return mAxisRight;
    }

    public AxisX getAxisTop() {
        return mAxisTop;
    }

    public AxisX getAxisBottom() {
        return mAxisBottom;
    }

    public void setOnScaleListener(OnViewportChangeListener onViewportChangeListener) {
        this.mOnViewportChangeListener = onViewportChangeListener;
    }

    public boolean isScaleXEnable() {
        return mScaleXEnable;
    }

    public void setScaleXEnable(boolean mScaleXEnable) {
        this.mScaleXEnable = mScaleXEnable;
    }
}