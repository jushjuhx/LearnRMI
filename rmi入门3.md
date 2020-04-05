标题: Java RMI入门(2)

创建: 2020-03-08 18:10
更新: 2020-04-01 16:50
链接: http://scz.617.cn:8/network/202003081810.txt

--------------------------------------------------------------------------

目录:

    ☆ 前言
    ☆ Java RMI
       13) "java.rmi.server.codebase"的误会
           13.1) HelloRMIInterface.java
           13.2) HelloRMIInterfaceImpl.java
           13.3) HelloRMIDynamicServer9.java
           13.4) HelloRMIClient9.java
           13.5) jndi.policy
           13.6) 编译
           13.7) 测试
               13.7.1) Client/Server位于同一主机同一目录(成功)
               13.7.2) Client/Server位于同一主机不同目录(失败)
               13.7.3) 调试分析"13.7.2"
       14) Dynamic code downloading using Java RMI
           14.1) ParamInterface.java
           14.2) MethodInterface.java
           14.3) MethodInterfaceImpl.java
           14.4) MethodInterfaceServer.java
           14.5) ParamInterfaceStringImpl.java
           14.6) MethodInterfaceClient.java
           14.7) jndi.policy
           14.8) 编译
           14.9) 测试
               14.9.1) Client/Server位于同一主机同一目录
               14.9.2) Client/Server/rmiregistry位于同一主机不同目录
          14.10) 相关stackoverflow问答点评
          14.11) server.policy
          14.12) client.policy
          14.13) MethodInterfaceClient1.java (more about SecurityManager)
          14.14) 什么情况下需要使用远程codebase
    ☆ 后记
    ☆ 参考资源

--------------------------------------------------------------------------

☆ 前言

参看

《Java RMI入门》
http://scz.617.cn:8/network/202002221000.txt

《Java RMI入门(3)》
http://scz.617.cn:8/network/202003121717.txt

《Java RMI入门(4)》
http://scz.617.cn:8/network/202003191728.txt

《Java RMI入门(5)》
http://scz.617.cn:8/network/202003241127.txt

《Java RMI入门(6)》
http://scz.617.cn:8/network/202004011650.txt

在学习Java RMI相关的远程动态加载时，我被官方文档带到沟里。13小节就是掉进沟
里的下场。产生了很多疑问，比如，在客户端、服务端到底哪些类必须出现在本地
CLASSPATH中，哪些类可以通过codebase机制远程动态加载；远程codebase与本地
codebase啥关系？不想只在CVE中看到某种特向化的远程动态加载，想知道这种机制
在现实世界中本来打算如何NB一把。虽然它最终被时代抛弃了，但我想知道它的设计
者们最初怎么思考的。

☆ Java RMI

13) "java.rmi.server.codebase"的误会

13.1) HelloRMIInterface.java

```java
/*
 * javac -encoding GBK -g HelloRMIInterface.java
 */
import java.rmi.*;

/*
 * The Interface must always be public and extend Remote.
 *
 * All methods described in the Remote interface must list RemoteException
 * in their throws clause.
 */
public interface HelloRMIInterface extends Remote
{
    public String Echo ( String sth ) throws RemoteException;
}
```

13.2) HelloRMIInterfaceImpl.java

```java
/*
 * javac -encoding GBK -g HelloRMIInterfaceImpl.java
 */
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class HelloRMIInterfaceImpl extends UnicastRemoteObject implements HelloRMIInterface
{
    private static final long   serialVersionUID    = 0x5120131473637a00L;

    protected HelloRMIInterfaceImpl () throws RemoteException
    {
        super();
    }

    @Override
    public String Echo ( String sth ) throws RemoteException
    {
        /*
         * 故意加一对[]，将来抓包时便于识别请求、响应
         */
        return( "[" + sth + "]" );
    }
}
```

13.3) HelloRMIDynamicServer9.java

```java
/*
 * javac -encoding GBK -g HelloRMIDynamicServer9.java
 */
import javax.naming.*;

public class HelloRMIDynamicServer9
{
    public static void main ( String[] argv ) throws Exception
    {
        if ( System.getSecurityManager() == null )
        {
            /*
             * 如果设置了SecurityManager，必须指定"java.security.policy"
             *
             * RMISecurityManager()已经废弃，不要使用
             */
            System.setSecurityManager( new SecurityManager() );
        }
        String              name        = argv[0];
        Context             ctx         = new InitialContext();
        /*
         * 为了简化测试，没有使用HelloRMIInterfaceImpl3
         */
        HelloRMIInterface   hello       = new HelloRMIInterfaceImpl();
        ctx.rebind( name, hello );
    }
}
```
13.4) HelloRMIClient9.java

```java
/*
 * javac -encoding GBK -g HelloRMIClient9.java
 */
import javax.naming.*;

public class HelloRMIClient9
{
    public static void main ( String[] argv ) throws Exception
    {
        if ( System.getSecurityManager() == null )
        {
            System.setSecurityManager( new SecurityManager() );
        }
        String              name    = argv[0];
        String              sth     = argv[1];
        Context             ctx     = new InitialContext();
        /*
         * 原来是
         *
         * HelloRMIInterface hello = ( HelloRMIInterface )ctx.lookup( name );
         *
         * 为了更好的暴露问题，拆分成两行代码
         */
        Object              obj     = ctx.lookup( name );
        HelloRMIInterface   hello   = ( HelloRMIInterface )obj;
        String              resp    = hello.Echo( sth );
        System.out.println( resp );
    }
}
```

