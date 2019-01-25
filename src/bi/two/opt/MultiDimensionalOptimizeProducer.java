package bi.two.opt;

import bi.two.telegram.TheBot;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.MultiDirectionalSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.*;

class MultiDimensionalOptimizeProducer extends OptimizeProducer {
    private static final int MAX_EVALS_COUNT = 120;
    private static final double RELATIVE_TOLERANCE = 1e-5;
    private static final double ABSOLUTE_TOLERANCE = 1e-7;

    private final MultivariateFunction m_function;
    private final double[] m_startPoint; // multiplied
    private final SimpleBounds m_bounds; // PowellOptimizer not supports bounds
    private String m_maxConfig;

    MultiDimensionalOptimizeProducer(List<OptimizeConfig> optimizeConfigs, MapConfig algoConfig) {
        super(optimizeConfigs, algoConfig);

        m_startPoint = buildStartPoint(m_optimizeConfigs);
        m_bounds = buildBounds(m_optimizeConfigs); // PowellOptimizer not supports bounds

        m_function = new MultivariateFunction() {
            @Override public double value(double[] point) {
                StringBuilder sb = new StringBuilder();
                int length = point.length;
                for (int i = 0; i < length; i++) {
                    double value = point[i];
                    OptimizeConfig fieldConfig = m_optimizeConfigs.get(i);
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

                    m_algoConfig.put(fieldName, val);
                    sb.append(fieldName).append("=").append(Utils.format5(val))
                            //.append("(").append(Utils.format5(value)).append(")")
                            .append("; ");
                }
                String cfg = sb.toString();
                sb.insert(0,"function(");
                sb.append(") => ");

                synchronized (m_sync) {
                    m_state = State.waitingResult;
                    m_sync.notify();

                    try {
                        log("BrentOptimizer start waiting for result: " + this);
                        m_sync.wait();
                        log("BrentOptimizer waiting done: " + this);
                    } catch (InterruptedException e) {
                        err("InterruptedException: " + e, e);
                    }
                    m_state = State.optimizerCalculation;

                    sb.append(m_onFinishTotalPriceRatio);
                    console(sb.toString());
                }

                if (m_onFinishTotalPriceRatio > m_maxTotalPriceRatio) {
                    m_maxTotalPriceRatio = m_onFinishTotalPriceRatio;
                    m_maxWatcher = m_lastWatcher;
                    m_maxConfig = cfg;
                }

                return m_onFinishTotalPriceRatio;
            }
        };
        startThread();
    }

