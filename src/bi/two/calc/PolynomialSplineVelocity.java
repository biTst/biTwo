package bi.two.calc;

import bi.two.ts.ITimesSeriesData;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

/* 3 points PolynomialSpline based velocity calculation */
public class PolynomialSplineVelocity extends Base3PointsVelocity {
    private final Interpolator m_interpolator = new Interpolator();

    public PolynomialSplineVelocity(ITimesSeriesData tsd, long period, float multiplier) {
        super(tsd, period, multiplier);
    }

    @Override protected Float calcVelocity(long x1, float y1, long x2, float y2, long x3, float y3) {
        PolynomialSplineFunction polynomialFunc = m_interpolator.interpolate(x1, y1, x2, y2, x3, y3);
        PolynomialFunction[] polynomials = polynomialFunc.getPolynomials();
        PolynomialFunction polynomial = polynomials[1];
        UnivariateFunction derivative = polynomial.derivative();
        double derivativeValue2 = derivative.value(x3 - x2);
        if (!Double.isNaN(derivativeValue2)) {
            float velocity = (float) (derivativeValue2 * m_multiplier);
            return velocity;
        }
        return null;
    }
}