13.5) jndi.policy

--------------------------------------------------------------------------
grant
{
permission java.security.AllPermission;
};
--------------------------------------------------------------------------

13.6) 编译

javac -encoding GBK -g HelloRMIInterface.java
javac -encoding GBK -g HelloRMIInterfaceImpl.java
javac -encoding GBK -g HelloRMIDynamicServer9.java
javac -encoding GBK -g HelloRMIClient9.java

13.7) 测试

13.7.1) Client/Server位于同一主机同一目录(成功)

启动两个服务端:

rmiregistry 1099

java \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
HelloRMIDynamicServer9 anything

测试客户端:

java \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
HelloRMIClient9 anything "msg from client"

测试正常，客户端输出:

[msg from client]

13.7.2) Client/Server位于同一主机不同目录(失败)

这实际上相当于Client/Server位于不同主机。

启动三个服务端:

python3 -m http.server -b 192.168.65.23 8080

rmiregistry 1099

java \
-Djava.rmi.server.useCodebaseOnly=false \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/ \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
HelloRMIDynamicServer9 anything

在另一目录测试客户端，假设该目录只有:

HelloRMIClient9.class
jndi.policy

没有:

HelloRMIInterface.class
HelloRMIInterfaceImpl.class

测试客户端:

java \
-Djava.rmi.server.useCodebaseOnly=false \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/ \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
HelloRMIClient9 anything "msg from client"

报错:

Exception in thread "main" java.lang.NoClassDefFoundError: HelloRMIInterface
        at HelloRMIClient9.main(HelloRMIClient9.java:25)
Caused by: java.lang.ClassNotFoundException: HelloRMIInterface
        at java.net.URLClassLoader.findClass(URLClassLoader.java:382)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:418)
        at sun.misc.Launcher$AppClassLoader.loadClass(Launcher.java:352)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:351)
        ... 1 more

ctx.lookup()正常返回，抛异常的是强制类型转换"(HelloRMIInterface)obj"。

但HTTP Server已经返回:

"GET /HelloRMIInterface.class HTTP/1.1" 200

13.7.3) 调试分析"13.7.2"

以调试方式启动客户端:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.rmi.server.useCodebaseOnly=false \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/ \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
HelloRMIClient9 anything "msg from client"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

或者用Eclipse跟踪。参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/java/net/URLClassLoader.java

```java
/**
 * Finds and loads the class with the specified name from the URL search
 * path. Any URLs referring to JAR files are loaded and opened as needed
 * until the class is found.
 *
 * @param name the name of the class
 * @return the resulting class
 * @exception ClassNotFoundException if the class could not be found,
 *            or if the loader is closed.
 * @exception NullPointerException if {@code name} is {@code null}.
 */
protected Class<?> findClass(final String name)
    throws ClassNotFoundException
{
    final Class<?> result;
    try {
        /*
         * 362行
         */
        result = AccessController.doPrivileged(
            new PrivilegedExceptionAction<Class<?>>() {
                public Class<?> run() throws ClassNotFoundException {
                    String path = name.replace('.', '/').concat(".class");
                    /*
                     * 366行，这条语句会触发:
                     *
                     * "GET /HelloRMIInterface.class HTTP/1.1"
                     *
                     * 几个有效条件断点:
                     *
                     * getURLs().length==1 && getURLs()[0].toString().equals("http://192.168.65.23:8080/")
                     * path.equals("HelloRMIInterface.class")
                     *
                     * 必须写正式的Java代码，不能直接访问私有成员。下面是
                     * 非法条件语句:
                     *
                     * ucp.path.size()==1 && ucp.path.get(0).toString().equals("http://192.168.65.23:8080/")
                     */
                    Resource res = ucp.getResource(path, false);
                    if (res != null) {
                        try {
                            return defineClass(name, res);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    } else {
                        return null;
                    }
                }
            }, acc);
    } catch (java.security.PrivilegedActionException pae) {
        throw (ClassNotFoundException) pae.getException();
    }
    if (result == null) {
        throw new ClassNotFoundException(name);
    }
    return result;
}
```

在Eclipse中可以对上面的362、366行设断，但用jdb时出了幺蛾子:

main[1] stop at java.net.URLClassLoader:362
Set breakpoint java.net.URLClassLoader:362

main[1] stop at java.net.URLClassLoader:366
Unable to set breakpoint java.net.URLClassLoader:366 : No code at line 366 in java.net.URLClassLoader

jdb中362行设断成功，366行断点设置失败。jdb也不支持条件断点。

参看前面366行附近的注释，用Eclipse的条件断点断下来，查看调用栈回溯:

getURLs().length==1 && getURLs()[0].toString().equals("http://192.168.65.23:8080/")

