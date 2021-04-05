package fr.upem.net.udp;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;

public class ServerIdUpperCaseUDP {

    private static final Logger logger = Logger.getLogger(ServerIdUpperCaseUDP.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final DatagramChannel dc;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);

    public ServerIdUpperCaseUDP(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        logger.info("ServerBetterUpperCaseUDP started on port " + port);
    }

    public void serve() throws IOException {
        while (!Thread.interrupted()) {
            // TODO
          /*
          1) receive request from client
          2) read id
          3) decode msg in request
          

          4) create packet with id, upperCaseMsg in UTF-8
          5) send the packet to client
          */
            buff.clear();
            var req = dc.receive(buff);
            buff.flip();
            if (buff.remaining() >= Long.BYTES) {

                var id = buff.getLong();
                var msg = UTF8.decode(buff).toString();
                String upperCaseMsg = msg.toUpperCase();
                var msgEncode = UTF8.encode(upperCaseMsg);
                buff.clear();
                buff.putLong(id);
                buff.put(msgEncode);
                buff.flip();
                dc.send(buff, req);
            }
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
        ServerIdUpperCaseUDP server;
        int port = Integer.valueOf(args[0]);
        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }
        try {
            server = new ServerIdUpperCaseUDP(port);
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
        server.serve();
    }
}
