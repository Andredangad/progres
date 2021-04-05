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

public class ServerEchoMultiPort {

  private static final Logger logger = Logger.getLogger(ServerEchoMultiPort.class.getName());


    private final Selector selector;

    public ServerEchoMultiPort(int port1, int port2) throws IOException {
        if(port2 < port1 || port2 <= 0 || port1 <= 0){
            throw new IllegalArgumentException();
        }
        selector = Selector.open();

        for(int i = port1;i <= port2; i++){
            var dc = DatagramChannel.open();
            dc.bind(new InetSocketAddress(i));
            dc.configureBlocking(false);
            dc.register(selector, SelectionKey.OP_READ, new Context());
        }

   }


    public void serve() throws IOException {
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
            Context context = (Context) key.attachment();
        if (key.isValid() && key.isWritable()) {
            context.doWrite(key);
        }
        if (key.isValid() && key.isReadable()) {
            context.doRead(key);
        }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    

	public static class Context{
        private final int BUFFER_SIZE = 1024;
        private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
        private InetSocketAddress addr;

        private void doRead(SelectionKey key) throws IOException {
            buff.clear();
            DatagramChannel dc = (DatagramChannel) key.channel();
            addr = (InetSocketAddress) dc.receive(buff);
            if(addr != null){
                System.out.println("Packet received !");
                key.interestOps(SelectionKey.OP_WRITE);
            }
            else{
                System.out.println("No packets");
            }
            buff.flip();
	 }

        private void doWrite(SelectionKey key) throws IOException {
            DatagramChannel dc = (DatagramChannel) key.channel();
            dc.send(buff, addr);
            if(!buff.hasRemaining()){
                key.interestOps(SelectionKey.OP_READ);
                System.out.println("Packet sent !");
            }
            else{
                buff.compact();
                System.out.println("No packet sent");
            }

	}
    }

    public static void usage() {
        System.out.println("Usage : ServerEcho port1 port2");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        ServerEchoMultiPort server= new ServerEchoMultiPort(Integer.valueOf(args[0]), Integer.valueOf(args[1]));
        server.serve();
    }


}
