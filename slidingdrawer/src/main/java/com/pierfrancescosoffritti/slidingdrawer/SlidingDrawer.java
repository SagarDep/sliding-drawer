package com.pierfrancescosoffritti.slidingdrawer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntDef;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;

public class SlidingDrawer extends LinearLayout {

    private static final byte UP = 0;
    private static final byte DOWN = 1;
    private static final byte NONE = 2;
    @IntDef({UP, DOWN, NONE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface SlidingDirection {}

    public static final int EXPANDED = 0;
    public static final int COLLAPSED = 1;
    public static final int SLIDING = 2;
    @IntDef({EXPANDED, COLLAPSED, SLIDING})
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {}

    private @State int state = COLLAPSED;

    private final ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
    private final int touchSlop = viewConfiguration.getScaledTouchSlop();

    // view that will slide
    private View slidableView;

    // current slide value, between 1.0 and 0.0 (1.0 = EXPANDED, 0.0 = COLLAPSED)
    private float currentSlide;

    // only view sensible to vertical dragging
    private View draggableView;

    // The fade color used for the panel covered by the slider.
    private final static int mCoveredFadeColor = 0x99000000;

    // max value by which sliding view can slide.
    private int maxSlide;
    private final int minSlide = 0;

    private boolean isSliding;
    private boolean canSlide = false;

    // distance between the view's edge and the yDown coordinate
    private float dY;
    private float yDown;

    private final Drawable shadowDrawable;

    private int shadowLength;

    private final Set<OnSlideListener> listeners;

    public SlidingDrawer(Context context) {
        this(context, null);
    }

    public SlidingDrawer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingDrawer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        applyAttributes(attrs);

        listeners = new HashSet<>();

        shadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.above_shadow);
        setWillNotDraw(false);
    }

    private void applyAttributes(AttributeSet attrs) {
        TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SlidingDrawer, 0, 0);

