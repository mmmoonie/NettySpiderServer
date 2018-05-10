# NettySpiderServer
NettySpiderServer 基于Netty 开发的WebViewSpider 控制服务器，用于支持WebViewSpider 的远程访问，主要使用了 DelimiterBasedFrameDecoder 以及 StringDecoder 两个编码器。

## 启动参数

> -port 7100 
>
> -path c:\app\WebViewSpider\WebViewSpider.exe

port : 监听的端口

path : WebViewSpider 可执行文件的绝对路径

## 请求控制流程

```sequence
JavaSocket->NettySpiderServer: connect到NettySpiderServer
Note over NettySpiderServer: 创建ServerSocket与子进程
WebViewSpider->NettySpiderServer: connect到ServerSocket
Note over WebViewSpider: 打开about:blank空白页面
Note over JavaSocket: 指令封包
JavaSocket->NettySpiderServer: 指令
Note over NettySpiderServer: Netty 解包
NettySpiderServer->WebViewSpider: 指令
Note over WebViewSpider: 执行指令
WebViewSpider->NettySpiderServer: 发送结果数据包
Note over NettySpiderServer: 封包
NettySpiderServer->JavaSocket: 封装好的数据包
Note over JavaSocket: 解包，获取数据
JavaSocket->NettySpiderServer: 断开链接
Note over NettySpiderServer: 关闭ServerSocket并杀掉子进程
```

1. 启动 NettySpiderServer，监听 7100 端口，随机生成若干个 27000-28000 的端口号
2. JavaSocket 连接到 NettySpiderServer，触发 channelActive，channelActive 中创建 ServerSocket 以及 子进程 WebViewSpider，ServerSocket 监听的端口通过命令行的形式传递给子进程
3. WebViewSpider 获取到 ServerSocket 端口号，并同步等待连接成功，成功后导航到 about:blank 页面，若失败则自动关闭
4. JavaSocket 对指令进行封包，并发送给 NettySpiderServer
5. NettySpiderServer 解包，并将指令发送给 WebViewSpider 进程
6. WebViewSpider 解析指令并执行，然后将执行后产生的数据发送给 NettySpiderServer
7. NettySpiderServer 对收到的数据进行封包，并发送给 JavaSocket
8. JavaSocket 解包，获取数据
9. JavaSocket 断开链接，NettySpiderServer 关闭 ServerSocket 并杀掉子进程