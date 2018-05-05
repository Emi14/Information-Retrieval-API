package dashboard.resource;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Ionut Emanuel Mihailescu on 5/2/18.
 */
@Getter
@Setter
public class Fragment {

    private int startOffset;
    private int endOffset;
    private int contentLength;
    private String text;
    private Set<String> terms = new HashSet<>();

    public int getTermsSize(){
        return terms.size();
    }

}
