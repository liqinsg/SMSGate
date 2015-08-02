/**
 * 
 */
package com.zx.sms.codec.cmpp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.zx.sms.codec.cmpp.msg.CmppSubmitRequestMessage;
import com.zx.sms.codec.cmpp.msg.CmppSubmitResponseMessage;
import com.zx.sms.codec.cmpp.msg.DefaultMessage;
import com.zx.sms.codec.cmpp.msg.LongMessageFrame;
import com.zx.sms.codec.cmpp.msg.Message;
import com.zx.sms.codec.cmpp.packet.CmppPacketType;
import com.zx.sms.codec.cmpp.packet.CmppSubmitRequest;
import com.zx.sms.codec.cmpp.packet.PacketType;
import com.zx.sms.common.GlobalConstance;
import com.zx.sms.common.util.CMPPCommonUtil;
import com.zx.sms.common.util.DefaultMsgIdUtil;
import com.zx.sms.common.util.LongMessageFrameHolder;

/**
 * @author huzorro(huzorro@gmail.com)
 * @author Lihuanghe(18852780@qq.com)
 */
public class CmppSubmitRequestMessageCodec extends MessageToMessageCodec<Message, CmppSubmitRequestMessage> {
	private PacketType packetType;

	/**
	 * 
	 */
	public CmppSubmitRequestMessageCodec() {
		this(CmppPacketType.CMPPSUBMITREQUEST);
	}