Thread [main] (Suspended (breakpoint at line 366 in java.net.URLClassLoader$1))
	java.net.URLClassLoader$1.run() line: 366
	java.net.URLClassLoader$1.run() line: 363
	java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
	sun.rmi.server.LoaderHandler$Loader(java.net.URLClassLoader).findClass(java.lang.String) line: 362
	sun.rmi.server.LoaderHandler$Loader(java.lang.ClassLoader).loadClass(java.lang.String, boolean) line: 418
	sun.rmi.server.LoaderHandler$Loader.loadClass(java.lang.String, boolean) line: 1207
	sun.rmi.server.LoaderHandler$Loader(java.lang.ClassLoader).loadClass(java.lang.String) line: 351
	java.lang.Class<T>.forName0(java.lang.String, boolean, java.lang.ClassLoader, java.lang.Class<?>) line: not available [native method]
	java.lang.Class<T>.forName(java.lang.String, boolean, java.lang.ClassLoader) line: 348
	sun.rmi.server.LoaderHandler.loadClassForName(java.lang.String, boolean, java.lang.ClassLoader) line: 1221
	sun.rmi.server.LoaderHandler.loadProxyInterfaces(java.lang.String[], java.lang.ClassLoader, java.lang.Class<?>[], boolean[]) line: 731
	sun.rmi.server.LoaderHandler.loadProxyClass(java.lang.String[], java.lang.ClassLoader, java.lang.ClassLoader, boolean) line: 674
	sun.rmi.server.LoaderHandler.loadProxyClass(java.lang.String, java.lang.String[], java.lang.ClassLoader) line: 611
	java.rmi.server.RMIClassLoader$2.loadProxyClass(java.lang.String, java.lang.String[], java.lang.ClassLoader) line: 646
	java.rmi.server.RMIClassLoader.loadProxyClass(java.lang.String, java.lang.String[], java.lang.ClassLoader) line: 311
	sun.rmi.transport.ConnectionInputStream(sun.rmi.server.MarshalInputStream).resolveProxyClass(java.lang.String[]) line: 265
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readProxyDesc(boolean) line: 1799
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readClassDesc(boolean) line: 1747
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readOrdinaryObject(boolean) line: 2041
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject0(boolean) line: 1572
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject() line: 430
	sun.rmi.registry.RegistryImpl_Stub.lookup(java.lang.String) line: 127
	com.sun.jndi.rmi.registry.RegistryContext.lookup(javax.naming.Name) line: 132
	com.sun.jndi.rmi.registry.RegistryContext.lookup(java.lang.String) line: 142
	javax.naming.InitialContext.lookup(java.lang.String) line: 417
	HelloRMIClient9.main(java.lang.String[]) line: 24

Resource res = ucp.getResource(path, false);

366行的这条语句会触发:

"GET /HelloRMIInterface.class HTTP/1.1"

单步跟进去，直至最终的socket操作。参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/net/www/protocol/http/HttpURLConnection.java
http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/java/net/SocketOutputStream.java

```java
/**
 * Writes to the socket with appropriate locking of the
 * FileDescriptor.
 * @param b the data to be written
 * @param off the start offset in the data
 * @param len the number of bytes that are written
 * @exception IOException If an I/O error has occurred.
 */
private void socketWrite(byte b[], int off, int len) throws IOException {


    if (len <= 0 || off < 0 || len > b.length - off) {
        if (len == 0) {
            return;
        }
        throw new ArrayIndexOutOfBoundsException("len == " + len
                + " off == " + off + " buffer length == " + b.length);
    }

    FileDescriptor fd = impl.acquireFD();
    try {
        /*
         * 111行，此时b中是TCP数据区内容，可以下条件断点:
         *
         * (new String(b)).startsWith("GET /HelloRMIInterface.class")
         */
        socketWrite0(fd, b, off, len);
    } catch (SocketException se) {
        if (se instanceof sun.net.ConnectionResetException) {
            impl.setConnectionResetPending();
            se = new SocketException("Connection reset");
        }
        if (impl.isClosedOrPending()) {
            throw new SocketException("Socket closed");
        } else {
            throw se;
        }
    } finally {
        impl.releaseFD();
    }
}

/**
 * Writes to the socket.
 * @param fd the FileDescriptor
 * @param b the data to be written
 * @param off the start offset in the data
 * @param len the number of bytes that are written
 * @exception IOException If an I/O error has occurred.
 */
private native void socketWrite0(FileDescriptor fd, byte[] b, int off,
                                 int len) throws IOException;
```
参看前面111行附近的注释，用Eclipse的条件断点断下来，查看调用栈回溯:

(new String(b)).startsWith("GET /HelloRMIInterface.class")

