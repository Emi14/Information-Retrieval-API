package dashboard.service;

import dashboard.core.Indexer;
import dashboard.core.analyzer.RomanianAnalyzerWithASCIIFolding;
import dashboard.resource.Fragment;
import dashboard.utils.Constants;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Created by Ionut Emanuel Mihailescu on 5/5/18.
 */
@Service
public class HighlighterService {
    private static final Logger LOGGER = Logger.getLogger(HighlighterService.class);

    private static final String SPACE_STRING = " ";
    private static final String ERROR_WHILE_PARSING_THE_QUERY = "Error while parsing the query, ";
    private static final String EMPTY_STRING = "";
    private static final String START_BOLD = " <b>";
    private static final String END_BOLD = "</b> ";
    private static final String ELLIPSIS = " ... ";
    private static final String COLON = ":";
    private static final String END_OF_LINE = "\n";

    @Autowired
    private RomanianAnalyzerWithASCIIFolding analyzerWithASCIIFolding;

    @Autowired
    private Indexer indexer;

    public String getHighlightedFragments (Document document, String searchQuery) throws IOException {
        String result = SPACE_STRING;

        Query query = initQuery(searchQuery);
        List<String> clauses = extractClausesFrom(query);
        List<Fragment> fragments = new ArrayList<>();


        if (clauses.size() > 0) {
            String content = document.get(Constants.CONTENT);
            content = content.replaceAll(END_OF_LINE, EMPTY_STRING);

            TokenStream tokenStream = indexer.getRomanianAnalyzerWithASCIIFolding()
                    .tokenStream(null, new StringReader(content));
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                int startOffset = offsetAttribute.startOffset();
                int endOffset = offsetAttribute.endOffset();
                String term = charTermAttribute.toString();

                if(clauses.contains(term)) {
                    fragments.add(buildFragment(startOffset, endOffset, content, clauses, term));
                }
            }
            tokenStream.end();
            tokenStream.close();

            if (fragments.size() > 0){
                result = buildResultFromBestFragments(mergeFragments(fragments,content), clauses);
            }

        }

