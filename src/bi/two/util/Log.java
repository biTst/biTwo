package bi.two.util;

public class Log {
    public static ILog s_impl = new StdLog();

    public static void log(String s) {
        s_impl.log(s);
    }

    public static void err(String s, Throwable t) {
        s_impl.err(s, t);
    }

    public interface ILog {
        void log(String s);
        void err(String s, Throwable t);
    }

    public static class NoLog implements ILog {
        @Override public void log(String s) { }
        @Override public void err(String s, Throwable t) { }
    }

    public static class StdLog implements ILog {
        // synchronized -- do not mess 2 threads outputs
        @Override public synchronized void log(String s) {
            System.out.println(s);
        }

        // synchronized -- do not mess 2 threads outputs
        @Override public synchronized void err(String s, Throwable t) {
            System.out.println(s);
            t.printStackTrace();
        }
    }

    public static class TimestampLog implements ILog {
        // synchronized -- do not mess 2 threads outputs
        @Override public synchronized void log(String s) {
            System.out.println(System.currentTimeMillis() + ": " + s);
        }

        // synchronized -- do not mess 2 threads outputs
        @Override public synchronized void err(String s, Throwable t) {
            System.out.println(System.currentTimeMillis() + ": " + s);
            t.printStackTrace();
        }
    }
}
