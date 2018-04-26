package xyz.supermoonie;

import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

/**
 *
 *
 * @author Administrator
 * @date 2018/2/28 0028
 */
public class Client {

    public static void main(String[] args) throws Exception {
        int tempPort = 7100;
        if (args != null && args.length > 0) {
            try {
                tempPort = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {
                // 采用默认值
            }
        }
        final int port = tempPort;
        for (int i = 0; i < 1; i ++) {
            new Thread(() -> {
                try {
                    new Client().connect("127.0.0.1", port);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
        Thread.sleep(Integer.MAX_VALUE);
    }

    private void connect(String host, int port) throws Exception {
        // 配置客户端 NIO 线程组
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ByteBuf delimiter = Unpooled.copiedBuffer(App.BOUNDARY.getBytes());
                            socketChannel.pipeline().addLast(new DelimiterBasedFrameDecoder(10240, delimiter));
                            socketChannel.pipeline().addLast(new StringDecoder());
                            socketChannel.pipeline().addLast(new Client.TimeClientHandler());
                        }
                    });
            ChannelFuture f = b.connect(host, port).sync();
            f.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    private class TimeClientHandler extends ChannelHandlerAdapter {

        public TimeClientHandler() {
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            JSONObject json = new JSONObject();
            json.put("op", "load");
            json.put("interceptor", "");
            json.put("url", "https://persons.shgjj.com");
            String req = json.toJSONString() + App.BOUNDARY;
            ctx.writeAndFlush(Unpooled.copiedBuffer(req.getBytes()));
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String body = (String) msg;
            System.out.println("this is: " + body);
            ctx.disconnect();
            ctx.close();
            ctx.channel().close().sync();
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            System.out.println("disconnect");
            super.disconnect(ctx, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (cause != null) {
                cause.printStackTrace();
            }
            ctx.close();
        }
    }
}
