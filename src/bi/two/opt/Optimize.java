package bi.two.opt;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.*;

public class Optimize {
    public static void optimizeOneDimension() {
        UnivariateFunction univariateFunction = new UnivariateFunction() {
            @Override public double value(double v) {
                return 0;
            }
        };
        double relativeTolerance = 1e-10;
        UnivariateOptimizer optimizer = new BrentOptimizer(relativeTolerance, 1e-14);
        double low = 0;
        double high = 1;
        UnivariatePointValuePair point = optimizer.optimize(new MaxEval(200),
                new UnivariateObjectiveFunction(univariateFunction),
                GoalType.MAXIMIZE,
                new SearchInterval(low, high));
        int iterations = optimizer.getIterations();
    }
}
