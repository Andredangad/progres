package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class ClientUpperCaseUDPTimeout {

    public static final int BUFFER_SIZE = 1024;
    private static final Logger logger =
            Logger.getLogger(ClientUpperCaseUDPTimeout.class.getName());
    private static void usage(){
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length!=3){
            usage();
            return;
        }

        InetSocketAddress server = new InetSocketAddress(args[0],Integer.parseInt(args[1]));
        Charset cs = Charset.forName(args[2]);

        final ArrayBlockingQueue<String> arrayBlockingQueue = new ArrayBlockingQueue(1);


        try(DatagramChannel dc = DatagramChannel.open()){
            try (Scanner scan = new Scanner(System.in);){
            Thread listener = new Thread(() ->{
                ByteBuffer bb = ByteBuffer.allocateDirect(BUFFER_SIZE);
                try{
                    while(!Thread.currentThread().isInterrupted()){
                        bb.clear();
                        dc.receive(bb);
                        bb.flip();
                        arrayBlockingQueue.put(cs.decode(bb).toString());

                    }
                } catch (InterruptedException e) {
                    logger.info("Listener : InterruptedExpection");
                } catch (IOException e) {
                    logger.info("Listener : IO Error");
                }
            });
            listener.start();
            while(scan.hasNextLine()){
                String line = scan.nextLine();
                    var byteBuff = cs.encode(line);
                    dc.send(byteBuff, server);
                    var msg = arrayBlockingQueue.poll(1000, TimeUnit.MILLISECONDS);
                    if(msg == null){
                        System.out.println("The server did not reply");
                    }
                    else{
                        System.out.println(msg);
                    }
                }
            }

        }

    }
}
