package bi.two;

import bi.two.chart.TickData;
import bi.two.chart.TickVolumeData;

import java.util.Date;
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
        @Override public TickData parseLine(String line) {
            // 20170901 000000727,1.190130,1.190170,0
            String[] tokens = line.split(",");
            String dateTime = tokens[0]; // "20170901 000000727"
            String date = dateTime.substring(0, 8); // "20170901"
            String yearStr = date.substring(0, 4);
            String monthStr = date.substring(4, 6);
            String dayStr = date.substring(6, 8);
            String time = dateTime.substring(9, 18); // "000000727"
            String hourStr = time.substring(0, 2);
            String minStr = time.substring(2, 4);
            String secStr = time.substring(4, 6);
            String millisStr = time.substring(6, 9);

            int year = Integer.parseInt(yearStr) - 1900;
            int month = Integer.parseInt(monthStr);
            int day = Integer.parseInt(dayStr);
            int hour = Integer.parseInt(hourStr);
            int min = Integer.parseInt(minStr);
            int sec = Integer.parseInt(secStr);
            int mil = Integer.parseInt(millisStr);

            Date parsed = new Date(year, month, day, hour, min, sec);
            long millis = parsed.getTime();

            String bidStr = tokens[1];
            float bid = Float.parseFloat(bidStr);
            String askStr = tokens[2];
            float ask = Float.parseFloat(askStr);
            TickData tickData = new TickData(millis + mil, (bid + ask) / 2);
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
