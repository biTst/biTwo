package bi.two.util;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Log {
    public static ILog s_impl = new StdLog();

    public static void log(String s) {
        s_impl.log(s);
    }

    public static void console(String s) {
        s_impl.console(s);
    }

    public static void err(String s, Throwable t) {
        s_impl.err(s, t);
    }

    public interface ILog {
        void log(String s);
        void console(String s);
        void err(String s, Throwable t);
        void flush();
    }

    public static class NoLog implements ILog {
        @Override public void log(String s) { }
        @Override public void console(String s) {}
        @Override public void err(String s, Throwable t) { }
        @Override public void flush() { }
    }


    // -----------------------------------------------------------------------------
    public static class StdLog implements ILog {
        // synchronized -- do not mess 2 threads outputs
        @Override public synchronized void log(String s) {
            System.out.println(s);
        }

        @Override public void console(String s) { System.out.println(s); }

        // synchronized -- do not mess 2 threads outputs
        @Override public synchronized void err(String s, Throwable t) {
            System.out.println(s);
            t.printStackTrace();
        }

        @Override public void flush() { }
    }


    // -----------------------------------------------------------------------------
    public static class TimestampLog implements ILog {
        // synchronized -- do not mess 2 threads outputs
        @Override public synchronized void log(String s) {
            logInt(s);
        }

        @Override public void console(String s) {
            logInt(s);
        }

        // synchronized -- do not mess 2 threads outputs
        @Override public synchronized void err(String s, Throwable t) {
            logInt(s);
            t.printStackTrace();
        }

        @Override public void flush() { }

        private void logInt(String s) {
            System.out.println(System.currentTimeMillis() + ": " + s);
        }
    }


    // -----------------------------------------------------------------------------
    public static class FileLog implements ILog {
        static final String LOG_DIR = "logs";
        static final String DEF_LOG_FILE = "log.log";
        public static final String DEF_LOG_FILE_LOCATION = LOG_DIR + File.separator + DEF_LOG_FILE;

        private final ExecutorService m_threadPool;
        private final OutputStream m_logStream;
        private final OutputStream m_consoleStream;

        public FileLog() {
            this(DEF_LOG_FILE_LOCATION);
        }

        public FileLog(String logFileLocation) {
            m_threadPool = Executors.newSingleThreadExecutor();

            File logFile = new File(logFileLocation);
            File logDir = logFile.getParentFile();

            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("error: logs dir not created: logFileLocation=" + logFileLocation + "; dir=" + logDir.getAbsolutePath());
                }
            }

            String fileName = logFile.getName();
            int indx = fileName.lastIndexOf(".");
            String name = (indx > 0) ? fileName.substring(0, indx) : fileName;
            String ext = (indx > 0) ? fileName.substring(indx) : ""; // extension with dot

            if (logFile.exists()) {
                String newFileName = name + "-" + System.currentTimeMillis() + ext;
                logFile.renameTo(new File(logDir, newFileName));
            }
            try {
                m_logStream = new BufferedOutputStream(new FileOutputStream(logFile));
            } catch (FileNotFoundException e) {
                throw new RuntimeException("unable to open log file: " + e, e);
            }

            // ---------------------
            String consoleName = name + "-" + "console";
            String consoleFileName = consoleName + ext;
            File consoleFile = new File(logDir, consoleFileName);
            if (consoleFile.exists()) {
                String newFileName = consoleName + "-" + System.currentTimeMillis() + ext;
                consoleFile.renameTo(new File(logDir, newFileName));
            }

            try {
                m_consoleStream = new BufferedOutputStream(new FileOutputStream(consoleFile));
            } catch (FileNotFoundException e) {
                throw new RuntimeException("unable to open console log file: " + e, e);
            }
        }

        @Override public void log(final String s) {
            m_threadPool.execute(new Runnable() {
                @Override public void run() {
                    String str = System.currentTimeMillis() + ": " + s + "\n";
                    logInt(str, m_logStream);
                }
            });
        }

        @Override public void console(final String s) {
            m_threadPool.execute(new Runnable() {
                @Override public void run() {
                    String str = System.currentTimeMillis() + ": " + s + "\n";
                    logInt(str, m_logStream);
                    logInt(str, m_consoleStream);
                    System.out.print(str);
                }
            });
        }

        private void logInt(String str, OutputStream stream) {
            try {
                stream.write(str.getBytes());
            } catch (IOException e) {
                System.out.println("log error: " + e);
                e.printStackTrace();
            }
        }

        @Override public void err(final String s, final Throwable t) {
            m_threadPool.execute(new Runnable() {
                @Override public void run() {
                    System.out.println(s);
                    t.printStackTrace();

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(bos);
                    try {
                        ps.print(System.currentTimeMillis());
                        ps.print(": ");
                        ps.println(s);
                        t.printStackTrace(ps);
                    } finally {
                        ps.flush();
                        ps.close();
                    }
                    try {
                        bos.writeTo(m_logStream);
                        bos.writeTo(m_consoleStream);
                    } catch (IOException e) {
                        System.out.println("log error: " + e);
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override public void flush() {
            m_threadPool.execute(new Runnable() {
                @Override public void run() {
                    try {
                        m_logStream.flush();
                        m_consoleStream.flush();
                    } catch (IOException e) {
                        System.out.println("flush error: " + e);
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
