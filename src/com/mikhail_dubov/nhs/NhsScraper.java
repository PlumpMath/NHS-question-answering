package com.mikhail_dubov.nhs;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


/**
 * NHS website scraper that produces a JSON object containing various information
 * about all the different conditions listed there.
 * 
 * @author Mikhail Dubov
 */
public class NhsScraper {
    
    private String rootUrl;
    
    /**
     * Initializes the scraper with the URL at which it should start its job.
     * 
     * @param rootUrl URL of the NHS webpage containing the list of possible conditions.
     */
    public NhsScraper(String rootUrl) {
        this.rootUrl = rootUrl;
    }
    
    /**
     * Scrapes the NHS website starting at rootUrl and returns the resulting JSON object.
     * 
     * @return JSON object containing various information about possible conditions.
     * @throws IOException If there are connection issues.
     */
    public JSONObject scrape() throws IOException {
        JSONObject res = new JSONObject();
        
        // We use the index to get as many conditions as possible
        for (char c = 'A'; c <= 'Z'; c++) {
        	// TODO: We could also use the link formatting on these pages
        	//       to infer some structure (e.g. various types of cancer
        	//       may be grouped togerther somehow).
            scrapeIndexInto(String.valueOf(c), res);
        }
        scrapeIndexInto("0-9", res);  // NOTE: the only non-alphabetical index item on NHS now
        
        return res;
    }
    
    private void scrapeIndexInto(String index, JSONObject res) throws IOException {
        Document doc = Jsoup.connect(this.rootUrl + "?Index=" + index).get();
        Elements conditions = doc.select("#ctl00_PlaceHolderMain_BodyMap_ConditionsByAlphabet a");
        for (Element condition : conditions) {
            String conditionName = condition.text().replace("\u00a0","");
            String conditionUrl = condition.attr("href");
            if (conditionUrl.toLowerCase().contains("conditions/")) {
                res.put(conditionName, this.scrapeCondition(conditionUrl));
            }
        }
    }
    
    /**
     * Scrapes the data about a single condition from the corresponding NHS webpage.
     * 
     * @param conditionPageUrl URL of the introductory webpage dedicated to a specific condition.
     * @return JSONObject containing the structured description of the condition.
     * @throws IOException If there are connection issues.
     */
    private JSONObject scrapeCondition(String conditionPageUrl) throws IOException {
        if (! conditionPageUrl.startsWith("http")) {
            conditionPageUrl = "http://" + this.getBaseUrl() + conditionPageUrl;
        }
        // Logging
        System.out.println(conditionPageUrl);
        // We may sometimes have a java.net.SocketTimeoutException here,
        // in this case we simply skip for now.
        Document doc;
        try {
            doc = Jsoup.connect(conditionPageUrl).followRedirects(true).get();
        } catch (Exception e) {
            return new JSONObject();
        }

        JSONObject res = new JSONObject();
        try {
        	res.put("Title", doc.select("h1").first().text().replace("\u00a0",""));
        } catch (NullPointerException e) {
        	res.put("Title", "");
        }
        // We don't just save the text but handle subsections, to keep our data structured.
        // NOTE: We reuse the scrapeSubSections() method both for the introductory page
        //       of each condition and for the pages describing symptoms / treatment / etc.
        res.put("Introduction", this.scrapeSubSections(conditionPageUrl));

        Elements furtherSections = doc.select("#ctl00_PlaceHolderMain_articles a");
        for (Element section : furtherSections) {
            String sectionName = section.ownText();  // use ownText() to skip hidden span tags
            res.put(sectionName, scrapeSubSections(section.attr("href")));
        }

        return res;
    }
    
    /**
     * Scrapes the data about some aspect (e.g. symptoms / causes / treatment / etc.
     * of a specific condition from the corresponding NHS webpage.
     * 
     * @param conditionPageUrl URL of the webpage dedicated to a specific condition.
     * @return JSONObject containing the structured description of the corresponding
     *         condition aspect.
     * @throws IOException If there are connection issues.
     */
    private JSONObject scrapeSubSections(String pageUrl) throws IOException {
        if (! pageUrl.startsWith("http")) {
            pageUrl = "http://" + this.getBaseUrl() + pageUrl;
        }
        // Logging
        System.out.println(pageUrl);
        Document doc;
        try {
            doc = Jsoup.connect(pageUrl).followRedirects(true).get();
        } catch (Exception e) {
            return new JSONObject();
        }

        // NOTE: The formatting is not consistent in the NHS data
        Element content = doc.select(".main-content, .article").first();
        Elements textElements = content.select("h3, p, ul");

        // Headers / texts are alternating in the HTML, so we should take care of that.
        JSONObject subSections = new JSONObject();
        subSections.put("URL", pageUrl);
        String currentSection;
        try {
        	currentSection = content.select("h2").first().text().replace("\u00a0","");
        } catch (NullPointerException e) {
        	return subSections;
        }
        StringBuilder currentText = new StringBuilder();
        for (Element textElement : textElements) {
            if (textElement.tagName().equals("h3") || textElement.tagName().equals("h2")) {
                subSections.put(currentSection, currentText.toString());
                currentSection = textElement.text();
                currentText = new StringBuilder();
            } else {
                currentText.append(textElement.text() + " ");
            }
        }
        subSections.put(currentSection, currentText.toString());
        return subSections;
    }
    
    /**
     * Returns the base URL of the NHS website based on rootUrl provided in the constructor.
     *
     * @return Base URL of the NHS website.
     * @throws MalformedURLException If the rootUrl was given incorrectly.
     */
    private String getBaseUrl() throws MalformedURLException {
        return new URL(this.rootUrl).getHost();
    }
    
    /**
     * Scraps the data from the NHS website and saves it into a JSON file.
     */
    public static void main(String[] args) throws IOException {
        NhsScraper scraper = new NhsScraper("http://www.nhs.uk/Conditions/Pages/BodyMap.aspx");
        JSONObject data = scraper.scrape();
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                                            new FileOutputStream("data/data.json"), "utf-8"));
            // NOTE: jsonviewer.stack.hu is a nice resource to visualize the result
            writer.write(data.toString());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
