import java.io.*;

/*
 * 必须实现Serializable接口
 */
class Message implements Serializable
{
    private static final long   serialVersionUID    = 0x5120131473637a00L;

    private String  msg;

    public Message ()
    {
    }

    public Message ( String msg )
    {
        this.msg    = msg;
    }

    public String getMsg ()
    {
        return( this.msg );
    }

    public void setMsg ( String msg )
    {
        this.msg    = msg;
    }
}