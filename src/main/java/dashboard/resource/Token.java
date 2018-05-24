package dashboard.resource;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Ionut Emanuel Mihailescu on 5/10/18.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Token {

    private String token;
    private int startOffset;
    private int endOffset;
    private int position;

}
