package com.domloge.heliumevents;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.annotation.PostConstruct;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;



@Component
public class HeliumApi {

    private static final Logger logger = LoggerFactory.getLogger(HeliumApi.class);

    private static final String HS_BASE_STAKEJOY = "https://helium-api.stakejoy.com";
    private static final String HS_BASE_HELIUM = "https://api.helium.io";

    @Value("${USE_HELIUM_API:false}")
    private boolean useHeliumApi;
    @Value("${USE_STAKEJOY_API:false}")
    private boolean useStakejoyApi;

    private String HS_BASE;

    private String HS_ACTIVITY_BASE;
    private String HS_ACTIVITY_CURSOR;
    private String HS_ACTIVITY_DATA;
    private String HS_DETAILS;

    private static final DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //2021-05-11T01:39:53Z

    @Value("${USER_AGENT:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36}")
    private String USER_AGENT;

    @Value("${INTERVAL:500}")
    private long interval;

    private HttpClient _client = HttpClient.newHttpClient();

    private JsonObject hotspotDetails;
    
    private HttpResponse<String> sendRequest(String url) throws HeliumApiException {

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .setHeader("user-agent", USER_AGENT)
            .build();

        HttpResponse<String> resp;
        try {
            resp = _client.send(req, BodyHandlers.ofString());
        } 
        catch (IOException | InterruptedException e) {
            throw new HeliumApiException("Failed to call Helium API", e);
        }

        if(resp.statusCode() != 200) {
            throw new HeliumApiException("Got response code "+resp.statusCode()+ " for URL "+url+ " and response "+resp.body());
        }
        
        try {
            Thread.sleep(interval);
        } 
        catch (InterruptedException e) {
            throw new HeliumApiException("Interrupted");
        }

        return resp;
    }

    @PostConstruct
    public void confid() {
        if(useHeliumApi && useStakejoyApi) throw new IllegalStateException("Can't use both APIs - choose one");
        if( ! useHeliumApi && ! useStakejoyApi) useHeliumApi = true;

        if(useStakejoyApi) 
            HS_BASE = HS_BASE_STAKEJOY;
        else
            HS_BASE = HS_BASE_HELIUM;

        logger.info("Using {} API", useHeliumApi ? "Helium" : "Stakejoy");

        HS_ACTIVITY_BASE = HS_BASE+"/v1/hotspots/%s/activity";
        HS_ACTIVITY_CURSOR = HS_ACTIVITY_BASE + "?limit=%s&filter_types=%s&min_time=%s&max_time=%s";
        HS_ACTIVITY_DATA = HS_ACTIVITY_BASE + "?cursor=%s";
        HS_DETAILS = HS_BASE+"/v1/hotspots/%s";

    }


    public String getHotspotName(String hotspotAddress) throws HeliumApiException {
        if(null == hotspotDetails) initHotspotDetails(hotspotAddress);
        return hotspotDetails.get("data").getAsJsonObject().get("name").getAsString();
    }

    public DateTime getHotspotBirithday(String hotspotAddress) throws HeliumApiException {
        if(null == hotspotDetails) initHotspotDetails(hotspotAddress);
        String timestamp = hotspotDetails.get("data").getAsJsonObject().get("timestamp_added").getAsString();
        return DateTime.parse(timestamp); // "2021-09-20T11:22:46.000000Z"
    }

    private void initHotspotDetails(String hotspotAddress) throws HeliumApiException {
        String url = String.format(HS_DETAILS, hotspotAddress);
        String json = sendRequest(url).body();
        hotspotDetails = JsonParser.parseString(json).getAsJsonObject();
    }

    /**
     * Setup a cursor in the API to pull data from in subsequent methods. All the params are specified here - we
     * pull data back in the other methods
     * @param hotspotAddress
     * @param date
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    JsonObject fetchHotspotActivityForDate(String hotspotAddress, DateTime date) throws HeliumApiException {
        // Build the URL
        String min_time = format.format(date.withTime(0, 0, 0, 0).toDate());
        String max_time = format.format(date.withTime(23, 59, 59, 999).toDate());
        String filter_types = "";//"poc_receipts_v1";
        int limit = 99;
        String url = String.format(HS_ACTIVITY_CURSOR, hotspotAddress, limit, filter_types, min_time, max_time);

        // Process the response and extract the cursor hash
        HttpResponse<String> resp = sendRequest(url);

        String json = resp.body();
        JsonObject jsObj = JsonParser.parseString(json).getAsJsonObject();
        if(jsObj.has("error")) {
            throw new HeliumApiException(jsObj.get("error").getAsString());
        }

        return jsObj;
    }

    public JsonArray fetchTransactions(String hotspotAddress, String cursor, String hotspotName) throws HeliumApiException {
        // Fetch the data
        String url = String.format(HS_ACTIVITY_DATA, hotspotAddress, cursor);
        HttpResponse<String> resp = sendRequest(url);

        // Process the transactions
        String json = resp.body();
        JsonObject jsObj = JsonParser.parseString(json).getAsJsonObject();
        return (JsonArray) jsObj.get("data");
    }
}