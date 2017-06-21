package bi.two.chart;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseTimesSeriesData<T extends ITickData>
        implements ITimesSeriesData<T>, ITimesSeriesData.ITimesSeriesListener {
    public ITimesSeriesData m_parent;
    private List<ITimesSeriesListener> m_listeners = new ArrayList<ITimesSeriesListener>();

    public BaseTimesSeriesData() {
    }

    public BaseTimesSeriesData(ITimesSeriesData parent) {
        setParent(parent);
    }

    public void setParent(ITimesSeriesData parent) {
        m_parent = parent;
        if (parent != null) {
            parent.addListener(this);
        }
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        notifyListeners(changed);
    }

    @Override public void addListener(ITimesSeriesListener listener) {
        m_listeners.add(listener);
    }

    protected void notifyListeners(boolean changed) {
        for (ITimesSeriesListener listener : m_listeners) {
            listener.onChanged(this, changed);
        }
    }


    public TimesSeriesData<TickData> getJoinNonChangedTs() {
        return new JoinNonChangedTimesSeriesData2(this);
    }



    //----------------------------------------------------------
    public class JoinNonChangedTimesSeriesData2 extends BaseJoinNonChangedTimesSeriesData {
        public JoinNonChangedTimesSeriesData2(ITimesSeriesData parent) {
            super(parent);
        }

        @Override protected ITickData getTickValue() {
            return BaseTimesSeriesData.this.getLastTick();
        }
    }
}
