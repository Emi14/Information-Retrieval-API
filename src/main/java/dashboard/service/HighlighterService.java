package dashboard.service;

import dashboard.core.analyzer.RomanianAnalyzerWithASCIIFolding;
import dashboard.resource.Fragment;
import dashboard.resource.Token;
import dashboard.utils.Constants;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    public String getHighlightedFragments (Document document, String searchQuery) throws IOException {
        String result = SPACE_STRING;

        Query query = initQuery(searchQuery);
        List<String> clauses = extractClauses(query);
        List<Fragment> fragments = new ArrayList<>();

        if (clauses.size() > 0) {
            String content = document.get(Constants.CONTENT);
            content = content.replaceAll(END_OF_LINE, SPACE_STRING);

            TokenStream tokenStream = new RomanianAnalyzerWithASCIIFolding()
                    .tokenStream(null, new StringReader(content));
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                String term = charTermAttribute.toString();

                if(clauses.contains(term)) {
                    fragments.add(buildFragment(offsetAttribute, content, clauses, term));
                }
            }
            endAndCloseToken(tokenStream);

             if (fragments.size() > 0){
                result = buildResultFromBestFragments(mergeFragments(fragments,content), clauses);
            }
        }

        return result;
    }

    private String buildResultFromBestFragments(List<Fragment> fragments, List<String> clauses) throws IOException {
        StringBuilder finalResult = new StringBuilder();
        Set<String> checkedTerms = new HashSet<>();
        boolean first = true;
        List<Fragment> finalFragments = new ArrayList<>();

        for(Fragment fragment : fragments) {
            Map<String, Integer> results = getHighlightedBestTokens(fragment, clauses);

            StringBuilder fragmentBuilder = new StringBuilder();
            Boolean existTerm = false;
            List<String> firstWords = new ArrayList<>();
            boolean finishFirst = false;

            String beforeWords;
            int position = 0;
            int newStartOffset = 0;
            for (String word : fragment.getText().split(SPACE_STRING)) {
                TokenStream tokenStream = new RomanianAnalyzerWithASCIIFolding().tokenStream(null,
                        new StringReader(word));
                CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

                resetAndIncrementToken(tokenStream);
                String term = charTermAttribute.toString();
                if (clauses.contains(term) && !checkedTerms.contains(term) && results.get(term).equals(position) ) {
                    checkedTerms.add(term);
                    existTerm = true;
                    fragment.getTerms().remove(term);

                    if(!finishFirst) {
                        finishFirst = true;
                        newStartOffset = String.join(SPACE_STRING, firstWords.subList(0,Math.max(firstWords.size() -
                                Constants.CONTEXT_WINDOW_LENGTH, 0))).length();
                        firstWords = firstWords.subList(Math.max(firstWords.size() -
                                Constants.CONTEXT_WINDOW_LENGTH, 0), firstWords.size());
                        beforeWords = String.join(SPACE_STRING, firstWords);
                        fragmentBuilder.append(beforeWords)
                                .append(getBoldWord(word));
                    } else {
                        fragmentBuilder.append(getBoldWord(word));
                    }
                } else {
                    if(!finishFirst) {
                        firstWords.add(word);
                    } else {
                        fragmentBuilder.append(word)
                                .append(SPACE_STRING);
                    }
                }
                endAndCloseToken(tokenStream);
                position++;
            }

            if (existTerm) {
                fragment.setText(fragmentBuilder.toString());
                if(newStartOffset > 0) {
                    fragment.setStartOffset(newStartOffset);
                }
            }
            finalFragments.add(fragment);
        }

        finalFragments.sort(Comparator.comparingInt(Fragment::getStartOffset));

        for (Fragment context:finalFragments) {
            if (first && context.getStartOffset() > 0) {
                first = addDelimiter(finalResult);
            }
            finalResult.append(context.getText());
            if (context.getContentLength() > context.getEndOffset()) {
                first = addDelimiter(finalResult);
            }
        }

        return finalResult.toString();
    }

    private boolean addDelimiter(StringBuilder finalResult) {
        finalResult.append(ELLIPSIS);
        return false;
    }

    private String getBoldWord(String word){
        return START_BOLD + word + END_BOLD;
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

        Set<Fragment> deletedFragments = new HashSet<>();
        for(int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            Set<String> fragmentTerms = new HashSet<>(fragment.getTerms());
            Set<String> copyOfFragmentTerms = new HashSet<>(fragment.getTerms());

            for(int j = 0; j < fragments.size(); j++) {
                if(j != i && !deletedFragments.contains(fragments.get(j))) {
                    Fragment secondTempFragments = fragments.get(j);
                    for (String term : fragmentTerms) {
                        if (secondTempFragments.getTerms().contains(term)) {
                            copyOfFragmentTerms.remove(term);
                        }
                    }
                }
            }

            if(copyOfFragmentTerms.isEmpty()) {
                deletedFragments.add(fragment);
            } else {
                finalFragments.add(fragment);
            }
        }
        finalFragments.sort(Comparator.comparing(Fragment::getTermsSize).reversed()
                .thenComparing(Fragment::getStartOffset));

        return finalFragments;
    }


    private Fragment buildFragment(OffsetAttribute offsetAttribute, String content, List<String> clauses, String term)
            throws IOException {
        Fragment fragment = new Fragment();
        fragment.getTerms().add(term);

        int startOffset = offsetAttribute.startOffset();
        int endOffset = offsetAttribute.endOffset();

        List<String> firstWords = getFirstWordsOfFragmentsWindow(content, startOffset);
        List<String> lastWords = getLastWordOfFragmentsWindow(content, endOffset);

        List<String> words = new ArrayList<>(firstWords);
        words.addAll(lastWords);

        for(String word:words) {
            TokenStream tokenStream = new RomanianAnalyzerWithASCIIFolding().tokenStream(null, new StringReader(word));
            CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

            resetAndIncrementToken(tokenStream);

            String termAttribute = charTermAttribute.toString();

            if(clauses.contains(termAttribute)){
                fragment.getTerms().add(termAttribute);
            }
            tokenStream.close();
        }

        String beforeFragments = String.join(SPACE_STRING, firstWords);
        String afterFragments = String.join(SPACE_STRING, lastWords);

        int start = Math.max(0, startOffset - beforeFragments.length() - 1);
        int end = Math.min(content.length(), endOffset + afterFragments.length() + 1);

        enrichFragment(content, fragment, startOffset, endOffset, beforeFragments, afterFragments, start, end);

        return fragment;
    }

    private void enrichFragment(String content, Fragment fragment, int startOffset, int endOffset, String beforeFragments, String afterFragments, int start, int end) {
        fragment.setText(beforeFragments + SPACE_STRING + content.substring(startOffset, endOffset) + SPACE_STRING + afterFragments);
        fragment.setStartOffset(start);
        fragment.setEndOffset(end);
    }

    private List<String> getFirstWordsOfFragmentsWindow(String content, int startOffset){
        List<String> beginWords = Arrays.asList(content.substring(0, startOffset).trim().split(SPACE_STRING));
        return beginWords.subList(Math.max(0, beginWords.size() - Constants.CONTEXT_WINDOW_LENGTH), beginWords.size());
    }

    private List<String> getLastWordOfFragmentsWindow(String content, int endOffset){
        List<String> endWords = Arrays.asList(content.substring(endOffset).trim().split(SPACE_STRING));
        return endWords.subList(0, Math.min(Constants.CONTEXT_WINDOW_LENGTH, endWords.size()));
    }

    private Query initQuery(String searchQuery) {
        QueryParser queryParser = new QueryParser(Constants.CONTENT, new RomanianAnalyzerWithASCIIFolding());
        try {
            return  queryParser.parse(searchQuery);
        } catch (ParseException e) {
            LOGGER.error(ERROR_WHILE_PARSING_THE_QUERY + e);
        }
        return null;
    }

    private List<String> extractClauses(Query query) {
        if(Objects.nonNull(query)) {
            return Arrays.asList(query.toString().replace(Constants.CONTENT.toLowerCase() + COLON, EMPTY_STRING)
                    .split(SPACE_STRING));
        }
        return new ArrayList<>();
    }


    private void resetAndIncrementToken(TokenStream tokenStream) throws IOException {
        tokenStream.reset();
        tokenStream.incrementToken();
    }

    private void endAndCloseToken(TokenStream tokenStream) throws IOException {
        tokenStream.end();
        tokenStream.close();
    }

    private Map<String, Integer> getHighlightedBestTokens(Fragment fragment, List<String> clauses) throws IOException {

        int position = 0;
        List<Token> tokens = new ArrayList<>();
        Map<String, Integer> results = new HashMap<>();
        Map<String, Integer> positions = new HashMap<>();

        for (String word : fragment.getText().split(" ")) {
            TokenStream tokenStream = new RomanianAnalyzerWithASCIIFolding().tokenStream(null, new StringReader(word));
            OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
            CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

            resetAndIncrementToken(tokenStream);
            String term = charTermAttribute.toString();
            int startOffset = offsetAttribute.startOffset();
            int endOffset = offsetAttribute.endOffset();

            if(clauses.contains(term)) {
                tokens.add(new Token(term, startOffset, endOffset,position));
            }

            position++;
            tokenStream.close();
        }

        for(Token token:tokens) {
            int distance = 0;
            for(Token token1:tokens) {
                distance += Math.abs(token1.getPosition() - token.getPosition());
            }

            results.putIfAbsent(token.getToken(), distance);
            positions.putIfAbsent(token.getToken(), token.getPosition());

            if(results.get(token.getToken()) > distance) {
                results.put(token.getToken(), distance);
                positions.put(token.getToken(), token.getPosition());
            }
        }

        return positions;
    }

}