	public CmppSubmitRequestMessageCodec(PacketType packetType) {
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

		CmppSubmitRequestMessage requestMessage = new CmppSubmitRequestMessage(msg.getHeader());

		ByteBuf bodyBuffer = msg.getBodyBuffer();

		requestMessage.setMsgid(DefaultMsgIdUtil.bytes2MsgId(bodyBuffer.readBytes(CmppSubmitRequest.MSGID.getLength()).array()));

		LongMessageFrame frame = new LongMessageFrame();

		frame.setPktotal(bodyBuffer.readUnsignedByte());
		frame.setPknumber(bodyBuffer.readUnsignedByte());

		requestMessage.setRegisteredDelivery(bodyBuffer.readUnsignedByte());
		requestMessage.setMsglevel(bodyBuffer.readUnsignedByte());
		requestMessage.setServiceId(bodyBuffer.readBytes(CmppSubmitRequest.SERVICEID.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());
		requestMessage.setFeeUserType(bodyBuffer.readUnsignedByte());

		requestMessage.setFeeterminalId(bodyBuffer.readBytes(CmppSubmitRequest.FEETERMINALID.getLength()).toString(GlobalConstance.defaultTransportCharset)
				.trim());

		requestMessage.setFeeterminaltype(bodyBuffer.readUnsignedByte());

		frame.setTppid(bodyBuffer.readUnsignedByte());
		frame.setTpudhi(bodyBuffer.readUnsignedByte());
		frame.setMsgfmt(bodyBuffer.readUnsignedByte());

		requestMessage.setMsgsrc(bodyBuffer.readBytes(CmppSubmitRequest.MSGSRC.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());

		requestMessage.setFeeType(bodyBuffer.readBytes(CmppSubmitRequest.FEETYPE.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());

		requestMessage.setFeeCode(bodyBuffer.readBytes(CmppSubmitRequest.FEECODE.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());

		requestMessage.setValIdTime(bodyBuffer.readBytes(CmppSubmitRequest.VALIDTIME.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());

		requestMessage.setAtTime(bodyBuffer.readBytes(CmppSubmitRequest.ATTIME.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());

		requestMessage.setSrcId(bodyBuffer.readBytes(CmppSubmitRequest.SRCID.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());

		requestMessage.setDestUsrtl(bodyBuffer.readUnsignedByte());
		String[] destTermId = new String[requestMessage.getDestUsrtl()];
		for (int i = 0; i < requestMessage.getDestUsrtl(); i++) {
			destTermId[i] = bodyBuffer.readBytes(CmppSubmitRequest.DESTTERMINALID.getLength()).toString(GlobalConstance.defaultTransportCharset).trim();
		}
		requestMessage.setDestterminalId(destTermId);

		requestMessage.setDestterminaltype(bodyBuffer.readUnsignedByte());

		short msgLength = bodyBuffer.readUnsignedByte();

		byte[] contentbytes = new byte[msgLength];
		bodyBuffer.readBytes(contentbytes);
		frame.setMsgContentBytes(contentbytes);

		requestMessage.setLinkID(bodyBuffer.readBytes(CmppSubmitRequest.LINKID.getLength()).toString(GlobalConstance.defaultTransportCharset).trim());
		// requestMessage.setReserve(bodyBuffer.readBytes(CmppSubmitRequest.RESERVE.getLength()).toString(GlobalConstance.defaultTransportCharset)
		// .trim()); /** CMPP3.0 无该字段 不解码*/
		ReferenceCountUtil.release(bodyBuffer);

		String content = LongMessageFrameHolder.INS.putAndget(StringUtils.join(destTermId, "|"), frame);

		if (content != null) {
			requestMessage.setMsgContent(content);
			out.add(requestMessage);
		}else{
			CmppSubmitResponseMessage responseMessage = new CmppSubmitResponseMessage(msg.getHeader());


			responseMessage.setMsgId(requestMessage.getMsgid());
			responseMessage.setResult(0);
			ctx.channel().writeAndFlush(responseMessage);
		}
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, CmppSubmitRequestMessage requestMessage, List<Object> out) throws Exception {
		List<LongMessageFrame> frameList = LongMessageFrameHolder.INS.splitmsgcontent(requestMessage.getMsgContent(), requestMessage.isSupportLongMsg());
		boolean first = true;
		for (LongMessageFrame frame : frameList) {

			ByteBuf bodyBuffer = ctx.alloc().buffer(
					CmppSubmitRequest.ATTIME.getBodyLength() + frame.getMsgLength() + requestMessage.getDestUsrtl()
							* CmppSubmitRequest.DESTTERMINALID.getLength());

			bodyBuffer.writeBytes(DefaultMsgIdUtil.msgId2Bytes(requestMessage.getMsgid()));

			bodyBuffer.writeByte(frame.getPktotal());
			bodyBuffer.writeByte(frame.getPknumber());
			bodyBuffer.writeByte(requestMessage.getRegisteredDelivery());
			bodyBuffer.writeByte(requestMessage.getMsglevel());

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getServiceId().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.SERVICEID.getLength(), 0));

			bodyBuffer.writeByte(requestMessage.getFeeUserType());

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getFeeterminalId().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.FEETERMINALID.getLength(), 0));
			bodyBuffer.writeByte(requestMessage.getFeeterminaltype());
			bodyBuffer.writeByte(frame.getTppid());
			bodyBuffer.writeByte(frame.getTpudhi());
			bodyBuffer.writeByte(frame.getMsgfmt());

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getMsgsrc().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.MSGSRC.getLength(), 0));

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getFeeType().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.FEETYPE.getLength(), 0));

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getFeeCode().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.FEECODE.getLength(), 0));

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getValIdTime().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.VALIDTIME.getLength(), 0));

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getAtTime().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.ATTIME.getLength(), 0));

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getSrcId().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.SRCID.getLength(), 0));

			bodyBuffer.writeByte(requestMessage.getDestUsrtl());
			for (int i = 0; i < requestMessage.getDestUsrtl(); i++) {
				String[] destTermId = requestMessage.getDestterminalId();
				bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(destTermId[i].getBytes(GlobalConstance.defaultTransportCharset),
						CmppSubmitRequest.DESTTERMINALID.getLength(), 0));
			}
			bodyBuffer.writeByte(requestMessage.getDestterminaltype());

			assert (frame.getMsgLength() == frame.getMsgContentBytes().length);
			assert (frame.getMsgLength() <=  140);
			bodyBuffer.writeByte(frame.getMsgLength());

			bodyBuffer.writeBytes(frame.getMsgContentBytes());

			bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getLinkID().getBytes(GlobalConstance.defaultTransportCharset),
					CmppSubmitRequest.LINKID.getLength(), 0));

			// bodyBuffer.writeBytes(CMPPCommonUtil.ensureLength(requestMessage.getReserve().getBytes(GlobalConstance.defaultTransportCharset),
			// CmppSubmitRequest.RESERVE.getLength(), 0));/**cmpp3.0 无该字段，不进行编解码
			// */

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
