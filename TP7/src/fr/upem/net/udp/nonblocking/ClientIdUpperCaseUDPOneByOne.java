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
import java.util.List;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPOneByOne {

    private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
    private static final Charset UTF8 = Charset.forName("UTF8");
    private static final int BUFFER_SIZE = 1024;

    private enum State {SENDING, RECEIVING, FINISHED}

    ;

    private final List<String> lines;
    private final List<String> upperCaseLines = new ArrayList<>();
    private final int timeout;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final Selector selector;
    private final SelectionKey uniqueKey;
    private final ByteBuffer sendingBuff = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer receiveBuff = ByteBuffer.allocate(BUFFER_SIZE);
    private long lastSend;
    private int currentId;
    // TODO add new fields

    private State state;

    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }

    public ClientIdUpperCaseUDPOneByOne(List<String> lines, int timeout, InetSocketAddress serverAddress) throws IOException {
        this.lines = lines;
        this.timeout = timeout;
        this.serverAddress = serverAddress;
        this.dc = DatagramChannel.open();
        dc.configureBlocking(false);
        dc.bind(null);
        this.selector = Selector.open();
        this.uniqueKey = dc.register(selector, SelectionKey.OP_WRITE);
        this.state = State.SENDING;
        sendingBuff.putLong(currentId);
        sendingBuff.put(UTF8.encode(lines.get((int) currentId)));
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
        ClientIdUpperCaseUDPOneByOne client = new ClientIdUpperCaseUDPOneByOne(lines, timeout, serverAddress);
        List<String> upperCaseLines = client.launch();
        Files.write(Paths.get(outFilename), upperCaseLines, UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

    }

    private List<String> launch() throws IOException, InterruptedException {
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
        if (this.state == State.SENDING || currentTime - lastSend >= timeout) {
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
        if (id != currentId) {

            logger.info("Wrong packet, ID: " + id + " currentID : "+ currentId);
            return;
        }
        currentId++;
        logger.info("Receive packet ID : "+ id);
        var decodeMsg = UTF8.decode(receiveBuff).toString();
        upperCaseLines.add(decodeMsg);
        if (currentId == lines.size()) {
            state = State.FINISHED;
            return;
        }
        state = State.SENDING;
        sendingBuff.clear();
        sendingBuff.putLong(currentId);
        var text = UTF8.encode(lines.get(currentId));
        sendingBuff.put(text);
    }

    /**
     * Tries to send the packets
     *
     * @throws IOException
     */

    private void doWrite() throws IOException {
        // TODO
        sendingBuff.flip();
        dc.send(sendingBuff, serverAddress);
        if (!sendingBuff.hasRemaining()) {
            logger.info("Sending packet : done ID : " + currentId);
            state = State.RECEIVING;
            lastSend = System.currentTimeMillis();
        } else {
            sendingBuff.compact();
            logger.info("Sending packet : failed");

        }

    }
}







