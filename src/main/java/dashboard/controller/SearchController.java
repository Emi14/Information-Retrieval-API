package dashboard.controller;

import dashboard.core.Searcher;
import dashboard.resource.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Created by Ionut Emanuel Mihailescu on 3/19/18.
 */
@Controller
@RequestMapping(value = "/api/search")
public class SearchController {

    @Autowired
    private Searcher searcher;

    @RequestMapping(value = "/singleQuery", method = RequestMethod.GET)
    public ResponseEntity<SearchResult> searchForQuery(@RequestParam String query){
        return new ResponseEntity<>(searcher.search(query), HttpStatus.OK);
    }

}
