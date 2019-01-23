package bi.two.opt;

import bi.two.telegram.TheBot;
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

import static bi.two.util.Log.*;

public class SingleDimensionalOptimizeProducer extends OptimizeProducer implements UnivariateFunction {
    public static final int MAX_EVALS_COUNT = 150;
    public static final double RELATIVE_TOLERANCE = 1e-6;
    public static final double ABSOLUTE_TOLERANCE = 1e-8;

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
//        console("BrentOptimizer value() value="+value);

        StringBuilder sb = new StringBuilder();
        sb.append("function(");

        OptimizeConfig fieldConfig = m_optimizeConfigs.get(0);
        Vary vary = fieldConfig.m_vary;
        String fieldName = vary.name();
        double multiplier = fieldConfig.m_multiplier;
        double val = value * multiplier;
        double min = fieldConfig.m_min.doubleValue();
        if (val < min) {
            log("doOptimize too low value=" + val + " of field " + fieldName + "; using min=" + min);
            val = min;
        }
        double max = fieldConfig.m_max.doubleValue();
        if (val > max) {
            log("doOptimize too high value=" + val + " of field " + fieldName + "; using max=" + max);
            val = max;
        }

        sb.append(fieldName).append("=").append(Utils.format5(val)).append("(").append(Utils.format5(value)).append("); ").append(") => ");

        m_algoConfig.put(fieldName, val);
//        console("BrentOptimizer for " + fieldName + "=" + Utils.format5(val) + "(" + Utils.format5(value) + ")");

        synchronized (m_sync) {
            m_state = State.waitingResult;
            m_sync.notify();

            try {
                log("BrentOptimizer start waiting for result " + this);
                m_sync.wait();
                log("BrentOptimizer waiting for done " + this);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            m_state = State.optimizerCalculation;

            sb.append(m_onFinishTotalPriceRatio);
            console(sb.toString());
        }

        //        console("BrentOptimizer value calculated for " + fieldName + "=" + Utils.format8(val) + "(" + Utils.format8(value)
//                + ") mult=" + multiplier + " => " + m_onFinishTotalPriceRatio);

        if (m_onFinishTotalPriceRatio > m_maxTotalPriceRatio) {
            m_maxTotalPriceRatio = m_onFinishTotalPriceRatio;
            m_maxWatcher = m_lastWatcher;
        }

        return m_onFinishTotalPriceRatio;
    }

    @Override public void run() {
        Thread.currentThread().setName("BrentOptimizer");
//        console("BrentOptimizer thread started");
        m_optimizer = new BrentOptimizer(RELATIVE_TOLERANCE, ABSOLUTE_TOLERANCE);
        double multiplier = m_fieldConfig.m_multiplier;
        try {
            m_optimizePoint = m_optimizer.optimize(new MaxEval(MAX_EVALS_COUNT),
                    new UnivariateObjectiveFunction(this),
                    GoalType.MAXIMIZE,
                    new SearchInterval(m_min, m_max, m_start));
        } catch (Exception e) {
            err("optimize error for field '" + m_fieldConfig.m_vary + "': " + e, e);
            throw e;
        }
        console("BrentOptimizer result for " + m_fieldConfig.m_vary.name()
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

    @Override public double logResults(int pad) {
        console("SingleDimensionalOptimizeProducer result: " + m_fieldConfig.m_vary.name()
                + "=" + Utils.format8(m_optimizePoint.getPoint() * m_fieldConfig.m_multiplier)
                + ";  " + Utils.pad(pad)
                + "totalPriceRatio=" + Utils.format8(m_maxTotalPriceRatio)
                + ";  iterations=" + m_optimizer.getIterations()
        );
        return m_maxTotalPriceRatio;
    }

    @Override public void logResultsEx(TheBot theBot, String botKey) {
        double gain = m_maxWatcher.totalPriceRatio(true);
        console(m_maxWatcher.getGainLogStr("MAX ", gain));

        long processedPeriod = m_maxWatcher.getProcessedPeriod();
        console("   processedPeriod=" + Utils.millisToYDHMSStr(processedPeriod) );

        double processedDays = ((double) processedPeriod) / TimeUnit.DAYS.toMillis(1);
        double perDay = Math.pow(gain, 1 / processedDays);
        double inYear = Math.pow(gain, 365 / processedDays);
        console(" processedDays=" + processedDays
                + "; perDay=" + Utils.format8(perDay)
                + "; inYear=" + Utils.format8(inYear)
        );

        if (theBot != null) {
            theBot.sendMsg(botKey + ": DONE d:" + Utils.format6(perDay) + " y:" + Utils.format5(inYear), false);
        }

    }

    @Override public int logKeyWidth() {
        return m_fieldConfig.m_vary.name().length() + Utils.format8(m_optimizePoint.getPoint() * m_fieldConfig.m_multiplier).length();
    }
}
