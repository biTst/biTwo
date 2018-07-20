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
    }

    public static class NoLog implements ILog {
        @Override public void log(String s) { }
        @Override public void console(String s) {}
        @Override public void err(String s, Throwable t) { }
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

        private void logInt(String s) {
            System.out.println(System.currentTimeMillis() + ": " + s);
        }
    }


    // -----------------------------------------------------------------------------
    public static class FileLog implements ILog {
        static final String LOG_FILE = "log.log";

        private final ExecutorService m_threadPool;
        private final FileOutputStream m_fos;

        public FileLog() {
            m_threadPool = Executors.newSingleThreadExecutor();
            File file = new File(LOG_FILE);
            if (file.exists()) {
                String newFileName = "log-" + System.currentTimeMillis() + ".log";
                file.renameTo(new File(newFileName));
            }
            try {
                m_fos = new FileOutputStream(LOG_FILE);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("unable to open lof file: " + e, e);
            }
        }

        @Override public void log(final String s) {
            m_threadPool.execute(new Runnable() {
                @Override public void run() {
                    String str = System.currentTimeMillis() + ": " + s + "\n";
                    logInt(str);
                }
            });
        }

        @Override public void console(final String s) {
            m_threadPool.execute(new Runnable() {
                @Override public void run() {
                    String str = System.currentTimeMillis() + ": " + s + "\n";
                    logInt(str);
                    System.out.print(str);
                }
            });
        }

        private void logInt(String str) {
            try {
                m_fos.write(str.getBytes());
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
                        bos.writeTo(m_fos);
                    } catch (IOException e) {
                        System.out.println("log error: " + e);
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