        return result;
    }

    private String buildResultFromBestFragments(List<Fragment> fragments, List<String> clauses) throws IOException {
        StringBuilder finalResult = new StringBuilder();
        Set<String> checkedTerms = new HashSet<>();

        for(Fragment fragment : fragments) {
            StringBuilder fragmentBuilder = new StringBuilder();
            Boolean existTerm = false;
            List<String> firstWords = new ArrayList<>();
            boolean finishFirst = false;

            String beforeWords;
            for (String word : fragment.getText().split(SPACE_STRING)) {
                TokenStream tokenStream = analyzerWithASCIIFolding.tokenStream(null, new StringReader(word));
                CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

                tokenStream.reset();
                tokenStream.incrementToken();
                String term = charTermAttribute.toString();
                if (clauses.contains(term) && !checkedTerms.contains(term)) {
                    checkedTerms.add(term);
                    existTerm = true;
                    fragment.setTerms(new HashSet<>());

                    if(!finishFirst) {
                        finishFirst = true;
                        firstWords = firstWords.subList(Math.max(firstWords.size() -
                                Constants.CONTEXT_WINDOW_LENGTH, 0), firstWords.size());
                        beforeWords = String.join(SPACE_STRING, firstWords);
                        fragmentBuilder.append(beforeWords)
                                .append(START_BOLD)
                                .append(word)
                                .append(END_BOLD);
                    } else {
                        fragmentBuilder.append(START_BOLD)
                                .append(word)
                                .append(END_BOLD);
                    }
                } else {
                    if(!finishFirst) {
                        firstWords.add(word);
                    } else {
                        fragmentBuilder.append(word)
                                .append(SPACE_STRING);
                    }
                }

                tokenStream.end();
                tokenStream.close();
            }

            if (existTerm) {
                finalResult.append(fragmentBuilder);

                if(fragment.getContentLength() > fragment.getEndOffset()) {
                    finalResult.append(START_BOLD)
                            .append(ELLIPSIS)
                            .append(END_BOLD);
                }
            }
        }

        return finalResult.toString();
    }


    private List<Fragment> mergeFragments(List<Fragment> fragments, String content) {
        List<Fragment> mergedFragments = new ArrayList<>();

        int startOffset = 0;
        int endOffset = 0;
        for (Fragment fragment : fragments) {
            if (fragment.getStartOffset() <= endOffset) {
                startOffset = Math.min(startOffset, fragment.getStartOffset());
                endOffset = Math.max(endOffset, fragment.getEndOffset());

                Fragment mergedFragment = createAndEnrichFragment(content, startOffset, endOffset, fragment);

                if(!mergedFragments.isEmpty()) {
                    Fragment lastAddedFragment = mergedFragments.get(mergedFragments.size() - 1);
                    mergedFragment.getTerms().addAll(lastAddedFragment.getTerms());
                    mergedFragments.remove(lastAddedFragment);
                }
                mergedFragments.add(mergedFragment);
            }
            else {
                startOffset = fragment.getStartOffset();
                endOffset = fragment.getEndOffset();
                fragment.setContentLength(content.length());

                mergedFragments.add(fragment);
            }
        }

        return analyseBestFragments(mergedFragments);
    }

    private Fragment createAndEnrichFragment(String content, int startOffset, int endOffset, Fragment fragment) {
        Fragment mergedFragment = new Fragment();
        mergedFragment.setStartOffset(startOffset);
        mergedFragment.setEndOffset(endOffset);
        mergedFragment.setText(content.substring(startOffset, endOffset));
        mergedFragment.setContentLength(content.length());
        mergedFragment.setTerms(fragment.getTerms());
        return mergedFragment;
    }

    private List<Fragment> analyseBestFragments(List<Fragment> fragments) {
        List<Fragment> finalFragments = new ArrayList<>();

        fragments.sort(Comparator.comparing(Fragment::getTermsSize)
                .thenComparing(Fragment::getStartOffset));

        Set<Fragment> deletedFragments = new HashSet<>();
        for(int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            Set<String> fragmentTerms = new HashSet<>(fragment.getTerms());
            Set<String> copyOfFragmentTerms = new HashSet<>(fragment.getTerms());

            for(int j = 0; j < fragments.size(); j++) {
                if(j != i && !deletedFragments.contains(fragments.get(j))) {
                    Fragment secondTempContext = fragments.get(j);
                    for (String term : fragmentTerms) {
                        if (secondTempContext.getTerms().contains(term)) {
                            copyOfFragmentTerms.remove(term);
                        }
                    }
                }
            }

            if(copyOfFragmentTerms.size() > 0) {
                finalFragments.add(fragment);
            } else {
                deletedFragments.add(fragment);
            }
        }

        finalFragments.sort(Comparator.comparingInt(Fragment::getStartOffset));
        return finalFragments;
    }


    private Fragment buildFragment(int startOffset, int endOffset, String content, List<String> clauses, String term)
            throws IOException {
        Fragment fragment = new Fragment();
        fragment.getTerms().add(term);

        List<String> firstWords = getFirstWordsOfContextWindow(content, startOffset);
        List<String> lastWords = getLastWordOfContextWindow(content, endOffset);

        List<String> words = new ArrayList<>(firstWords);
        words.addAll(lastWords);

        for(String word:words) {
            TokenStream tokenStream = analyzerWithASCIIFolding.tokenStream(null, new StringReader(word));
            CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

            tokenStream.reset();
            tokenStream.incrementToken();

            String termAttribute = charTermAttribute.toString();

            if(clauses.contains(termAttribute)){
                fragment.getTerms().add(termAttribute);
            }
            tokenStream.close();
        }

        String beforeContext = String.join(SPACE_STRING, firstWords);
        String afterContext = String.join(SPACE_STRING, lastWords);

        int start = Math.max(0, startOffset - beforeContext.length() - 1);
        int end = Math.min(content.length(), endOffset + afterContext.length() + 1);

        fragment.setText(beforeContext + SPACE_STRING + content.substring(startOffset, endOffset) + SPACE_STRING + afterContext);
        fragment.setStartOffset(start);
        fragment.setEndOffset(end);

        return fragment;
    }

    private List<String> getFirstWordsOfContextWindow(String content, int startOffset){
        List<String> beginWords = Arrays.asList(content.substring(0, startOffset).trim().split(SPACE_STRING));
        return beginWords.subList(Math.max(0, beginWords.size() - Constants.CONTEXT_WINDOW_LENGTH), beginWords.size());
    }

    private List<String> getLastWordOfContextWindow(String content, int endOffset){
        List<String> endWords = Arrays.asList(content.substring(endOffset).trim().split(SPACE_STRING));
        return endWords.subList(0, Math.min(Constants.CONTEXT_WINDOW_LENGTH, endWords.size()));
    }

    private Query initQuery(String searchQuery) {
        QueryParser queryParser = new QueryParser(Constants.CONTENT, analyzerWithASCIIFolding);

        try {
            return  queryParser.parse(searchQuery);
        } catch (ParseException e) {
            LOGGER.error(ERROR_WHILE_PARSING_THE_QUERY + e);
        }

        return null;
    }

    private List<String> extractClausesFrom(Query query) {
        if(Objects.nonNull(query)) {
            return Arrays.asList(query.toString().replace(Constants.CONTENT.toLowerCase() + COLON, EMPTY_STRING)
                    .split(SPACE_STRING));
        }
        return new ArrayList<>();
    }


}
