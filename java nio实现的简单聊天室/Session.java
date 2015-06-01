import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

class SearchResult {
	private static final int EXPIRATION_HOUR = 2;
	private Date expirationDate;
	private String result;
	
	public SearchResult(String result) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(Calendar.HOUR_OF_DAY, EXPIRATION_HOUR);
		expirationDate = calendar.getTime();
		this.result = result;
	}
	
	public boolean expired() {
		if (expirationDate.compareTo(new Date()) < 0) {
			return true;
		}
		return false;
	}
	
	public String getResult() {
		return result;
	}
}


public class Session implements Serializable {
	private static final long serialVersionUID = 1L;
	private static Map<Type, Map<String, SearchResult>> map = new HashMap<Type, Map<String, SearchResult>>();
	
	public static SearchResult getResult(Type type, String content) {
		SearchResult sr = null;
		if (map.containsKey(type)) {
			Map<String, SearchResult> tHash = map.get(type);
			if (tHash.containsKey(content)) {
				SearchResult tSr = tHash.get(content);
				if (!tSr.expired()) {
					sr = tSr;
				}
			}
		} else {
			map.put(type, new HashMap<String, SearchResult>());
		}
		return sr;
	}
	
	public static void setResult(Type type, String content, SearchResult result) {
		Map<String, SearchResult> hash = map.get(type);
		hash.put(content, result);
	}
	
	public static void main(String[] args) {
		//TODO
	}

}
