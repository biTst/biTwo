package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

public class TicksVelocity extends BaseTimesSeriesData<ITickData> {
    private final BarSplitter m_splitter;
    private final float m_multiplier;
    private boolean m_initialized;
    private boolean m_dirty;
    private boolean m_filled;
    private BarSplitter.BarHolder m_newestBar;
    private BarSplitter.BarHolder m_olderBar;
    private final Interpolator m_interpolator = new Interpolator();
    private TickData m_tickData;

    public TicksVelocity(ITimesSeriesData tsd, long period, float multiplier) {
        m_splitter = new BarSplitter(tsd, 2, period); // 2 bars only
        m_multiplier = multiplier;
        setParent(m_splitter);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            if (!m_initialized) {
                BarSplitter.BarHolder newestBar = m_splitter.m_newestBar;
                if (newestBar != null) {
                    BarSplitter.BarHolder olderBar = newestBar.getOlderBar();
                    if (olderBar != null) {
                        m_initialized = true;
                        m_newestBar = newestBar;
                        m_olderBar = olderBar;
                        newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                            @Override public void onTickEnter(ITickData tickData) {
                                m_dirty = true;
                            }
                            @Override public void onTickExit(ITickData tickData) {}
                        });
                        olderBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                            @Override public void onTickEnter(ITickData tickData) {
                                m_dirty = true;
                            }
                            @Override public void onTickExit(ITickData tickData) {
                                m_dirty = true;
                                m_filled = true;
                            }
                        });
                    } else {
                        return; // not initialized
                    }
                } else {
                    return; // not initialized
                }
            }
            iAmChanged = m_dirty && m_filled;
        }
        super.onChanged(this, iAmChanged); // notifyListeners
    }

    @Override public ITickData getLatestTick() {
        if (m_filled && m_dirty) {
            BarSplitter.TickNode newest = m_newestBar.getLatestTick();
            if (newest != null) {
                BarSplitter.TickNode mid = m_olderBar.getLatestTick();
                if (mid != null) {
                    BarSplitter.TickNode oldest = m_olderBar.getOldestTick();
                    if ((oldest != null) && (oldest != mid)) { // not a one tick bar
                        ITickData newestTick = newest.m_param;
                        Long x3 = newestTick.getTimestamp();
                        float y3 = newestTick.getClosePrice();
                        ITickData midTick = mid.m_param;
                        Long x2 = midTick.getTimestamp();
                        float y2 = midTick.getClosePrice();
                        ITickData oldestTick = oldest.m_param;
                        Long x1 = oldestTick.getTimestamp();
                        float y1 = oldestTick.getClosePrice();
                        if ((x1 < x2) && (x2 < x3)) {
                            PolynomialSplineFunction polynomialFunc = m_interpolator.interpolate(x1, y1, x2, y2, x3, y3);
                            PolynomialFunction[] polynomials = polynomialFunc.getPolynomials();
                            PolynomialFunction polynomial = polynomials[1];
                            UnivariateFunction derivative = polynomial.derivative();
                            double derivativeValue2 = derivative.value(x3 - x2);
                            if (!Double.isNaN(derivativeValue2)) {
                                long timestamp = m_parent.getLatestTick().getTimestamp();
                                m_tickData = new TickData(timestamp, (float) (derivativeValue2 * m_multiplier));
                                m_dirty = false;
                            }
                        }
                    }
                }
            }
        }
        return m_tickData;
    }
}
