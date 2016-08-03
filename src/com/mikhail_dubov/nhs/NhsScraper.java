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
 * about most popular conditions listed there.
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
        Document doc = Jsoup.connect(this.rootUrl).get();
        JSONObject res = new JSONObject();

        Elements sections = doc.select("#LinkListZone div");
        for (Element section : sections) {
            String title = section.select("h2").first().text();
            if (! title.toLowerCase().contains("condition")) {
                // This allows to skip the NHS guides section,
                // which it is particularly relevant.
                continue;
            }
            JSONObject conditions = new JSONObject();
            Elements conditionElements = section.select("ul li a");
            for (Element conditionElement : conditionElements) {
                String conditionName = conditionElement.text();
                String conditionUrl = conditionElement.attr("href");
                conditions.put(conditionName, this.scrapeCondition(conditionUrl));
            }
            res.put(title, conditions);
        }
        
        return res;
    }
    
    /**
     * Scrapes the data about a single condition from the corresponding NHS webpage.
     * 
     * @param conditionPageUrl URL of the introductory webpage dedicated to a specific condition.
     * @return JSONObject containing the structured description of the condition.
     * @throws IOException If there are connection issues.
     */
    private JSONObject scrapeCondition(String conditionPageUrl) throws IOException {
        String fullUrl = "http://" + this.getBaseUrl() + conditionPageUrl;
        // We may sometimes have a java.net.SocketTimeoutException here,
        // in this case a simple retrying helps.
        Document doc = Jsoup.connect(fullUrl).followRedirects(true).ignoreHttpErrors(true).get();

        JSONObject res = new JSONObject();
        res.put("URL", fullUrl);
        res.put("Title", doc.select("h1").first().text().replace("\u00a0",""));
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
        String fullUrl = "http://" + this.getBaseUrl() + pageUrl;
        Document doc = Jsoup.connect(fullUrl).followRedirects(true).get();

        Element content = doc.select(".main-content").first();
        Elements textElements = content.select("h3, p, ul");

        // Headers / texts are alternating in the HTML, so we should take care of that.
        JSONObject subSections = new JSONObject();
        String currentSection = content.select("h2").first().text().replace("\u00a0","");
        StringBuilder currentText = new StringBuilder();
        for (Element textElement : textElements) {
            if (textElement.tagName().equals("h3")) {
                subSections.put(currentSection, currentText.toString());
                currentSection = textElement.text();
                currentText = new StringBuilder();
            } else {
                currentText.append(textElement.text() + " ");
            }
        }
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
        NhsScraper scraper = new NhsScraper("http://www.nhs.uk/Conditions/Pages/hub.aspx");
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
