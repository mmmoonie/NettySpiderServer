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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wangchao
 * @date 2018/4/28
 */
public class ChildChannelHandler extends ChannelHandlerAdapter {

    static final String BOUNDARY = "boundary-----------";

    private static final AtomicInteger COUNTER = new AtomicInteger(27000);

    private ServerSocket server = null;
    private Socket socket = null;
    private BufferedReader in = null;
    private PrintWriter out = null;
    private Process process;
    private String exePath;

    ChildChannelHandler(String exePath) {
        this.exePath = exePath;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        System.out.println(dateFormat.format(new Date()) + " channel active!");
        int port = COUNTER.getAndIncrement();
        try {
            server = new ServerSocket(port, 2);
            server.setSoTimeout(30000);
            server.setReceiveBufferSize(10240);
        } catch (IOException e) {
            COUNTER.decrementAndGet();
            exceptionCaught(ctx, e);
            return;
        }
        process = Runtime.getRuntime().exec(exePath + " " + server.getLocalPort());
        socket = server.accept();
        socket.setSoTimeout(30000);
        socket.setKeepAlive(false);
        socket.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), "UTF-8"), true);
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String body = (String) msg;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        System.out.println(dateFormat.format(new Date()) + " receive: " + body);
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
        COUNTER.decrementAndGet();
        close();
        ctx.close();
        super.channelInactive(ctx);
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
        COUNTER.decrementAndGet();
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
