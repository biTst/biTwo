package bi.two.chart;

import bi.two.exch.OrderSide;
import bi.two.util.Utils;

import java.awt.Graphics2D;

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

    public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe, boolean highlightTick) {}
}
