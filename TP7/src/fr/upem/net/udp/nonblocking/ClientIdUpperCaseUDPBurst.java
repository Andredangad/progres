package fr.upem.net.udp.nonblocking;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

    private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
    private static final Charset UTF8 = Charset.forName("UTF8");
    private static final int BUFFER_SIZE = 1024;

    private enum State {SENDING, RECEIVING, FINISHED}

    ;

    private final List<String> lines;
    private final int timeout;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final Selector selector;
    private final int nbLines;
    private final String[] upperCaseLines; //
    private final SelectionKey uniqueKey;
    private final ByteBuffer sendingBuff = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer receiveBuff = ByteBuffer.allocate(BUFFER_SIZE);
    private long lastSend;
    private int currentId;
    private final BitSet bs;
    // TODO add new fields

    private State state;

    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }

    public ClientIdUpperCaseUDPBurst(List<String> lines, int timeout, InetSocketAddress serverAddress) throws IOException {
        this.lines = lines;
        this.timeout = timeout;
        this.nbLines = lines.size();
        this.serverAddress = serverAddress;
        this.dc = DatagramChannel.open();
        this.bs = new BitSet(nbLines);
        dc.configureBlocking(false);
        dc.bind(null);
        this.selector = Selector.open();
        this.uniqueKey = dc.register(selector, SelectionKey.OP_WRITE);
        this.state = State.SENDING;
        this.upperCaseLines = new String[nbLines];


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
        ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines, timeout, serverAddress);
        String[] upperCaseLines = client.launch();
        Files.write(Paths.get(outFilename), Arrays.asList(upperCaseLines), UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

    }

    private String[] launch() throws IOException, InterruptedException {
        while (!isFinished()) {
            try {
                selector.select(this::treatKey, updateInterestOps());
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
        dc.close();
        return upperCaseLines;
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                doRead();
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Updates the interestOps on key based on state of the context
     *
     * @return the timeout for the next select (0 means no timeout)
     */

    private int updateInterestOps() {
        // TODO

        var currentTime = System.currentTimeMillis();
        if (currentTime - lastSend >= timeout){
            uniqueKey.interestOps(SelectionKey.OP_WRITE);
            return 0;
        }
        if (this.state == State.SENDING) {
            uniqueKey.interestOps(SelectionKey.OP_WRITE);
            return 0;
        }
        if (this.state == State.RECEIVING) {
            uniqueKey.interestOps(SelectionKey.OP_READ);

        }

        return (int) Math.abs(currentTime - lastSend - timeout);
    }

    private boolean isFinished() {
        return state == State.FINISHED;
    }

    /**
     * Performs the receptions of packets
     *
     * @throws IOException
     */

    private void doRead() throws IOException {
        receiveBuff.clear();
        var exp = dc.receive(receiveBuff);
        if (exp == null) {
            logger.info("Read : null");
            return;
        }
        receiveBuff.flip();
        if (receiveBuff.remaining() < Long.BYTES) {
            logger.info("ID in buff : error");
            return;
        }
        var id = receiveBuff.getLong();
        if(bs.get((int) id)){
            return;
        }

        logger.info("Receive packet ID : "+ id);
        bs.set((int) id);
        var decodeMsg = UTF8.decode(receiveBuff).toString();
        upperCaseLines[(int) id] = decodeMsg;
        if (bs.cardinality() == lines.size()) {
            state = State.FINISHED;
        }


    }

    /**
     * Tries to send the packets
     *
     * @throws IOException
     */

    private void doWrite() throws IOException {
        if(currentId > lines.size() -1){
            currentId = 0;
            state = State.RECEIVING;
            lastSend = System.currentTimeMillis();
            return;
        }

        if(bs.get(currentId)){
            currentId++;
            return;
        }

        sendingBuff.clear();
        sendingBuff.putLong(currentId);
        var text = UTF8.encode(lines.get(currentId));
        currentId++;
        sendingBuff.put(text);
        sendingBuff.flip();
        dc.send(sendingBuff, serverAddress);
        if (!sendingBuff.hasRemaining()) {
            logger.info("Sending packet : done ID : " + currentId);

        }

    }
}







