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
         * 通过jndi的方式直接访问地址执行
         */
        Context ctx1 = new InitialContext();
        HelloRMIInterface hello1 = (HelloRMIInterface) ctx1.lookup("rmi://" + addr + ":" + port + "/" + name);
        String resp1 = hello1.Echo(content);
        System.out.println("with scheme:" + resp1);

        /*
         * 通过JNDI的方式，先初始化上下文，然后查找name
         */
        Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.rmi.registry.RegistryContextFactory");
        env.put(Context.PROVIDER_URL, "rmi://" + addr + ":" + port);
        Context ctx2 = new InitialContext(env);
        HelloRMIInterface hello2 = (HelloRMIInterface) ctx2.lookup(name);
        String resp2 = hello2.Echo(content);
        System.out.println("use Properties:" + resp2);
    }
}