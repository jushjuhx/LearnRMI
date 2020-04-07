import javax.naming.Context;
import javax.naming.InitialContext;
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
        System.out.println("without scheme:" + resp);
        /*
         * HelloRMIInterface hello1 = (HelloRMIInterface) r.lookup("rmi://localhost:" + port + "/" + name);
         * String resp1 = hello1.Echo(content);
         * System.out.println("with scheme:" + resp1);
         * 这种方式是行不通的，lookup找不到
         */

        /*
         * 通过jndi的方式也能行得通
         */
        Context ctx = new InitialContext();
        HelloRMIInterface hello1 = (HelloRMIInterface) ctx.lookup("rmi://localhost:" + port + "/" + name);
        String resp1 = hello1.Echo(content);
        System.out.println("with scheme:" + resp1);
    }
}