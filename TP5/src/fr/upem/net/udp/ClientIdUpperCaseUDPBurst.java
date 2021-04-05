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
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

    private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final List<String> lines;
    private final int nbLines;
    private final String[] upperCaseLines; //
    private final int timeout;
    private final String outFilename;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final AnswersLog answersLog;         // Thread-safe structure keeping track of missing responses

    public static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
    }

    public ClientIdUpperCaseUDPBurst(List<String> lines, int timeout, InetSocketAddress serverAddress, String outFilename) throws IOException {
        this.lines = lines;
        this.nbLines = lines.size();
        this.timeout = timeout;
        this.outFilename = outFilename;
        this.serverAddress = serverAddress;
        this.dc = DatagramChannel.open();
        dc.bind(null);
        this.upperCaseLines = new String[nbLines];
        this.answersLog = new AnswersLog(nbLines);
    }

    private void senderThreadRun() {
        var sendBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        try {
            while (!Thread.currentThread().isInterrupted()) {

                for (var i = 0; i < nbLines; i++) {
                    if (!answersLog.isSent(i)) {
                        sendBuffer.clear();
                        sendBuffer.putLong(i);
                        sendBuffer.put(UTF8.encode(lines.get(i)));
                        try {
                            sendBuffer.flip();
                            dc.send(sendBuffer, serverAddress);
                        } catch (ClosedByInterruptException e) {
                            logger.info("Listener : ClosedByInterrupt");
                        } catch (AsynchronousCloseException e) {
                            logger.info("Listener : AsynchronousClose");
                        } catch (IOException e) {
                            logger.log(Level.SEVERE, "Sender : IOException");
                        }
                    }
                }

                Thread.sleep(timeout);
            }
        } catch (InterruptedException e) {
            logger.info("Sender has been interrupted");
        }
        System.out.println("finishSEnd");
    }

    public void launch() throws IOException {
        Thread senderThread = new Thread(this::senderThreadRun);
        senderThread.start();
        ByteBuffer rcvBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        // TODO : body of the receiver thread
        while (!answersLog.allSent()) {
            rcvBuffer.clear();
            dc.receive(rcvBuffer);
            rcvBuffer.flip();
            var id = rcvBuffer.getLong();
            answersLog.setReceived((int) id);
            var msg = UTF8.decode(rcvBuffer).toString();

            upperCaseLines[(int) id] = msg;
        }
        senderThread.interrupt();

        Files.write(Paths.get(outFilename), Arrays.asList(upperCaseLines), UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        dc.close();
        System.out.println("finishReceinved");
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
        InetSocketAddress serverAddress = new InetSocketAddress(host, port);

        //Read all lines of inFilename opened in UTF-8
        List<String> lines = Files.readAllLines(Paths.get(inFilename), UTF8);
        //Create client with the parameters and launch it
        ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines, timeout, serverAddress, outFilename);
        client.launch();


    }

    private static class AnswersLog {
        private final BitSet bs;
        private final int bitSetSize;

        public AnswersLog(int size) {
            bs = new BitSet(size);
            bitSetSize = size;

        }

        private final Object lock = new Object();

        public void setReceived(int value) {
            synchronized (lock) {
                bs.set(value);
            }
        }

        public boolean isSent(int value) {
            synchronized (lock) {
                return bs.get(value);

            }
        }

        public boolean allSent() {
            if (bitSetSize == bs.cardinality()) {

                return true;
            }
            return false;
        }


        // TODO Thread-safe class handling the information about missing lines

    }
}


