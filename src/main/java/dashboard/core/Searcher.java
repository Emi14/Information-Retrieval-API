package dashboard.core;

import dashboard.resource.SearchResult;
import dashboard.service.HighlighterService;
import dashboard.utils.Constants;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by Ionut Emanuel Mihailescu on 3/19/18.
 */
@Component
@DependsOn("indexer")
public class Searcher {
    private static final Logger LOGGER = Logger.getLogger(Searcher.class);

    private static final String STATUS = "status";
    private static final String SUCCESS = "success";
    private static final String RESULTS_COUNT = "resultsCount";
    private static final String STARTED_SEARCH = "Started search for query %s .";
    private static final String SEARCH_ERROR = "Search for query %s , failed due to %s .";
    private static final int ZERO = 0;
    private static final String ERROR = "error";
    private static final String LONG_LINE = "-------------------------------";
    private static final String ONE_SPACE = " ";
    private static final String TFIDF = "TFIDF: ";
    private static final String TF = "TF: ";
    private static final String IDF = "IDF: ";
    private static final String ARROW = " -> ";
    private static final String END_OF_LINE = "\n";

    @Autowired
    private Indexer indexer;

    @Autowired
    private HighlighterService highlighterService;

    private IndexSearcher indexSearcher;

    private QueryParser queryParser;

    @PostConstruct
    private void createIndexSearcher() throws IOException {
        Directory indexDirectory = FSDirectory.open(Paths.get(indexer.getIndexDirectoryPath()));
        DirectoryReader directoryReader = DirectoryReader.open(indexDirectory);

        queryParser = new QueryParser(Constants.CONTENT, indexer.getIndexWriter().getAnalyzer());
        indexSearcher = new IndexSearcher(directoryReader);
    }

    private TopDocs executeQuery(String query) throws ParseException, IOException {
        return indexSearcher.search(queryParser.parse(query), Constants.MAX_HITS);
    }


    public SearchResult search(String query) {
        LOGGER.info(String.format(STARTED_SEARCH, query));

        SearchResult searchResult = new SearchResult();

        double numDocs = indexSearcher.getIndexReader().numDocs();
        String[] searchTerms = query.toLowerCase().split(ONE_SPACE);

        StringBuilder idf = new StringBuilder();

        Map<String, Double> idfValues = new HashMap<>();

        try {
            for (String word : searchTerms) {
                TopDocs hits = executeQuery(word);
                double value = Math.log10(numDocs / (double) hits.totalHits);
                idf.append(IDF).append(word).append(ARROW).append(value).append("\n");
                idfValues.put(word, value);
            }

            System.out.println(LONG_LINE);
            System.out.println(idf);

            TopDocs hits = executeQuery(query);

            if (Objects.nonNull(hits)) {

                addSearchDetails(searchResult, SUCCESS, hits.scoreDocs.length);
                addDocumentsDetails(searchResult, hits, query, idfValues);

            } else {
                addSearchDetails(searchResult, SUCCESS, ZERO);
            }
        } catch (ParseException | IOException e) {
            LOGGER.error(String.format(SEARCH_ERROR, query, e));
            addSearchDetails(searchResult, ERROR, ZERO);
        }

        return searchResult;
    }

    private void addSearchDetails(SearchResult searchResult, String status, int resultCount) {
        searchResult.addSearchDetail(STATUS, status);
        searchResult.addSearchDetail(RESULTS_COUNT, String.valueOf(resultCount));
    }

    private void addDocumentsDetails(SearchResult searchResult, TopDocs hits, String query, Map<String, Double> idfValues) {

        String[] searchTerms = query.toLowerCase().split(ONE_SPACE);

        Arrays.stream(hits.scoreDocs).sorted((o1, o2) -> Math.round(o1.score - o2.score))
                .forEachOrdered(hit -> {
                    try {
                        Document document = indexSearcher.getIndexReader().document(hit.doc);

                        System.out.println(LONG_LINE + document.getField(Constants.FILE_NAME).stringValue() + LONG_LINE);

                        StringBuilder tf = new StringBuilder();

                        Map<String, Integer> documentWords = new HashMap<>();
                        Terms terms = indexSearcher.getIndexReader().getTermVector(hit.doc, Constants.CONTENT);
                        TermsEnum termsEnum = terms.iterator();
                        BytesRef term;
                        PostingsEnum postings = null;
                        while ((term = termsEnum.next()) != null) {
                            String termText = term.utf8ToString();
                            postings = termsEnum.postings(postings, PostingsEnum.FREQS);
                            postings.nextDoc();
                            int freq = postings.freq();
                            documentWords.put(termText, freq);
                        }
                        for (String word : searchTerms) {

                            tf.append(TF).append(word).append(ARROW);
                            Integer counter = documentWords.get(word.substring(0, word.length() - 1));
                            double tfValue = 0;
                            if (Objects.nonNull(counter)) {
                                tfValue = 1 + Math.log10(counter);
                            }
                            tf.append(tfValue).append(END_OF_LINE);
                            double idf = idfValues.get(word);
                            tf.append(TFIDF).append(word).append(ARROW).append(tfValue * idf).append(END_OF_LINE);
                        }

                        String highlightedFragments = highlighterService.getHighlightedFragments(document, query);

                        Map<String, String> documentDetails = new HashMap<>();
                        documentDetails.put(Constants.CONTENT, highlightedFragments);
                        documentDetails.put(Constants.FILE_NAME, document.get(Constants.FILE_NAME));
                        searchResult.getSearchResults().add(documentDetails);

                        System.out.println(tf.toString());

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }


}

