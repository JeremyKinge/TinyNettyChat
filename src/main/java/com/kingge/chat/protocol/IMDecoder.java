package com.kingge.chat.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.msgpack.MessagePack;
import org.msgpack.MessageTypeException;

/**
 * 自定义IM协议的编码器
 */
public class IMDecoder extends ByteToMessageDecoder {

	//解析IM写一下请求内容的正则
	private Pattern pattern = Pattern.compile("^\\[(.*)\\](\\s\\-\\s(.*))?");//(\s\-\s(.*))? 获取消息体，消息体是可有可无的所以使用？
	
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in,List<Object> out) throws Exception {
		try{
			//先获取可读字节数
	        final int length = in.readableBytes();
	        final byte[] array = new byte[length];
	        //字节数组转化为字符串
	        String content = new String(array,in.readerIndex(),length);
	        
	        //空消息不解析
	        if(!(null == content || "".equals(content.trim()))){
	        	if(!IMP.isIMP(content)){
	        		ctx.channel().pipeline().remove(this);
	        		return;
	        	}
	        }
	        
	        in.getBytes(in.readerIndex(), array, 0, length);
	        out.add(new MessagePack().read(array,IMMessage.class));
	        in.clear();
		}catch(MessageTypeException e){
			ctx.channel().pipeline().remove(this);
		}
	}
	
	/**
	 * 字符串解析成自定义即时通信协议
	 * @param msg
	 * @return
	 */
	public IMMessage decode(String msg){
		if(null == msg || "".equals(msg.trim())){ return null; }
		try{
			Matcher m = pattern.matcher(msg);
			String header = "";
			String content = "";
			if(m.matches()){
				header = m.group(1);//获取请求头
				content = m.group(3);//获取请求体
			}
			//header到了这一步值大概是：SYSTEM][124343423123][Tom老师
			String [] heards = header.split("\\]\\[");//所以以][为分隔符，获取数据
			long time = 0;
			try{ time = Long.parseLong(heards[1]); } catch(Exception e){}//根据自定义报文协议规则，事件是在第二位，那么数组下标是1
			String nickName = heards[2];//根据IMP自定义报文协议，发送人/接收人的名称是在第三位，那么数组下标是2
			//昵称最多十个字
			nickName = nickName.length() < 10 ? nickName : nickName.substring(0, 9);
			
			if(msg.startsWith("[" + IMP.LOGIN.getName() + "]")){
				return new IMMessage(heards[0],time,nickName);
			}else if(msg.startsWith("[" + IMP.CHAT.getName() + "]")){
				return new IMMessage(heards[0],time,nickName,content);
			}else if(msg.startsWith("[" + IMP.FLOWER.getName() + "]")){
				return new IMMessage(heards[0],time,nickName);
			}else{
				return null;
			}
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
	}
}
