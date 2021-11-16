package com.domloge.heliumevents;

import java.io.IOException;

import javax.annotation.PostConstruct;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class Trawler {

    private static final Logger logger = LoggerFactory.getLogger(Trawler.class);

    @Autowired
    private HeliumApi heliumApi;

    @Autowired
    private ElasticSearchApi esApi;

    @Value("${HOTSPOT:112Xa4p36ExdVDktAPFKx9zd8EwiMw7vC35hxyqiiYANC527BLiF}")
    private String hotspot;

    private String hotspotName;

    @PostConstruct
    void prepAndRun() throws IOException, InterruptedException {
        
        hotspotName = heliumApi.getHotspotName(hotspot);
        
        // check that the metadata index exists
        ResponseEntity<String> metadataJson = esApi.getRaw("/metadataindex/_doc/metadata");
        if(metadataJson.getStatusCode() != HttpStatus.OK) {
            logger.info("Setting up metadata index");
            esApi.postDoc("metadataindex", "metadata", "{ \"position\": 0 }");
        }

        // create mapping for timestamp in documents
        ResponseEntity<String> mappingJson = esApi.getRaw("/"+hotspotName+"/_mapping");
        if(mappingJson.getStatusCode() != HttpStatus.OK) {
            logger.info("Setting up 'time' mapping as a date field");
            esApi.postDocRaw("/" + hotspotName, "{ \"mappings\": { \"properties\": { \"time\": { \"type\": \"date\" } } } }");
        }
        
        trawl();
    }


    public void trawl() throws IOException, InterruptedException {
        DateTime hsBday = heliumApi.getHotspotBirithday(hotspot);
        DateTime latestTrawlCompleteDay = getLatestSuccessfulTrawlCompleteDay();
        if(null == latestTrawlCompleteDay) latestTrawlCompleteDay = hsBday;
        latestTrawlCompleteDay = latestTrawlCompleteDay.plusDays(-1); // go back a day  to make sure we get a full day
        
        
        logger.info("Synching from {} for hotspot {}, born on {}", latestTrawlCompleteDay, hotspotName, hsBday);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        DateTime dateCursor = latestTrawlCompleteDay;
        
        while(dateCursor.isBefore(new DateTime())) {
            logger.info("Fetching events for {}", dateCursor.toString());
            String cursor = heliumApi.fetchHotspotActivityCursorForDate(hotspot, dateCursor);
            if(null != cursor) {

                JsonArray transactions = heliumApi.fetchTransactions(
                    hotspot, 
                    cursor,
                    hotspotName);

                for(int i=0; i < transactions.size(); i++) {
                    JsonObject doc = transactions.get(i).getAsJsonObject();
                    patch(doc);
        
                    String identifier = doc.get("hash").getAsString();
                    String docStr = new Gson().toJson(doc);
                    logger.trace(docStr);
                    
                    esApi.postDoc(hotspotName, identifier, docStr);
                }
                storeMetadata(dateCursor);
                logger.info("Added {} docs for date", transactions.size());
            }
            else {
                logger.info("No transactions on {}", dateCursor.toString());
            }
            
            dateCursor = dateCursor.plusDays(1);
        }
        logger.info("Synch complete");
    }

    private static final String LAST_RUN_DATE = "lastRun";

    private void storeMetadata(DateTime lastRunDate) {
        JsonObject metadata = new JsonObject();
        metadata.add(LAST_RUN_DATE, new JsonPrimitive(lastRunDate.getMillis()));
        metadata.add(LAST_RUN_DATE+"HumanReadable", new JsonPrimitive(lastRunDate.toString()));
        esApi.postDoc("metadataindex", "metadata", metadata.toString());
    }

    private DateTime getLatestSuccessfulTrawlCompleteDay() {
        JsonObject metadata = esApi.getDoc("metadataindex", "metadata");
        if(null == metadata || ! metadata.has(LAST_RUN_DATE)) return null;
        return new DateTime(metadata.get(LAST_RUN_DATE).getAsLong());
    }

    private void patch(JsonObject heliumDoc) {
        // add zeros to the time to make it epoc millis instead of seconds
        long timeSeconds = heliumDoc.get("time").getAsLong();
        DateTime epoch = new DateTime(timeSeconds*1000);
        String esFormatTimestamp = epoch.toString("YYYY-MM-dd'T'HH:mm:ssZ");
        heliumDoc.add("time", new JsonPrimitive(esFormatTimestamp));
    }
}
