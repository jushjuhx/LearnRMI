import javax.naming.Context;
import javax.naming.InitialContext;

/*
 * Dynamic是强调这里只有动态端口部分，周知端口部分被分离了
 */
public class SomeDynamicServer {
    public static void main(String[] argv) throws Exception {
        String name = "any";
        /*
         * 保持一般性，使用JNDI，用JVM参数传递env
         */
        Context ctx = new InitialContext();
        SomeInterface some = new SomeInterfaceImpl();
        ctx.rebind(name, some);
    }
}