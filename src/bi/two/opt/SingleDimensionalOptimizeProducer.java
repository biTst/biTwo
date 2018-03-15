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
import java.util.concurrent.TimeUnit;

public class SingleDimensionalOptimizeProducer extends OptimizeProducer implements UnivariateFunction {
    public static final int MAX_EVALS_COUNT = 200;
    public static final double RELATIVE_TOLERANCE = 1e-6;
    public static final double ABSOLUTE_TOLERANCE = 1e-9;

    private final OptimizeConfig m_fieldConfig;
    private final double m_start; // multiplied
    private final double m_min; // multiplied
    private final double m_max; // multiplied
    private BrentOptimizer m_optimizer;
    private UnivariatePointValuePair m_optimizePoint;

    SingleDimensionalOptimizeProducer(List<OptimizeConfig> optimizeConfigs, MapConfig algoConfig) {
        super(optimizeConfigs, algoConfig);
        m_fieldConfig = m_optimizeConfigs.get(0);

        double multiplier = m_fieldConfig.m_multiplier;
        m_start = m_fieldConfig.m_start.doubleValue() / multiplier;
        m_min = m_fieldConfig.m_min.doubleValue() / multiplier;
        m_max = m_fieldConfig.m_max.doubleValue() / multiplier;

        startThread();
    }

    @Override public double value(double value) {
//        System.out.println("BrentOptimizer value() value="+value);
        Vary vary = m_fieldConfig.m_vary;
        String fieldName = vary.name();
        double multiplier = m_fieldConfig.m_multiplier;
        double val = value * multiplier;
        if (value < m_min) {
            val = m_fieldConfig.m_min.doubleValue();
            System.out.println("doOptimize too low value=" + val + " of field " + fieldName + "; using min=" + val);
        }
        if (value > m_max) {
            val = m_fieldConfig.m_max.doubleValue();
            System.out.println("doOptimize too high value=" + val + " of field " + fieldName + "; using max=" + val);
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
                + ") mult=" + multiplier + " => " + m_onFinishTotalPriceRatio);

        if (m_onFinishTotalPriceRatio > m_maxTotalPriceRatio) {
            m_maxTotalPriceRatio = m_onFinishTotalPriceRatio;
            m_maxWatcher = m_lastWatcher;
        }

        return m_onFinishTotalPriceRatio;
    }

    @Override public void run() {
        Thread.currentThread().setName("BrentOptimizer");
//        System.out.println("BrentOptimizer thread started");
        m_optimizer = new BrentOptimizer(RELATIVE_TOLERANCE, ABSOLUTE_TOLERANCE);
        double multiplier = m_fieldConfig.m_multiplier;
        m_optimizePoint = m_optimizer.optimize(new MaxEval(MAX_EVALS_COUNT),
                new UnivariateObjectiveFunction(this),
                GoalType.MAXIMIZE,
                new SearchInterval(m_min, m_max, m_start));
        System.out.println("BrentOptimizer result for " + m_fieldConfig.m_vary.name()
                + ": point=" + (m_optimizePoint.getPoint() * multiplier)
                + "; value=" + m_optimizePoint.getValue()
                + "; iterations=" + m_optimizer.getIterations()
        );

        m_active = false;

        synchronized (m_sync) {
            m_state = State.finished;
            m_sync.notify();
        }
    }

    @Override public double logResults() {
        System.out.println("SingleDimensionalOptimizeProducer result: " + m_fieldConfig.m_vary.name()
                + "=" + Utils.format8(m_optimizePoint.getPoint() * m_fieldConfig.m_multiplier)
                + "; iterations=" + m_optimizer.getIterations()
                + "; totalPriceRatio=" + Utils.format8(m_maxTotalPriceRatio));
        return m_maxTotalPriceRatio;
    }

    @Override public void logResultsEx() {
        double gain = m_maxWatcher.totalPriceRatio(true);
        System.out.println(m_maxWatcher.getGainLogStr("MAX ", gain));

        long processedPeriod = m_maxWatcher.getProcessedPeriod();
        System.out.println("   processedPeriod=" + Utils.millisToYDHMSStr(processedPeriod) );

        double processedDays = ((double) processedPeriod) / TimeUnit.DAYS.toMillis(1);
        System.out.println(" processedDays=" + processedDays
                + "; perDay=" + Utils.format8(Math.pow(gain, 1 / processedDays))
                + "; inYear=" + Utils.format8(Math.pow(gain, 365 / processedDays))
        );
    }
}
