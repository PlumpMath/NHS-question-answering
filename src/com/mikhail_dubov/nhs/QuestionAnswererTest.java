package com.mikhail_dubov.nhs;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.json.simple.parser.ParseException;

public class QuestionAnswererTest {
    
	/**
	 * This allows us to test the behaviour of our system for various queries.
	 */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        QuestionAnswerer qa = new QuestionAnswerer("data/data.json", "data/stopwords.txt");
        String[] testQueries = new String[] {
            "What are the symptoms of cancer?",
            "What are the symptoms of oesophageal cancer?",
            "treatments for headaches"
        };
        // NOTE: jsonviewer.stack.hu is a nice resource to visualize the result
        for (String query : testQueries) {
            System.out.println(qa.answer(query).toString());
            System.out.println();
        }
    }

}
