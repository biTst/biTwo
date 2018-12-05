package bi.two.calc;

import bi.two.ts.BaseTimesSeriesData;

import java.util.ArrayList;
import java.util.List;

// --------------------------------------------------------------------------------------
public class VelocityArray {
    public final List<BaseTimesSeriesData> m_velocities = new ArrayList<>();
    public final Average m_velocityAvg;

    public VelocityArray(BaseTimesSeriesData tsd, long barSize, float velocityStart, float velocityStep, int steps, int multiplier) {
        for (int i = -steps; i <= steps; i++) {
            PolynomialSplineVelocity velocity = new PolynomialSplineVelocity(tsd, (long) (barSize * (velocityStart + i * velocityStep)), multiplier);
            m_velocities.add(velocity);
        }
        m_velocityAvg = new Average(m_velocities, tsd);
    }
}
