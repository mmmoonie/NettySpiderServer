package xyz.supermoonie;

import com.alibaba.fastjson.util.IOUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static xyz.supermoonie.ChildChannelHandler.BOUNDARY;

/**
 * @author supermoonie
 * @date 2018/12/28
 */
public class WebViewDriver implements Closeable{

    private ServerSocket server;

    private Socket socket;

    private BufferedReader in;

    private PrintWriter out;

    private Process process;

    private long deadLine = -1;

    private final String exePath;

    private final int port;

    public WebViewDriver(String exePath, int port) throws IOException {
        this.exePath = exePath;
        this.port = port;
        server = new ServerSocket(port, 2);
        server.setSoTimeout(30000);
        server.setReceiveBufferSize(10240);
        socket = server.accept();
        socket.setSoTimeout(30000);
        socket.setKeepAlive(false);
        socket.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), "UTF-8"), true);
    }

    public void startWebView() throws IOException {
        if (null == process) {
            process = Runtime.getRuntime().exec(exePath + " " + port);
        }
    }

    public String send(String msg) throws IOException {
        out.write(msg);
        out.flush();
        return in.readLine() + "\r\n" + BOUNDARY + "\r\n";
    }

    public long getDeadLine() {
        return deadLine;
    }

    public void setDeadLine(long deadLine) {
        this.deadLine = System.currentTimeMillis() + deadLine;
    }

    @Override
    public void close() {
        IOUtils.close(in);
        IOUtils.close(out);
        IOUtils.close(socket);
        if (null != process && process.isAlive()) {
            process.destroy();
        }
        IOUtils.close(server);
    }
}
