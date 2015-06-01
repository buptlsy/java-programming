import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
/**
 * 多客户端聊天程序
 * 功能1： 客户端通过Java NIO连接到服务端，支持多客户端的连接。
 * 功能2：客户端初次连接时，服务端提示输入昵称，如果昵称已经有人使用，提示重新输入，如果昵称唯一，则登录成功，之后发送消息都需要按照规定格式带着昵称发送消息。
 * 功能3：客户端登录后，发送已经设置好的欢迎信息和在线人数给客户端，并且通知其他客户端该客户端上线。
 * 功能4：服务器收到已登录客户端输入内容，转发至其他登录客户端。
 * 功能5：当某一客户端输入"/quit"时，会通知其他客户端该客户端已下线。
 * 功能6：当客户端60s内没有发送任何消息的话，则服务器主动断开连接。
 * 功能7：增加了带缓存的查询功能，缓存过期时间设置为2小时。
 * 
 */
public class Server {

    private Selector selector = null;
    static final int port = 9999;
    private Charset charset = Charset.forName("UTF-8");
    // 用来记录在线人数，以及昵称
    private static HashSet<String> users = new HashSet<String>();
    
    private static String USER_EXIST = "system message: user exist, please change a name";
    // 相当于自定义协议格式，与客户端协商好
    private static String USER_CONTENT_SPILIT = "#@#";
    private static String COMMAND_SPLIT = " ";
    private Map<String, Date> inactiveTime = new HashMap<String, Date>();
    
    TimerTask tt = new TimerTask() {

		@Override
		public void run() {
			SocketChannel sc = null;
			try {
				for (SelectionKey key : selector.keys()) {
		            Channel targetchannel = key.channel();
		            if (targetchannel instanceof SocketChannel) {
		                sc = (SocketChannel)targetchannel;
		                Date preTime = inactiveTime.get(sc.getRemoteAddress().toString());
		                System.out.println(sc.getRemoteAddress() + ", time:" + preTime);
		                long difference = (new Date().getTime() - preTime.getTime())/1000;
		                if (difference > 6) {
		                	sc.write(charset.encode("canceled"));
		                	sc.close();
		                }
		            }
		        }
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
    	
    };
    
    public void init() throws IOException {
        selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(port));
        // 非阻塞的方式
        server.configureBlocking(false);
        // 注册到选择器上，设置为监听状态
        server.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("Server is listening now...");
        new Timer().schedule(tt, 0, 60000); // 每个60s执行一次
        while (true) {
        	int readyChannels = selector.select();
            if (readyChannels == 0) continue; 
            Set selectedKeys = selector.selectedKeys();  // 可以通过这个方法，知道可用通道的集合
            Iterator keyIterator = selectedKeys.iterator();
            while (keyIterator.hasNext()) {
                 SelectionKey sk = (SelectionKey) keyIterator.next();
                 keyIterator.remove();
                 dealWithSelectionKey(server,sk);
            }
        }
    }
    
    public void dealWithSelectionKey(ServerSocketChannel server, SelectionKey sk) throws IOException {
        if (sk.isAcceptable()) {
            SocketChannel sc = server.accept();
            // 非阻塞模式
            sc.configureBlocking(false);
            // 注册选择器，并设置为读取模式，收到一个连接请求，然后起一个SocketChannel，并注册到selector上，之后这个连接的数据，就由这个SocketChannel处理
            sc.register(selector, SelectionKey.OP_READ);
            
            // 将此对应的channel设置为准备接受其他客户端请求
            sk.interestOps(SelectionKey.OP_ACCEPT);
            System.out.println("Server is listening from client :" + sc.getRemoteAddress());
            inactiveTime.put(sc.getRemoteAddress().toString(), new Date());
            sc.write(charset.encode("Please input your name."));
        }
        // 处理来自客户端的数据读取请求
        if (sk.isReadable()) {
            // 返回该SelectionKey对应的 Channel，其中有数据需要读取
            SocketChannel sc = (SocketChannel)sk.channel(); 
            ByteBuffer buff = ByteBuffer.allocate(1024);
            StringBuilder content = new StringBuilder();
            try {
                while(sc.read(buff) > 0) {
                    buff.flip();
                    content.append(charset.decode(buff));
                }
                inactiveTime.put(sc.getRemoteAddress().toString(), new Date());
                System.out.println("Server is listening from client " + sc.getRemoteAddress() + " data rev is: " + content);
                // 将此对应的channel设置为准备下一次接受数据
                sk.interestOps(SelectionKey.OP_READ);
            }
            catch (IOException io) {
                sk.cancel();
                if(sk.channel() != null) {
                    sk.channel().close();
                }
            }
            boolean flag = true;
            if (content.length() > 0) {
                String[] arrayContent = content.toString().split(USER_CONTENT_SPILIT);
                // 注册用户
                if (arrayContent != null && arrayContent.length ==1) {
                    String name = arrayContent[0];
                    if(users.contains(name)) {
                        sc.write(charset.encode(USER_EXIST));
                    } else {
                        users.add(name);
                        int num = OnlineNum(selector);
                        String message = "welcome "+name+" to chat room! Online numbers:"+num;
                        sc.write(charset.encode(message));
                        message = name + "上线";
                        BroadCast(selector, sc, message);
                    }
                } 
                // 发送消息
                else if (arrayContent != null && arrayContent.length >1) {
                    String name = arrayContent[0];
                    String message = content.substring(name.length()+USER_CONTENT_SPILIT.length());
                    if (!message.startsWith("/")) {
                    	message = name + " say " + message;
                    } else if ("/quit".equals(message)) {
                    	message = name + "下线";
                    	sc.socket().close();
                    } else {
                    	// 其他命令。比如查询服务
                    	flag = false;
                    	message = message.substring(1);
                    	String[] strs = message.split(COMMAND_SPLIT);
                    	String searchContent = "";
                    	for (int i = 1; i < strs.length; i++) searchContent += strs[i];
                    	Type type = Type.fromCode(strs[0]);
                    	SearchResult sr = Session.getResult(type, searchContent);
                    	String str = "";
                    	if (sr == null) {
                    		String result = "22"; // 调用第三方接口，得到数据。这里假设返回的数据总是22
                    		sr = new SearchResult(result);
                    		Session.setResult(type, searchContent, sr);
                    	} else {
                    		str += "from session:";
                    	}
                    	str += sr.getResult();
                    	sc.write(charset.encode(str));
                    }
                    if(users.contains(name) && flag) {
                        BroadCast(selector, sc, message);
                    }
                }
            }
            
        }
    }
    
    public static int OnlineNum(Selector selector) {
        int res = 0;
        for (SelectionKey key : selector.keys()) {
            Channel targetchannel = key.channel();
            
            if (targetchannel instanceof SocketChannel) {
                res++;
            }
        }
        return res;
    }
    
    public void BroadCast(Selector selector, SocketChannel except, String content) throws IOException {
        // 广播数据到所有的SocketChannel中
        for (SelectionKey key : selector.keys()) {
            Channel targetchannel = key.channel();
            if (targetchannel instanceof SocketChannel && targetchannel != except) {
                SocketChannel dest = (SocketChannel)targetchannel;
                dest.write(charset.encode(content));
            }
        }
    }
    
    public static void main(String[] args) throws IOException {
        new Server().init();
    }
}