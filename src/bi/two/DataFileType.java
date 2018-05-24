package bi.two;

import bi.two.chart.TickData;
import bi.two.chart.TickVolumeData;

import java.util.*;
import java.util.concurrent.TimeUnit;

public enum DataFileType {
    CSV("csv") {
        @Override public TickData parseLine(String line) {
            int indx1 = line.indexOf(",");
            if (indx1 > 0) {
                int priceIndex = indx1 + 1;
                int indx2 = line.indexOf(",", priceIndex);
                if (indx2 > 0) {
                    String timestampStr = line.substring(0, indx1);
                    String priceStr = line.substring(priceIndex, indx2);
                    String volumeStr = line.substring(indx2 + 1);

                    //System.out.println("timestampStr = " + timestampStr +"; priceStr = " + priceStr +"; volumeStr = " + volumeStr );

                    long timestampSeconds = Long.parseLong(timestampStr);
                    float price = Float.parseFloat(priceStr);
                    float volume = Float.parseFloat(volumeStr);

                    long timestampMs= timestampSeconds * 1000;
                    TickVolumeData tickData = new TickVolumeData(timestampMs, price, volume);
                    return tickData;
                }
            }
            return null;
        }
    },
    TABBED("tabbed") {
        private final long STEP = TimeUnit.MINUTES.toMillis(5);

        private long m_time = 0;

        @Override public TickData parseLine(String line) {
            String[] extra = line.split("\t");
            float price = Float.parseFloat(extra[0]);
            m_time += STEP;
            Main.TickExtraData tickData = new Main.TickExtraData(m_time, price, extra);
            return tickData;
        }
    },
    BITFINEX("bitfinex") {
//        198787874,1519054093604,0.00926031,11160
        @Override public TickData parseLine(String line) {
            int length = line.length();
            if (length > 9) {
                String[] tokens = line.split(",");
                String millisStr = tokens[1];
                String priceStr = tokens[3];
                long millis = Long.parseLong(millisStr);
                float price = Float.parseFloat(priceStr);
                TickData tickData = new TickData(millis, price);
                return tickData;
            }
            return null;
        }
    },
    FOREX("forex") {
//        #<TICKER>,<DTYYYYMMDD>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>
//        EURUSD,20010102,230100,0.9507,0.9507,0.9507,0.9507,4
        @Override public TickData parseLine(String line) {
            String[] tokens = line.split(",");
            String date = tokens[1];
            String yearStr = date.substring(0, 4);
            String monthStr = date.substring(4, 6);
            String dayStr = date.substring(6, 8);
            String time = tokens[2];
            String hourStr = time.substring(0, 2);
            String minStr = time.substring(2, 4);

            int year = Integer.parseInt(yearStr) - 1900;
            int month = Integer.parseInt(monthStr);
            int day = Integer.parseInt(dayStr);
            int hour = Integer.parseInt(hourStr);
            int min = Integer.parseInt(minStr);

            Date parsed = new Date(year, month, day, hour, min, 0);
            long millis = parsed.getTime();

            String close = tokens[6];
            float price = Float.parseFloat(close);
            TickData tickData = new TickData(millis, price);
            return tickData;
        }
    },
    FOREX2("forex2") {
        private GregorianCalendar m_gmtCalendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.getDefault());

        @Override public TickData parseLine(String line) {
            // 20170901 000000727,1.190130,1.190170,0
            int indx1 = line.indexOf(' ');
            int indx2 = line.indexOf(',');
            int indx3 = line.indexOf(',', indx2 + 1);
            int indx4 = line.indexOf(',', indx3 + 1);
            String date = line.substring(0, indx1); // "20170901"
            String yearStr = date.substring(0, 4);
            String monthStr = date.substring(4, 6);
            String dayStr = date.substring(6, 8);
            String time = line.substring(indx1 + 1, indx2); // "000000727"
            String hourStr = time.substring(0, 2);
            String minStr = time.substring(2, 4);
            String secStr = time.substring(4, 6);
            String millisStr = time.substring(6, 9);

            int year = Integer.parseInt(yearStr);
            int month = Integer.parseInt(monthStr);
            int day = Integer.parseInt(dayStr);
            int hour = Integer.parseInt(hourStr);
            int min = Integer.parseInt(minStr);
            int sec = Integer.parseInt(secStr);
            int mil = Integer.parseInt(millisStr);

            m_gmtCalendar.set(year, month - 1, day, hour, min, sec); // month is zero-based
            m_gmtCalendar.set(Calendar.MILLISECOND, mil);
            long millis = m_gmtCalendar.getTimeInMillis();

            String bidStr = line.substring(indx2 + 1, indx3);
            float bid = Float.parseFloat(bidStr);
            String askStr = line.substring(indx3 + 1, indx4);
            float ask = Float.parseFloat(askStr);
            TickData tickData = new TickData(millis, (bid + ask) / 2);
            return tickData;
        }
    },
    SIMPLE("forex2") {
        @Override public TickData parseLine(String line) {
            // 1526858169812;8444.0
            int indx1 = line.indexOf(';');
            String timeStr = line.substring(0, indx1); // "1526858169812"
            String priceStr = line.substring(indx1 + 1); // "8444.0"

            long time = Long.parseLong(timeStr);
            float price = Float.parseFloat(priceStr);
            TickData tickData = new TickData(time, price);
            return tickData;
        }
    },
    ;

    private final String m_type;

    DataFileType(String type) {
        m_type = type;
    }

    public static DataFileType get(String type) {
        for (DataFileType dataFileType : values()) {
            if (dataFileType.m_type.equals(type)) {
                return dataFileType;
            }
        }
        throw new RuntimeException("Unknown DataFileType '" + type + "'");
    }

    public TickData parseLine(String line) { throw new RuntimeException("must be overridden"); }
}
