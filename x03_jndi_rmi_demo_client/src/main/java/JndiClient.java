import javax.naming.Context;
import javax.naming.InitialContext;

public class JndiClient {
    public static void main(String[] args) throws Exception {
        Context ctx = new InitialContext();
        Object x = ctx.lookup("rmi://127.0.0.1:1099/anything");
        System.out.println(x);
    }
}
