package de.scarpex.mrf;

import de.scarpex.mrf.account.MSAccountManager;
import de.scarpex.mrf.chromedriver.ChromeDriverUtils;
import de.scarpex.mrf.chromedriver.OSType;
import de.scarpex.mrf.chromedriver.ProxyUtils;
import de.scarpex.mrf.command.CommandManager;
import de.scarpex.mrf.command.bing.SearchCommand;
import de.scarpex.mrf.command.general.ClearCommand;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.io.Zip;

import java.io.*;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Scanner;

/**
 * The main class of the project.
 */
public class Launcher {
    private static final Logger log = LogManager.getLogger(Launcher.class);

    public static final String PC_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36 Edge/17.17134";

//    public static final String MOBILE_AGENT = "Mozilla/5.0 (Windows Phone 10.0; Android 4.2.1; WebView/3.0) " +
//            "AppleWebKit/537.36 (KHTML, like Gecko) coc_coc_browser/64.118.222 Chrome/52.0.2743.116 Mobile " +
//            "Safari/537.36 Edge/15.15063";
    public static final String MOBILE_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 8_0 like Mac OS X) AppleWebKit/600.1.3 (KHTML, like Gecko) Version/8.0 Mobile/12A4345d Safari/600.1.4";

    private static final String CONFIG = "config.properties";
    private Properties properties;
    private MSAccountManager accountManager;

    public static void main(String[] args) {
        new Launcher();
    }

    public Launcher() {
        long epoch = System.currentTimeMillis();
        String dir = System.getProperty("user.dir");

        System.setProperty("webdriver.chrome.logfile", new File("logs\\chromedriver.log").getAbsolutePath());
        System.setProperty("webdriver.chrome.driver", String.format("%s/chromedriver/chromedriver.exe", dir));

        loadPropertiesFile(new File(dir));

        log.info("Checking for new version of Chromedriver...");
        checkDriver();

        this.accountManager = new MSAccountManager(new File(dir));

        CommandManager manager = new CommandManager();
        manager.registerCommand(new ClearCommand());
        manager.registerCommand(new SearchCommand(this.accountManager, new File(dir)));

        log.info(String.format("The application is now ready to use. (took %sms)",
                (System.currentTimeMillis() - epoch)));

        Scanner scanner = new Scanner(System.in);
        while (true) {
            if (scanner.hasNext()) manager.readCommandLine(scanner.nextLine());
        }
    }

    /**
     * 检查驱动程序
     */
    private void checkDriver() {
        File path = new File("chromedriver");
        if (!path.exists()) {
            log.info("No Chromedriver found. Downloading the latest Chromedriver version from the internet...");
            path.mkdirs();

            OSType os = ChromeDriverUtils.getOS();
            if (os == null) {
                log.warn("The OS can not be determined. Please use this application only on Windows, Linux or Mac.");
                System.exit(69); // https://opensource.apple.com/source/Libc/Libc-320/include/sysexits.h
            }
            download(ChromeDriverUtils.getDownloadLink(), path, false);
        } else {
            String version = this.properties.getProperty("chromedriver.version");
            if (version == null || !ChromeDriverUtils.getLatestVersion().equals(version)) {
                log.info("New version found! Start downloading...");
                download(ChromeDriverUtils.getDownloadLink(), path, true);
            }
        }
    }

    /**
     * Loads the configuration file. If there is no "config.properties" file
     * a new configuration file will be created.
     * 加载配置文件
     *
     * @param folder The place where the configuration file is located.
     */
    private void loadPropertiesFile(File folder) {
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, CONFIG);
        if (!file.exists()) {
            log.info("No configuration file found. Create a new file...");

            try (InputStream input = getClass().getResourceAsStream("/" + CONFIG)) {
                try (OutputStream output = new FileOutputStream(file)) {
                    IOUtils.copy(input, output);
                    log.info("The configuration file was created. You can make a few settings, but you have to " +
                            "close the application first. If you need help, contact the developer.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.properties = new Properties();

        try {
            this.properties.load(new FileInputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Configuration file loaded successfully.");
    }

    /**
     * Download the latest version of the Chromedriver and replace it with the
     * old version.
     * 下载驱动
     *
     * @param url    The download link to the latest version.
     * @param path   The path to the download folder.
     * @param update Will the version be updated?
     */
    private void download(String url, File path, boolean update) {
        URLConnection urlConnection = ProxyUtils.urlProxy(url);
        try {
            log.info(String.format("Start download for %s driver...", System.getProperty("os.name")));
            File file = new File(path, "chromedriver.zip");
            inputStream2File(urlConnection.getInputStream(), file);

        } catch (IOException e) {
            log.error("It seems something went wrong.", e);
        } finally {
            log.info("Download complete!");

            if (update) {
                try {
                    FileUtils.deleteDirectory(path);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    path.mkdirs();
                }
            }

            try {
                log.info("Unzip...");
                Zip.unzip(new FileInputStream(new File(path, "chromedriver.zip")),
                        new File("chromedriver"));
                FileUtils.forceDelete(new File(path, "chromedriver.zip"));
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                log.info("Unzip complete.");
            }

            try {
                this.properties.setProperty("chromedriver.version", ChromeDriverUtils.getLatestVersion());
                this.properties.store(new FileOutputStream(CONFIG), null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 输入流转换文件
     *
     * @param inputStream 文件输入流
     * @param file        文件
     * @throws IOException ioexception
     */

    public static void inputStream2File(InputStream inputStream, File file) throws IOException {
        OutputStream outputStream = null;
        try {
            outputStream = Files.newOutputStream(file.toPath());
            int len = 0;
            byte[] buffer = new byte[1024];

            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            inputStream.close();
        }
    }

}
