package dashboard.core.analyzer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.tartarus.snowball.ext.RomanianStemmer;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by Ionut Emanuel Mihailescu on 3/24/18.
 */
//@Component
//@Scope(value = BeanDefinition.SCOPE_PROTOTYPE)
public class RomanianAnalyzerWithASCIIFolding extends Analyzer {
    public static final String ERROR_WHILE_LOADING_STOPWORDS = "Error while loading the stopwords: %s";
    private static final Logger LOGGER = Logger.getLogger(RomanianAnalyzerWithASCIIFolding.class);
    CharArraySet stopwords;
    @Value("${stopwords.path}")
    private String stopwordsPath="/Users/ionutmihailescu/My stuff/InformationRetrievalApi/src/main/resources/stopwords.txt";

    @PostConstruct
    public void init(){
        this.stopwords = loadStopwords();
    }

    public RomanianAnalyzerWithASCIIFolding(){
        this.stopwords = loadStopwords();
    }

    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new StandardTokenizer();
        TokenStream result = new StandardFilter(source);

        result = new LowerCaseFilter(result);
        result = new ASCIIFoldingFilter(result);
        result = new StopFilter(result, this.stopwords);
        result = new SnowballFilter(result, new RomanianStemmer());

        return new TokenStreamComponents(source, result);
    }

    private CharArraySet loadStopwords() {
        List<String> stopWordsAsStrings = new ArrayList<>();

        try (Scanner scanner = new Scanner(Paths.get(stopwordsPath))) {

            while (scanner.hasNext()) {
                String stopword = scanner.nextLine();
                stopWordsAsStrings.add(stopword);
                stopWordsAsStrings.add(StringUtils.stripAccents(stopword));
            }

        } catch (IOException e) {
            LOGGER.error(String.format(ERROR_WHILE_LOADING_STOPWORDS,e));
        }

        CharArraySet stopwords = new CharArraySet(stopWordsAsStrings,false);

        return stopwords;
    }

}
