package bi.two.opt;

import bi.two.algo.BaseAlgo;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

class MultiDimensionalOptimizeProducer extends OptimizeProducer {
    public static final int MAX_EVALS_COUNT = 200;
    public static final double RELATIVE_TOLERANCE = 1e-7;
    public static final double ABSOLUTE_TOLERANCE = 1e-10;

    private final MultivariateFunction m_function;
    private final double[] m_startPoint; // multiplied
    // private final SimpleBounds m_bounds; // PowellOptimizer not supports bounds

    public MultiDimensionalOptimizeProducer(List<OptimizeConfig> optimizeConfigs, MapConfig algoConfig) {
        super(optimizeConfigs, algoConfig);

        m_startPoint = buildStartPoint(m_optimizeConfigs);
        // m_bounds = buildBounds(m_optimizeConfigs); // PowellOptimizer not supports bounds

        m_function = new MultivariateFunction() {
            @Override public double value(double[] point) {
                StringBuilder sb = new StringBuilder();
                sb.append("function(");
                int length = point.length;
                for (int i = 0; i < length; i++) {
                    double value = point[i];
                    OptimizeConfig fieldConfig = m_optimizeConfigs.get(i);
                    Vary vary = fieldConfig.m_vary;
                    String fieldName = vary.m_key;
                    double multiplier = fieldConfig.m_multiplier;
                    double val = value * multiplier;
                    double min = fieldConfig.m_min.doubleValue();
                    if (val < min) {
                        System.out.println("doOptimize too low value=" + val + " of field " + fieldName + "; using min=" + min);
                        val = min;
                    }
                    double max = fieldConfig.m_max.doubleValue();
                    if (val > max) {
                        System.out.println("doOptimize too high value=" + val + " of field " + fieldName + "; using max=" + max);
                        val = max;
                    }

                    m_algoConfig.put(fieldName, val);
                    sb.append(fieldName).append("=").append(Utils.format5(val)).append("(").append(Utils.format5(value)).append("); ");
                }
                sb.append(") => ");

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

                sb.append(m_onFinishTotalPriceRatio);
                System.out.println(sb.toString());

                if (m_onFinishTotalPriceRatio > m_maxTotalPriceRatio) {
                    m_maxTotalPriceRatio = m_onFinishTotalPriceRatio;
                    m_maxWatcher = m_lastWatcher;
                }

                return m_onFinishTotalPriceRatio;
            }
        };
        startThread();
    }

    @Override public void run() {
        Thread.currentThread().setName("PowellOptimizer");

        System.out.println("PowellOptimizer thread started");

        PowellOptimizer optimize = new PowellOptimizer(
                RELATIVE_TOLERANCE, ABSOLUTE_TOLERANCE            //  1e-13, FastMath.ulp(1d)
        );
        try {
            PointValuePair pair1 = optimize.optimize(
                    new ObjectiveFunction(m_function),
                    new MaxEval(MAX_EVALS_COUNT),
                    GoalType.MAXIMIZE,
                    new InitialGuess(m_startPoint)/*,
                    m_bounds*/    // PowellOptimizer not supports bounds
            );

            System.out.println("point=" + Arrays.toString(pair1.getPoint()) + "; value=" + pair1.getValue());
            System.out.println("optimize: Evaluations=" + optimize.getEvaluations()
                    + "; Iterations=" + optimize.getIterations());
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }

        m_active = false;

        synchronized (m_sync) {
            m_state = State.finished;
            m_sync.notify();
        }
    }

    private SimpleBounds buildBounds(List<OptimizeConfig> optimizeConfigs) {
        int dimension = optimizeConfigs.size();
        double[] mins = new double[dimension];
        double[] maxs = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            OptimizeConfig fieldConfig = optimizeConfigs.get(i);
            Vary field = fieldConfig.m_vary;
            double min = fieldConfig.m_min.doubleValue();
            double max = fieldConfig.m_max.doubleValue();
            double multiplier = fieldConfig.m_multiplier;
            double minim = min / multiplier;
            double maxim = max / multiplier;
            mins[i] = minim;
            maxs[i] = maxim;
            System.out.println("buildBounds field[" + field.m_key + "] min=" + minim + "(" + min + "); max=" + maxim + "(" + max + ")");
        }
        return new SimpleBounds(mins, maxs);
    }

    private double[] buildStartPoint(List<OptimizeConfig> optimizeConfigs) {
        int dimension = optimizeConfigs.size();
        double[] startPoint = new double[dimension];
        StringBuilder sb = new StringBuilder();
        sb.append("startPoint: ");
        for (int i = 0; i < dimension; i++) {
            OptimizeConfig fieldConfig = optimizeConfigs.get(i);
            Vary field = fieldConfig.m_vary;
            double start = fieldConfig.m_start.doubleValue();
            double strt = start / fieldConfig.m_multiplier;
            startPoint[i] = strt;
            sb.append(field.m_key).append("=").append(Utils.format5(start)).append("(").append(Utils.format5(strt)).append("); ");
        }
        System.out.println(sb.toString());
        return startPoint;
    }

    @Override public double logResults() {
        System.out.println("MultiDimensionalOptimizeProducer result: totalPriceRatio=" + m_maxTotalPriceRatio);
        return m_maxTotalPriceRatio;
    }

    @Override public void logResultsEx() {
        double gain = m_maxWatcher.totalPriceRatio(true);
        BaseAlgo ralgo = m_maxWatcher.m_algo;
        String key = ralgo.key(true);
        System.out.println("GAIN[" + key + "]: " + Utils.format8(gain)
                + "   trades=" + m_maxWatcher.m_tradesNum + " .....................................");

        long processedPeriod = m_maxWatcher.getProcessedPeriod();
        System.out.println("   processedPeriod=" + Utils.millisToYDHMSStr(processedPeriod) );

        double processedDays = ((double) processedPeriod) / TimeUnit.DAYS.toMillis(1);
        System.out.println(" processedDays=" + processedDays
                + "; perDay=" + Utils.format8(Math.pow(gain, 1 / processedDays))
                + "; inYear=" + Utils.format8(Math.pow(gain, 365 / processedDays))
        );
    }
}
