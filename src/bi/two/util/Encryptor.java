package bi.two.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

public class Encryptor {
    private static void log(String s) { System.out.println(s); }

    public static void main(String[] args) {
        try {
            log("Encryptor started on : " + new Date());

            File file = new File("cfg/encryptor.txt");
            if (checkFileReadAccess(file)) {
                FileReader reader = new FileReader(file);
                try {
                    go(reader, file.getParentFile());
                } finally {
                    reader.close();
                }
            }
        } catch (Exception e) {
            log("Encryptor error: " + e);
            e.printStackTrace();
        }
    }

    private static boolean checkFileReadAccess(File file) {
        boolean exists = file.exists();
        boolean isFile = file.isFile();
        boolean canRead = file.canRead();
        if (exists && isFile && canRead) {
            return true;
        }
        log("file access error: exists=" + exists + "; isFile=" + isFile + "; canRead=" + canRead + "; file=" + file);
        return false;
    }

    private static boolean checkFileWriteAccess(File file) throws IOException {
        if (!file.exists()) {
            boolean created = file.createNewFile();
            if (!created) {
                log("createNewFile error; file=" + file);
                return false;
            }
        }
        boolean canWrite = file.canWrite();
        if (canWrite) {
            return true;
        }
        log("file access error: canWrite=" + canWrite + "; file=" + file);
        return false;
    }

    private static void go(FileReader reader, File baseDir) throws Exception {
        Properties properties = new Properties();
        properties.load(reader);

        String command = properties.getProperty("command");
        log("command=" + command);
        if (command != null) {
            if (command.equals("encrypt")) {
                encrypt(properties, baseDir);
            } else {
                log("ERROR: unsupported command=" + command);
            }
        } else {
            log("ERROR: no command key");
        }
    }

    private static void encrypt(Properties properties, File baseDir) throws Exception {
        String input = properties.getProperty("encrypt.input");
        log("encrypt.input=" + input);
        if (input != null) {
            String output = properties.getProperty("encrypt.output");
            log("encrypt.output=" + output);
            if (output != null) {
                String keys = properties.getProperty("encrypt.keys");
                log("encrypt.keys=" + keys);
                if (keys != null) {
                    String pwd = properties.getProperty("encrypt.pwd");
                    encrypt1(input, output, keys, pwd, baseDir);
                } else {
                    log("ERROR: no encrypt.keys key");
                }
            } else {
                log("ERROR: no encrypt.output key");
            }
        } else {
            log("ERROR: no encrypt.input key");
        }
    }

    private static void encrypt1(String input, String output, String keys, String pwd, File baseDir) throws Exception {
        File inFile = new File(baseDir, input);
        if (checkFileReadAccess(inFile)) {
            FileReader reader = new FileReader(inFile);
            try {
                File outFile = new File(baseDir, output);
                if (checkFileWriteAccess(outFile)) {
                    FileWriter writer = new FileWriter(outFile);
                    try {
                        encrypt2(reader, writer, keys.split(";"), pwd);
                    } finally {
                        writer.close();
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

    private static void encrypt2(FileReader reader, FileWriter writer, String[] keys, String pwdIn) throws Exception {
        String pwd = pwdIn;
        if (pwdIn == null) {
            pwd = ConsoleReader.readConsolePwd("pwd>");
            if (pwd == null) {
                log("no console - use real console, not inside IDE");
                return;
            }
        }

        Properties propertiesIn = new Properties();
        propertiesIn.load(reader);

        Properties propertiesOut = new Properties();
        for (String key : keys) {
            String input = propertiesIn.getProperty(key);
            if (input == null) {
                throw new RuntimeException("no key '" + key + "' found in input");
            }
            String encrypted = encrypt3(input, pwd);
            propertiesOut.setProperty(key, encrypted);
        }
        propertiesOut.store(writer, "encr");
    }

    private static Cipher getCipher(String encryptionKey, int cipherMode) throws Exception {
        String encryptionAlgorithm = "AES";
        SecretKeySpec keySpecification = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), encryptionAlgorithm);
        Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
        cipher.init(cipherMode, keySpecification);

        return cipher;
    }

    private static String encrypt3(String input, String pwd) throws Exception {
        Cipher cipher = getCipher(pwd, Cipher.ENCRYPT_MODE);
        byte[] encryptedBytes = cipher.doFinal(input.getBytes());
        String output = DatatypeConverter.printBase64Binary(encryptedBytes);
        System.out.println("encrypted string: " + output);

        { // verify
            String verify = decrypt(output, pwd);
            if(!verify.equals(input)) {
                throw new RuntimeException("verify!=input");
            }
        }

        return output;
    }

    public static String decrypt(String encrypted, String pwd) throws Exception {
        Cipher cipher = getCipher(pwd, Cipher.DECRYPT_MODE);
        byte[] bytes = DatatypeConverter.parseBase64Binary(encrypted);
        byte[] plainBytes = cipher.doFinal(bytes);
        return new String(plainBytes);
    }
}
