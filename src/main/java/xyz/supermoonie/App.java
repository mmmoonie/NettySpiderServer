package xyz.supermoonie;

import com.alibaba.fastjson.util.IOUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Hello world!
 *
 * @author wangchao
 */
public class App 
{
    public static final String BOUNDARY = "boundary-----------";

    private static final int MAX_SOCKETS = 60;

    private static final Queue<Integer> PORT_LIST = new LinkedBlockingQueue<>(MAX_SOCKETS);

    static {
        Random random = new Random();
        random.ints(MAX_SOCKETS, 27000, 28000).forEach(PORT_LIST::add);
    }

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

        private boolean setServerSocket() {
            int max = 10;
            for (int i = 0; i < max; i ++) {
                try {
                    int port = PORT_LIST.poll();
                    System.out.println("---------------> " + port);
                    server = new ServerSocket(port, 1, null);
                    return true;
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
            return false;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if (setServerSocket()) {
                process = Runtime.getRuntime().exec("C:\\app\\WebViewSpider\\WebViewSpider.exe " + server.getLocalPort());
                socket = server.accept();
                in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), "UTF-8"), true);
            } else {
                // TODO
            }
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String body = (String) msg;
            out.write(body);
            out.flush();
            String info = in.readLine();
            info += "\r\n";
            info += BOUNDARY;
            info += "\r\n";
            ByteBuf resp = Unpooled.copiedBuffer(info.getBytes("UTF-8"));
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
            if (cause != null) {
                cause.printStackTrace();
            }
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
}
