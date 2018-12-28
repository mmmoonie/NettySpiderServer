package xyz.supermoonie;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author supermoonie
 * @date 2018/12/28
 */
public class WebViewDriverPool {

    public static final Map<String, WebViewDriver> DRIVER_POOL = new ConcurrentHashMap<>();

    public static void start() {
        try {
            new Thread(() -> {
                while (true) {
                    try {
                        for (int i = 0; i < DRIVER_POOL.size(); i ++) {
                            WebViewDriver webViewDriver = DRIVER_POOL.get(i);
                            if (System.currentTimeMillis() > webViewDriver.getDeadLine()) {
                                webViewDriver.close();
                            }
                            Thread.sleep(50);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
