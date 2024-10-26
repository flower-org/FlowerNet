package com.flower.dns;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DnsMessageHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {
    final static Logger LOGGER = LoggerFactory.getLogger(DnsMessageHandler.class);

    public final DnsOverTlsClient client;

    public DnsMessageHandler(DnsOverTlsClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
        DnsQuestion qQuestion = query.recordAt(DnsSection.QUESTION, 0);
        LOGGER.info("{} | request: {}", query.id(), qQuestion.name());
        query.retain();

        client.query(query,
                tcpResponse -> {
                    if (tcpResponse.count(DnsSection.QUESTION) > 0) {
                        DnsQuestion question = tcpResponse.recordAt(DnsSection.QUESTION, 0);
                        LOGGER.info("{} | response: {}", tcpResponse.id(), question.name());
                    }
                    for (int i = 0, count = tcpResponse.count(DnsSection.ANSWER); i < count; i++) {
                        DnsRecord record = tcpResponse.recordAt(DnsSection.ANSWER, i);
                        if (record.type() == DnsRecordType.A) {
                            //just print the IP after query
                            DnsRawRecord raw = (DnsRawRecord) record;
                            LOGGER.info("{} | {}", tcpResponse.id(), NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
                        }
                    }

                    DatagramDnsResponse udpResponse = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
                    udpResponse.retain();
                    udpResponse.setCode(tcpResponse.code());
                    for (DnsSection section : DnsSection.values()) {
                        int count = tcpResponse.count(section);
                        for (int i = 0; i < count; i++) {
                            DnsRecord record = tcpResponse.recordAt(section, i);
                            udpResponse.addRecord(section, record);
                        }
                    }

                    ctx.writeAndFlush(udpResponse);
                }
        );
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("DnsMessageHandler.exceptionCaught", cause);
    }
}