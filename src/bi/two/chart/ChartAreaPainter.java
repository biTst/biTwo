package bi.two.chart;

import bi.two.Colors;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.Utils;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static bi.two.util.Log.console;

public class ChartAreaPainter {

    public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, int xMin, int xMax, long timeMin, long timeMax, Point crossPoint) {

    }

    //-----------------------------------------------------------------
    public static class PolynomChartAreaPainter extends ChartAreaPainter {
        private final TicksTimesSeriesData m_ticksTs;
        private double m_shareVelocity;
        private double m_shareError;

        public PolynomChartAreaPainter(TicksTimesSeriesData ticksTs) {
            m_ticksTs = ticksTs;
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe,
                                             int xMin, int xMax, long timeMin, long timeMax, Point crossPoint) {
            synchronized (m_ticksTs.syncObject()) {
                if (crossPoint != null) {
                    int crossX = crossPoint.x;
                    int rightTickIndex = findTickIndexFromX(m_ticksTs, crossX, xAxe);
                    if (rightTickIndex > 0) {
                        ITickData rightTick = m_ticksTs.getTick(rightTickIndex);
                        long rightTickMillis = rightTick.getTimestamp();

                        console("--------------------------------------------------------------");
                        int start = 3;
                        int count = 14;
                        double velocitySum = 0;
                        TreeMap<Double, Double> map = new TreeMap<>();
                        for (int i = 0; i < count; i++) {
                            paintFrame(g2, xAxe, yAxe, rightTickIndex, rightTickMillis, Utils.MIN_IN_MILLIS * (start + i), i);
                            map.put(m_shareError, m_shareVelocity);
                            console("velocity: " + m_shareVelocity + "; error: " + m_shareError);
                            velocitySum += m_shareVelocity;
                        }

                        double velocityAvg = velocitySum / count;

                        float rightTickValue = rightTick.getClosePrice();

                        int x1 = xAxe.translateInt(rightTickMillis);
                        int y1 = yAxe.translateInt(rightTickValue);

                        g2.setColor(Color.blue);
                        g2.drawLine(x1, y1, x1 + 40, (int) (y1 - velocityAvg * 100000000000d));
                    }
                }
            }
        }

        private void paintFrame(Graphics2D g2, Axe.AxeLong xAxe, Axe yAxe,
                                int rightTickIndex, long rightTickMillis, long frameSize, int lineNum) {
            long leftTickMillis = rightTickMillis - frameSize;
            int leftTickIndex = rightTickIndex - 1;
            int size = m_ticksTs.getTicksNum();
            while (leftTickIndex < size) {
                ITickData tick = m_ticksTs.getTick(leftTickIndex);
                long timestamp = tick.getTimestamp();
                if (timestamp < leftTickMillis) {
                    leftTickIndex--;
                    break;
                }
                leftTickIndex++; // ticks are in reverse order than on screen. older ticks are with bigger index
            }
            if (leftTickIndex >= size) {
                leftTickIndex = size - 1;
            }
            if (leftTickIndex > rightTickIndex) { // ticks are in reverse order than on screen
                ITickData leftTick = m_ticksTs.getTick(leftTickIndex);
                leftTickMillis = leftTick.getTimestamp();
                long frameWidth = rightTickMillis - leftTickMillis;
                final WeightedObservedPoints obs = new WeightedObservedPoints();
                for (int index = leftTickIndex, i = 0; index >= rightTickIndex; index--, i++) {
                    ITickData tick = m_ticksTs.getTick(index);
                    long timestamp = tick.getTimestamp();
                    float value = tick.getClosePrice();
                    long offset = timestamp - leftTickMillis;
                    double weight = (double) offset / frameWidth;
                    obs.add(weight, offset, value);
                }

                final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(3);
                List<WeightedObservedPoint> points = obs.toList();
                final double[] coeff = fitter.fit(points);
                PolynomialFunction polynomialFunction = new PolynomialFunction(coeff);

                PolynomialFunction polynomialDerivative = polynomialFunction.polynomialDerivative();

                int lastX = Integer.MAX_VALUE;
                int lastY = Integer.MAX_VALUE;

                int xLeftLeft = xAxe.translateInt(leftTickMillis);
                int xRightRight = xAxe.translateInt(rightTickMillis);


g2.setColor((lineNum==0) ? Color.red : Colors.alpha(Color.red, 100));
                for (int x = xLeftLeft; x <= xRightRight; x++) {
                    long time = (long) xAxe.translateReverse(x);
                    long xInFrame = time - leftTickMillis;
                    double value = polynomialFunction.value(xInFrame);
                    int y = yAxe.translateInt(value);

                    if (lastX != Integer.MAX_VALUE) {
                        g2.drawLine(lastX, lastY, x, y);
                    }
                    lastX = x;
                    lastY = y;
                }

                int pointsNum = points.size();
                double errorSum = 0;
                for (WeightedObservedPoint wop : points) {
                    double modelValue = polynomialFunction.value(wop.getX());
                    double observedValue = wop.getY();
                    double weight = wop.getWeight();
                    double error = Math.pow(observedValue - modelValue, 2) * weight;
                    errorSum += error;
                }
                m_shareError = Math.sqrt(errorSum / pointsNum);

                m_shareVelocity = polynomialDerivative.value(frameWidth);
                return;
            }
            m_shareVelocity = 0;
            m_shareError = 0;
        }
    }
    
    //-----------------------------------------------------------------
    public static class SplineChartAreaPainter extends ChartAreaPainter {
        private final TicksTimesSeriesData m_ticksTs;
        private final int m_points;
        private ITickData[] m_ticks;
        private final SplineInterpolator m_spline = new SplineInterpolator();
        private final double[] m_interpolateX;
        private final double[] m_interpolateY;

        public SplineChartAreaPainter(TicksTimesSeriesData ticksTs, int points) {
            m_ticksTs = ticksTs;
            m_points = points;
            m_ticks = new ITickData[m_points];
            m_interpolateX = new double[m_points];
            m_interpolateY = new double[m_points];
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe,
                                             int xMin, int xMax, long timeMin, long timeMax, Point crossPoint) {
            synchronized (m_ticksTs.syncObject()) {
                if (crossPoint != null) {
                    int crossX = crossPoint.x;
                    int mainTickIndex = findTickIndexFromX(m_ticksTs, crossX, xAxe);
                    if (mainTickIndex > 0) {
                        ITickData iTickData = m_ticksTs.getTick(mainTickIndex);
                        int lastIndex = m_points - 1;
                        m_ticks[lastIndex] = iTickData; // [oldest, ... , newest]
                        long timestamp = iTickData.getTimestamp() - Utils.MIN_IN_MILLIS;
                        boolean allFine = true;
                        int ticksNum = m_ticksTs.getTicksNum();
                        long prevTickTimestamp = timestamp+1;
                        for (int index = lastIndex - 1; index >= 0; index--) {
                            int tickIndex = findTickIndexFromMillis(m_ticksTs, timestamp);
                            if ((tickIndex > 0) && (tickIndex < ticksNum)) {
                                iTickData = m_ticksTs.getTick(tickIndex);
                                long thisTickTimestamp = iTickData.getTimestamp();
                                if (thisTickTimestamp < prevTickTimestamp) { // MonotonicSequence - points X should strictly increasing
                                    m_ticks[index] = iTickData; // [oldest, ... , newest]
                                    prevTickTimestamp = thisTickTimestamp;
                                } else { // non MonotonicSequence
                                    allFine = false;
                                    break;
                                }
                            } else {
                                allFine = false;
                                break;
                            }
                            timestamp -= Utils.MIN_IN_MILLIS;
                        }

                        if (allFine) {
                            int rightIndex = m_ticks.length - 1;
                            for (int i = 0; i < rightIndex; i++) {
                                ITickData tick1 = m_ticks[i];
                                ITickData tick2 = m_ticks[i + 1];
                                drawLine(g2, xAxe, yAxe, tick1, tick2);
                            }

                            for (int i = 0; i <= rightIndex; i++) {
                                ITickData tick = m_ticks[i];
                                m_interpolateX[i] = tick.getTimestamp();
                                m_interpolateY[i] = tick.getClosePrice();
                            }


                            PolynomialSplineFunction polynomialFunc = null;
                            try {
//                            polynomialFunc = m_interpolator.interpolate(minMillis, left.m_value, midMillis, mid.m_value, maxMillis, right.m_value);
                                polynomialFunc = m_spline.interpolate(m_interpolateX, m_interpolateY);
                            } catch (Exception e) {
                                e.printStackTrace();
                                return;
                            }


                            PolynomialFunction[] polynomials = polynomialFunc.getPolynomials();

                            int lastX = Integer.MAX_VALUE;
                            int lastY = Integer.MAX_VALUE;

                            ITickData leftTick = m_ticks[0];
                            long leftTickMillis = leftTick.getTimestamp();
                            int xLeftLeft = xAxe.translateInt(leftTickMillis);

                            ITickData rightTick = m_ticks[rightIndex];
                            long rightTickMillis = rightTick.getTimestamp();
                            int xRightRight = xAxe.translateInt(rightTickMillis);

                            for (int x = xLeftLeft; x <= xRightRight; x++) {
                                long time = (long) xAxe.translateReverse(x);

                                int polyIndex = m_points - 2;
                                for (int i = 1; i < m_points; i++) {
                                    ITickData tick = m_ticks[i];
                                    long tickTimestamp = tick.getTimestamp();
                                    if (time < tickTimestamp) {
                                        polyIndex = i - 1;
                                        break;
                                    }
                                }

                                PolynomialFunction polynomial = polynomials[polyIndex];
//                              UnivariateFunction derivative = polynomial.derivative();
                                ITickData polyTick = m_ticks[polyIndex];
                                long polyTickTimestamp = polyTick.getTimestamp();
                                long offset = time - polyTickTimestamp;
                                double value = polynomial.value(offset);
                                int yy = yAxe.translateInt(value);
                                if (lastX != Integer.MAX_VALUE) {
                                    g2.drawLine(lastX, lastY, x, yy);
                                }
                                lastX = x;
                                lastY = yy;
                            }
                        }
                    }
                }
            }
        }

        private void drawLine(Graphics2D g2, List<? extends ITickData> ticks, Axe.AxeLong xAxe, Axe yAxe, int tickIndex1, int tickIndex2) {
            ITickData tick1 = ticks.get(tickIndex1);
            ITickData tick2 = ticks.get(tickIndex2);

            drawLine(g2, xAxe, yAxe, tick1, tick2);
        }

        private void drawLine(Graphics2D g2, Axe.AxeLong xAxe, Axe yAxe, ITickData tick1, ITickData tick2) {
            long millis1 = tick1.getTimestamp();
            long millis2 = tick2.getTimestamp();

            int x1 = xAxe.translateInt(millis1);
            int x2 = xAxe.translateInt(millis2);

            float value1 = tick1.getClosePrice();
            float value2 = tick2.getClosePrice();

            int y1 = yAxe.translateInt(value1);
            int y2 = yAxe.translateInt(value2);

            g2.drawLine(x1, y1, x2, y2);
        }
    }

    //-----------------------------------------------------------------
    public static class TicksChartAreaPainter extends ChartAreaPainter {
        private final TickPainter m_tickPainter;

        TicksChartAreaPainter(TickPainter tickPainter) {
            m_tickPainter = tickPainter;
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, int xMin, int xMax, long timeMin, long timeMax, Point crossPoint) {
            int highlightTickIndex = -1;
            synchronized (ticksData.syncObject()) {
                if (crossPoint != null) {
                    int crossX = crossPoint.x;
                    highlightTickIndex = findTickIndexFromX(ticksData, crossX, xAxe);
                }

                ITickData prevTick = null;

                int size = ticksData.getTicksNum();
                if (size <= 1) {
                    return; // ~nothing to paint
                }

                m_tickPainter.startPaintTicks(xMin, xMax);

//                long timeDiff = timeMax - timeMin;
//Log.console("paintChartArea xMin=" + xMin + "; xMax=" + xMax + "; timeMin=" + timeMin + "; timeMax=" + timeMax + "; timeDiff=" + Utils.millisToYDHMSStr(timeDiff));

                // ticks are in reverse order - first at right

//                ITickData firstTick = ticksData.getTick(size - 1);
//                ITickData lastTick = ticksData.getTick(0);
//                long firstTickTimestamp = firstTick.getTimestamp();
//                long lastTickTimestamp = lastTick.getTimestamp();
//                long ticksDiff = lastTickTimestamp - firstTickTimestamp;
//Log.console(" firstTickTimestamp=" + firstTickTimestamp + "; lastTickTimestamp=" + lastTickTimestamp + "; ticksDiff=" + Utils.millisToYDHMSStr(ticksDiff) + "; size=" + size);

                int maxTickIndex = findTickIndexFromMillis(ticksData, timeMax);
                maxTickIndex--; // add 1 point to draw the line
                if (maxTickIndex < 0) {
                    maxTickIndex = 0;
                } else if (maxTickIndex >= size) {
                    maxTickIndex = size - 1;
                }
                int minTickIndex = findTickIndexFromMillis(ticksData, timeMin);
                minTickIndex++; // add 1 point to draw the line
                if (minTickIndex < 0) {
                    minTickIndex = 0;
                } else if (minTickIndex >= size) {
                    minTickIndex = size - 1;
                }
//Log.console(" minTickIndex=" + minTickIndex + "; maxTickIndex=" + maxTickIndex);

                ITickData maxTick = ticksData.getTick(maxTickIndex);
//                long maxTickTimestamp = maxTick.getTimestamp();
//Log.console("  maxTick=" + maxTick + "; maxTickTimestamp=" + maxTickTimestamp);

                ITickData minTick = ticksData.getTick(minTickIndex);
//                long minTickTimestamp = minTick.getTimestamp();
//Log.console("  minTick=" + minTick + "; minTickTimestamp=" + minTickTimestamp);

                int from = maxTickIndex; // 0;
                int to = minTickIndex;   // size;
                for (int i = from; i < to; i++) {
                    ITickData tick = ticksData.getTick(i);
                    long timestamp = tick.getTimestamp();
                    boolean timeAfterFrame = (timestamp > timeMax);
                    if (!timeAfterFrame) {
                        boolean highlightTick = (i == highlightTickIndex);
                        m_tickPainter.paintTick(g2, tick, prevTick, xAxe, yAxe, highlightTick);
                        boolean timeBeforeFrame = (timestamp < timeMin);
                        if (timeBeforeFrame) {
                            break;
                        }
                    }
                    prevTick = tick;
                }
                m_tickPainter.endPaintTicks(g2);
            }
        }
    }

    private static int findTickIndexFromX(ITicksData ticksData, final int crossX, final Axe.AxeLong xAxe) {
        Comparator<ITickData> comparator = new Comparator<ITickData>() {
            @Override public int compare(ITickData td1, ITickData td2) {
                long millis = td1.getTimestamp();
                double tickX = xAxe.translateDouble(millis);
                return tickX > crossX
                        ? 1
                        : tickX < crossX
                        ? -1
                        : 0;
            }
        };

        int highlightTickIndex = ticksData.binarySearch(null, comparator);
        if (highlightTickIndex < 0) {
            highlightTickIndex = -highlightTickIndex - 1;
        }
        if ((highlightTickIndex >= 1) && (highlightTickIndex < ticksData.getTicksNum())) {
            ITickData tick1 = ticksData.getTick(highlightTickIndex);
            ITickData tick2 = ticksData.getTick(highlightTickIndex - 1);

            long timestamp1 = tick1.getTimestamp();
            double x1 = xAxe.translateDouble(timestamp1);
            long timestamp2 = tick2.getTimestamp();
            double x2 = xAxe.translateDouble(timestamp2);

            double diff1 = Math.abs(x1 - crossX);
            double diff2 = Math.abs(x2 - crossX);
            if (diff2 < diff1) {
                highlightTickIndex--;
            }
            return highlightTickIndex;
        }
        return -1;
    }

    private static int findTickIndexFromMillis(ITicksData ticksData, final long millis) {
        Comparator<ITickData> comparator = new Comparator<ITickData>() {
            @Override public int compare(ITickData td1, ITickData td2) {
                long tickMillis = td1.getTimestamp();
                return Long.compare(tickMillis, millis);
            }
        };

        int highlightTickIndex = ticksData.binarySearch(null, comparator);
        if (highlightTickIndex < 0) {
            highlightTickIndex = -highlightTickIndex - 1;
        }
        if ((highlightTickIndex >= 1) && (highlightTickIndex > ticksData.getTicksNum())) {
            ITickData tick1 = ticksData.getTick(highlightTickIndex);
            ITickData tick2 = ticksData.getTick(highlightTickIndex - 1);

            long timestamp1 = tick1.getTimestamp();
            long timestamp2 = tick2.getTimestamp();

            long diff1 = Math.abs(timestamp1 - millis);
            long diff2 = Math.abs(timestamp2 - millis);
            if (diff2 < diff1) {
                highlightTickIndex--;
            }
        }
        return highlightTickIndex;
    }
}
