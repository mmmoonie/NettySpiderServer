package xyz.supermoonie;

import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wangchao
 * @date 2018/4/28
 */
public class ChildChannelHandler extends ChannelHandlerAdapter {

    static final String BOUNDARY = "boundary-----------";

    private static final Pattern ID_DEAD_LINE_PATTERN = Pattern.compile("^id:([0-9a-zA-Z]{32})deadline:(\\d+)$");

    private static final Pattern ID_PATTERN = Pattern.compile("^id:([0-9a-zA-Z]{32})$");

    public static final Stack<Integer> PORTS = new Stack<>();

    static {
        for (int i = 0; i < 200; i++) {
            PORTS.push(27000 + i);
        }
    }

    private String exePath;

    private WebViewDriver webViewDriver;

    ChildChannelHandler(String exePath) {
        this.exePath = exePath;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        System.out.println(dateFormat.format(new Date()) + " channel active!");
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String body = (String) msg;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        System.out.println(dateFormat.format(new Date()) + " receive: " + body);
        Matcher matcher = ID_DEAD_LINE_PATTERN.matcher(body);
        Matcher idMatcher = ID_PATTERN.matcher(body);
        if (matcher.find()) {
            if (null == webViewDriver) {
                throw new IllegalArgumentException("StayCommand could not be first command!");
            }
            String id = matcher.group(1);
            long deadLine = Long.parseLong(matcher.group(2));
            if (deadLine <= 0) {
                throw new IllegalArgumentException("deadline less than zero!");
            }
            webViewDriver.setDeadLine(System.currentTimeMillis() + deadLine);
            WebViewDriverPool.DRIVER_POOL.putIfAbsent(id, webViewDriver);
            JSONObject data = new JSONObject();
            data.put("code", 200);
            data.put("data", "success");
            data.put("desc", "");
            ByteBuf resp = Unpooled.copiedBuffer((data.toJSONString() + "\r\n" + BOUNDARY + "\r\n").getBytes("UTF-8"));
            ctx.writeAndFlush(resp);
        } else if (idMatcher.find()) {
            String id = idMatcher.group(1);
            JSONObject data = new JSONObject();
            data.put("code", 200);
            data.put("desc", "");
            WebViewDriver driver = WebViewDriverPool.DRIVER_POOL.remove(id);
            if (null != driver) {
                driver.setDeadLine(-1);
                this.webViewDriver = driver;
                data.put("data", true);
            } else {
                data.put("data", false);
            }
            ByteBuf resp = Unpooled.copiedBuffer((data.toJSONString() + "\r\n" + BOUNDARY + "\r\n").getBytes("UTF-8"));
            ctx.writeAndFlush(resp);
        } else {
            if (null == webViewDriver) {
                Integer port = PORTS.pop();
                if (null == port) {
                    throw new IllegalStateException("PORT_QUEUE empty!");
                }
                System.out.println("WebViewSpider is listening on " + port);
                webViewDriver = new WebViewDriver(exePath, port);
                try {
                    webViewDriver.startWebView();
                } catch (IOException e) {
                    PORTS.push(webViewDriver.getPort());
                    exceptionCaught(ctx, e);
                    return;
                }
            }
            String data = webViewDriver.send(body);
            ByteBuf resp = Unpooled.copiedBuffer(data.getBytes("UTF-8"));
            ctx.writeAndFlush(resp);
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (null != webViewDriver && webViewDriver.getDeadLine() <= 0) {
            webViewDriver.close();
        }
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
        data.append(errorJson.toJSONString()).append("\r\n").append(BOUNDARY).append("\r\n");
        ByteBuf resp = Unpooled.copiedBuffer(data.toString().getBytes("UTF-8"));
        ctx.writeAndFlush(resp);
        webViewDriver.close();
        ctx.close();
    }
}
