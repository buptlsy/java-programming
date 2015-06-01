import java.util.HashMap;
import java.util.Map;


public enum Type {
	WEATHER(1, "天气", "天气"),
	STOCK(2, "股票", "股票"),
	TRAIN_TICKETS(3, "火车票", "火车票");
	
    private static final Map<String, Type> code2Type;
	
    static {
    	code2Type = new HashMap<String, Type>();
    	for (Type type : Type.values()) {
    		code2Type.put(type.getCode(), type);
    	}
    }
    
	private int id;
    private String code;
    private String description;
	
	Type() {}
	
	Type(int id, String code, String description) {
        this.id = id;
        this.code = code;
        this.description = description;
    }
	
	public static Type fromCode(String code) {
		return code2Type.get(code);
	}
	
	public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
	
}
