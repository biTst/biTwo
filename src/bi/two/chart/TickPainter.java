package bi.two.chart;

import java.awt.*;

public enum TickPainter {
    TRADE {
        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe) {
            float price = tick.getMaxPrice();
            if ((price != Float.MAX_VALUE) && (price != 0)) {
                int y = yAxe.translateInt(price);
                long timestamp = tick.getTimestamp();
                int x = xAxe.translateInt(timestamp);
                g2.drawLine(x - 7, y, x + 7, y);
                g2.drawLine(x, y - 7, x, y + 7);
            }
        }
    },
    LINE {
        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe) {
            if (prevTick != null) {
                float price = tick.getMaxPrice();
                if ((price != Float.MAX_VALUE) && (price != 0)) {
                    float prevPrice = prevTick.getMaxPrice();
                    if ((prevPrice != Float.MAX_VALUE) && (prevPrice != 0)) {
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
        @Override public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe) {
            float maxPrice = tick.getMaxPrice();
            float minPrice = tick.getMinPrice();
            if ((minPrice != Float.MAX_VALUE) && (maxPrice != 0)) {
                long barSize = tick.getBarSize();
                int top = yAxe.translateInt(maxPrice);
                int bottom = yAxe.translateInt(minPrice);
                long timestamp = tick.getTimestamp();
                int right = xAxe.translateInt(timestamp);
                int left = xAxe.translateInt(timestamp - barSize + 1);
                g2.drawRect(left, top, right - left, bottom - top);
            }
        }
    };

    public void paintTick(Graphics2D g2, ITickData tick, ITickData prevTick, Axe xAxe, Axe yAxe) {}
}
