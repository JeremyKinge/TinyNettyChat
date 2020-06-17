package com.kingge.chat.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.kingge.chat.protocol.IMDecoder;
import com.kingge.chat.protocol.IMEncoder;
import com.kingge.chat.server.handler.HttpHandler;
import com.kingge.chat.server.handler.SocketHandler;
import com.kingge.chat.server.handler.WebSocktHandler;

public class ChatServer{
	
	private static Logger LOG = Logger.getLogger(ChatServer.class);
	
	private int port = 80;
	
    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1024)
            .childHandler(new ChannelInitializer<SocketChannel>() {//这里支持多个协议的解析。这样能够应对不同的场景
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
	                
                		ChannelPipeline pipeline = ch.pipeline();
	                	
	                	/** 解析自定义协议 */
	                	pipeline.addLast(new IMDecoder());//ChannelInboundHandlerAdapter
	                	pipeline.addLast(new IMEncoder());//ChannelOutboundHandlerAdapter
	                	pipeline.addLast(new SocketHandler());//ChannelInboundHandlerAdapter
	                
	                	/** 解析Http请求 */
	            		pipeline.addLast(new HttpServerCodec());//即是inbound又是outbound
	            		//主要是将同一个http请求或响应的多个消息对象变成一个 fullHttpRequest完整的消息对象
	            		pipeline.addLast(new HttpObjectAggregator(64 * 1024));//ChannelInboundHandlerAdapter
	            		//主要用于处理大数据流,比如一个1G大小的文件如果你直接传输肯定会撑暴jvm内存的 ,加上这个handler我们就不用考虑这个问题了
	            		pipeline.addLast(new ChunkedWriteHandler());//即是inbound又是outbound
	            		pipeline.addLast(new HttpHandler());//inbound

	            		/** 解析WebSocket请求 */
	            		pipeline.addLast(new WebSocketServerProtocolHandler("/im"));//ChannelInboundHandlerAdapter
	            		pipeline.addLast(new WebSocktHandler());//ChannelInboundHandlerAdapter

                    //总而言之，如果服务端接收到客户端发送的数据，那么就会经过当前服务端创建的
                    //NIOSocketChannel的pipeline进行处理
                    //整个处理入站数据处理器链是：
                    // IMDecoder -> SocketHandler -> HttpServerCodec -> HttpObjectAggregator -> ChunkedWriteHandler
                    // - > HttpHandler - > WebSocketServerProtocolHandler - >WebSocktHandler

                    //当服务端处理完数据后，如果有数据返回，那么整个处理链是：ChunkedWriteHandler -> HttpServerCodec -> IMEncoder

            		
                }
            }); 
            ChannelFuture f = b.bind(this.port).sync();
            LOG.info("服务已启动,监听端口" + this.port);
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
    
    
    public static void main(String[] args) throws IOException{
        new ChatServer().start();
    }
    
}
