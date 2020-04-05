import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class SomeInterfaceImpl extends UnicastRemoteObject implements SomeInterface {
    /*
     * 跟Message的不同
     */
    private static final long serialVersionUID = 0x5120131473637a01L;

    protected SomeInterfaceImpl() throws RemoteException {
        super();
    }

    @Override
    public String Echo(Message sth) throws RemoteException {
        System.out.println("receive " + sth);
        return ("[" + sth.getMsg() + "]");
    }
}