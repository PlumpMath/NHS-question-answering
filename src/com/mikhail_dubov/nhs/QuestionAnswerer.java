package com.mikhail_dubov.nhs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.mikhail_dubov.nhs.lib.Stemmer;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;

/**
 * Question Answerer that can handle queries about conditions, symptoms, treatments etc.
 * written in English. It uses the NHS data to answer those queries.
 *
 * @author Mikhail Dubov
 */
public class QuestionAnswerer {
    
    private JSONObject nhsData;
    private Set<String> stopwords;
    
    /**
     * Initializes the Question Answerer.
     *
     * @param dataPath Path to the data scraped from NHS by NhsScraper.
     * @param stopwordsPath Path to a text file containing English Stopwords.
     * @throws FileNotFoundException If one of the files does not exist.
     * @throws IOException If there were problems reading from the input files.
     * @throws ParseException If the data is not a valid JSON string.
     */
    public QuestionAnswerer(String dataPath, String stopwordsPath)
            throws FileNotFoundException, IOException, ParseException {
        // Load the NHS data that has been scraped earlier
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(dataPath));
        this.nhsData = (JSONObject) obj;
        
        // Also load the list of English stopwords, needed to process the queries
        this.stopwords = new HashSet<String>();
        BufferedReader in = new BufferedReader(new FileReader(stopwordsPath));
        String word;
        while((word = in.readLine()) != null){
            this.stopwords.add(word);
        }
        in.close();
    }
    
    /**
     * Answers a query providing a structured reply in JSON format.
     *
     * @param query Healthcare-related query in English, e.g. "What are the symptoms of cancer?".
     * @return JSONObject containing the URL of the corresponding NHS page as well as
     *         fields corresponding to the various aspects of the reply to the query.
     */
    public JSONObject answer(String query) {
        // Query preprocessing (tokenization, stopwords filtering, stemming)
        Set<String> bagOfWords = preprocess(query);
        
        // Start the recursive search in the JSON tree for the "most specific" node
        // with respect to the query.
        Object response = search(this.nhsData, bagOfWords, 0);
        JSONObject result = new JSONObject();
        result.put("query", query);
        result.put("response", response);
        return result;
    }
    
    /**
     * Transforms the input string (query / JSON key) into a "bag of words",
     * also filtering stopwords and performing stemming.
     * 
     * Example: "What are the Symptoms of cancer?" -> {"symptom", "cancer"}.
     *
     * @param str The input string.
     * @return A set of tokens.
     */
    private Set<String> preprocess(String str) {
        Iterator<Word> words = PTBTokenizer.newPTBTokenizer(new StringReader(str.toLowerCase()));
        Stemmer stemmer = new Stemmer();
        HashSet<String> bagOfWords = new HashSet<String>();
        boolean insideBrackets = false;
        while (words.hasNext()) {
            Word token = words.next();
            // Omit text in brackets
            if (token.word().equals("-LRB-")) {
            	insideBrackets = true;
            } else if (token.word().equals("-RRB-")) {
            	insideBrackets = false;
            }
            // Filter out stopwords + 1-character words (to get rid of punctuation)
            if (! this.stopwords.contains(token.word())
            		&& token.word().length() >= 2
            		&& ! insideBrackets) {
                bagOfWords.add(stemmer.stem(token.word()));
            }
        }
        return bagOfWords;
    }
    
    /**
     * Looks for the most appropriate page in the NHS data matching the query keywords.
     * 
     * High-level idea of the search algorithm: try to get as deep as possible
     * in the JSON tree until the query matches at least some words in the keys.
     * When nothing matches the query, return null.
     *
     * NOTE: Obviously, this can be improved in many ways, just to list a few:
     *         * Support query extension via synonyms etc.;
     *         * Support typo corrections in the queries;
     *         * Look not only at JSON keys but also at values (i.e. texts) while
     *           performing the search.
     *
     * @param data the input JSON object.
     * @param keywords keywords extracted from the query.
     * @param depth the current depth (level of the JSON tree) of the search.
     * @return best-matching" subtree (JSON object).
     */
    private Object search(JSONObject data, Set<String> keywords, int depth) {
        // Base case: we are deep enough, so return.
    	// TODO: there may be use cases when it makes sense to get even more
    	//       detailed and investigate deeper levels of our data.
        if (depth == 2) {
            return data;
        }
        // General case: try to find the subtree whose key contains
        // the most words from the query. This is important to distinguish
        // e.g. "oesophageal cancer" from just "cancer".
        JSONObject jsonData = (JSONObject) data;
        int maxWords = 0;
        JSONObject bestSubtree = null;
        // Sort keys by length to start with simplest possible things
        List<String> keysSortedByLength = new ArrayList<String>();
        for (Object key : jsonData.keySet()) {
        	keysSortedByLength.add((String) key);
        }
        Collections.sort(keysSortedByLength, new LengthComparator());
        for (String key : keysSortedByLength) {
            Set<String> keyPreprocessed = preprocess(key);
            // Compute the intersection of two sets: keyPreprocessed and keywords
            keyPreprocessed.retainAll(keywords);
            if (keyPreprocessed.size() > maxWords) {
            	maxWords = keyPreprocessed.size();
            	bestSubtree = (JSONObject) jsonData.get(key);
            }
        }
        if (maxWords > 0) {
        	// We found the "best-matching" subtree, proceed recursively
        	// NOTE: this may be not the best solution as there may be multiple
        	//       "best-matching" subtrees anyway.
            return search(bestSubtree, keywords, depth + 1);
        } else {
	        // The search is unsuccessful, return null in this case.
	        return null;
        }
    }
    
    /**
     * Client application that takes the paths to the NHS data and the stopwords list as
     * its first two arguments, the query as its third argument and outputs to stdout
     * the resulting JSON.
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        QuestionAnswerer qa = new QuestionAnswerer(args[0], args[1]);
        System.out.print(qa.answer(args[2]));
    }
}


class LengthComparator implements java.util.Comparator<String> {

    public int compare(String s1, String s2) {
        return s1.length() - s2.length();
    }
}
