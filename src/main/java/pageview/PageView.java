package pageview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Adapter;
import android.widget.RelativeLayout;
import com.gulfstream.gcms.client.controls.R;

public class PageView extends RelativeLayout {
    private ViewFlow mViewFlow;

    public PageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.page_view, this);

        mViewFlow = (ViewFlow) findViewById(R.id.viewflow);
        PageIndicator indicator = (PageIndicator) findViewById(R.id.pageindic);

        mViewFlow.setFlowIndicator(indicator);
    }

    public void getAdapter() {
        mViewFlow.getAdapter();
    }

    public void setAdapter(Adapter adapter) {
        mViewFlow.setAdapter(adapter);
    }

    public void getSelectedView() {
        mViewFlow.getSelectedView();
    }

}