        try {
            shadowLength = typedArray.getDimensionPixelSize(R.styleable.SlidingDrawer_shadow_length, 10);
        } finally {
            typedArray.recycle();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if(draggableView == null)
            throw new IllegalStateException("draggableView == null");

        final int action = MotionEventCompat.getActionMasked(event);

        // stop sliding and let the child handle the touch event
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            isSliding = false;
            canSlide = false;

            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                // save touch coordinates
                float xDown = event.getRawX();
                yDown = event.getRawY();

                final int[] viewCoordinates = new int[2];
                draggableView.getLocationInWindow(viewCoordinates);

                final float viewX = viewCoordinates[0];
                final float viewWidth = draggableView.getWidth();
                final float viewY = viewCoordinates[1];
                final float viewHeight = draggableView.getHeight();

                // slidableView can slide only if the ACTION_DOWN event is within draggableView bounds
                canSlide = !(xDown < viewX || xDown > viewX + viewWidth || yDown < viewY || yDown > viewY + viewHeight);

                if(!canSlide)
                    return false;

                yDown = event.getY();

                dY = slidableView.getY() - yDown;

                // intercept only if sliding
                return isSliding;
            }
            case MotionEvent.ACTION_MOVE: {
                if (isSliding)
                    return true;
                if(!canSlide)
                    return false;

                // start sliding only if the user dragged for more than touchSlop
                final float diff = Math.abs(event.getY() - yDown);

                if (diff > touchSlop/2) {
                    // Start sliding
                    isSliding = true;
                    return true;
                }
                break;
            }
        }

        // In general, we don't want to intercept touch events. They should be handled by the child view.
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float eventY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                if(isSliding && state != EXPANDED && state != COLLAPSED)
                    completeSlide(currentSlide, eventY > yDown ? DOWN : UP);

                canSlide = false;
                isSliding = false;
                break;
            case MotionEvent.ACTION_MOVE:

                if(!isSliding || !canSlide)
                    return false;

                // stay within the bounds (maxSlide and 0)
                if(eventY + dY > maxSlide)
                    dY = maxSlide - eventY;
                else if(eventY + dY < minSlide)
                    dY = -eventY;

                updateSliding(normalizeSlide(eventY + dY));
                break;
        }
        return true;
    }

    private void completeSlide(float currentSlide, @SlidingDirection int direction) {
        float finalY = -1;

        switch (direction) {
            case UP:
                if(currentSlide > 0.1)
                    finalY = minSlide;
                else
                    finalY = maxSlide;
                break;
            case DOWN:
                if(currentSlide < 0.9)
                    finalY = maxSlide;
                else
                    finalY = minSlide;
                break;
            // handle the single touch scenario
            case NONE:
                if(currentSlide == 1)
                    finalY = minSlide;
                else if(currentSlide == 0)
                    finalY = maxSlide;
                break;
        }

        if(finalY == -1)
            return;

        slideTo(normalizeSlide(finalY));
    }

    private float normalizeSlide(float currentY) {
        // currentSlide_Normalized : x = 1 : maxSliding
        return Math.abs(currentY - maxSlide) / maxSlide;
    }

    /**
     * always use this method to update the position of the sliding view.
     * @param newPositionNormalized new view position, normalized
     */
    private void updateSliding(float newPositionNormalized) {
        currentSlide = newPositionNormalized;

        state = currentSlide == 1 ? EXPANDED : currentSlide == 0 ? COLLAPSED : SLIDING;

        float slideY = Math.abs((currentSlide * maxSlide) - maxSlide);

        slidableView.setY(slideY);
        invalidate();

        notifyListeners(currentSlide);
    }

    /**
     * Ask all children to measure themselves and compute the measurement of this
     * layout based on the children.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();

        if (count != 2)
            throw new IllegalStateException("SlidingDrawer must have exactly 2 children.");

        initSlidingChild();

        // Measurement will ultimately be computing these values.
        int maxHeight = 0;
        int maxWidth = 0;
        int childState = 0;

        // Iterate through all children, measuring them and computing our dimensions
        // from their size.
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE)
                continue;

            // Measure the child.
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);

            // Update our size information based on the layout params.  Children
            // that asked to be positioned on the left or right go in those gutters.
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);

            maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            childState = combineMeasuredStates(childState, child.getMeasuredState());
        }

        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        // Report our final dimensions.
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec, childState << MEASURED_HEIGHT_STATE_SHIFT));
    }

    private void initSlidingChild() {
        slidableView = getChildAt(1);

        maxSlide = getChildAt(0).getHeight();

        //checkPadding(slidableView);
        checkPadding2(slidableView);
    }

    private void checkPadding2(View slidableView) {
        String tag = (String) slidableView.getTag();
        System.out.println(tag);

        if(tag != null && tag.equals("relative"))
            addPadding(slidableView);

        if(!(slidableView instanceof ViewGroup))
            return;

        ViewGroup root = (ViewGroup) slidableView;

        for (int i=0; i<root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            checkPadding2(child);
        }
    }

    private void checkPadding(View slidableView) {
        if(!(slidableView instanceof ViewGroup)) {
            addPadding(slidableView);
            return;
        }

        ViewGroup root = (ViewGroup) slidableView;

        if(isLastRoot(root)) {
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child instanceof RecyclerView) // && i == root.getChildCount()-1
                    return;
            }
            addPadding(root);
        } else {
            for (int i = 0; i < root.getChildCount(); i++) {
                checkPadding(root.getChildAt(i));
            }
        }
    }

    private void addPadding(View root) {
        System.out.println("paddig");
        int top = root.getPaddingTop();
        int left = root.getPaddingLeft();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();

        bottom = maxSlide;
        root.setPadding(left, top, right, bottom);
    }

    private boolean isLastRoot(ViewGroup root) {
        boolean result = true;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof ViewGroup && ((ViewGroup) root.getChildAt(i)).getChildCount() > 0)
                result = false;

            if(root.getChildAt(i) instanceof RecyclerView)
                result = true;
        }


        return result;
    }

    private final Rect tmpContainerRect = new Rect();
    private final Rect tmpChildRect = new Rect();

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        int firstChildHeight = 0;

        int parentTop = getPaddingTop();
        int parentBottom = getPaddingBottom();
        int parentLeft = getPaddingLeft();
        int parentRight = getPaddingRight();

        for (int i=0; i<2; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE)
                continue;

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int width = child.getMeasuredWidth();
            final int height = child.getMeasuredHeight();

            tmpContainerRect.left = parentLeft + lp.leftMargin;
            tmpContainerRect.right = width - lp.rightMargin - parentRight;

            tmpContainerRect.top = parentTop + lp.topMargin;
            tmpContainerRect.bottom = parentTop + height - lp.bottomMargin - parentBottom;

            if(i==0)
                firstChildHeight = child.getMeasuredHeight();
            else if(i==1)
                tmpContainerRect.bottom += firstChildHeight;

            parentTop = tmpContainerRect.bottom;

            Gravity.apply(lp.gravity, width, height, tmpContainerRect, tmpChildRect);

            child.layout(tmpChildRect.left, tmpChildRect.top, tmpChildRect.right, tmpChildRect.bottom);
        }
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);

        // draw the shadow
        if (shadowDrawable != null) {
            final int right = slidableView.getRight();
            final int top = (int) (slidableView.getY() - shadowLength);
            final int bottom = (int) slidableView.getY();
            final int left = slidableView.getLeft();

            shadowDrawable.setBounds(left, top, right, bottom);
            shadowDrawable.draw(c);
        }
    }

    private final Rect tmpRect = new Rect();
    private final Paint coveredFadePaint = new Paint();

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result;
        final int save = canvas.save(Canvas.CLIP_SAVE_FLAG);

        if (slidableView != child) { // if main view
            // Clip against the slider; no sense drawing what will immediately be covered,
            // Unless the panel is set to overlay content
            canvas.getClipBounds(tmpRect);
            tmpRect.bottom = Math.min(tmpRect.bottom, slidableView.getTop());

            result = super.drawChild(canvas, child, drawingTime);

            if (currentSlide > 0) {
                final int baseAlpha = (mCoveredFadeColor & 0xff000000) >>> 24;
                final int imag = (int) (baseAlpha * currentSlide);
                final int color = imag << 24;
                coveredFadePaint.setColor(color);
                canvas.drawRect(tmpRect, coveredFadePaint);
            }
        } else {
            result = super.drawChild(canvas, child, drawingTime);
        }

        canvas.restoreToCount(save);

        return result;
    }

    /**
     * Use this method in order to slide at a specific position
     * @param finalYNormalized Normalized value (between 0.0 and 1.0) representing the final slide position
     */
    public void slideTo(float finalYNormalized) {
        ValueAnimator va = ValueAnimator.ofFloat(currentSlide, finalYNormalized);
        va.setInterpolator(new DecelerateInterpolator(1.5f));
        va.setDuration(300);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                updateSliding((Float) animation.getAnimatedValue());
            }
        });
        va.start();
    }

    public void setDraggableView(View draggableView) {
        this.draggableView = draggableView;
    }

    /**
     * @return shadow height in pixels
     */
    public int getShadowLength() {
        return shadowLength;
    }

    /**
     * @param shadowLength shadow height in pixels
     */
    public void setShadowLength(int shadowLength) {
        this.shadowLength = shadowLength;
    }

    public int getState() {
        return state;
    }

    public void setState(@State int state) {
        switch (state) {
            case EXPANDED:
                slideTo(1);
                break;
            case COLLAPSED:
                slideTo(0);
                break;
            case SLIDING:
                break;
        }
    }

    public void addSlideListener(OnSlideListener listener) {
        listeners.add(listener);
    }

    public void removeListener(OnSlideListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(float currentSlide) {
        for(OnSlideListener listener : listeners)
            listener.onSlide(this, currentSlide);
    }

    public interface OnSlideListener {
        void onSlide(SlidingDrawer slidingDrawer, float currentSlide);
    }
}