Thread [main] (Suspended (breakpoint at line 111 in java.net.SocketOutputStream))
	java.net.SocketOutputStream.socketWrite(byte[], int, int) line: 111
	java.net.SocketOutputStream.write(byte[], int, int) line: 155
	java.io.BufferedOutputStream.flushBuffer() line: 82
	java.io.BufferedOutputStream.flush() line: 140
	java.io.PrintStream.flush() line: 338
	sun.net.www.MessageHeader.print(java.io.PrintStream) line: 301
	sun.net.www.http.HttpClient.writeRequests(sun.net.www.MessageHeader, sun.net.www.http.PosterOutputStream) line: 644
	sun.net.www.http.HttpClient.writeRequests(sun.net.www.MessageHeader, sun.net.www.http.PosterOutputStream, boolean) line: 655
	sun.net.www.protocol.http.HttpURLConnection.writeRequests() line: 694
	sun.net.www.protocol.http.HttpURLConnection.getInputStream0() line: 1591
	sun.net.www.protocol.http.HttpURLConnection.access$200(sun.net.www.protocol.http.HttpURLConnection) line: 92
	sun.net.www.protocol.http.HttpURLConnection$9.run() line: 1490
	sun.net.www.protocol.http.HttpURLConnection$9.run() line: 1488
	java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
	java.security.AccessController.doPrivilegedWithCombiner(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext, java.security.Permission...) line: 784
	sun.net.www.protocol.http.HttpURLConnection.getInputStream() line: 1487
	sun.misc.URLClassPath$Loader.getResource(java.lang.String, boolean) line: 747
	sun.misc.URLClassPath.getResource(java.lang.String, boolean) line: 249
	java.net.URLClassLoader$1.run() line: 366
	java.net.URLClassLoader$1.run() line: 363
	java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
	sun.rmi.server.LoaderHandler$Loader(java.net.URLClassLoader).findClass(java.lang.String) line: 362
	sun.rmi.server.LoaderHandler$Loader(java.lang.ClassLoader).loadClass(java.lang.String, boolean) line: 418
	sun.rmi.server.LoaderHandler$Loader.loadClass(java.lang.String, boolean) line: 1207
	sun.rmi.server.LoaderHandler$Loader(java.lang.ClassLoader).loadClass(java.lang.String) line: 351
	java.lang.Class<T>.forName0(java.lang.String, boolean, java.lang.ClassLoader, java.lang.Class<?>) line: not available [native method]
	java.lang.Class<T>.forName(java.lang.String, boolean, java.lang.ClassLoader) line: 348
	sun.rmi.server.LoaderHandler.loadClassForName(java.lang.String, boolean, java.lang.ClassLoader) line: 1221
	sun.rmi.server.LoaderHandler.loadProxyInterfaces(java.lang.String[], java.lang.ClassLoader, java.lang.Class<?>[], boolean[]) line: 731
	sun.rmi.server.LoaderHandler.loadProxyClass(java.lang.String[], java.lang.ClassLoader, java.lang.ClassLoader, boolean) line: 674
	sun.rmi.server.LoaderHandler.loadProxyClass(java.lang.String, java.lang.String[], java.lang.ClassLoader) line: 611
	java.rmi.server.RMIClassLoader$2.loadProxyClass(java.lang.String, java.lang.String[], java.lang.ClassLoader) line: 646
	java.rmi.server.RMIClassLoader.loadProxyClass(java.lang.String, java.lang.String[], java.lang.ClassLoader) line: 311
	sun.rmi.transport.ConnectionInputStream(sun.rmi.server.MarshalInputStream).resolveProxyClass(java.lang.String[]) line: 265
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readProxyDesc(boolean) line: 1799
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readClassDesc(boolean) line: 1747
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readOrdinaryObject(boolean) line: 2041
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject0(boolean) line: 1572
	sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject() line: 430
	sun.rmi.registry.RegistryImpl_Stub.lookup(java.lang.String) line: 127
	com.sun.jndi.rmi.registry.RegistryContext.lookup(javax.naming.Name) line: 132
	com.sun.jndi.rmi.registry.RegistryContext.lookup(java.lang.String) line: 142
	javax.naming.InitialContext.lookup(java.lang.String) line: 417
	HelloRMIClient9.main(java.lang.String[]) line: 24

在调用栈中选中这行:

sun.rmi.server.LoaderHandler.loadProxyClass(java.lang.String, java.lang.String[], java.lang.ClassLoader) line: 611

在Variables窗口首次看到codebaseProperty，值为"http://192.168.65.23:8080/"。
与此同时，codebaseURLs值为"[http://192.168.65.23:8080/]"。

在调用栈中选中这行:

java.net.URLClassLoader$1.run() line: 366

在Expressions窗口检查getURLs()，值为"[http://192.168.65.23:8080/]"。这意味
着CLASSPATH中出现了远程URL，对于HelloRMIClient9.java第24行的ctx.lookup()，
这次可以找到HelloRMIInterface.class。

接着修改URLClassLoader.java的366行处的条件断点，将条件改成:

path.equals("HelloRMIInterface.class")

继续执行，命中时调用栈回溯如下:

Thread [main] (Suspended (breakpoint at line 366 in java.net.URLClassLoader$1))
    java.net.URLClassLoader$1.run() line: 366
    java.net.URLClassLoader$1.run() line: 363
    java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
    sun.misc.Launcher$ExtClassLoader(java.net.URLClassLoader).findClass(java.lang.String) line: 362
    sun.misc.Launcher$ExtClassLoader(java.lang.ClassLoader).loadClass(java.lang.String, boolean) line: 418
    sun.misc.Launcher$AppClassLoader(java.lang.ClassLoader).loadClass(java.lang.String, boolean) line: 405
    sun.misc.Launcher$AppClassLoader.loadClass(java.lang.String, boolean) line: 352
    sun.misc.Launcher$AppClassLoader(java.lang.ClassLoader).loadClass(java.lang.String) line: 351
    HelloRMIClient9.main(java.lang.String[]) line: 25

在Expressions窗口检查getURLs()，值为:

[
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/cldrdata.jar,
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/dnsns.jar,
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/jaccess.jar,
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/localedata.jar,
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/nashorn.jar,
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/sunec.jar,
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/sunjce_provider.jar,
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/sunpkcs11.jar,
file:/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.232.b09-0.el7_7.x86_64/jre/lib/ext/zipfs.jar
]

还有一次命中，getURLs()值为当前目录。总之，对于HelloRMIClient9.java第25行
的"(HelloRMIInterface)obj"，CLASSPATH中从未出现过远程URL。

显然，"java.rmi.server.codebase"只影响了ctx.lookup()，只影响了跟RMI强相关
的代码，在RMI内部根据"java.rmi.server.codebase"动态修正过CLASSPATH。

