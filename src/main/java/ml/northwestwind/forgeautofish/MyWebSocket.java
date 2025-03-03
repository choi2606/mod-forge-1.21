package ml.northwestwind.forgeautofish;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ml.northwestwind.forgeautofish.config.Config;
import ml.northwestwind.forgeautofish.handler.AutoFishHandler;
import net.minecraft.client.Minecraft;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Định nghĩa WebSocket
public class MyWebSocket implements WebSocketListener {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public void clickPredic(double e) {
        long delay;

        if(AutoFish.width == 640 && AutoFish.height == 480) {
            if (e < 0.25) {
                delay = 150;
            } else if (e < 0.3) {
                delay = 300;
            } else if (e < 0.4) {
                delay = 460;
            } else if (e < 0.5) {
                delay = 550;
            } else if (e < 0.6) {
                delay = 750;
            } else if (e < 0.8) {
                delay = 960;
            } else if (e < 1) {
                delay = 1150;
            } else if (e < 1.2) {
                delay = 1250;
            } else if (e < 1.5) {
                delay = 1900;
            } else {
                delay = 0; // Nếu không thuộc trường hợp nào, không cần delay
            }
        }
        else {
            if (e < 0.25) {
                delay = 150;
            } else if (e < 0.3) {
                delay = 200;
            } else if (e < 0.5) {
                delay = 430;
            } else if (e < 0.6) {
                delay = 650;
            } else if (e < 0.8) {
                delay = 750;
            } else if (e < 1) {
                delay = 1050;
            } else if (e < 1.2) {
                delay = 1150;
            } else if (e < 1.5) {
                delay = 1700;
            } else {
                delay = 0; // Nếu không thuộc trường hợp nào, không cần delay
            }
        }


        // Lên lịch cho hành động recast sau khoảng thời gian delay
        scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                AutoFishHandler.recast(Minecraft.getInstance().player);
            });
        }, delay, TimeUnit.MILLISECONDS);
    }
    @Override
    public void onWebSocketText(String message) {
        AutoFish.LOGGER.info("Received message: " + message);
        // Phân tích cú pháp chuỗi JSON
        JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
        // Lấy giá trị elapsed_time
        String username = jsonObject.get("username").getAsString();
        double elapsedTime = jsonObject.get("elapsed_time").getAsDouble();
        String nameCheck = (username.split("\\|"))[1];
        if (nameCheck.contains(Minecraft.getInstance().getUser().getName())) {
            clickPredic(elapsedTime);
        }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        cause.printStackTrace();
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        // Xử lý tin nhắn nhị phân nếu cần
    }

    @Override
    public void onWebSocketConnect(Session session) {
        AutoFish.LOGGER.info("Connected to server: " + session.getRemote());
    }
}