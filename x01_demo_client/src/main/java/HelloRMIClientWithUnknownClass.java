import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;

public class HelloRMIClientWithUnknownClass {
    public static void main(String[] argv) throws Exception {
        String addr = "127.0.0.1";
        int port = 1099;
        String name = "HelloRMIInterfaceTest001";
        String content = "Hello World";
        //https://docs.oracle.com/javase/8/docs/api/java/rmi/registry/LocateRegistry.html

        /*
         * 服务端返回一个客户端ClassPath中不存在的类的对象。
         */
        Properties env4 = new Properties();
        System.setProperty("java.security.policy", "jndi.policy");
        System.setProperty("java.rmi.server.useCodebaseOnly", "false");
        System.setProperty("java.rmi.server.codebase", "http://127.0.0.1:8086/");
        env4.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.rmi.registry.RegistryContextFactory");
        env4.put(Context.PROVIDER_URL, "rmi://" + addr + ":" + port);
        Context ctx4 = new InitialContext(env4);
        HelloRMIInterface hello4 = (HelloRMIInterface) ctx4.lookup(name);
        Object resp4 = hello4.EchoObject(content);
        System.out.println("use Properties:" + resp4);

    }
}