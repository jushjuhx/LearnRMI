import javax.naming.Context;
import javax.naming.InitialContext;

public class JndiClient {
    /*
下载远端Class时的调用栈信息如下
sun.net.www.protocol.http.Handler.openConnection(Handler.java:62)
sun.net.www.protocol.http.Handler.openConnection(Handler.java:57)
java.net.URL.openConnection(URL.java:971)
sun.net.www.protocol.jar.JarURLConnection.<init>(JarURLConnection.java:84)
sun.net.www.protocol.jar.Handler.openConnection(Handler.java:41)
java.net.URL.openConnection(URL.java:971)
sun.misc.URLClassPath$JarLoader.getJarFile(URLClassPath.java:708)
sun.misc.URLClassPath$JarLoader.access$600(URLClassPath.java:587)
sun.misc.URLClassPath$JarLoader$1.run(URLClassPath.java:667)
sun.misc.URLClassPath$JarLoader$1.run(URLClassPath.java:660)
java.security.AccessController.doPrivileged(Native Method)
sun.misc.URLClassPath$JarLoader.ensureOpen(URLClassPath.java:659)
sun.misc.URLClassPath$JarLoader.<init>(URLClassPath.java:610)
sun.misc.URLClassPath$3.run(URLClassPath.java:362)
sun.misc.URLClassPath$3.run(URLClassPath.java:352)
java.security.AccessController.doPrivileged(Native Method)
sun.misc.URLClassPath.getLoader(URLClassPath.java:351)
sun.misc.URLClassPath.getLoader(URLClassPath.java:328)
sun.misc.URLClassPath.getResource(URLClassPath.java:194)
java.net.URLClassLoader$1.run(URLClassLoader.java:358)
java.net.URLClassLoader$1.run(URLClassLoader.java:355)
java.security.AccessController.doPrivileged(Native Method)
java.net.URLClassLoader.findClass(URLClassLoader.java:354)
java.lang.ClassLoader.loadClass(ClassLoader.java:423)
java.net.FactoryURLClassLoader.loadClass(URLClassLoader.java:789)
java.lang.ClassLoader.loadClass(ClassLoader.java:356)
java.lang.Class.forName0(Native Method)
java.lang.Class.forName(Class.java:266)
com.sun.naming.internal.VersionHelper12.loadClass(VersionHelper12.java:85)
javax.naming.spi.NamingManager.getObjectFactoryFromReference(NamingManager.java:158)
javax.naming.spi.NamingManager.getObjectInstance(NamingManager.java:319)
com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:456)
com.sun.jndi.rmi.registry.RegistryContext.lookup(RegistryContext.java:120)
com.sun.jndi.toolkit.url.GenericURLContext.lookup(GenericURLContext.java:203)
javax.naming.InitialContext.lookup(InitialContext.java:411)
JndiClient.main(JndiClient.java:28)
     */
    public static void main(String[] args) throws Exception {
        Context ctx = new InitialContext();
        Object x = ctx.lookup("rmi://127.0.0.1:1099/anything");
        System.out.println(x);

    }
}