    @Override public void run() {

        Thread.currentThread().setName("MultiDimensionalOptimizeProducer");
        try {
            log("start PowellOptimizer=======================");
            MultivariateOptimizer optimize = new PowellOptimizer(
                    RELATIVE_TOLERANCE, ABSOLUTE_TOLERANCE            //  1e-13, FastMath.ulp(1d)
            );

            PointValuePair pair1 = optimize.optimize(
                    new ObjectiveFunction(m_function),
                    new MaxEval(MAX_EVALS_COUNT),
                    GoalType.MAXIMIZE,
                    new InitialGuess(m_startPoint)/*,
                    m_bounds*/    // PowellOptimizer not supports bounds
            );

            console("PowellOptimizer: point=" + Arrays.toString(pair1.getPoint()) + "; value=" + pair1.getValue()
                    + ";; optimize: Evaluations=" + optimize.getEvaluations() + "; Iterations=" + optimize.getIterations());
        } catch (Exception e) {
            console("error: " + e);
            e.printStackTrace();
        }


        // -----------------------------------------------------------------------------------
        try {
            log("start BOBYQAOptimizer=======================");

            // its value must be in the interval [n+2, (n+1)(n+2)/2]. Choices that exceed {@code 2n+1} are not recommended
            int numberOfInterpolationPoints = 2 * m_startPoint.length + 1; // + additionalInterpolationPoints
            MultivariateOptimizer optimize = new BOBYQAOptimizer(numberOfInterpolationPoints,
                    BOBYQAOptimizer.DEFAULT_INITIAL_RADIUS,
                    BOBYQAOptimizer.DEFAULT_STOPPING_RADIUS);

            PointValuePair pair1 = optimize.optimize(
                    new ObjectiveFunction(m_function),
                    new MaxEval(MAX_EVALS_COUNT),
                    GoalType.MAXIMIZE,
                    new InitialGuess(m_startPoint),
                    m_bounds
            );

            console("BOBYQAOptimizer: point=" + Arrays.toString(pair1.getPoint()) + "; value=" + pair1.getValue()
                    + ";; optimize: Evaluations=" + optimize.getEvaluations() + "; Iterations=" + optimize.getIterations());
        } catch (Exception e) {
            console("error: " + e);
            e.printStackTrace();
        }

        // -----------------------------------------------------------------------------------
        try {
            log("start SimplexOptimizer=======================");
            double relativeThreshold = 1e-3;
            double absoluteThreshold = 1e-5;
            MultivariateOptimizer optimize = new SimplexOptimizer(relativeThreshold, absoluteThreshold);

            PointValuePair pair1 = optimize.optimize(
                    new ObjectiveFunction(m_function),
                    new MaxEval(MAX_EVALS_COUNT),
                    GoalType.MAXIMIZE,
                    new InitialGuess(m_startPoint),
                    new MultiDirectionalSimplex(m_startPoint.length) // new NelderMeadSimplex(initialGuess.length)
            );

            console("SimplexOptimizer: point=" + Arrays.toString(pair1.getPoint()) + "; value=" + pair1.getValue()
                    + ";; optimize: Evaluations=" + optimize.getEvaluations() + "; Iterations=" + optimize.getIterations());
        } catch (Exception e) {
            console("error: " + e);
            e.printStackTrace();
        }

//        double rel = 1e-8;
//        double abs = 1e-10;
//        int maxIterations = 2000;
//        //Double.NEGATIVE_INFINITY;
//        double stopFitness = 0;
//        boolean isActiveCMA = true;
//        int diagonalOnly = 20;
//        int checkFeasableCount = 1;
//        RandomGenerator random = new Well19937c();
//        boolean generateStatistics = false;
//        ConvergenceChecker<PointValuePair> checker = new SimpleValueChecker(rel, abs);
//        // Iterate this for stability in the initial guess
//        return new CMAESOptimizer(maxIterations, stopFitness, isActiveCMA, diagonalOnly, checkFeasableCount, random, generateStatistics, checker);



//        MultivariateOptimizer optimize =
//                new BOBYQAOptimizer(variables.size()*2);
//        //new PowellOptimizer(0.01, 0.05);
//        RandomGenerator rng = getRandomGenerator();
//        MultiStartMultivariateOptimizer multiOptimize = new MultiStartMultivariateOptimizer(optimize, numStarts, new UncorrelatedRandomVectorGenerator(variables.size(), new UniformRandomGenerator(rng)));
//        PointValuePair result = multiOptimize.optimize(
//                new MaxEval(evaluations),
//                new SimpleBounds(lower, upper),
//                goal,
//                new InitialGuess(mid),
//                new ObjectiveFunction(this)
//        );
//        apply(result.getPointRef());

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
            log("buildBounds field[" + field.name() + "] min=" + minim + "(" + min + "); max=" + maxim + "(" + max + ")");
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
            sb.append(field.name()).append("=").append(Utils.format5(start))
                    //.append("(").append(Utils.format5(strt)).append(")")
                    .append("; ");
        }
        console(sb.toString());
        return startPoint;
    }

    @Override public double logResults(int pad) {
        console("MultiDimensionalOptimizeProducer result: totalPriceRatio=" + m_maxTotalPriceRatio + " : " + m_maxConfig);
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
        console(" processedDays=" + Utils.format2(processedDays)
                + "; perDay=" + Utils.format8(perDay)
                + "; inYear=" + Utils.format8(inYear)
        );

        if (theBot != null) {
            theBot.sendMsg(botKey + ": DONE d:" + Utils.format6(perDay) + " y:" + Utils.format5(inYear), false);
        }
    }
}
