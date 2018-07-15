package bi.two.main2;

import bi.two.DataFileType;
import bi.two.chart.TickData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TicksCacheReader {
    public final DataFileType m_dataFileType;
    public final File m_cacheDir;

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
}
