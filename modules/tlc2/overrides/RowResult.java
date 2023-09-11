package tlc2.overrides;

public class RowResult {
    public long finger_print;
    public String json_string;

    public RowResult(Long finger_print, String json_string)  {
        this.finger_print = finger_print;
        this.json_string = json_string;
    }
}