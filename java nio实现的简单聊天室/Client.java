import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;


public class Client {

    private Selector selector = null;
    static final int port = 9999;
    private Charset charset = Charset.forName("UTF-8");
    private SocketChannel sc = null;
    private String name = "";
    private static String USER_EXIST = "system message: user exist, please change a name";
    private static String USER_CONTENT_SPILIT = "#@#";
    private volatile boolean canceled;
    
    public void init() throws IOException, InterruptedException {
        selector = Selector.open();
        sc = SocketChannel.open(new InetSocketAddress("127.0.0.1",port));
        sc.configureBlocking(false);
        sc.register(selector, SelectionKey.OP_READ);
        Thread thread = new Thread(new ClientThread());
        thread.start();
        Scanner scan = new Scanner(System.in);
        while (scan.hasNextLine() && !canceled) {
            String line = scan.nextLine();
            if ("".equals(line)) continue; // 不允许发空消息
            if ("".equals(name)) {
                name = line;
                line = name + USER_CONTENT_SPILIT;
            } else {
                line = name + USER_CONTENT_SPILIT + line;
            }
            sc.write(charset.encode(line));
            if (line.toString().contains("/quit")) {
            	canceled = true;
            	break;
            }
        }
        if (canceled) {
        	System.out.println("connection has been canceled.");
        }
    }
    
    private class ClientThread implements Runnable {
        public void run() {
            try {
                while(!canceled) {
                    int readyChannels = selector.select();
                    if(readyChannels == 0) continue; 
                    Set selectedKeys = selector.selectedKeys();  //可以通过这个方法，知道可用通道的集合
                    Iterator keyIterator = selectedKeys.iterator();
                    while(keyIterator.hasNext()) {
                         SelectionKey sk = (SelectionKey) keyIterator.next();
                         keyIterator.remove();
                         dealWithSelectionKey(sk);
                    }
                }
            }
            catch (IOException io) {
            	throw new RuntimeException(io);
            }
        }

        private void dealWithSelectionKey(SelectionKey sk) throws IOException {
            if (sk.isReadable()) {
                // 使用 NIO 读取 Channel中的数据，这个和全局变量sc是一样的，因为只注册了一个SocketChannel
                // sc既能写也能读，这边是读
                SocketChannel sc = (SocketChannel)sk.channel();
                
                ByteBuffer buff = ByteBuffer.allocate(1024);
                String content = "";
                while (sc.read(buff) > 0) {
                    buff.flip();
                    content += charset.decode(buff);
                }
                if ("canceled".equals(content)) {
                	canceled = true;
                	return;
                }
                // 若系统发送通知名字已经存在，则需要换个昵称
                if (USER_EXIST.equals(content)) {
                    name = "";
                }
                System.out.println(content);
                sk.interestOps(SelectionKey.OP_READ);
            }
        }
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        new Client().init();
    }
}