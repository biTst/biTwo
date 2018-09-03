package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.TickData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;

//----------------------------------------------------------
public abstract class BarsBasedCalculator extends TicksTimesSeriesData<TickData> {

    private final BarSplitter m_barSplitter;
    private final BarTicksProcessor m_processor;

    protected abstract BarTicksProcessor createBarTicksProcessor();

    public BarsBasedCalculator(final BarSplitter barSplitter) {
        super(barSplitter);
        m_barSplitter = barSplitter;
        m_processor = createBarTicksProcessor();
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            boolean anyNotified = false;
            int counter = 0;

            for (BarSplitter.BarHolder barHolder : m_barSplitter.getBarsIterable()) { // iterate all bars
                m_processor.restart(barHolder, counter);
                Boolean notified = barHolder.iterateTicks(m_processor);
                anyNotified |= notified;
                counter++;
            }
            if (!anyNotified) {
                notifyListeners(true);
            }
        } else {
            notifyListeners(changed);
        }
    }

    //----------------------------------------------------------
    protected abstract class BarTicksProcessor implements BarSplitter.BarHolder.ITicksProcessor<Boolean> {
        private BarSplitter.BarHolder m_barHolder;
        private int m_barIndex;

        protected abstract float calcValue();

        protected BarTicksProcessor() {
        }

        void restart(BarSplitter.BarHolder barHolder, int barIndex) {
            m_barHolder = barHolder;
            m_barIndex = barIndex;
        }

        @Override public Boolean done() {
            boolean notified;
            long time = m_barHolder.getTime();
            float value = calcValue();
            int size = getTicksNum();
            if (m_barIndex < size) { // if known bar changed - update my tick
                TickData tickData = getTick(m_barIndex); // my ticks
                tickData.init(time, value);
                notified = false;
            } else { // bars number is more than my ticks - add tick
                TickData tickData = new TickData(time, value) {
                    @Override public long getBarSize() {
                        return m_barHolder.getBarSize();
                    }
                };
                addOlderTick(tickData); // will notify listeners inside
                notified = true;
            }
            return notified;
        }
    }
}
