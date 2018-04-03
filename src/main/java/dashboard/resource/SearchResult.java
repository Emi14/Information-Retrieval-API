package dashboard.resource;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Ionut Emanuel Mihailescu on 3/20/18.
 */
@Getter
@Setter
public class SearchResult {

   private Map<String, String> searchDetails = new HashMap<>();
   private List<Map<String, String>> searchResults = new ArrayList<>();

   public void addSearchDetail(String key, String value){
       this.searchDetails.put(key,value);
   }

}
