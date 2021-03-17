package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientUpperCaseUDPRetry {
    public static final int BUFFER_SIZE = 1024;
    private static final Logger logger =
            Logger.getLogger(fr.upem.net.udp.ClientUpperCaseUDPTimeout.class.getName());
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

        final BlockingQueue<String> queue = new SynchronousQueue<>();


        try(DatagramChannel dc = DatagramChannel.open()){
            try (Scanner scan = new Scanner(System.in);){
                Thread listener = new Thread(() ->{
                    ByteBuffer bb = ByteBuffer.allocateDirect(BUFFER_SIZE);
                    try{
                        while(!Thread.currentThread().isInterrupted()){
                            bb.clear();
                            dc.receive(bb);
                            bb.flip();
                            queue.put(cs.decode(bb).toString());

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
                    var msg = queue.poll(1000, TimeUnit.MILLISECONDS);
                    if(msg == null){
                        byteBuff.flip();
                        dc.send(byteBuff, server);
                        queue.poll(1000, TimeUnit.MILLISECONDS);
                        System.out.println("The server did not reply, resending a request...");
                    }
                    else{
                        System.out.println(msg);
                    }
                }
            }

        }

    }
}
