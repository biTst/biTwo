package bi.two.chart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseTimesSeriesData<T extends ITickData>
        implements ITimesSeriesData<T>, ITimesSeriesData.ITimesSeriesListener {
    public ITimesSeriesData m_parent;
    private List<ITimesSeriesListener> m_listeners = new CopyOnWriteArrayList<ITimesSeriesListener>();

    public ITimesSeriesData getParent() { return m_parent; }

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

    @Override public void removeListener(ITimesSeriesListener listener) {
        m_listeners.remove(listener);
    }

    protected void notifyListeners(boolean changed) {
        for (ITimesSeriesListener listener : m_listeners) {
            listener.onChanged(this, changed);
        }
    }


    public TimesSeriesData<TickData> getJoinNonChangedTs() {
        return new JoinNonChangedTimesSeriesData(this);
    }



    //----------------------------------------------------------
    public class JoinNonChangedTimesSeriesData extends BaseJoinNonChangedTimesSeriesData {
        public JoinNonChangedTimesSeriesData(ITimesSeriesData parent) {
            super(parent);
        }

        @Override protected ITickData getTickValue() {
            return BaseTimesSeriesData.this.getLatestTick();
        }
    }
}
