package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPOneByOne {

    private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final List<String> lines;
    private final List<String> upperCaseLines = new ArrayList<>(); //
    private final int timeout;
    private final String outFilename;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;

    private final BlockingQueue<Response> queue = new SynchronousQueue<>();



    public static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }

    public ClientIdUpperCaseUDPOneByOne(List<String> lines,int timeout,InetSocketAddress serverAddress,String outFilename) throws IOException {
        this.lines = lines;
        this.timeout = timeout;
        this.outFilename = outFilename;
        this.serverAddress = serverAddress;
        this.dc = DatagramChannel.open();
        dc.bind(null);
    }

    private void listenerThreadRun(){
       while(!Thread.currentThread().isInterrupted()){
           ByteBuffer rcvBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
           try {
               rcvBuffer.clear();
               dc.receive(rcvBuffer);
               rcvBuffer.flip();
               var id = rcvBuffer.getLong();
               var msg = UTF8.decode(rcvBuffer).toString();
               Response res = new Response(id,msg);
               queue.put(res);
           }catch (ClosedByInterruptException e) {
               logger.info("Listener : ClosedByInterrupt");
           } catch (AsynchronousCloseException e) {
               logger.info("Listener : AsynchronousClose");
           } catch (IOException e) {
               logger.log(Level.SEVERE,"Listener : IOException", e);
           }
           catch (InterruptedException e) {
               logger.info("Listener has been interrupted");
           }

       }
	 }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length !=5) {
            usage();
            return;
        }

        String inFilename = args[0];
        String outFilename = args[1];
        int timeout = Integer.valueOf(args[2]);
        String host=args[3];
        int port = Integer.valueOf(args[4]);
        InetSocketAddress serverAddress = new InetSocketAddress(host,port);

        //Read all lines of inFilename opened in UTF-8
        List<String> lines= Files.readAllLines(Paths.get(inFilename),UTF8);
        //Create client with the parameters and launch it
        ClientIdUpperCaseUDPOneByOne client = new ClientIdUpperCaseUDPOneByOne(lines,timeout,serverAddress,outFilename);
        client.launch();

    }

    public void launch() throws IOException, InterruptedException {
        Thread listenerThread = new Thread(this::listenerThreadRun);
        listenerThread.start();
        long lastSend = 0;
        ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
        for(int i = 0; i < lines.size(); i++){
            bb.clear();
            bb.putLong(i);
            bb.put(UTF8.encode(lines.get(i)));
            Response msg;
            while((msg = queue.poll(timeout, TimeUnit.MILLISECONDS)) == null || msg.id != i){
                var currentTime = System.currentTimeMillis();

                if (currentTime - lastSend >= timeout) {
                    bb.flip();
                    dc.send(bb, serverAddress);
                    lastSend = currentTime;
                }
            }
            upperCaseLines.add(msg.msg);

        }
        listenerThread.interrupt();

        Files.write(Paths.get(outFilename), upperCaseLines, UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

        dc.close();
    }



    private static class Response {
        long id;
        String msg;

        Response(long id, String msg) {
            this.id = id;
            this.msg = msg;
        }
    }
}



