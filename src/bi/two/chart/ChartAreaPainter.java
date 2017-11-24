package bi.two.chart;

import bi.two.ts.TimesSeriesData;
import bi.two.util.Utils;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChartAreaPainter {

    public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, long timeMin, long timeMax, Point crossPoint) {

    }

    //-----------------------------------------------------------------
    public static class PolynomChartAreaPainter extends ChartAreaPainter {
        private final TimesSeriesData m_ticksTs;

        public PolynomChartAreaPainter(TimesSeriesData ticksTs) {
            m_ticksTs = ticksTs;
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe,
                                             long timeMin, long timeMax, Point crossPoint) {
            List<? extends ITickData> ticks = m_ticksTs.getTicks();
            synchronized (ticks) {
                if (crossPoint != null) {
                    int crossX = crossPoint.x;
                    int rightTickIndex = findTickIndexFromX(ticks, crossX, xAxe);
                    if (rightTickIndex > 0) {
                        ITickData rightTick = ticks.get(rightTickIndex);
                        long rightTickMillis = rightTick.getTimestamp();

                        int start = 5;
                        int count = 5;
                        double velocitySum = 0;
                        for (int i = 0; i < count; i++) {
                            double velocity = paintFrame(g2, xAxe, yAxe, ticks, rightTickIndex, rightTickMillis, Utils.MIN_IN_MILLIS * (start + i));
                            velocitySum += velocity;
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

        private double paintFrame(Graphics2D g2, Axe.AxeLong xAxe, Axe yAxe, List<? extends ITickData> ticks, int rightTickIndex, long rightTickMillis, long frameSize) {
            long leftTickMillis = rightTickMillis - frameSize;
            int leftTickIndex = rightTickIndex - 1;
            int size = ticks.size();
            while (leftTickIndex < size) {
                ITickData tick = ticks.get(leftTickIndex);
                long timestamp = tick.getTimestamp();
                if (timestamp < leftTickMillis) {
                    leftTickIndex--;
                    break;
                }
                leftTickIndex++; // ticks are in reverse order than on screen. older ticks are  with bigger index
            }
            if (leftTickIndex > rightTickIndex) { // ticks are in reverse order than on screen
                ITickData leftTick = ticks.get(leftTickIndex);
                leftTickMillis = leftTick.getTimestamp();
                long frameWidth = rightTickMillis - leftTickMillis;
                final WeightedObservedPoints obs = new WeightedObservedPoints();
                for (int index = leftTickIndex, i = 0; index >= rightTickIndex; index--, i++) {
                    ITickData tick = ticks.get(index);
                    long timestamp = tick.getTimestamp();
                    float value = tick.getClosePrice();
                    long offset = timestamp - leftTickMillis;
                    double weight = (double) offset / frameWidth;
                    obs.add(weight, offset, value);
                }

                final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(3);
                final double[] coeff = fitter.fit(obs.toList());
                PolynomialFunction polynomialFunction = new PolynomialFunction(coeff);

                PolynomialFunction polynomialDerivative = polynomialFunction.polynomialDerivative();

                int lastX = Integer.MAX_VALUE;
                int lastY = Integer.MAX_VALUE;
//                            int lastY2 = Integer.MAX_VALUE;

                int xLeftLeft = xAxe.translateInt(leftTickMillis);
                int xRightRight = xAxe.translateInt(rightTickMillis);

                double velocity = polynomialDerivative.value(frameWidth);

//                double baseValue = polynomialFunction.value(frameWidth);

                for (int x = xLeftLeft; x <= xRightRight; x++) {
                    long time = (long) xAxe.translateReverse(x);
                    long xInFrame = time - leftTickMillis;
                    double value = polynomialFunction.value(xInFrame);
                    int y = yAxe.translateInt(value);

//                                double derivative = polynomialDerivative.value(xInFrame);
//                                int y2 = yAxe.translateInt(baseValue + derivative);

                    if (lastX != Integer.MAX_VALUE) {
                        g2.drawLine(lastX, lastY, x, y);
//                                    g2.drawLine(lastX, lastY2, x, y2);
                    }
                    lastX = x;
                    lastY = y;
//                                lastY2 = y2;
                }
                return velocity;
            }
            return 0;
        }
    }
    
    //-----------------------------------------------------------------
    public static class SplineChartAreaPainter extends ChartAreaPainter {
        private final TimesSeriesData m_ticksTs;
        private final int m_points;
        private ITickData[] m_ticks;
        private final SplineInterpolator m_spline = new SplineInterpolator();
        private final double[] m_interpolateX;
        private final double[] m_interpolateY;

        public SplineChartAreaPainter(TimesSeriesData ticksTs, int points) {
            m_ticksTs = ticksTs;
            m_points = points;
            m_ticks = new ITickData[m_points];
            m_interpolateX = new double[m_points];
            m_interpolateY = new double[m_points];
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe,
                                             long timeMin, long timeMax, Point crossPoint) {
            List<? extends ITickData> ticks = m_ticksTs.getTicks();
            synchronized (ticks) {
                if (crossPoint != null) {
                    int crossX = crossPoint.x;
                    int mainTickIndex = findTickIndexFromX(ticks, crossX, xAxe);
                    if (mainTickIndex > 0) {
                        ITickData iTickData = ticks.get(mainTickIndex);
                        int lastIndex = m_points - 1;
                        m_ticks[lastIndex] = iTickData; // [oldest, ... , newest]
                        long timestamp = iTickData.getTimestamp() - Utils.MIN_IN_MILLIS;
                        boolean allFine = true;
                        int ticksNum = ticks.size();
                        long prevTickTimestamp = timestamp+1;
                        for (int index = lastIndex - 1; index >= 0; index--) {
                            int tickIndex = findTickIndexFromMillis(ticks, timestamp);
                            if ((tickIndex > 0) && (tickIndex < ticksNum)) {
                                iTickData = ticks.get(tickIndex);
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

        public TicksChartAreaPainter(TickPainter tickPainter) {
            m_tickPainter = tickPainter;
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, long timeMin, long timeMax, Point crossPoint) {
            List<? extends ITickData> ticks = ticksData.getTicks();
            int highlightTickIndex = -1;
            synchronized (ticks) {
                if (crossPoint != null) {
                    int crossX = crossPoint.x;
                    highlightTickIndex = findTickIndexFromX(ticks, crossX, xAxe);
                }


                ITickData prevTick = null;
                int size = ticks.size();
                for (int i = 0; i < size; i++) {
                    ITickData tick = ticks.get(i);
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
            }
        }
    }

    private static int findTickIndexFromX(List<? extends ITickData> ticks, int crossX, Axe.AxeLong xAxe) {
        int highlightTickIndex = Collections.binarySearch(ticks, null, new Comparator<ITickData>() {
            @Override public int compare(ITickData td1, ITickData td2) {
                long millis = td1.getTimestamp();
                double tickX = xAxe.translateDouble(millis);
                return tickX > crossX
                        ? -1
                        : tickX < crossX
                            ? 1
                            : 0;
            }
        });
        if (highlightTickIndex < 0) {
            highlightTickIndex = -highlightTickIndex - 1;
        }
        if ((highlightTickIndex >= 1) && (highlightTickIndex < ticks.size())) {
            ITickData tick1 = ticks.get(highlightTickIndex);
            ITickData tick2 = ticks.get(highlightTickIndex - 1);

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

    private static int findTickIndexFromMillis(List<? extends ITickData> ticks, long millis) {
        int highlightTickIndex;
        highlightTickIndex = Collections.binarySearch(ticks, null, new Comparator<ITickData>() {
            @Override public int compare(ITickData td1, ITickData td2) {
                long tickMillis = td1.getTimestamp();
                return tickMillis > millis
                        ? -1
                        : tickMillis < millis
                            ? 1
                            : 0;
            }
        });
        if (highlightTickIndex < 0) {
            highlightTickIndex = -highlightTickIndex - 1;
        }
        if ((highlightTickIndex >= 1) && (highlightTickIndex > ticks.size())) {
            ITickData tick1 = ticks.get(highlightTickIndex);
            ITickData tick2 = ticks.get(highlightTickIndex - 1);

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
