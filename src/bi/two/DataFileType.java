package bi.two;

import bi.two.chart.TickData;
import bi.two.chart.TickVolumeData;

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
    };

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
