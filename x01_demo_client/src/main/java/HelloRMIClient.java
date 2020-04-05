import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class HelloRMIClient {
    public static void main(String[] argv) throws Exception {
        String addr = "127.0.0.1";
        int port = 1099;
        String name = "HelloRMIInterfaceTest001";
        String content = "Hello World";
        //https://docs.oracle.com/javase/8/docs/api/java/rmi/registry/LocateRegistry.html
        Registry r = LocateRegistry.getRegistry(addr, port);
        HelloRMIInterface hello = (HelloRMIInterface) r.lookup(name);
        String resp = hello.Echo(content);
        System.out.println(resp);
    }
}