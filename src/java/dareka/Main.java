package dareka;

import java.io.File;
import java.io.IOException;

import dareka.common.Config;
import dareka.common.Logger;
import dareka.processor.impl.Cache;

public class Main {
    // public so that external tools can read.
    public static final String VER_STRING = "NicoCache v0.45";

    // accessor for avoiding static link
    public static String getVersion() {
        return VER_STRING;
    }

    private static Server server;

    public static void stop() {
        if (server != null) {
            server.stop();
        }
    }

    public static void main(String[] args) {
        try {
            mainBody();
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    private static void mainBody() throws IOException {
        // [nl] iniにしたい人用。でも正確にはiniファイルじゃないよ
        File configFile = new File("config.ini");
        if (configFile.exists() == false) {
            configFile = new File("config.properties");
        }

        // 設定ファイルがなくてデフォルト設定ファイルがあるならリネームして使う
        if (configFile.exists() == false) {
            File defFile = new File("config.properties.default");
            if (defFile.exists()) {
                defFile.renameTo(configFile);
            }
        }

        Config config = configure(configFile);

        Logger.info(VER_STRING);
        Logger.info("    Running with Java %s on %s",
                System.getProperty("java.version"),
                System.getProperty("os.name"));

        Logger.info("port=" + Integer.getInteger("listenPort"));
        if (System.getProperty("proxyHost").equals("")) {
            Logger.info("direct mode (no secondary proxy)");
        } else {
            Logger.info("proxy host=" + System.getProperty("proxyHost"));
            Logger.info("proxy port=" + Integer.getInteger("proxyPort"));
        }
        Logger.info("title=" + Boolean.getBoolean("title"));

        if (Boolean.getBoolean("resumeDownload")) {
            Logger.info("Resume suspended download: On");
        }

        if (Boolean.getBoolean(("touchCache"))) {
            Logger.info("Touch Cache File: On");
        }

        if (Boolean.getBoolean("dareka.debug")) {
            Logger.info("debug mode");
        }

        Cache.init();
        Cache.cleanup();
        Logger.info("total cache size=%,dbytes", Long.valueOf(Cache.size()));

		Logger.info("----------");

        registerShutdownHook(Thread.currentThread());

        server = new Server(config);

        server.start();
    }

    private static Config configure(File configFile) throws IOException {
        Config config = new BasicConfig(configFile);
        Config.setConfig(config);

        return config;
    }

    private static void registerShutdownHook(Thread serverThread) {
        Runtime.getRuntime().addShutdownHook(
                new CleanerHookThread(serverThread));
    }
}
