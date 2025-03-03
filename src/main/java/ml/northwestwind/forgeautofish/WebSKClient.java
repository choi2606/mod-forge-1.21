package ml.northwestwind.forgeautofish;

import ml.northwestwind.forgeautofish.handler.AutoFishHandler;
import net.minecraft.client.Minecraft;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.concurrent.Future;

public class WebSKClient {

    public static void main(String[] args) {
        // Khởi tạo WebSocket client

        WebSocketClient client = null;
        try {
            if(AutoFishHandler.isMainAcc)
                client = new WebSocketClient();
        } catch (RuntimeException e) {
            AutoFish.LOGGER.error("Lỗi khi kết nối WebSocket: ", e);
            client = null;
        }

        try {
            client.start(); // Bắt đầu client
            // Địa chỉ WebSocket của máy chủ
            URI uri = new URI("ws://localhost:2606/"); // Thay đổi URL theo máy chủ của bạn
            // Tạo và kết nối đến WebSocket
            MyWebSocket socket = new MyWebSocket();
            Future<Session> future = client.connect(socket, uri);
            Session session = future.get(); // Chờ cho đến khi kết nối thành công

            // Lấy thông tin PID gửi cho server
            String processName = ManagementFactory.getRuntimeMXBean().getName();
            String pid = processName.split("@")[0]; // Tách PID
            AutoFish.LOGGER.info("PID cua qua trinh Minecraft: " + pid);

            // Lấy size game
            String size = AutoFish.width + "|" + AutoFish.height;

            // Gửi một thông điệp
            String username = Minecraft.getInstance().getUser().getName();
            String userNamePid = pid + "|" + username + "|" + size;
            session.getRemote().sendString(userNamePid);
            AutoFish.LOGGER.info("da gui thong diep: " + userNamePid);

            // Thực hiện các xử lý khác nếu cần
            Thread.sleep(Long.MAX_VALUE); // Giữ chương trình chạy mãi mãi
        } catch (Exception e) {
            System.out.print("loi roi");
        } finally {
            try {
                client.stop(); // Ngừng client khi chương trình kết thúc
            } catch (Exception e) {
                AutoFish.LOGGER.error("Lỗi khi kết nối WebSocket: ", e);
            }
        }
    }
}