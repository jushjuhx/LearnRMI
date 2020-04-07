import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * 注意HelloRMIInterfaceImpl继承自UnicastRemoteObject，而UnicastRemoteObject有个构造函数，可以指定 RMIServerSocketFactory，
 * 所以可以实现一个类，继承RMIServerSocketFactory并重写的createServerSocket方法，实现监听指定端口的功能
 */
public class HelloRMIInterfaceImpl extends UnicastRemoteObject implements HelloRMIInterface {
    private static final long serialVersionUID = 0x5120131473637a00L;

    protected HelloRMIInterfaceImpl() throws RemoteException {
        super();
    }

    @Override
    public String EchoString(String content) throws RemoteException {
        /*
         * 故意加一对[]，将来抓包时便于识别请求、响应
         */
        System.out.println("收到" + content);
        return ("[" + content + "]");
    }

    @Override
    public Object EchoObject(String content) throws RemoteException {
        /*
         * 故意加一对[]，将来抓包时便于识别请求、响应
         */
        System.out.println("收到" + content);
        return (new Message(content));
    }
}