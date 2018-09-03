package bi.two.main2;

import bi.two.DataFileType;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.util.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TicksCacheReader {
    public final DataFileType m_dataFileType;
    public final File m_cacheDir;

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public TicksCacheReader(DataFileType dataFileType, String cacheDir) {
        this(dataFileType, new File(cacheDir));
    }

    public TicksCacheReader(DataFileType dataFileType, File cacheDir) {
        m_dataFileType = dataFileType;
        m_cacheDir = cacheDir;
    }

    public List<TickData> loadTrades(String fileName) throws IOException {
        File cacheFile = new File(m_cacheDir, fileName);
        BufferedReader br = new BufferedReader(new FileReader(cacheFile));
        try {
            List<TickData> ret = new ArrayList<>();
            String line;
            while((line = br.readLine())!=null) {
                TickData tickData = m_dataFileType.parseLine(line);
                ret.add(tickData);
            }
            return ret;
        } finally {
            br.close();
        }
    }

    public void writeToCache(List<? extends ITickData> trades, String fileName) {
        if (!m_cacheDir.exists()) {
            m_cacheDir.mkdirs();
        }
        File file = new File(m_cacheDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            try {
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                try {
                    for (int i = trades.size() - 1; i >= 0; i--) {
                        ITickData trade = trades.get(i);
                        long timestamp = trade.getTimestamp();
                        float price = trade.getClosePrice();
                        bos.write(Long.toString(timestamp).getBytes());
                        bos.write(';');
                        bos.write(Float.toString(price).getBytes());
                        bos.write('\n');
                    }
                } finally {
                    bos.flush();
                    bos.close();
                }
            } finally {
                fos.close();
            }

            log(" writeToCache ok; fileLen=" + file.length());
        } catch (Exception e) {
            err("writeToCache error: " + e, e);
            throw new RuntimeException("writeToCache error: " + e, e);
        }
    }

}
