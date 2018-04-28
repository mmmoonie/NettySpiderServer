package xyz.supermoonie;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.util.IOUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author wangchao
 * @date 2018/4/28
 */
public class ChildChannelHandler extends ChannelHandlerAdapter {

    public static final String BOUNDARY = "boundary-----------";

    private static final int MAX_SOCKETS = 60;

    private static final Queue<Integer> PORT_LIST = new LinkedBlockingQueue<>(MAX_SOCKETS);

    static {
        Random random = new Random();
        random.ints(MAX_SOCKETS, 27000, 28000).forEach(PORT_LIST::add);
    }

    private ServerSocket server = null;
    private Socket socket = null;
    private BufferedReader in = null;
    private PrintWriter out = null;
    private Process process;
    private String exePath;

    public ChildChannelHandler(String exePath) {
        this.exePath = exePath;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        int max = 10;
        for (int i = 0; i < max; i ++) {
            int port = PORT_LIST.poll();
            try {
                server = new ServerSocket(port, 2);
                server.setSoTimeout(10000);
                server.setReceiveBufferSize(10240);
            } catch (IOException e) {
                if (i >= max) {
                    exceptionCaught(ctx, e);
                    return;
                }
                PORT_LIST.offer(port);
            }
        }
        process = Runtime.getRuntime().exec(exePath + " " + server.getLocalPort());
        socket = server.accept();
        socket.setSoTimeout(10000);
        socket.setKeepAlive(false);
        socket.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), "UTF-8"), true);
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String body = (String) msg;
        out.write(body);
        out.flush();
        StringBuilder data = new StringBuilder();
        try {
            data.append(in.readLine()).append("\r\n").append(BOUNDARY).append("\r\n");
        } catch (IOException e) {
            exceptionCaught(ctx, e);
            return;
        }
        ByteBuf resp = Unpooled.copiedBuffer(data.toString().getBytes("UTF-8"));
        ctx.writeAndFlush(resp);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        PORT_LIST.offer(server.getLocalPort());
        close();
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        StringBuilder data = new StringBuilder();
        JSONObject errorJson = new JSONObject();
        errorJson.put("code", "500");
        errorJson.put("data", "");
        if (cause != null) {
            errorJson.put("desc", cause.getMessage());
        } else {
            errorJson.put("desc", "unknown exception");
        }
        data.append(errorJson.toJSONString());
        ByteBuf resp = Unpooled.copiedBuffer(data.toString().getBytes("UTF-8"));
        ctx.writeAndFlush(resp);
        PORT_LIST.offer(server.getLocalPort());
        close();
        ctx.close();
    }

    private void close() {
        IOUtils.close(in);
        IOUtils.close(out);
        IOUtils.close(socket);
        if (process.isAlive()) {
            process.destroy();
        }
        IOUtils.close(server);
    }
}
