package pageview;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Adapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.gulfstream.gcms.client.controls.R;

import java.util.ArrayList;
import java.util.List;

public class PageIndicator extends LinearLayout implements FlowIndicator {
    private int defaultPageImageId;
    private int currentPageImageId;
    private int margin;
    private int currentPageNumber = 0;
    private int pagesCount;
    private Context context;
    private List<ImageView> pageViews = new ArrayList<ImageView>();
    private ViewFlow mViewFlow;

    public PageIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.context = context;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PageViewIndicator);
        currentPageImageId = a.getResourceId(R.styleable.PageViewIndicator_activePageImage, R.drawable.dot_on);
        defaultPageImageId = a.getResourceId(R.styleable.PageViewIndicator_inactivePageImage, R.drawable.dot_off);
        margin = (int) a.getDimension(R.styleable.PageViewIndicator_eachImageMargin, 3);
    }

    @Override
    public void setViewFlow(ViewFlow view) {
        mViewFlow = view;
        onSetAdapter();
    }

    @Override
    public void onSetAdapter() {
        Adapter adapter = mViewFlow.getAdapter();
        if (adapter != null) {
            pagesCount = adapter.getCount();
            setUpView();
        }
    }

    @Override
    public void onScrolled(int h, int v, int oldh, int oldv) {
    }

    @Override
    public void onSwitched(View view, int position) {
        currentPageNumber = position;

        updateView();
    }

    private void setUpView() {
        LayoutParams p = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        p.setMargins(margin, 0, margin, 0);

        for (int i = 0; i < pagesCount; i++) {
            ImageView dot = new ImageView(context);
            dot.setImageResource((i == currentPageNumber) ? currentPageImageId : defaultPageImageId);
            pageViews.add(dot);
            this.addView(dot, p);
        }
    }

    private void updateView() {
        for (int i = 0; i < pageViews.size(); i++) {
            ImageView imageView = pageViews.get(i);
            imageView.setImageResource((i == currentPageNumber) ? currentPageImageId : defaultPageImageId);
        }
    }
}
