package xyz.supermoonie;

import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wangchao
 * @date 2018/4/28
 */
public class ChildChannelHandler extends ChannelHandlerAdapter {

    static final String BOUNDARY = "boundary-----------";

    private static final Pattern ID_DEAD_LINE_PATTERN = Pattern.compile("^id:([0-9a-zA-Z]{32})deadline:(\\d+)$");

    private static final AtomicInteger COUNTER = new AtomicInteger(27000);

    private String exePath;

    private WebViewDriver webViewDriver;

    ChildChannelHandler(String exePath) {
        this.exePath = exePath;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        System.out.println(dateFormat.format(new Date()) + " channel active!");
        int port = COUNTER.incrementAndGet();
        System.out.println("WebViewSpider is listening on " + port);
        try {
            webViewDriver = new WebViewDriver(exePath, port);
        } catch (IOException e) {
            COUNTER.decrementAndGet();
            exceptionCaught(ctx, e);
            return;
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        webViewDriver.startWebView();
        String body = (String) msg;
        Matcher matcher = ID_DEAD_LINE_PATTERN.matcher(body);
        if (matcher.find()) {
            String id = matcher.group(1);
            long deadLine = Long.parseLong(matcher.group(2));
            if (deadLine <= 0) {
                throw new IllegalArgumentException("deadline less than zero!");
            }
            webViewDriver.setDeadLine(deadLine);
            WebViewDriverPool.DRIVER_POOL.putIfAbsent(id, webViewDriver);
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        System.out.println(dateFormat.format(new Date()) + " receive: " + body);
        try {
            String data = webViewDriver.send(body);
            ByteBuf resp = Unpooled.copiedBuffer(data.getBytes("UTF-8"));
            ctx.writeAndFlush(resp);
        } catch (IOException e) {
            exceptionCaught(ctx, e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        COUNTER.decrementAndGet();
        if (webViewDriver.getDeadLine() == -1) {
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
        data.append(errorJson.toJSONString());
        ByteBuf resp = Unpooled.copiedBuffer(data.toString().getBytes("UTF-8"));
        ctx.writeAndFlush(resp);
        webViewDriver.close();
        ctx.close();
    }
}
