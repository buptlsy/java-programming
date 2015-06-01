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
 * ��ͻ����������
 * ����1�� �ͻ���ͨ��Java NIO���ӵ�����ˣ�֧�ֶ�ͻ��˵����ӡ�
 * ����2���ͻ��˳�������ʱ���������ʾ�����ǳƣ�����ǳ��Ѿ�����ʹ�ã���ʾ�������룬����ǳ�Ψһ�����¼�ɹ���֮������Ϣ����Ҫ���չ涨��ʽ�����ǳƷ�����Ϣ��
 * ����3���ͻ��˵�¼�󣬷����Ѿ����úõĻ�ӭ��Ϣ�������������ͻ��ˣ�����֪ͨ�����ͻ��˸ÿͻ������ߡ�
 * ����4���������յ��ѵ�¼�ͻ����������ݣ�ת����������¼�ͻ��ˡ�
 * ����5����ĳһ�ͻ�������"/quit"ʱ����֪ͨ�����ͻ��˸ÿͻ��������ߡ�
 * ����6�����ͻ���60s��û�з����κ���Ϣ�Ļ���������������Ͽ����ӡ�
 * ����7�������˴�����Ĳ�ѯ���ܣ��������ʱ������Ϊ2Сʱ��
 * 
 */
public class Server {

    private Selector selector = null;
    static final int port = 9999;
    private Charset charset = Charset.forName("UTF-8");
    // ������¼�����������Լ��ǳ�
    private static HashSet<String> users = new HashSet<String>();
    
    private static String USER_EXIST = "system message: user exist, please change a name";
    // �൱���Զ���Э���ʽ����ͻ���Э�̺�
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
        // �������ķ�ʽ
        server.configureBlocking(false);
        // ע�ᵽѡ�����ϣ�����Ϊ����״̬
        server.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("Server is listening now...");
        new Timer().schedule(tt, 0, 60000); // ÿ��60sִ��һ��
        while (true) {
        	int readyChannels = selector.select();
            if (readyChannels == 0) continue; 
            Set selectedKeys = selector.selectedKeys();  // ����ͨ�����������֪������ͨ���ļ���
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
            // ������ģʽ
            sc.configureBlocking(false);
            // ע��ѡ������������Ϊ��ȡģʽ���յ�һ����������Ȼ����һ��SocketChannel����ע�ᵽselector�ϣ�֮��������ӵ����ݣ��������SocketChannel����
            sc.register(selector, SelectionKey.OP_READ);
            
            // ���˶�Ӧ��channel����Ϊ׼�����������ͻ�������
            sk.interestOps(SelectionKey.OP_ACCEPT);
            System.out.println("Server is listening from client :" + sc.getRemoteAddress());
            inactiveTime.put(sc.getRemoteAddress().toString(), new Date());
            sc.write(charset.encode("Please input your name."));
        }
        // �������Կͻ��˵����ݶ�ȡ����
        if (sk.isReadable()) {
            // ���ظ�SelectionKey��Ӧ�� Channel��������������Ҫ��ȡ
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
                // ���˶�Ӧ��channel����Ϊ׼����һ�ν�������
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
                // ע���û�
                if (arrayContent != null && arrayContent.length ==1) {
                    String name = arrayContent[0];
                    if(users.contains(name)) {
                        sc.write(charset.encode(USER_EXIST));
                    } else {
                        users.add(name);
                        int num = OnlineNum(selector);
                        String message = "welcome "+name+" to chat room! Online numbers:"+num;
                        sc.write(charset.encode(message));
                        message = name + "����";
                        BroadCast(selector, sc, message);
                    }
                } 
                // ������Ϣ
                else if (arrayContent != null && arrayContent.length >1) {
                    String name = arrayContent[0];
                    String message = content.substring(name.length()+USER_CONTENT_SPILIT.length());
                    if (!message.startsWith("/")) {
                    	message = name + " say " + message;
                    } else if ("/quit".equals(message)) {
                    	message = name + "����";
                    	sc.socket().close();
                    } else {
                    	// ������������ѯ����
                    	flag = false;
                    	message = message.substring(1);
                    	String[] strs = message.split(COMMAND_SPLIT);
                    	String searchContent = "";
                    	for (int i = 1; i < strs.length; i++) searchContent += strs[i];
                    	Type type = Type.fromCode(strs[0]);
                    	SearchResult sr = Session.getResult(type, searchContent);
                    	String str = "";
                    	if (sr == null) {
                    		String result = "22"; // ���õ������ӿڣ��õ����ݡ�������践�ص���������22
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
        // �㲥���ݵ����е�SocketChannel��
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