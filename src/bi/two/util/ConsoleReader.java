package bi.two.util;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

public abstract class ConsoleReader extends Thread {
    protected abstract void beforeLine();
    /** @return true if exit command detected */
    protected abstract boolean processLine(String line) throws Exception;

    public ConsoleReader() {
        super("ConsoleReader");
    }

    @Override public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while(!isInterrupted()) {
                beforeLine();
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                try {
                    boolean exit = processLine(line);
                    if(exit) {
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("error for command '"+line+"': " + e);
                    e.printStackTrace();
                }
            }
            System.err.println("ConsoleReader finished");
        } catch (Exception e) {
            System.err.println("error: " + e);
            e.printStackTrace();
        }
    }

    public static String readConsolePwd(String prefix) throws IOException {
        System.out.print(prefix);
        Console console = System.console();
        if (console == null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String pwd = br.readLine();
            return pwd;
        }
        String pwd = new String(console.readPassword());
        return pwd;
    }
}
