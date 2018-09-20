package bi.two.ts;

import bi.two.chart.TickData;
import bi.two.util.MapConfig;

import java.io.*;

public enum TradesWriter {
    simple("simple") {
        @Override public IInstance instance(MapConfig config) { return new FileInstance(config); }

        @Override public void writeTick(IInstance instance, TickData trade) throws IOException {
            long timestamp = trade.getTimestamp();
            float price = trade.getClosePrice();
            instance.write(Long.toString(timestamp).getBytes());
            instance.write(';');
            instance.write(Float.toString(price).getBytes());
            instance.write('\n');
        }
    };

    private final String m_name;

    TradesWriter(String name) {
        m_name = name;
    }

    public static TradesWriter get(String name) {
        for (TradesWriter tradesWriter : values()) {
            if (tradesWriter.m_name.equals(name)) {
                return tradesWriter;
            }
        }
        throw new RuntimeException("Unknown TradesWriter '" + name + "'");
    }

    public abstract IInstance instance(MapConfig config);

    public abstract void writeTick(IInstance instance, TickData tickData) throws IOException;


    // ---------------------------------------
    public interface IInstance {
        void write(byte[] bytes) throws IOException;
        void write(char c) throws IOException;
        void close() throws IOException;
    }

    // ---------------------------------------
    private static class FileInstance implements IInstance {
        private final OutputStream m_os;

        public FileInstance(MapConfig config) {
            String filePath = config.getString("tick.writer.file");
            File file = new File(filePath);
            File dir = file.getParentFile();
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    throw new RuntimeException("tick.writer.file parent dir creation error: " + dir.getAbsolutePath());
                }
            }

            FileOutputStream fos;
            try {
                fos = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("tick.writer.file open error: " + file.getAbsolutePath() + " : " + e, e);
            }
            m_os = new BufferedOutputStream(fos);
        }

        @Override public void write(byte[] bytes) throws IOException {
            m_os.write(bytes);
        }

        @Override public void write(char c) throws IOException {
            m_os.write(c);
        }

        @Override public void close() throws IOException {
            m_os.flush();
            m_os.close();
        }
    }
}
