package pageview;

public interface FlowIndicator extends ViewFlow.ViewSwitchListener {

    public void setViewFlow(ViewFlow view);

    public void onSetAdapter();

    public void onScrolled(int h, int v, int oldh, int oldv);
}
