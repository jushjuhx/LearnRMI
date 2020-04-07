import javax.naming.Context;
import javax.naming.InitialContext;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Properties;

public class HelloRMIClient {
    public static void main(String[] argv) throws Exception {
        String addr = "127.0.0.1";
        int port = 1099;
        String name = "HelloRMIInterfaceTest001";
        String content = "Hello World";
        //https://docs.oracle.com/javase/8/docs/api/java/rmi/registry/LocateRegistry.html

        /*
         * 使用原生RMI的方式执行
         */
        Registry registry1 = LocateRegistry.getRegistry(addr, port);
        HelloRMIInterface hello1 = (HelloRMIInterface) registry1.lookup(name);
        String resp1 = hello1.EchoString(content);
        System.out.println("without scheme:" + resp1);
        /*
         * HelloRMIInterface hello1 = (HelloRMIInterface) r.lookup("rmi://localhost:" + port + "/" + name);
         * String resp1 = hello1.Echo(content);
         * System.out.println("with scheme:" + resp1);
         * 这种方式是行不通的，lookup找不到
         */

        /*
         * 通过jndi的方式直接访问地址执行
         */
        Context ctx2 = new InitialContext();
        HelloRMIInterface hello2 = (HelloRMIInterface) ctx2.lookup("rmi://" + addr + ":" + port + "/" + name);
        String resp2 = hello2.EchoString(content);
        System.out.println("with scheme:" + resp2);

        /*
         * 通过JNDI的方式，先初始化上下文，然后查找name
         */
        Properties env3 = new Properties();
        env3.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.rmi.registry.RegistryContextFactory");
        env3.put(Context.PROVIDER_URL, "rmi://" + addr + ":" + port);
        Context ctx3 = new InitialContext(env3);
        HelloRMIInterface hello3 = (HelloRMIInterface) ctx3.lookup(name);
        String resp3 = hello3.EchoString(content);
        System.out.println("use Properties:" + resp3);

    }
}