package xyz.supermoonie;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

import java.io.File;
import java.nio.charset.Charset;

/**
 * Hello world!
 *
 * @author wangchao
 */
public class App 
{

    public static void main( String[] args ) throws Exception {
        int port = 0;
        String exePath = "";
        if (args != null && args.length > 0) {
            int factory = 2;
            if (args.length % factory != 0) {
                System.out.println("invalid argument");
                return;
            }
            for (int i = 0; i < args.length; i ++) {
                String key = args[i];
                i ++;
                String val = args[i];
                if ("-port".equals(key)) {
                    try {
                        port = Integer.parseInt(val);
                    } catch (NumberFormatException ignored) {
                        System.out.println("invalid port");
                        return;
                    }
                } else if ("-path".equals(key)) {
                    File exeFile = new File(val);
                    if (exeFile.exists() && exeFile.canExecute()) {
                        exePath = val;
                    } else {
                        System.out.println(val + "not found or can't execute");
                        return;
                    }
                }
            }
        } else {
            System.out.println("-port \t the netty server listen on, eg: 7100");
            System.out.println("-path \t the WebViewSpider path, eg: C:\\app\\WebViewSpider\\WebViewSpider.exe");
            return;
        }
        new App().start(port, exePath);
    }

    private void start(int port,final String exePath) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_KEEPALIVE, false)
                    .option(ChannelOption.SO_TIMEOUT, 10000)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ByteBuf delimiter = Unpooled.copiedBuffer(ChildChannelHandler.BOUNDARY.getBytes("UTF-8"));
                            socketChannel.pipeline().addLast(new DelimiterBasedFrameDecoder(10240, delimiter));
                            socketChannel.pipeline().addLast(new StringDecoder(Charset.forName("UTF-8")));
                            socketChannel.pipeline().addLast(new ChildChannelHandler(exePath));
                        }
                    });
            ChannelFuture f = b.bind(port).sync();
            System.out.println("the server is start in port: " + port);
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