前面为了减少干挠，Client/Server同时指定了"java.rmi.server.codebase"，后面
将对此进行深究。

14) Dynamic code downloading using Java RMI

参[40]，这是官方示例，利用RMI架构远程计算PI值。本节不关心数学问题，我将该
示例简化，集中火力在类的远程动态加载上。

14.1) ParamInterface.java

```java
/*
 * javac -encoding GBK -g ParamInterface.java
 */

public interface ParamInterface<T>
{
    public T callback ();
}
```
用了模板。

14.2) MethodInterface.java

```java
/*
 * javac -encoding GBK -g MethodInterface.java
 */
import java.rmi.*;

public interface MethodInterface extends Remote
{
    public <T> T dosomething ( ParamInterface<T> p ) throws RemoteException;
}
```
14.3) MethodInterfaceImpl.java

```java
/*
 * javac -encoding GBK -g MethodInterfaceImpl.java
 */
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class MethodInterfaceImpl extends UnicastRemoteObject implements MethodInterface
{
    private static final long   serialVersionUID    = 0x5120131473637a02L;

    protected MethodInterfaceImpl () throws RemoteException
    {
        super();
    }

    @Override
    public <T> T dosomething ( ParamInterface<T> p ) throws RemoteException
    {
        /*
         * 客户端提供的代码
         */
        return p.callback();
    }
}
```
p.callback()是客户端提供的代码，服务端没有相关.class，将来通过RMI机制从客
户端获取并执行。

14.4) MethodInterfaceServer.java

```java
/*
 * javac -encoding GBK -g MethodInterfaceServer.java
 */
import javax.naming.*;

public class MethodInterfaceServer
{
    public static void main ( String[] argv ) throws Exception
    {
        if ( System.getSecurityManager() == null )
        {
            System.setSecurityManager( new SecurityManager() );
        }
        String          name    = argv[0];
        Context         ctx     = new InitialContext();
        MethodInterface m       = new MethodInterfaceImpl();
        ctx.rebind( name, m );
    }
}
```
14.5) ParamInterfaceStringImpl.java

```java
/*
 * javac -encoding GBK -g ParamInterfaceStringImpl.java
 */
import java.io.Serializable;

public class ParamInterfaceStringImpl implements ParamInterface<String>, Serializable
{
    private static final long   serialVersionUID    = 0x5120131473637a03L;

    private String  sth;

    public ParamInterfaceStringImpl ( String sth )
    {
        this.sth    = sth;
    }

    /*
     * 将来在服务端执行，而不是在客户端执行
     */
    public String callback ()
    {
        return( "[" + this.sth + "]" );
    }
}
```

callback()是客户端代码，但并不在客户端执行，而是通过RMI机制弄到服务端去执
行，再通过RMI机制将返回值取回到客户端。假设callback()是高负荷代码，服务端
硬件性能优越，此时很有现实意义。

MethodInterfaceServer在useCodebaseOnly为false时执行来自客户端的callback()，
这在2013年之前就是大洞。

RMI vulnerability in Java, as used with WebSphere eXtreme Scale - [2013-06-06]
https://www.ibm.com/support/pages/node/495735
https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2013-1537

据说这个现实版的大洞迫使Oracle将useCodebaseOnly默认值由false改成true。

14.6) MethodInterfaceClient.java

```java
/*
 * javac -encoding GBK -g MethodInterfaceClient.java
 */
import javax.naming.*;

public class MethodInterfaceClient
{
    public static void main ( String[] argv ) throws Exception
    {
        if ( System.getSecurityManager() == null )
        {
            System.setSecurityManager( new SecurityManager() );
        }
        String          name    = argv[0];
        String          sth     = argv[1];
        Context         ctx     = new InitialContext();
        MethodInterface m       = ( MethodInterface )ctx.lookup( name );
        ParamInterfaceStringImpl
                        param   = new ParamInterfaceStringImpl( sth );
        String          resp    = m.dosomething( param );
        System.out.println( resp );
    }
}
```

14.7) jndi.policy

--------------------------------------------------------------------------
grant
{
permission java.security.AllPermission;
};
--------------------------------------------------------------------------

14.8) 编译

javac -encoding GBK -g ParamInterface.java
javac -encoding GBK -g MethodInterface.java
javac -encoding GBK -g MethodInterfaceImpl.java
javac -encoding GBK -g MethodInterfaceServer.java
javac -encoding GBK -g ParamInterfaceStringImpl.java
javac -encoding GBK -g MethodInterfaceClient.java

14.9) 测试

14.9.1) Client/Server位于同一主机同一目录

启动两个服务端:

rmiregistry 1099

java \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceServer anything

测试客户端:

java \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceClient anything "msg from client"

测试正常，客户端输出:

[msg from client]

14.9.2) Client/Server/rmiregistry位于同一主机不同目录

Server和rmiregistry必须位于同一主机，Client可以位于不同主机。后面的测试方
案实际上相当于Client/Server位于不同主机。

假设目录结构是:

.
|
+---test0
+---test1
|       jndi.policy
|       MethodInterface.class
|       MethodInterfaceImpl.class
|       MethodInterfaceServer.class
|       ParamInterface.class
|
+---test2
|       jndi.policy
|       MethodInterface.class
|       MethodInterfaceClient.class
|       ParamInterface.class
|       ParamInterfaceStringImpl.class
|
+---testclientbase
|       ParamInterfaceStringImpl.class
|
\---testserverbase
        MethodInterface.class
        ParamInterface.class

