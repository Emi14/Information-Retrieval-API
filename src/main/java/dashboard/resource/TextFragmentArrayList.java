package dashboard.resource;

import org.apache.lucene.search.highlight.TextFragment;

import java.util.ArrayList;

/**
 * Created by Ionut Emanuel Mihailescu on 5/12/18.
 */
public class TextFragmentArrayList extends ArrayList<TextFragment> {

    private static final long serialVersionUID = -3229035962744948571L;

    public boolean add(TextFragment fragment) {
        if (this.isEmpty())  {
            return super.add(fragment);
        }
        TextFragment currentFragment = this.stream().filter(value -> fragment.follows(value))
                .findFirst().orElse(null);
        if (currentFragment == null) {
            return super.add(fragment);
        }
        currentFragment.merge(fragment);
        return false;
    }
}