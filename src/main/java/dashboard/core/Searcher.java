package dashboard.core;

import dashboard.resource.SearchResult;
import dashboard.service.HighlighterService;
import dashboard.utils.Constants;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
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
import java.util.stream.Collectors;

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

        try {
            TopDocs hits = executeQuery(query);

            if(Objects.nonNull(hits)){

                addSearchDetails(searchResult, SUCCESS, hits.scoreDocs.length);
                addDocumentsDetails(searchResult, hits, query);

            } else {
                addSearchDetails(searchResult, SUCCESS, ZERO);
            }
        } catch (ParseException | IOException | InvalidTokenOffsetsException e) {
            LOGGER.error(String.format(SEARCH_ERROR, query, e));
            addSearchDetails(searchResult, ERROR, ZERO);
        }

        return searchResult;
    }

    private void addSearchDetails(SearchResult searchResult, String status, int resultCount) {
        searchResult.addSearchDetail(STATUS, status);
        searchResult.addSearchDetail(RESULTS_COUNT, String.valueOf(resultCount));
    }

    private void addDocumentsDetails(SearchResult searchResult, TopDocs hits, String query)
            throws IOException, ParseException, InvalidTokenOffsetsException {

        Arrays.stream(hits.scoreDocs).sorted((o1, o2) -> Math.round(o1.score-o2.score))
                .forEachOrdered(hit -> {
                    try {
                        Document document = indexSearcher.getIndexReader().document(hit.doc);
                        System.out.println(Arrays.stream(document.get(Constants.CONTENT)
                                .replace("\n"," ")
                                .split(" "))
                                .filter(word -> word.toLowerCase().equals("mama"))
                                .collect(Collectors.toList()).size());
                        String highlightedFragments = highlighterService.getHighlightedFragments(document, query);

                        Map<String, String> documentDetails = new HashMap<>();
                        documentDetails.put(Constants.CONTENT, highlightedFragments);
                        documentDetails.put(Constants.FILE_NAME, document.get(Constants.FILE_NAME));

                        searchResult.getSearchResults().add(documentDetails);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }


}

