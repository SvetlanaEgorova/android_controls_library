package pageview;

import android.content.Context;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * A horizontally scrollable {@link android.view.ViewGroup} with items populated from an
 * {@link android.widget.Adapter}. The ViewFlow uses a buffer to store loaded {@link android.view.View}s in.
 * The default size of the buffer is 3 elements on both sides of the currently
 * visible {@link android.view.View}, making up a total buffer size of 3 * 2 + 1 = 7. The
 * buffer size can be changed using the {@code sidebuffer} xml attribute.
 *
 */
public class ViewFlow extends AdapterView<Adapter> {

	private static final int SNAP_VELOCITY = 1000;
	private static final int INVALID_SCREEN = -1;
	private final static int TOUCH_STATE_REST = 0;
	private final static int TOUCH_STATE_SCROLLING = 1;

	private LinkedList<View> loadedViews;
	private int currentBufferIndex;
	private int currentAdapterIndex;
	private int sideBuffer = 2;
	private Scroller scroller;
	private VelocityTracker velocityTracker;
	private int touchState = TOUCH_STATE_REST;
	private float lastMotionX;
	private int touchSlop;
	private int maximumVelocity;
	private int currentScreen;
	private int nextScreen = INVALID_SCREEN;
	private boolean firstLayout = true;
	private ViewSwitchListener viewSwitchListener;
	private Adapter adapter;
	private int lastScrollDirection;
	private AdapterDataSetObserver dataSetObserver;
	private FlowIndicator indicator;

	private OnGlobalLayoutListener orientationChangeListener = new OnGlobalLayoutListener() {

		@Override
		public void onGlobalLayout() {
			getViewTreeObserver().removeGlobalOnLayoutListener(
					orientationChangeListener);
			setSelection(currentAdapterIndex);
		}
	};

    public boolean canGoBack() {
        return currentAdapterIndex != 0;
    }

    public boolean canGoForward() {
        return currentAdapterIndex != adapter.getCount() - 1;
    }

    public static interface ViewSwitchListener {
		void onSwitched(View view, int position);
	}

	public ViewFlow(Context context) {
		super(context);
		sideBuffer = 3;
		init();
	}

	public ViewFlow(Context context, int sideBuffer) {
		super(context);
		this.sideBuffer = sideBuffer;
		init();
	}

