package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class ServerLongSum {

    private static final Logger logger = Logger.getLogger(ServerLongSum.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final DatagramChannel dc;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final ByteBuffer sendBuff = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final HashMap<InetSocketAddress, HashMap<Long, ClientData>> map = new HashMap<>();
    private final byte OP_CODE = 1;
    private final byte ACK_CODE = 2;
    private final byte RES_CODE = 3;

    public ServerLongSum(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        logger.info("ServerBetterUpperCaseUDP started on port " + port);
    }

    public void serve() throws IOException {
        while (!Thread.interrupted()) {
            buff.clear();
            var res = dc.receive(buff);
            buff.flip();
            if (buff.remaining() > Byte.BYTES) {
                var op = buff.get();
                if (op == OP_CODE) {
                    if (buff.remaining() > Long.BYTES) {
                        var sessionId = buff.getLong();
                        if (buff.remaining() > Long.BYTES) {
                            var idPos = buff.getLong();
                            if (buff.remaining() > Long.BYTES) {
                                var totalOper = buff.getLong();
                                if (buff.remaining() >= Long.BYTES) {
                                    var value = buff.getLong();
                                    //VERIFIER SI SESSIONID EXISTE
                                    var cd = new ClientData(totalOper);
                                    HashMap<Long, ClientData> temp = new HashMap<>();
                                    temp.put(sessionId, cd);
                                    map.putIfAbsent((InetSocketAddress) res, temp);
                                    if (!map.get(res).containsKey(sessionId)) {
                                        map.get(res).put(sessionId, cd);
                                    }

                                    if (map.get(res).get(sessionId).update(value, idPos)) {
                                        sendBuff.clear();
                                        sendBuff.put(RES_CODE);
                                        sendBuff.putLong(sessionId);
                                        sendBuff.putLong(map.get(res).get(sessionId).getTotal());
                                        sendBuff.flip();
                                        dc.send(sendBuff, res);

                                    } else {
                                        sendBuff.clear();

                                        sendBuff.put(ACK_CODE);
                                        sendBuff.putLong(sessionId);
                                        sendBuff.putLong(idPos);
                                        sendBuff.flip();
                                        dc.send(sendBuff, res);
                                    }

                                }
                            }
                        }
                    }

                }
            }

        }
        dc.close();
    }


    public static class ClientData {
        private final BitSet bs;
        private long total;
        private final long totalOPer;

        public ClientData(long totalOPer) {
            this.bs = new BitSet((int) totalOPer);
            this.totalOPer = totalOPer;
        }

        public boolean update(long value, long orderVal) {
            if (!bs.get((int) orderVal)) {
                bs.set((int) orderVal);
                total += value;
            }

            if (bs.cardinality() == totalOPer) {
                return true;
            }
            return false;
        }

        public long getTotal() {
            return total;
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerIdUpperCaseUDP port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerLongSum server;
        int port = Integer.valueOf(args[0]);
        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }
        try {
            server = new ServerLongSum(port);
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
        server.serve();
    }
}