test0是空目录，其他目录下有哪些.class是精心设计过的，不要多也不要少。

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录执行:

rmiregistry -J-Djava.rmi.server.useCodebaseOnly=false 1099

参[43]，"java.rmi.server.useCodebaseOnly"的官方描述是:

--------------------------------------------------------------------------
If this value is true, automatic loading of classes is prohibited except
from the local CLASSPATH and from the java.rmi.server.codebase property
set on this VM. Use of this property prevents client VMs from dynamically
downloading bytecodes from other codebases.
--------------------------------------------------------------------------

useCodebaseOnly为true时，当前JVM只认CLASSPATH和本地codebase，不认远程
codebase。参[42]，从JDK 7u21开始，useCodebaseOnly默认为true。

--------------------------------------------------------------------------
From JDK 7 Update 21, the RMI property java.rmi.server.useCodebaseOnly is
set to true by default. In earlier releases, the default value was false.
--------------------------------------------------------------------------

测试所用Java是8u232，必须将rmiregistry所在JVM的useCodebaseOnly设为false，
否则来自MethodInterfaceServer的远程codebase不被使用，ctx.rebind()会抛异常。

很多示例会让周知端口与动态端口紧耦合，即使分离了二者，也会让二者从同一目录
启动，这无形中避免了很多麻烦，但也会让初学者忽略背后的某些机制。本小节刻意
让周知端口与动态端口从不同目录启动，从而暴露某些背后的机制。

在test1目录执行:

java \
-Djava.rmi.server.useCodebaseOnly=false \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testserverbase/ \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceServer anything

如果是之前没有ctx.rebind()过，会在HTTP Server中看到:

"GET /testserverbase/MethodInterface.class HTTP/1.1" 200
"GET /testserverbase/ParamInterface.class HTTP/1.1" 200

rmiregistry有缓存机制，如果之前ctx.rebind()过，可能看不到GET请求。

ctx.rebind()首先会让MethodInterfaceServer主动访问rmiregistry，其次会引起
rmiregistry访问MethodInterfaceServer指定的远程codebase。上述两个GET请求就
是rmiregistry使用来自MethodInterfaceServer的远程codebase，从中下载.class。
MethodInterfaceServer必须指定codebase，否则rmiregistry找不到上述两个类，
ctx.rebind()会抛异常，因为rmiregistry的CLASSPATH中没有相关类，rmiregistry
亦未指定本地codebase。

必须将useCodebaseOnly设成false，此次测试方案中MethodInterfaceServer将来会
用到来自MethodInterfaceClient的远程codebase，尽管启动MethodInterfaceServer
时并未涉及远程codebase。

在test2目录执行:

java \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testclientbase/ \
-Djava.security.policy=jndi.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceClient anything "msg from client"

测试正常，客户端输出:

[msg from client]

如果是之前没有m.dosomething()过，会在HTTP Server中看到:

"GET /testclientbase/ParamInterfaceStringImpl.class HTTP/1.1" 200

MethodInterfaceServer有缓存机制，如果之前m.dosomething()过，可能看不到GET
请求。

m.dosomething()导致MethodInterfaceServer试图执行ParamInterfaceStringImpl的
callback()。MethodInterfaceServer在本地CLASSPATH中找不到
ParamInterfaceStringImpl.class，由于MethodInterfaceServer所在JVM已将
useCodebaseOnly设为false，于是MethodInterfaceServer使用来自
MethodInterfaceClient的远程codebase寻找ParamInterfaceStringImpl.class，对
应上述GET请求。

本地CLASSPATH优先级最高。假设useCodebaseOnly为false，远程codebase优先级高
于本地codebase，这点要注意；若同时有远程codebase、本地codebase，假设远程
codebase返回404，RMI内部并不会继续尝试本地codebase，这点更要注意。假设
useCodebaseOnly为true，先试本地CLASSPATH，再试本地codebase，忽略可能存在的
远程codebase。

此次测试方案中MethodInterfaceClient不需要使用远程codebase，也就不涉及
useCodebaseOnly的设置。

参[41]，注意这几段内容:

--------------------------------------------------------------------------
The remote object's codebase is specified by the remote object's server by
setting the java.rmi.server.codebase property. The Java RMI server
registers a remote object, bound to a name, with the Java RMI registry.
The codebase set on the server VM is annotated to the remote object
reference in the Java RMI registry.
--------------------------------------------------------------------------
If the codebase property is set on the client application, then that
codebase is annotated to the subtype instance when the subtype class is
loaded by the client. If the codebase is not set on the client, the remote
object will mistakenly use its own codebase.
--------------------------------------------------------------------------
A subtype is either:

An implementation of the interface that is declared as the method
parameter (or return) type

A subclass of the class that is declared as the method parameter (or
return) type
--------------------------------------------------------------------------

上文实际讲述了远程codebase这个信息如何传递的，注意"annotated"。

14.10) 相关stackoverflow问答点评

Java RMI codebase not working - [2014-05-21]
https://stackoverflow.com/questions/23794997/java-rmi-codebase-not-working

这篇没有说到点子上。题主的错误在于codebase没有以"/"结尾，只能用jar包是胡说
八道。

RMI server does not dynamically load missing classes from its HTTP codebase server - [2014-08-14]
https://stackoverflow.com/questions/25317419/rmi-server-does-not-dynamically-load-missing-classes-from-its-http-codebase-serv

