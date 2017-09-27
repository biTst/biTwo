package bi.two.opt;

import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;

import java.util.List;

public class SingleDimensionalOptimizeProducer extends OptimizeProducer implements UnivariateFunction {
    public static final int MAX_EVALS_COUNT = 200;
    public static final double RELATIVE_TOLERANCE = 1e-7;
    public static final double ABSOLUTE_TOLERANCE = 1e-10;

    private final OptimizeConfig m_fieldConfig;
    private final double m_start;
    private final double m_min;
    private final double m_max;
    private BrentOptimizer m_optimizer;
    private UnivariatePointValuePair m_optimizePoint;

    SingleDimensionalOptimizeProducer(List<OptimizeConfig> optimizeConfigs, MapConfig algoConfig) {
        super(optimizeConfigs, algoConfig);
        m_fieldConfig = m_optimizeConfigs.get(0);

        m_start = m_fieldConfig.m_start.doubleValue() ;
        m_min = m_fieldConfig.m_min.doubleValue();
        m_max = m_fieldConfig.m_max.doubleValue();

        startThread();
    }

    @Override public double value(double value) {
//        System.out.println("BrentOptimizer value() value="+value);
        Vary vary = m_fieldConfig.m_vary;
        String fieldName = vary.m_key;
        double multiplier = m_fieldConfig.m_multiplier;
        double val = value * multiplier;
        if (val < m_min) {
            System.out.println("doOptimize too low value=" + val + " of field " + fieldName + "; using min=" + m_min);
            val = m_min;
        }
        if (val > m_max) {
            System.out.println("doOptimize too high value=" + val + " of field " + fieldName + "; using max=" + m_max);
            val = m_max;
        }

        m_algoConfig.put(fieldName, val);
//        System.out.println("BrentOptimizer for " + fieldName + "=" + Utils.format5(val) + "(" + Utils.format5(value) + ")");

        synchronized (m_sync) {
            m_state = State.waitingResult;
            m_sync.notify();

            try {
//                System.out.println("BrentOptimizer start waiting for result");
                m_sync.wait();
//                System.out.println("BrentOptimizer waiting for done");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_state = State.optimizerCalculation;
        }

        System.out.println("BrentOptimizer value calculated for " + fieldName + "=" + Utils.format8(val) + "(" + Utils.format8(value)
                + ") mult=" + multiplier + " => " + m_totalPriceRatio);
        return m_totalPriceRatio;
    }

    @Override public void run() {
        Thread.currentThread().setName("BrentOptimizer");
//        System.out.println("BrentOptimizer thread started");
        m_optimizer = new BrentOptimizer(RELATIVE_TOLERANCE, ABSOLUTE_TOLERANCE);
        double multiplier = m_fieldConfig.m_multiplier;
        m_optimizePoint = m_optimizer.optimize(new MaxEval(MAX_EVALS_COUNT),
                new UnivariateObjectiveFunction(this),
                GoalType.MAXIMIZE,
                new SearchInterval(m_min / multiplier, m_max / multiplier, m_start / multiplier));
        System.out.println("BrentOptimizer result for " + m_fieldConfig.m_vary.m_key
                + ": point=" + m_optimizePoint.getPoint()
                + "; value=" + m_optimizePoint.getValue()
                + "; iterations=" + m_optimizer.getIterations()
        );

        m_active = false;

        synchronized (m_sync) {
            m_state = State.finished;
            m_sync.notify();
        }
    }

    @Override public void logResults() {
        System.out.println("SingleDimensionalOptimizeProducer result: field=" + m_fieldConfig.m_vary.m_key
                + ": point=" + m_optimizePoint.getPoint()
                + "; value=" + m_optimizePoint.getValue()
                + "; iterations=" + m_optimizer.getIterations()
                + "; totalPriceRatio=" + m_totalPriceRatio);
    }

}
