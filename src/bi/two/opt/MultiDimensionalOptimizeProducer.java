package bi.two.opt;

import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.SimpleBounds;

import java.util.List;

class MultiDimensionalOptimizeProducer extends OptimizeProducer {
    private final MultivariateFunction m_function;

    public MultiDimensionalOptimizeProducer(List<OptimizeConfig> optimizeConfigs, MapConfig algoConfig) {
        super(optimizeConfigs, algoConfig);

//            for (OptimizeConfig optimizeConfig : m_optimizeConfigs) {
//
//            }
        double[] startPoint = buildStartPoint(m_optimizeConfigs);
        SimpleBounds bounds = buildBounds(m_optimizeConfigs);

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
                    double val = value * fieldConfig.m_multiplier;
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

                    m_algoConfig.put(fieldName, value);
                    sb.append(fieldName).append("=").append(Utils.format5(val)).append("(").append(Utils.format5(value)).append("); ");
                }
                sb.append(")...");
                System.out.println(sb.toString());

                double value;
//                    try {
//                        Map<String, Double> averageProjected = processAllTicks(datas);
//                        log("averageProjected: " + averageProjected);
//                        value = averageProjected.get(algoName);
//                    } catch (Exception e) {
//                        err("error in optimize.function: " + e, e);
                value = 0;
//                    }
                System.out.println("...averageProjected=" + Utils.format5(value));
                return value;
            }
        };
        startThread();
    }

    @Override public void run() {
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
            double minim = min / fieldConfig.m_multiplier;
            double maxim = max / fieldConfig.m_multiplier;
            mins[i] = minim;
            maxs[i] = maxim;
            System.out.println("field[" + field.m_key + "] min=" + minim + "(" + min + "); max=" + maxim + "(" + max + ")");
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

}