useCodebaseOnly设为false之后，题主追问了个新问题:

--------------------------------------------------------------------------
When useCodebaseOnly was defaulted to true the server JVM should have
ignored client codebase added to the RMI stream, but it should have anyway
used its own codebase to retrieve the WorkRequestSquare class.

This should have been successful anyway because client and server codebase
values are identical.
--------------------------------------------------------------------------

他说这种情况下应该成功，这个预期是对的，我用前面的示例做了等价测试，符合预
期。但题主说这种情况下他没有成功，他应该犯了一些未在文中表述的低级错误。

后面另有两个有价值的问答:

--------------------------------------------------------------------------
Q:

What is the minimal set of classes that need to be in the classpath?

A:

The classes that are actually used by name in the client code. For example,
the remote interface, the types of the formal arguments and the result,
exception types named in remote method signatures.
--------------------------------------------------------------------------
Q:

what are the classes that can be missing in the classpath, provided they
are available using the codebase server?

A:

Classes that implement classes or interfaces above and that aren't used by
name in the client code.
--------------------------------------------------------------------------

这两个问答解释了"java.rmi.server.codebase的误会"这一小节的现象。估计很多
RMI初学者会跳这个坑。14小节的测试方案没有违背上面的问答。

RMI dynamic class loading - [2017-12-13]
https://stackoverflow.com/questions/47794554/rmi-dynamic-class-loading

早先我被13小节的测试方案整迷糊后四处找人解惑。随后KINGX陷入迷惑，后来他找到
这篇。题主的问题与13小节几乎一模一样，答主是这样说的:

--------------------------------------------------------------------------
If the client is using the Hello interface by name, it must be present on
its classpath. Same as it does when compiling it.

The codebase feature is for classes derived from those mentioned in remote
interfaces. In this case there is no apparent need to use the codebase
feature at all, but if you do, the codebase property needs to be set at
the JVM which is sending instances of those classes.
--------------------------------------------------------------------------

现在我已经彻底搞清楚了充分、必要条件是什么，上面这段回答是自然而然的结论。

Problem with Server RMI CODEBASE, [ClassNotFoundException] - [2014-05-05]
https://coderanch.com/t/633232/java/Server-RMI-CODEBASE-ClassNotFoundException

这是金超前找来的一篇，题主的问题与13小节几乎一模一样，但没人正确回答。我再
强调一下，强制类型转换不是RMI的一部分，与codebase毫无关系，实际与想像中但
并不真实存在的远程CLASSPATH相关。

remote jars in the classpath - [2013-01-09]
https://stackoverflow.com/questions/14247716/remote-jars-in-the-classpath

13小节实际是在问，如何在CLASSPATH中指定远程URL，本质同上面这个问题。有个答
主给了一段示例代码:

--------------------------------------------------------------------------
Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
method.setAccessible(true);
method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{new URL("http://somewhere.net/library.jar")});
Class.forName("your.remote.ClassName");
--------------------------------------------------------------------------

有人提到jar文件中"META-INF/MANIFEST.MF"可以指定"Class-Path:"，一个空格分隔
的URL序列，为URLClassLoader所用。

14.11) server.policy

--------------------------------------------------------------------------
grant codeBase "http://192.168.65.23:8080/testclientbase/"
{
permission java.security.AllPermission;
};

grant
{
permission java.net.SocketPermission "192.168.65.23:1099", "connect,resolve";
permission java.net.SocketPermission "192.168.65.23:*", "accept,resolve";
permission java.net.SocketPermission "192.168.65.23:8080", "connect,resolve";
};
--------------------------------------------------------------------------

因为服务端启用了SecurityManager，所以很多行为都需要显式允许。jndi.policy出
于便捷考虑，直接开放了所有权限。server.policy相对定制化一些，按需开放权限。

permission java.security.AllPermission;

这条其实没必要，来自客户端的callback()不涉及特权操作。可以删掉这个grant。

permission java.net.SocketPermission "192.168.65.23:1099", "connect,resolve";

动态端口服务需要访问周知端口服务，本条允许主动创建TCP连接。

permission java.net.SocketPermission "192.168.65.23:*", "accept,resolve";

客户端需要访问周知端口、动态端口。本条表示动态端口允许哪些源IP、源端口来连
自己。无法提前预知客户端所用源端口，本条用通配符允许所有源端口。好像端口那
里不支持逗号分隔的列表，但支持-号。

permission java.net.SocketPermission "192.168.65.23:8080", "connect,resolve";

服务端会访问客户端指定的codebase。

14.12) client.policy

--------------------------------------------------------------------------
grant
{
permission java.net.SocketPermission "192.168.65.23:*", "connect,resolve";
};
--------------------------------------------------------------------------

客户端需要访问周知端口、动态端口。本条指定目标IP、目标端口。不便提前预知动
态端口，本条用通配符允许所有目标端口。

在test1目录执行:

java \
-Djava.rmi.server.useCodebaseOnly=false \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testserverbase/ \
-Djava.security.policy=server.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceServer anything

在test2目录执行:

java \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testclientbase/ \
-Djava.security.policy=client.policy \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceClient anything "msg from client"

14.13) MethodInterfaceClient1.java (more about SecurityManager)

本小节测试方案中，MethodInterfaceClient并未与远程codebase打交道，指定的本
地codebase是让MethodInterfaceServer用的。这种情况下，MethodInterfaceClient
并不需要SecurityManager，可以裁剪成MethodInterfaceClient1.java，然后扔掉
.policy文件。

