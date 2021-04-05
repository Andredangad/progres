package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoPlus {

    private static final Logger logger = Logger.getLogger(ServerEchoPlus.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final ByteBuffer sendBb = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress exp;
    private int port;

    public ServerEchoPlus(int port) throws IOException {
        this.port=port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
 		  // TODO set dc in non-blocking mode and register it to the selector
   }


    public void serve() throws IOException {
        logger.info("ServerEcho started on port "+port);
        while (!Thread.interrupted()) {
            try{
                selector.select(this::treatKey);
            }
            catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try{
        if (key.isValid() && key.isWritable()) {
            doWrite(key);
        }
        if (key.isValid() && key.isReadable()) {
            doRead(key);
        }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private void doRead(SelectionKey key) throws IOException {
        buff.clear();
   		exp = dc.receive(buff);
   		if(exp != null){
            buff.flip();
            sendBb.clear();
            while(buff.hasRemaining()){
                sendBb.put((byte) (buff.get() + 1%255));
            }
            key.interestOps(SelectionKey.OP_WRITE);
        }
   		else{
            System.out.println("No packets");
        }

	 }

    private void doWrite(SelectionKey key) throws IOException {
        sendBb.flip();
   		dc.send(sendBb, exp);
   		if(!sendBb.hasRemaining()){
            System.out.println("Packet sent !");
            key.interestOps(SelectionKey.OP_READ);
        }
   		else{
   		    sendBb.compact();
            System.out.println("No packet sent");
        }

	}

    public static void usage() {
        System.out.println("Usage : ServerEcho port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerEchoPlus server= new ServerEchoPlus(Integer.valueOf(args[0]));
        server.serve();
    }




}
