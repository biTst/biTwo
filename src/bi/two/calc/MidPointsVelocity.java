package bi.two.calc;

import bi.two.ts.ITimesSeriesData;

/* 2 lines mid points based velocity calculation */
public class MidPointsVelocity extends Base3PointsVelocity {
    public MidPointsVelocity(ITimesSeriesData tsd, long period, float multiplier) {
        super(tsd, period, multiplier);
    }

    @Override protected Float calcVelocity(long x1, float y1, long x2, float y2, long x3, float y3) {
        float mid1y = (y1 + y2) / 2;
        float mid2y = (y2 + y3) / 2;
        float diffY = mid2y - mid1y;

        long mid1x = (x1 + x2) / 2;
        long mid2x = (x2 + x3) / 2;
        long diffX = mid2x - mid1x;

        if (diffX > 0) {
            float velocity = (diffY / diffX) * m_multiplier;
            return velocity;
        }
        return null;
    }
}
