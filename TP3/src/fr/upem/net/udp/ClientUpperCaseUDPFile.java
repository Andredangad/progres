package fr.upem.net.udp;


import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientUpperCaseUDPFile {

    private final static Charset UTF8 = StandardCharsets.UTF_8;
    private final static int BUFFER_SIZE = 1024;
    private static final Logger logger =
            Logger.getLogger(ClientUpperCaseUDPFile.class.getName());

    private static void usage() {
        System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 5) {
            usage();
            return;
        }

        String inFilename = args[0];
        String outFilename = args[1];
        int timeout = Integer.valueOf(args[2]);
        String host = args[3];
        int port = Integer.valueOf(args[4]);
        SocketAddress dest = new InetSocketAddress(host, port);
        final BlockingQueue<String> queue = new SynchronousQueue<>();

        //Read all lines of inFilename opened in UTF-8
        List<String> lines = Files.readAllLines(Paths.get(inFilename), UTF8);
        ArrayList<String> upperCaseLines = new ArrayList<>();


        try (DatagramChannel dc = DatagramChannel.open()) {

            Thread listener = new Thread(() -> {
                ByteBuffer rcvBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        rcvBuffer.clear();
                        dc.receive(rcvBuffer);
                        rcvBuffer.flip();
                        queue.put(UTF8.decode(rcvBuffer).toString());
                    }
                    logger.info("Listened : out of loop");
                } catch (InterruptedException e) {
                    logger.info("Listener : InterruptedException");
                } catch (ClosedByInterruptException e) {
                    logger.info("Listener : ClosedByInterrupt");
                } catch (AsynchronousCloseException e) {
                    logger.info("Listener : AsynchronousClose");
                } catch (IOException e) {
                    logger.info("Listener : IOERROR");
                }


            });
            listener.start();
            for (var line : lines) {
                var bb = UTF8.encode(line);
                dc.send(bb, dest);
                String msg;
                while ((msg = queue.poll(timeout, TimeUnit.MILLISECONDS) )== null) {

                    bb.flip();
                    dc.send(bb, dest);


                }
                upperCaseLines.add(msg);


            }
            listener.interrupt();

            // Write upperCaseLines to outFilename in UTF-8
            Files.write(Paths.get(outFilename), upperCaseLines, UTF8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);


        }

    }
}


