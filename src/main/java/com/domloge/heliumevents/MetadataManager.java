package com.domloge.heliumevents;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MetadataManager {

    private static final String LAST_RUN_DATE = "lastRun";
    private static final String LAST_RUN_WAS_STAKEJOY = "lastRunWasStakejoy";
    
    @Autowired
    private ElasticSearchApi esApi;

    // public void setLatestDoc(String hotspotName, JsonObject doc) {
    //     String isoTime = doc.get("time").getAsString();
    //     DateTime latestDocTime = DateTime.parse(isoTime);
    //     JsonObject metadata = new JsonObject();
    //     metadata.add(LAST_RUN_DATE, new JsonPrimitive(latestDocTime.getMillis()));
    //     metadata.add(LAST_RUN_DATE+"HumanReadable", new JsonPrimitive(latestDocTime.toString()));
    //     esApi.patchDoc("metadataindex", hotspotName, metadata.toString());
    // }

    // public DateTime getLatestDocumentDateTime(String hotspotName) {
    //     JsonObject metadata = esApi.getDoc("metadataindex", hotspotName);
    //     if(null == metadata || ! metadata.has(LAST_RUN_DATE)) return null;
    //     return new DateTime(metadata.get(LAST_RUN_DATE).getAsLong());
    // }

    public void storeMetadata(String hotspotName, DateTime lastRunDate, boolean lastApiWasStakejoy) {
        JsonObject metadata = new JsonObject();
        metadata.add(LAST_RUN_DATE, new JsonPrimitive(lastRunDate.getMillis()));
        metadata.add(LAST_RUN_DATE+"HumanReadable", new JsonPrimitive(lastRunDate.toString()));
        metadata.add(LAST_RUN_WAS_STAKEJOY, new JsonPrimitive(lastApiWasStakejoy));
        esApi.postDoc("metadataindex", hotspotName, metadata.toString());
    }

    // public boolean getLastRunWasWithStakeJoy(String hotspotName) {
    //     JsonObject metadata = esApi.getDoc("metadataindex", hotspotName);
    //     if(null == metadata || ! metadata.has(LAST_RUN_WAS_STAKEJOY)) return true;
    //     return metadata.get(LAST_RUN_WAS_STAKEJOY).getAsBoolean();
    // }    
}
