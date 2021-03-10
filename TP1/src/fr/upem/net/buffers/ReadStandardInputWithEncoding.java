package fr.upem.net.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ReadStandardInputWithEncoding {

    private static final int BUFFER_SIZE = 1024;

    private static void usage(){
        System.out.println("Usage: ReadStandardInputWithEncoding charset");
    }



    private static String stringFromStandardInput(Charset cs) throws IOException {
        try(ReadableByteChannel in = Channels.newChannel(System.in)){
            ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
            while(in.read(bb) != -1){
                if(!bb.hasRemaining()){
                    ByteBuffer temp = ByteBuffer.allocate(bb.capacity()*2);
                    bb.flip();
                    temp.put(bb);
                    bb = temp;
                }
            }
            bb.flip();
            return cs.decode(bb).toString();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length!=1){
            usage();
            return;
        }
        Charset cs=Charset.forName(args[0]);
        System.out.print(stringFromStandardInput(cs));


    }


}
