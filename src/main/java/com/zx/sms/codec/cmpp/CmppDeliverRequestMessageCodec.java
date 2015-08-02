/**
 * 
 */
package com.zx.sms.codec.cmpp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

import com.zx.sms.codec.cmpp.msg.CmppDeliverRequestMessage;
import com.zx.sms.codec.cmpp.msg.CmppDeliverResponseMessage;
import com.zx.sms.codec.cmpp.msg.CmppReportRequestMessage;
import com.zx.sms.codec.cmpp.msg.DefaultMessage;
import com.zx.sms.codec.cmpp.msg.LongMessageFrame;
import com.zx.sms.codec.cmpp.msg.Message;
import com.zx.sms.codec.cmpp.packet.CmppDeliverRequest;
import com.zx.sms.codec.cmpp.packet.CmppPacketType;
import com.zx.sms.codec.cmpp.packet.CmppReportRequest;
import com.zx.sms.codec.cmpp.packet.PacketType;
import com.zx.sms.common.GlobalConstance;
import com.zx.sms.common.util.CMPPCommonUtil;
import com.zx.sms.common.util.DefaultMsgIdUtil;
import com.zx.sms.common.util.LongMessageFrameHolder;

/**
 * @author huzorro(huzorro@gmail.com)
 * @author Lihuanghe(18852780@qq.com)
 */
public class CmppDeliverRequestMessageCodec extends MessageToMessageCodec<Message, CmppDeliverRequestMessage> {
	private PacketType packetType;

	/**
	 * 
	 */
	public CmppDeliverRequestMessageCodec() {
		this(CmppPacketType.CMPPDELIVERREQUEST);
	}

