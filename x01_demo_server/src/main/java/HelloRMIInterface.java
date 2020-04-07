import java.rmi.Remote;
import java.rmi.RemoteException;

/*
 * The Interface must always be public and extend Remote.
 *
 * All methods described in the Remote interface must list RemoteException
 * in their throws clause.
 */
public interface HelloRMIInterface extends Remote {
    public String EchoString(String sth) throws RemoteException;

    public Object EchoObject(String sth) throws RemoteException;
}