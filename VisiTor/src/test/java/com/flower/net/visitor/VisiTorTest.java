package com.flower.net.visitor;

import com.flower.crypt.PkiUtil;
import com.flower.net.utils.IpAddressUtil;
import com.flower.net.visitor.cells.NetInfoTorCell;
import com.flower.net.visitor.cells.TorCell;
import com.flower.net.visitor.client.TorV3Client;
import io.netty.channel.Channel;

import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public class VisiTorTest {
    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getInsecureTrustManagerFactory();

    @Test
    public void test0() throws SSLException, InterruptedException {
        TorV3Client client = new TorV3Client(TRUST_MANAGER, 50000);
        //client.establishConnection(IpAddressUtil.fromString("1.1.1.1"), 443);
        Promise<Channel> channelPromise = client.establishConnection(IpAddressUtil.fromString("131.188.40.189"), 443);

        channelPromise.addListener(future -> {
            if (future.isSuccess()) {
                Channel channel = (Channel)future.getNow();
                client.setCellListener(torCell -> {
                    System.out.println("Received TorCell " + torCell);
                    if (torCell instanceof NetInfoTorCell) {
                        List<NetInfoTorCell.NetInfoAddress> myAddresses = ((NetInfoTorCell)torCell).myAddresses;
                        NetInfoTorCell myNetInfo =
                                new NetInfoTorCell(0, 0,
                                        checkNotNull(myAddresses.get(0)),
                                        List.of(NetInfoTorCell.EMPTY_ADDRESS));
                        client.sendNetInfo(channel, myNetInfo);
                    }
                });

                client.initTorHandshake(channel);
            } else {
                throw new RuntimeException(future.cause());
            }
        });

        Thread.sleep(10000);
    }
}