	public CmppDeliverRequestMessageCodec(PacketType packetType) {
		this.packetType = packetType;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {

		long commandId = ((Long) msg.getHeader().getCommandId()).longValue();
		if (packetType.getCommandId() != commandId) {
			// 不解析，交给下一个codec
			out.add(msg);
			return;
		}

		CmppDeliverRequestMessage requestMessage = new CmppDeliverRequestMessage(msg.getHeader());

		ByteBuf bodyBuffer = msg.getBodyBuffer();

		requestMessage.setMsgId(DefaultMsgIdUtil.bytes2MsgId(bodyBuffer.readBytes(CmppDeliverRequest.MSGID.getLength()).array()));
		requestMessage.setDestId(bodyBuffer.readBytes(CmppDeliverRequest.DESTID.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());
		requestMessage.setServiceid(bodyBuffer.readBytes(CmppDeliverRequest.SERVICEID.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());

		LongMessageFrame frame = new LongMessageFrame();
		frame.setTppid(bodyBuffer.readUnsignedByte());
		frame.setTpudhi(bodyBuffer.readUnsignedByte());
		frame.setMsgfmt(bodyBuffer.readUnsignedByte());

		requestMessage.setSrcterminalId(bodyBuffer.readBytes(CmppDeliverRequest.SRCTERMINALID.getLength()).toString(GlobalConstance.defaultTransportCharset)
				.trim());
		requestMessage.setSrcterminalType(bodyBuffer.readUnsignedByte());
		requestMessage.setRegisteredDelivery(bodyBuffer.readUnsignedByte());

		int frameLength = bodyBuffer.readUnsignedByte();

		if (requestMessage.getRegisteredDelivery() == 0) {
			byte[] contentbytes = new byte[frameLength];
			bodyBuffer.readBytes(contentbytes);
			frame.setMsgContentBytes(contentbytes);
		} else {
			requestMessage.setReportRequestMessage(new CmppReportRequestMessage());
			requestMessage.getReportRequestMessage().setMsgId(DefaultMsgIdUtil.bytes2MsgId(bodyBuffer.readBytes(CmppReportRequest.MSGID.getLength()).array()));
			requestMessage.getReportRequestMessage().setStat(
					bodyBuffer.readBytes(CmppReportRequest.STAT.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());
			requestMessage.getReportRequestMessage().setSubmitTime(
					bodyBuffer.readBytes(CmppReportRequest.SUBMITTIME.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());
			requestMessage.getReportRequestMessage().setDoneTime(
					bodyBuffer.readBytes(CmppReportRequest.DONETIME.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());
			requestMessage.getReportRequestMessage().setDestterminalId(
					bodyBuffer.readBytes(CmppReportRequest.DESTTERMINALID.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());
			requestMessage.getReportRequestMessage().setSmscSequence(bodyBuffer.readUnsignedInt());
		}

		requestMessage.setLinkid(bodyBuffer.readBytes(CmppDeliverRequest.LINKID.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());
		// requestMessage.setReserved(bodyBuffer
		// .readBytes(CmppDeliverRequest.RESERVED.getLength())
		// .toString(GlobalConstance.defaultTransportCharset).trim());

		ReferenceCountUtil.release(bodyBuffer);

		String content = LongMessageFrameHolder.INS.putAndget(requestMessage.getSrcterminalId(), frame);

		if (content != null) {
			requestMessage.setMsgContent(content);
			out.add(requestMessage);
		}else{
			//收到一个短信片断立即回复resp,但不通知应用层
	        CmppDeliverResponseMessage responseMessage = new CmppDeliverResponseMessage(msg.getHeader());
			responseMessage.setMsgId(requestMessage.getMsgId());
			responseMessage.setResult(0);
			ctx.channel().writeAndFlush(responseMessage);
		}

	}

	@Override
	protected void encode(ChannelHandlerContext ctx, CmppDeliverRequestMessage requestMessage, List<Object> out) throws Exception {

		List<LongMessageFrame> frameList = LongMessageFrameHolder.INS.splitmsgcontent(requestMessage.getMsgContent(), requestMessage.isSupportLongMsg());
		boolean first = true;
		for (LongMessageFrame frame : frameList) {
			// bodyBuffer 会在CmppHeaderCodec.encode里释放
			ByteBuf bodyBuffer = ctx.alloc().buffer(CmppDeliverRequest.DESTID.getBodyLength() + frame.getMsgLength());

			bodyBuffer.writeBytes(DefaultMsgIdUtil.msgId2Bytes(requestMessage.getMsgId()));
			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getDestId().getBytes(GlobalConstance.defaultTransportCharset),
					CmppDeliverRequest.DESTID.getLength(), 0));
			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getServiceid().getBytes(GlobalConstance.defaultTransportCharset),
					CmppDeliverRequest.SERVICEID.getLength(), 0));
			bodyBuffer.writeByte(frame.getTppid());
			bodyBuffer.writeByte(frame.getTpudhi());
			bodyBuffer.writeByte(frame.getMsgfmt());
			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getSrcterminalId().getBytes(GlobalConstance.defaultTransportCharset),
					CmppDeliverRequest.SRCTERMINALID.getLength(), 0));
			bodyBuffer.writeByte(requestMessage.getSrcterminalType());
			bodyBuffer.writeByte(requestMessage.getRegisteredDelivery());
			bodyBuffer.writeByte(frame.getMsgLength());

			if (!requestMessage.isReport()) {
				bodyBuffer.writeBytes(frame.getMsgContentBytes());
			} else {
				bodyBuffer.writeBytes(DefaultMsgIdUtil.msgId2Bytes(requestMessage.getReportRequestMessage().getMsgId()));
				bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(
						requestMessage.getReportRequestMessage().getStat().getBytes(GlobalConstance.defaultTransportCharset),
						CmppReportRequest.STAT.getLength(), 0));
				bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(
						requestMessage.getReportRequestMessage().getSubmitTime().getBytes(GlobalConstance.defaultTransportCharset),
						CmppReportRequest.SUBMITTIME.getLength(), 0));
				bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(
						requestMessage.getReportRequestMessage().getDoneTime().getBytes(GlobalConstance.defaultTransportCharset),
						CmppReportRequest.DONETIME.getLength(), 0));
				bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(
						requestMessage.getReportRequestMessage().getDestterminalId().getBytes(GlobalConstance.defaultTransportCharset),
						CmppReportRequest.DESTTERMINALID.getLength(), 0));

				bodyBuffer.writeInt((int) requestMessage.getReportRequestMessage().getSmscSequence());
			}

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getLinkid().getBytes(GlobalConstance.defaultTransportCharset),
					CmppDeliverRequest.LINKID.getLength(), 0));

			if (first) {
				DefaultMessage defaultMsg = new DefaultMessage();
				defaultMsg.setHeader(requestMessage.getHeader());
				defaultMsg.setBodyBuffer(bodyBuffer);
				out.add(defaultMsg);
				first = false;
			} else {
				DefaultMessage defaultMsg = new DefaultMessage(requestMessage.getPacketType());
				defaultMsg.setBodyBuffer(bodyBuffer);
				out.add(defaultMsg);
			}
		}

	}

}
