package xyz.supermoonie;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author wangchao
 * @date 2018/4/26
 */
public class TempServerSocket {

    public static void main(String[] args) throws IOException {
        int port = 7200;
        if (args != null && args.length > 0) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {
                // 采用默认值
            }
        }
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            System.out.println("the time server is start in port: " + port);
            Socket socket = null;
            while (true) {
                socket = server.accept();
                System.out.println(socket.toString());
            }
        } finally {
            System.out.println("The time server close");
            server.close();
            server = null;
        }
    }
}
