package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;



public class NetcatUDP {

    public static final int BUFFER_SIZE = 1024;

    private static void usage(){
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException {
        if (args.length!=3){
            usage();
            return;
        }

        InetSocketAddress server = new InetSocketAddress(args[0],Integer.parseInt(args[1]));
        Charset cs = Charset.forName(args[2]);
        ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);

        try (Scanner scan = new Scanner(System.in);){
            while(scan.hasNextLine()){
                String line = scan.nextLine();

                try(DatagramChannel dc = DatagramChannel.open()){ ;
                    bb = cs.encode(line);
                    dc.send(bb, server);
                    bb.clear();
                    InetSocketAddress exp=(InetSocketAddress) dc.receive(bb);
                    bb.flip();
                    System.out.println("Received "+ bb.remaining() + " bytes from "+ exp);
                    System.out.println("String: "+ cs.decode(bb));

                    bb.clear();
                }
            }

        }

    }
}