	public ViewFlow(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

    public void switchForward() {
        if (canGoForward())
            setSelection(currentAdapterIndex + 1);
    }

    public void switchBack() {
        if (canGoBack())
            setSelection(currentAdapterIndex -1);
    }


	private void init() {
		loadedViews = new LinkedList<View>();
		scroller = new Scroller(getContext());
		final ViewConfiguration configuration = ViewConfiguration
				.get(getContext());
		touchSlop = configuration.getScaledTouchSlop();
		maximumVelocity = configuration.getScaledMaximumFlingVelocity();
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		getViewTreeObserver().addOnGlobalLayoutListener(
				orientationChangeListener);
	}

	public int getViewsCount() {
		return adapter.getCount();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		final int width = MeasureSpec.getSize(widthMeasureSpec);
		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		if (widthMode != MeasureSpec.EXACTLY) {
			throw new IllegalStateException(
					"ViewFlow can only be used in EXACTLY mode.");
		}

		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		if (heightMode != MeasureSpec.EXACTLY) {
			throw new IllegalStateException(
					"ViewFlow can only be used in EXACTLY mode.");
		}

		// The children are given the same width and height as the workspace
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
		}

		if (firstLayout) {
			scrollTo(currentScreen * width, 0);
			firstLayout = false;
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int childLeft = 0;

		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			if (child.getVisibility() != View.GONE) {
				final int childWidth = child.getMeasuredWidth();
				child.layout(childLeft, 0, childLeft + childWidth,
						child.getMeasuredHeight());
				childLeft += childWidth;
			}
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (getChildCount() == 0)
			return false;

		if (velocityTracker == null) {
			velocityTracker = VelocityTracker.obtain();
		}
		velocityTracker.addMovement(ev);

		final int action = ev.getAction();
		final float x = ev.getX();

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			if (!scroller.isFinished()) {
				scroller.abortAnimation();
			}

			lastMotionX = x;

			touchState = scroller.isFinished() ? TOUCH_STATE_REST
					: TOUCH_STATE_SCROLLING;

			break;

		case MotionEvent.ACTION_MOVE:
			final int xDiff = (int) Math.abs(x - lastMotionX);

			boolean xMoved = xDiff > touchSlop;

			if (xMoved) {
				touchState = TOUCH_STATE_SCROLLING;
			}

			if (touchState == TOUCH_STATE_SCROLLING) {
				final int deltaX = (int) (lastMotionX - x);
				lastMotionX = x;

				final int scrollX = getScrollX();
				if (deltaX < 0) {
					if (scrollX > 0) {
						scrollBy(Math.max(-scrollX, deltaX), 0);
					}
				} else if (deltaX > 0) {
					final int availableToScroll = getChildAt(
							getChildCount() - 1).getRight()
							- scrollX - getWidth();
					if (availableToScroll > 0) {
						scrollBy(Math.min(availableToScroll, deltaX), 0);
					}
				}
				return true;
			}
			break;

		case MotionEvent.ACTION_UP:
			if (touchState == TOUCH_STATE_SCROLLING) {
				final VelocityTracker velocityTracker = this.velocityTracker;
				velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
				int velocityX = (int) velocityTracker.getXVelocity();

				if (velocityX > SNAP_VELOCITY && currentScreen > 0) {
					snapToScreen(currentScreen - 1);
				} else if (velocityX < -SNAP_VELOCITY
						&& currentScreen < getChildCount() - 1) {
					snapToScreen(currentScreen + 1);
				} else {
					snapToDestination();
				}

				if (this.velocityTracker != null) {
					this.velocityTracker.recycle();
					this.velocityTracker = null;
				}
			}

			touchState = TOUCH_STATE_REST;

			break;
		case MotionEvent.ACTION_CANCEL:
			touchState = TOUCH_STATE_REST;
		}
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (getChildCount() == 0)
			return false;

		if (velocityTracker == null) {
			velocityTracker = VelocityTracker.obtain();
		}
		velocityTracker.addMovement(ev);

		final int action = ev.getAction();
		final float x = ev.getX();

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			if (!scroller.isFinished()) {
				scroller.abortAnimation();
			}

			lastMotionX = x;

			touchState = scroller.isFinished() ? TOUCH_STATE_REST
					: TOUCH_STATE_SCROLLING;

			break;

		case MotionEvent.ACTION_MOVE:
			final int xDiff = (int) Math.abs(x - lastMotionX);

			boolean xMoved = xDiff > touchSlop;

			if (xMoved) {
				touchState = TOUCH_STATE_SCROLLING;
			}

			if (touchState == TOUCH_STATE_SCROLLING) {
				final int deltaX = (int) (lastMotionX - x);
				lastMotionX = x;

				final int scrollX = getScrollX();
				if (deltaX < 0) {
					if (scrollX > 0) {
						scrollBy(Math.max(-scrollX, deltaX), 0);
					}
				} else if (deltaX > 0) {
					final int availableToScroll = getChildAt(
							getChildCount() - 1).getRight()
							- scrollX - getWidth();
					if (availableToScroll > 0) {
						scrollBy(Math.min(availableToScroll, deltaX), 0);
					}
				}
				return true;
			}
			break;

		case MotionEvent.ACTION_UP:
			if (touchState == TOUCH_STATE_SCROLLING) {
				final VelocityTracker velocityTracker = this.velocityTracker;
				velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
				int velocityX = (int) velocityTracker.getXVelocity();

				if (velocityX > SNAP_VELOCITY && currentScreen > 0) {
					snapToScreen(currentScreen - 1);
				} else if (velocityX < -SNAP_VELOCITY
						&& currentScreen < getChildCount() - 1) {
					snapToScreen(currentScreen + 1);
				} else {
					snapToDestination();
				}

				if (this.velocityTracker != null) {
					this.velocityTracker.recycle();
					this.velocityTracker = null;
				}
			}

			touchState = TOUCH_STATE_REST;

			break;
		case MotionEvent.ACTION_CANCEL:
			touchState = TOUCH_STATE_REST;
		}
		return true;
	}

	@Override
	protected void onScrollChanged(int h, int v, int oldh, int oldv) {
		super.onScrollChanged(h, v, oldh, oldv);
		if (indicator != null) {
			int hPerceived = h + (currentAdapterIndex - currentBufferIndex)
					* getWidth();
			indicator.onScrolled(hPerceived, v, oldh, oldv);
		}
	}

	private void snapToDestination() {
		final int screenWidth = getWidth();
		final int whichScreen = (getScrollX() + (screenWidth / 2))
				/ screenWidth;

		snapToScreen(whichScreen);
	}

	private void snapToScreen(int whichScreen) {
		lastScrollDirection = whichScreen - currentScreen;
		if (!scroller.isFinished())
			return;

		whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() - 1));

		nextScreen = whichScreen;

		final int newX = whichScreen * getWidth();
		final int delta = newX - getScrollX();
		scroller.startScroll(getScrollX(), 0, delta, 0, Math.abs(delta) * 2);
		invalidate();
	}

	@Override
	public void computeScroll() {
		if (scroller.computeScrollOffset()) {
			scrollTo(scroller.getCurrX(), scroller.getCurrY());
			postInvalidate();
		} else if (nextScreen != INVALID_SCREEN) {
			currentScreen = Math.max(0,
					Math.min(nextScreen, getChildCount() - 1));
			nextScreen = INVALID_SCREEN;
			postViewSwitched(lastScrollDirection);
		}
	}

	private void setVisibleView(int indexInBuffer, boolean uiThread) {
		currentScreen = Math.max(0,
				Math.min(indexInBuffer, getChildCount() - 1));
		int dx = (currentScreen * getWidth()) - scroller.getCurrX();
		scroller.startScroll(scroller.getCurrX(), scroller.getCurrY(), dx,
				0, 0);
		if (uiThread)
			invalidate();
		else
			postInvalidate();
	}

	public void setOnViewSwitchListener(ViewSwitchListener l) {
		viewSwitchListener = l;
	}

	@Override
	public Adapter getAdapter() {
		return adapter;
	}

	@Override
	public void setAdapter(Adapter adapter) {
		if (this.adapter != null) {
			this.adapter.unregisterDataSetObserver(dataSetObserver);
		}

		this.adapter = adapter;

		if (this.adapter != null) {
			dataSetObserver = new AdapterDataSetObserver();
			this.adapter.registerDataSetObserver(dataSetObserver);

		}
		if (this.adapter.getCount() == 0)
			return;

		for (int i = 0; i < Math.min(this.adapter.getCount(), sideBuffer + 1); i++) {
			loadedViews.addLast(makeAndAddView(i, true, null));
		}

		currentAdapterIndex = 0;
		currentBufferIndex = 0;
		requestLayout();
		setVisibleView(currentBufferIndex, false);
		if (viewSwitchListener != null)
			viewSwitchListener.onSwitched(loadedViews.get(0), 0);

        if (indicator != null) {
            indicator.onSetAdapter();
        }
	}

	@Override
	public View getSelectedView() {
		return (currentAdapterIndex < loadedViews.size() ? loadedViews
				.get(currentBufferIndex) : null);
	}

	public void setFlowIndicator(FlowIndicator flowIndicator) {
		indicator = flowIndicator;
		indicator.setViewFlow(this);
	}

	@Override
	public void setSelection(int position) {
		if (adapter == null || position >= adapter.getCount())
			return;

		ArrayList<View> recycleViews = new ArrayList<View>();
		View recycleView;
		while (!loadedViews.isEmpty()) {
			recycleViews.add(recycleView = loadedViews.remove());
			detachViewFromParent(recycleView);
		}

		for (int i = Math.max(0, position - sideBuffer); i < Math.min(
				adapter.getCount(), position + sideBuffer + 1); i++) {
			loadedViews.addLast(makeAndAddView(i, true,
					(recycleViews.isEmpty() ? null : recycleViews.remove(0))));
			if (i == position)
				currentBufferIndex = loadedViews.size() - 1;
		}
		currentAdapterIndex = position;

		for (View view : recycleViews) {
			removeDetachedView(view, false);
		}
		requestLayout();
		setVisibleView(currentBufferIndex, false);
		if (viewSwitchListener != null) {
			if (indicator != null) {
				indicator.onSwitched(loadedViews.get(currentBufferIndex),
                        currentAdapterIndex);
			}
			viewSwitchListener
					.onSwitched(loadedViews.get(currentBufferIndex),
                            currentAdapterIndex);
		}
	}

	private void resetFocus() {
		logBuffer();
		loadedViews.clear();
		removeAllViewsInLayout();

		for (int i = Math.max(0, currentAdapterIndex - sideBuffer); i < Math
				.min(adapter.getCount(), currentAdapterIndex + sideBuffer
						+ 1); i++) {
			loadedViews.addLast(makeAndAddView(i, true, null));
			if (i == currentAdapterIndex)
				currentBufferIndex = loadedViews.size() - 1;
		}
		logBuffer();
		requestLayout();
	}

	private void postViewSwitched(int direction) {
		if (direction == 0)
			return;

		if (direction > 0) { // to the right
			currentAdapterIndex++;
			currentBufferIndex++;

			View recycleView = null;

			// Remove view outside buffer range
			if (currentAdapterIndex > sideBuffer) {
				recycleView = loadedViews.removeFirst();
				detachViewFromParent(recycleView);
				// removeView(recycleView);
				currentBufferIndex--;
			}

			// Add new view to buffer
			int newBufferIndex = currentAdapterIndex + sideBuffer;
			if (newBufferIndex < adapter.getCount())
				loadedViews.addLast(makeAndAddView(newBufferIndex, true,
						recycleView));

		} else { // to the left
			currentAdapterIndex--;
			currentBufferIndex--;
			View recycleView = null;

			// Remove view outside buffer range
			if (adapter.getCount() - 1 - currentAdapterIndex > sideBuffer) {
				recycleView = loadedViews.removeLast();
				detachViewFromParent(recycleView);
			}

			// Add new view to buffer
			int newBufferIndex = currentAdapterIndex - sideBuffer;
			if (newBufferIndex > -1) {
				loadedViews.addFirst(makeAndAddView(newBufferIndex, false,
						recycleView));
				currentBufferIndex++;
			}

		}

		requestLayout();
		setVisibleView(currentBufferIndex, true);
		if (indicator != null) {
			indicator.onSwitched(loadedViews.get(currentBufferIndex),
                    currentAdapterIndex);
		}
		if (viewSwitchListener != null) {
			viewSwitchListener
					.onSwitched(loadedViews.get(currentBufferIndex),
                            currentAdapterIndex);
		}
		logBuffer();
	}

	private View setupChild(View child, boolean addToEnd, boolean recycle) {
		LayoutParams p = (LayoutParams) child
				.getLayoutParams();
		if (p == null) {
			p = new AbsListView.LayoutParams(
					LayoutParams.MATCH_PARENT,
					LayoutParams.WRAP_CONTENT, 0);
		}
		if (recycle)
			attachViewToParent(child, (addToEnd ? -1 : 0), p);
		else
			addViewInLayout(child, (addToEnd ? -1 : 0), p, true);
		return child;
	}

	private View makeAndAddView(int position, boolean addToEnd, View convertView) {
		View view = adapter.getView(position, convertView, this);
		return setupChild(view, addToEnd, convertView != null);
	}

	class AdapterDataSetObserver extends DataSetObserver {

		@Override
		public void onChanged() {
			View v = getChildAt(currentBufferIndex);
			if (v != null) {
				for (int index = 0; index < adapter.getCount(); index++) {
					if (v.equals(adapter.getItem(index))) {
						currentAdapterIndex = index;
						break;
					}
				}
			}
			resetFocus();
		}

		@Override
		public void onInvalidated() {
		}

	}

	private void logBuffer() {

		Log.d("viewflow", "Size of loadedViews: " + loadedViews.size() +
				"X: " + scroller.getCurrX() + ", Y: " + scroller.getCurrY());
		Log.d("viewflow", "IndexInAdapter: " + currentAdapterIndex
				+ ", IndexInBuffer: " + currentBufferIndex);
	}
}
