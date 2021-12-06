package com.domloge.heliumevents;

import java.math.BigDecimal;

import javax.annotation.PostConstruct;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class Trawler {

    private static final Logger logger = LoggerFactory.getLogger(Trawler.class);

    @Autowired
    private HeliumApi heliumApi;

    @Autowired
    private ElasticSearchApi esApi;

    @Value("${HOTSPOT}")
    private String hotspot;

    private String hotspotName;

    @Value("${TEST:false}")
    private boolean testMode;

    @PostConstruct
    void prepAndRun() {
        if(testMode) return;
        try {
            hotspotName = heliumApi.getHotspotName(hotspot);
        } 
        catch (HeliumApiException hex) {
            handleError(hex);
            return;
        }
        
        // check that the metadata index exists
        ResponseEntity<String> metadataJson = esApi.getRaw("/metadataindex/_doc/"+hotspotName);
        if(metadataJson.getStatusCode() != HttpStatus.OK) {
            logger.info("Setting up metadata index");
            esApi.postDoc("metadataindex", hotspotName, "{ \"position\": 0 }");
        }

        // create mapping for timestamp in documents
        ResponseEntity<String> mappingJson = esApi.getRaw("/"+hotspotName+"/_mapping");
        if(mappingJson.getStatusCode() != HttpStatus.OK) {
            JsonObject payload = new JsonObject();
            JsonObject mappings = new JsonObject();
            payload.add("mappings", mappings);
            JsonObject properties = new JsonObject();
            mappings.add("properties", properties);

            JsonObject timeMapping = new JsonObject();
            timeMapping.addProperty("type", "date");
            properties.add("time", timeMapping);

            JsonObject challengeeLocationMapping = new JsonObject();
            challengeeLocationMapping.addProperty("type", "geo_point");
            properties.add("path.challengee_location", challengeeLocationMapping);

            logger.info("Setting up mappings for time (date field) and path.challengee_location (geo_point)");
            String json = new Gson().toJson(payload);
            esApi.postDocRaw("/" + hotspotName, json);
        }
        
        trawl();
    }


    public void trawl() {
        try {
            DateTime hsBday = heliumApi.getHotspotBirithday(hotspot);
            DateTime latestTrawlDateTime = getLatestSuccessfulTrawlCompleteDay(hotspotName);
            if(null == latestTrawlDateTime) latestTrawlDateTime = hsBday;

            boolean lastRunWasWithStakejoy = getLastRunWasWithStakeJoy(hotspotName);
            logger.info("Using {} API", lastRunWasWithStakejoy ? "Helium" : "Stakejoy");
            
            logger.info("Synching from {} for hotspot {}, born on {}", 
                latestTrawlDateTime.toString("dd-MMM-yyyy hh:mm"), 
                hotspotName, 
                hsBday.toString("dd-MMM-yyyy' 'hh:mm"));

            while(latestTrawlDateTime.isBefore(new DateTime())) {
                logger.debug("Fetching events since {}", latestTrawlDateTime.toString("dd-MMM-yyyy"));
                Stats stats = new Stats();
                int transactionCount = 0;
                JsonObject response = heliumApi.fetchHotspotActivityForDate(hotspot, latestTrawlDateTime);
                if(response.has("data")) {
                    transactionCount += processData(response.getAsJsonArray("data"), stats);
                }

                while(response.has("cursor")) {
                    response = heliumApi.fetchTransactions(
                        hotspot, 
                        response.get("cursor").getAsString(),
                        hotspotName);
                    transactionCount += processData((JsonArray) response.get("data"), stats);
                }
                
                logger.info("{} processed, fetched {} transactions: {} new, {} already known", 
                    latestTrawlDateTime.toString("dd-MMM-yyyy hh:mm"), 
                    transactionCount,
                    stats.getNewDocs(),
                    stats.getDuplicateDocs());

                storeMetadata(hotspotName, dateCursor);
                dateCursor = dateCursor.plusDays(1);
            }
            logger.info("Synch complete");
        }
        catch(HeliumApiException hex) {
            handleError(hex);
        }
    }

    private void handleError(HeliumApiException hex) {
        logger.info("**************************");
        String message = hex.getCause() != null ? hex.getCause().getMessage() : hex.getMessage();
        logger.info("Critical error calling Helium API: {}", message);
        if(logger.isDebugEnabled()) {
            logger.error("Error details:\n", hex);
        }
    }

    private int processData(JsonArray transactions, Stats stats) {
        for(int i=0; i < transactions.size(); i++) {
            JsonObject doc = transactions.get(i).getAsJsonObject();
            patch(doc);

            String identifier = doc.get("hash").getAsString();
            if( ! esApi.exists(hotspotName, identifier)) {
                String docStr = new Gson().toJson(doc);
                logger.trace(docStr);
                esApi.postDoc(hotspotName, identifier, docStr);
                stats.incrementNewDocs();
            }
            else {
                stats.incrementDuplicateDocs();
            }
        }
        return transactions.size();
    }

    private static final String LAST_RUN_DATE = "lastRun";
    private static final String LAST_RUN_WAS_STAKEJOY = "lastRunWasStakejoy";

    private void storeMetadata(String hotspotName, DateTime lastRunDate, boolean lastApiWasStakejoy) {
        JsonObject metadata = new JsonObject();
        metadata.add(LAST_RUN_DATE, new JsonPrimitive(lastRunDate.getMillis()));
        metadata.add(LAST_RUN_DATE+"HumanReadable", new JsonPrimitive(lastRunDate.toString()));
        metadata.add(LAST_RUN_WAS_STAKEJOY, new JsonPrimitive(lastApiWasStakejoy));
        esApi.postDoc("metadataindex", hotspotName, metadata.toString());
    }

    private DateTime getLatestSuccessfulTrawlCompleteDay(String hotspotName) {
        JsonObject metadata = esApi.getDoc("metadataindex", hotspotName);
        if(null == metadata || ! metadata.has(LAST_RUN_DATE)) return null;
        return new DateTime(metadata.get(LAST_RUN_DATE).getAsLong());
    }

    private boolean getLastRunWasWithStakeJoy(String hotspotName) {
        JsonObject metadata = esApi.getDoc("metadataindex", hotspotName);
        if(null == metadata || ! metadata.has(LAST_RUN_WAS_STAKEJOY)) return true;
        return metadata.get(LAST_RUN_WAS_STAKEJOY).getAsBoolean();
    }

    private void patch(JsonObject heliumDoc) {
        // add zeros to the time to make it epoc millis instead of seconds
        long timeSeconds = heliumDoc.get("time").getAsLong();
        DateTime epoch = new DateTime(timeSeconds*1000);
        String esFormatTimestamp = epoch.toString("YYYY-MM-dd'T'HH:mm:ssZ");
        heliumDoc.add("time", new JsonPrimitive(esFormatTimestamp));

        if(heliumDoc.get("type").getAsString().equals("poc_receipts_v1")) patchChallengeeLocation(heliumDoc);
        if(heliumDoc.get("type").getAsString().equals("rewards_v2")) patchHNT(heliumDoc);
    }

    private void patchHNT(JsonObject heliumDoc) {
        JsonArray rewards = heliumDoc.get("rewards").getAsJsonArray();
        long totalBones = 0;
        for(int i=0; i < rewards.size(); i++) {
            totalBones += rewards.get(i).getAsJsonObject().get("amount").getAsLong();
        }
        heliumDoc.addProperty("totalBones", totalBones);
        heliumDoc.addProperty("totalHnt", new BigDecimal(totalBones).divide(new BigDecimal(100000000)));
    }

    private void patchChallengeeLocation(JsonObject heliumDoc) {
        JsonObject path = heliumDoc.get("path").getAsJsonArray().get(0).getAsJsonObject();
        float lon = -1; 
        float lat = -1;
        if(path.has("challengee_lon")) {
            lon = path.get("challengee_lon").getAsFloat();
        }
        if(path.has("challengee_lat")) {
            lat = path.get("challengee_lat").getAsFloat();
        }
        path.add("challengee_location", new JsonPrimitive(lat+","+lon));
    }

}