```java
/*
 * javac -encoding GBK -g MethodInterfaceClient1.java
 */
import javax.naming.*;

public class MethodInterfaceClient1
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        String          sth     = argv[1];
        Context         ctx     = new InitialContext();
        MethodInterface m       = ( MethodInterface )ctx.lookup( name );
        ParamInterfaceStringImpl
                        param   = new ParamInterfaceStringImpl( sth );
        String          resp    = m.dosomething( param );
        System.out.println( resp );
    }
}
```
在test2目录执行:

java \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testclientbase/ \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceClient1 anything "msg from client"

只有己方需要使用远程codebase时，己方才需要启用SecurityManager、指定policy
文件。

不必在源码中启用SecurityManager，可以用JVM参数启用之。在test2目录执行:

java \
-Djava.security.manager \
-Djava.security.policy=client.policy \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testclientbase/ \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceClient1 anything "msg from client"

关于SecurityManager，有个调试开关，在test2目录执行:

java \
-Djava.security.debug=all \
-Djava.security.manager \
-Djava.security.policy=client.policy \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testclientbase/ \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
MethodInterfaceClient1 anything "msg from client"

14.14) 什么情况下需要使用远程codebase

一般来说有三种情况。

a)

假设周知端口与动态端口分离了，启动时位于不同目录，此时周知端口很可能需要使
用来自动态端口的远程codebase，就像
"14.9.2) Client/Server/rmiregistry位于同一主机不同目录"小节演示的那样。

b)

对于动态端口，假设客户端传递的参数是接口参数类型的子类，或者像
"14.5) ParamInterfaceStringImpl.java"那种涉及模板的情形，此时服务端先在本
地CLASSPATH中找，没有找到合适的类，就会使用来自客户端的远程codebase。

c)

对于客户端，假设远程调用的返回值是接口返回值类型的子类，客户端试图调用子类
中重载过的方法，此时客户端先在本地CLASSPATH中找，没有找到合适的类，就会使
用来自服务端的远程codebase。

☆ 后记

要点就是，凡是被直接使用到的类，都不能远程加载。只有RMI机制内部透明加载的
类，才有可能远程加载，还受制于各种参数配置。codebase不hold直接用到的类，这
部分不归codebase管，codebase不能简单理解成远程CLASSPATH。

本文没有系统地回答前言里的那些疑问，因为这是我的个人学习笔记，不是教课书，
只记录了我觉得需要记录的部分，那些在我脑中的先验知识并未出现在笔记中。把参
考资源都捋一遍，足以建立框架性概念。

☆ 参考资源

[40]
    Trail: RMI
    https://docs.oracle.com/javase/tutorial/rmi/
    https://docs.oracle.com/javase/tutorial/rmi/overview.html (指出rmic不再必要)
    https://docs.oracle.com/javase/tutorial/rmi/server.html
    https://docs.oracle.com/javase/tutorial/rmi/designing.html
    https://docs.oracle.com/javase/tutorial/rmi/examples/compute/Compute.java
    https://docs.oracle.com/javase/tutorial/rmi/examples/compute/Task.java
    https://docs.oracle.com/javase/tutorial/rmi/implementing.html (讲了UnicastRemoteObject.exportObject)
    https://docs.oracle.com/javase/tutorial/rmi/examples/engine/ComputeEngine.java
    https://docs.oracle.com/javase/tutorial/rmi/client.html
    https://docs.oracle.com/javase/tutorial/rmi/examples/client/ComputePi.java
    https://docs.oracle.com/javase/tutorial/rmi/examples/client/Pi.java (不必细究数学部分)
    https://docs.oracle.com/javase/tutorial/rmi/example.html
    https://docs.oracle.com/javase/tutorial/rmi/compiling.html
    https://docs.oracle.com/javase/tutorial/rmi/running.html (提到java.net.InetAddress.getLocalHost)
    https://docs.oracle.com/javase/tutorial/rmi/examples/server.policy
    https://docs.oracle.com/javase/tutorial/rmi/examples/client.policy
    https://docs.oracle.com/javase/tutorial/rmi/end.html

[41]
    Dynamic code downloading using Java RMI (Using the java.rmi.server.codebase Property)
    https://docs.oracle.com/javase/8/docs/technotes/guides/rmi/codebase.html

[42]
    Why RMI registry is ignoring the java.rmi.server.codebase property - [2013-05-27]
    https://stackoverflow.com/questions/16769729/why-rmi-registry-is-ignoring-the-java-rmi-server-codebase-property

    RMI server does not dynamically load missing classes from its HTTP codebase server - [2014-08-14]
    https://stackoverflow.com/questions/25317419/rmi-server-does-not-dynamically-load-missing-classes-from-its-http-codebase-serv

    Enhancements in JDK 7
    https://docs.oracle.com/javase/7/docs/technotes/guides/rmi/enhancements-7.html

    RMI dynamic class loading - [2017-12-13]
    https://stackoverflow.com/questions/47794554/rmi-dynamic-class-loading

    remote jars in the classpath - [2013-01-09]
    https://stackoverflow.com/questions/14247716/remote-jars-in-the-classpath

[43]
    java.rmi Properties
    https://docs.oracle.com/javase/8/docs/technotes/guides/rmi/javarmiproperties.html

    sun.rmi Properties
    https://docs.oracle.com/javase/8/docs/technotes/guides/rmi/sunrmiproperties.html