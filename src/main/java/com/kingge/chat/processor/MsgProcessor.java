package com.kingge.chat.processor;

import com.alibaba.fastjson.JSONObject;
import com.kingge.chat.protocol.IMDecoder;
import com.kingge.chat.protocol.IMEncoder;
import com.kingge.chat.protocol.IMMessage;
import com.kingge.chat.protocol.IMP;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * 主要用于自定义协议内容的逻辑处理
 *
 */
public class MsgProcessor {
	
	//记录在线用户
	//这里我们使用了netty提供的ChannelGroup来保存登录用户信息，数据结构是set
	private static ChannelGroup onlineUsers = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	
	//定义一些扩展属性
	private final AttributeKey<String> NICK_NAME = AttributeKey.valueOf("nickName");
	private final AttributeKey<String> IP_ADDR = AttributeKey.valueOf("ipAddr");
	private final AttributeKey<JSONObject> ATTRS = AttributeKey.valueOf("attrs");
	
	//自定义解码器
	private IMDecoder decoder = new IMDecoder();
	//自定义编码器
	private IMEncoder encoder = new IMEncoder();
	
	/**
	 * 获取用户昵称
	 * @param client
	 * @return
	 */
	public String getNickName(Channel client){
		return client.attr(NICK_NAME).get();
	}
	/**
	 * 获取用户远程IP地址
	 * @param client
	 * @return
	 */
	public String getAddress(Channel client){
		return client.remoteAddress().toString().replaceFirst("/","");
	}
	
	/**
	 * 获取扩展属性
	 * @param client
	 * @return
	 */
	public JSONObject getAttrs(Channel client){
		try{
			return client.attr(ATTRS).get();
		}catch(Exception e){
			return null;
		}
	}
	
	/**
	 * 获取扩展属性
	 * @param client
	 * @return
	 */
	private void setAttrs(Channel client,String key,Object value){
		try{
			JSONObject json = client.attr(ATTRS).get();
			json.put(key, value);
			client.attr(ATTRS).set(json);
		}catch(Exception e){
			JSONObject json = new JSONObject();
			json.put(key, value);
			client.attr(ATTRS).set(json);
		}
	}
	
	/**
	 * 登出通知
	 * @param client
	 */
	public void logout(Channel client){
		//如果nickName为null，没有遵从聊天协议的连接，表示未非法登录
		if(getNickName(client) == null){ return; }
		for (Channel channel : onlineUsers) {
			IMMessage request = new IMMessage(IMP.SYSTEM.getName(), sysTime(), onlineUsers.size(), getNickName(client) + "离开");
			String content = encoder.encode(request);
			channel.writeAndFlush(new TextWebSocketFrame(content));
		}
		onlineUsers.remove(client);
	}
	
	/**
	 * 发送消息
	 * @param client
	 * @param msg
	 */
	public void sendMsg(Channel client,IMMessage msg){
		sendMsg(client,encoder.encode(msg));
	}
	
	/**
	 * 发送消息
	 * @param client
	 * @param msg
	 */
	public void sendMsg(Channel client,String msg){
		IMMessage request = decoder.decode(msg);
		if(null == request){ return; }
		
		String addr = getAddress(client);
		
		if(request.getCmd().equals(IMP.LOGIN.getName())){//表示当前客户端连接到服务器
			client.attr(NICK_NAME).getAndSet(request.getSender());
			client.attr(IP_ADDR).getAndSet(addr);
			onlineUsers.add(client);//保存登录用户，这样以后可以群发消息
			
			for (Channel channel : onlineUsers) {
				if(channel != client){//通知其他在线用户，有个新用户登录
					request = new IMMessage(IMP.SYSTEM.getName(), sysTime(), onlineUsers.size(), getNickName(client) + "加入");
				}else{//如果是自己上线，那么就提示 已经与服务器建立连接
					request = new IMMessage(IMP.SYSTEM.getName(), sysTime(), onlineUsers.size(), "已与服务器建立连接！");
				}
				String content = encoder.encode(request);
				channel.writeAndFlush(new TextWebSocketFrame(content));//构建一个websocket消息，发送出去，交给下个处理链WebSocktHandler
			}
		}else if(request.getCmd().equals(IMP.CHAT.getName())){//说明客户端发送了一条消息
			for (Channel channel : onlineUsers) {
				if (channel == client) {
					request.setSender("you");
				}else{
					request.setSender(getNickName(client));
				}
				request.setTime(sysTime());
				String content = encoder.encode(request);
				channel.writeAndFlush(new TextWebSocketFrame(content));
			}
		}else if(request.getCmd().equals(IMP.FLOWER.getName())){//说明客户端点击了发送鲜花按钮
			JSONObject attrs = getAttrs(client);
			long currTime = sysTime();
			if(null != attrs){
				long lastTime = attrs.getLongValue("lastFlowerTime");
				//10秒之内不允许重复刷鲜花
				int secends = 10;
				long sub = currTime - lastTime;
				if(sub < 1000 * secends){
					request.setSender("you");
					request.setCmd(IMP.SYSTEM.getName());
					request.setContent("您送鲜花太频繁," + (secends - Math.round(sub / 1000)) + "秒后再试");
					String content = encoder.encode(request);
					client.writeAndFlush(new TextWebSocketFrame(content));
					return;
				}
			}
			
			//正常送花
			for (Channel channel : onlineUsers) {
				if (channel == client) {
					request.setSender("you");
					request.setContent("你给大家送了一波鲜花雨");
					setAttrs(client, "lastFlowerTime", currTime);
				}else{
					request.setSender(getNickName(client));
					request.setContent(getNickName(client) + "送来一波鲜花雨");
				}
				request.setTime(sysTime());
				
				String content = encoder.encode(request);
				channel.writeAndFlush(new TextWebSocketFrame(content));
			}
		}
	}
	
	/**
	 * 获取系统时间
	 * @return
	 */
	private Long sysTime(){
		return System.currentTimeMillis();
	}
	
}
