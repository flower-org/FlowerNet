package com.flower.net.visitor;

import com.flower.crypt.PkiUtil;
import com.flower.net.utils.IpAddressUtil;
import com.flower.net.visitor.cells.VersionsTorCell;
import com.flower.net.visitor.client.TorClientV3;
import io.netty.channel.Channel;

import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

public class VisiTorTest {
    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getInsecureTrustManagerFactory();

    @Test
    public void test0() throws SSLException, InterruptedException {
        TorClientV3 client = new TorClientV3(TRUST_MANAGER, 50000);
        //client.establishConnection(IpAddressUtil.fromString("1.1.1.1"), 443);
        Promise<Channel> channelPromise = client.establishConnection(IpAddressUtil.fromString("199.58.81.140"), 443);

        channelPromise.addListener(future -> {
            if (future.isSuccess()) {
                Channel channel = (Channel)future.getNow();
                channel.write(new VersionsTorCell(0, 3));
            } else {
                throw new RuntimeException(future.cause());
            }
        });

        Thread.sleep(10000);
    }
}
