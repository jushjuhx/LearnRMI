
/*
 * javac -encoding GBK -g SomeInterface.java
 */

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SomeInterface extends Remote {
    /*
     * Echo形参是Object，不是Primitive类型
     */
    public String Echo(Message sth) throws RemoteException;
}