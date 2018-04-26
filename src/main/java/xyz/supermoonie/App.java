package xyz.supermoonie;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Hello world!
 *
 * @author wangchao
 */
public class App 
{
    public static final String BOUNDARY = "boundary-----------";

    public static void main( String[] args ) throws Exception {
        int port = 7100;
        if (args != null && args.length > 0) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {
                // 采用默认值
            }
        }
        new App().bind(port);
    }

    private void bind(int port) throws Exception {
        // 配置服务端的 NIO 线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
//                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new App.ChildChannelHandler());
            // 绑定端口，同步等待成功
            ChannelFuture f = b.bind(port).sync();
            System.out.println("the server is start in port: " + port);
            // 等待服务端监听端口关闭
            f.channel().closeFuture().sync();
        } finally {
            // 退出
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private class ChildChannelHandler extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
            ByteBuf delimiter = Unpooled.copiedBuffer(BOUNDARY.getBytes());
            socketChannel.pipeline().addLast(new DelimiterBasedFrameDecoder(10240, delimiter));
            socketChannel.pipeline().addLast(new StringDecoder());
            socketChannel.pipeline().addLast(new App.TimeServerHandler());
        }
    }

    private class TimeServerHandler extends ChannelHandlerAdapter {

        private ServerSocket server = null;
        private Socket socket = null;
        private BufferedReader in = null;
        private PrintWriter out = null;
        private Process process;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            server = new ServerSocket(7200, 1, null);
            process = Runtime.getRuntime().exec("C:\\app\\WebViewSpider\\WebViewSpider.exe");
            socket = server.accept();
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            out = new PrintWriter(this.socket.getOutputStream(), true);
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String body = (String) msg;
            System.out.println("--------------> " + body);
            out.write(body);
            out.flush();
            String info;
            while ((info = in.readLine()) != null) {
                System.out.println(info);
                ByteBuf resp = Unpooled.copiedBuffer(info.getBytes());
                ctx.writeAndFlush(resp);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            System.out.println("channel read complete");
//            ctx.close();
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
