package bi.two.chart;

import bi.two.exch.OrderSide;
import bi.two.util.Utils;

import java.awt.*;

public enum TickPainter {
    TICK {
        static final int DIAMETER = 10;
        static final int RADIUS = DIAMETER/2;

        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe, boolean highlightTick) {
            float price = tick.getMaxPrice();
            if ((price != Utils.INVALID_PRICE) && (price != 0)) {
                int y = yAxe.translateInt(price);
                long timestamp = tick.getTimestamp();
                int x = xAxe.translateInt(timestamp);
                g2.drawLine(x - X_RADIUS, y, x + X_RADIUS, y);
                g2.drawLine(x, y - X_RADIUS, x, y + X_RADIUS);

                if(highlightTick) {
                    g2.fillOval(x - RADIUS, y - RADIUS, DIAMETER, DIAMETER);
                }
            }
        }
    },
    TICK_JOIN {
        static final int DIAMETER = 8;
        static final int RADIUS = DIAMETER / 2;

        private int[] m_max = new int[500];
        private int[] m_min = new int[500];
        private int m_xMin;
        private int m_width;
        private int m_highLightY;
        private int m_highLightX;

        public void startPaintTicks(int xMin, int xMax) {
            int width = xMax - xMin + 1;
            if (width > m_max.length) {
                m_max = new int[width];
                m_min = new int[width];
            }
            m_width = width;
            m_xMin = xMin;

            for (int i = 0; i < width; i++) {
                m_max[i] = -1;
                m_min[i] = -1;
            }

            m_highLightY = -1;
            m_highLightX = -1;
        }

        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe, boolean highlightTick) {
            float price = tick.getClosePrice();
            if ((price != Utils.INVALID_PRICE) && (price != 0)) {
                long timestamp = tick.getTimestamp();
                int x = xAxe.translateInt(timestamp);
                int indx = x - m_xMin;

                if (indx >= 0 && indx < m_width) {
                    int y = yAxe.translateInt(price);

                    m_max[indx] = Math.max(m_max[indx], y);
                    m_min[indx] = Math.min(m_max[indx], y);

                    if (highlightTick) {
                        m_highLightX = x;
                        m_highLightY = y;
                    }
                }
            }
        }

        public void endPaintTicks(Graphics2D g2) {
            for (int i = 0; i < m_width; i++) {
                int max = this.m_max[i];
                if (max != -1) {
                    int min = m_min[i];
                    int x = m_xMin + i;
                    if (max == min) { // paint cross
                        int y = max;
                        g2.drawLine(x - X_RADIUS, y, x + X_RADIUS, y);
                        g2.drawLine(x, y - X_RADIUS, x, y + X_RADIUS);
                    } else { // paint v-line
                        g2.drawLine(x, min, x, max);
                    }
                }
            }
            if (m_highLightX != -1) {
                g2.fillOval(m_highLightX - RADIUS, m_highLightY - RADIUS, DIAMETER, DIAMETER);
            }
        }
    },
    LINE {
        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe, boolean highlightTick) {
            if (prevTick != null) {
                float price = tick.getMaxPrice();
                if ((price != Utils.INVALID_PRICE) && !Float.isInfinite(price)) {
                    float prevPrice = prevTick.getMaxPrice();
                    if ((prevPrice != Utils.INVALID_PRICE) && !Float.isInfinite(prevPrice)) {
                        int y = yAxe.translateInt(price);
                        long timestamp = tick.getTimestamp();
                        int x = xAxe.translateInt(timestamp);

                        int prevY = yAxe.translateInt(prevPrice);
                        long prevTimestamp = prevTick.getTimestamp();
                        int prevX = xAxe.translateInt(prevTimestamp);

                        g2.drawLine(prevX, prevY, x, y);
                    }
                }
            }
        }
    },
    BAR {
        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe, boolean highlightTick) {
            float maxPrice = tick.getMaxPrice();
            float minPrice = tick.getMinPrice();
            if ((minPrice != Utils.INVALID_PRICE) && (maxPrice != Utils.INVALID_PRICE)) {
                long barSize = tick.getBarSize();
                int top = yAxe.translateInt(maxPrice);
                int bottom = yAxe.translateInt(minPrice);
                long timestamp = tick.getTimestamp();
                int right = xAxe.translateInt(timestamp);
                int left = xAxe.translateInt(timestamp - barSize + 1);
                g2.drawRect(left, top, right - left, bottom - top);
            } else {
//                System.out.println("minPrice=" + minPrice + "; maxPrice=" + maxPrice);
            }
        }
    },
    RIGHT_CIRCLE {
        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe, boolean highlightTick) {
            float maxPrice = tick.getMaxPrice();
            if (maxPrice != Utils.INVALID_PRICE) {
                long barSize = tick.getBarSize();

                long timestamp = tick.getTimestamp();
                int right = xAxe.translateInt(timestamp);
                int left = (barSize > 0) ? xAxe.translateInt(timestamp - barSize + 1) : right - 12;
                int width = right - left;
                if (width > 4) {
                    width = width / 2;
                }
                int radius = width / 2;

                int centerY = yAxe.translateInt(maxPrice);

                g2.fillOval(right - radius, centerY - radius, width, width);
            } else {
//                System.out.println("minPrice=" + minPrice + "; maxPrice=" + maxPrice);
            }
        }
    },
    TRADE {
        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe, boolean highlightTick) {
            TradeData trade = (TradeData) tick;
            float price = trade.getClosePrice();
            if ((price != Utils.INVALID_PRICE) && (price != 0)) {
                int y = yAxe.translateInt(price);
                long timestamp = tick.getTimestamp();
                int x = xAxe.translateInt(timestamp);
                if (trade.m_side == OrderSide.BUY) { // arrow up
                    g2.drawLine(x - X_RADIUS, y + X_RADIUS, x, y - X_RADIUS);
                    g2.drawLine(x + X_RADIUS, y + X_RADIUS, x, y - X_RADIUS);
                    g2.drawLine(x - X_RADIUS, y + X_RADIUS, x + X_RADIUS, y + X_RADIUS);
                } else {
                    g2.drawLine(x - X_RADIUS, y - X_RADIUS, x, y + X_RADIUS);
                    g2.drawLine(x + X_RADIUS, y - X_RADIUS, x, y + X_RADIUS);
                    g2.drawLine(x - X_RADIUS, y - X_RADIUS, x + X_RADIUS, y - X_RADIUS);
                }
            }
        }
    };

    public static final int X_RADIUS = 4;

    public void startPaintTicks(int xMin, int xMax) {}
    public abstract void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe, boolean highlightTick);
    public void endPaintTicks(Graphics2D g2) {}
}
