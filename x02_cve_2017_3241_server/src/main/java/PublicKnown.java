import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/*
 * 假设这是在服务端正常存在且位于CLASSPATH中的类
 */
public class PublicKnown implements Serializable {
    /*
     * 与Message、SomeInterfaceImpl不同
     */
    private static final long serialVersionUID = 0x5120131473637a02L;

    /*
     * 所找PublicKnown必须有实现这个函数，否则无法利用CVE-2017-3241漏洞
     */
    private void readObject(ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        System.out.println("PublicKnown.readObject()");
        ois.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        System.out.println("PublicKnown.writeObject()");
        oos.defaultWriteObject();
    }
}