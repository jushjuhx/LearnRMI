
/*
 * javac -encoding GBK -g SomeInterface.java
 */
import java.rmi.*;

public interface SomeInterface extends Remote
{
    /*
     * Echo形参是Object，不是Primitive类型
     */
    public String Echo ( Message sth ) throws RemoteException;
}