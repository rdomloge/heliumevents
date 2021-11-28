package com.domloge.heliumevents;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Scanner;

import javax.annotation.PostConstruct;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StopWatch;



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

    @Value("${USER_AGENT:heliumevents}")
    private String USER_AGENT;

    @Value("${INTERVAL:500}")
    private long interval;

    @Autowired
    private ResourceLoader resourceLoader;

    private HttpClient _client = HttpClient.newHttpClient();

    private JsonObject hotspotDetails;
    
    

    @PostConstruct
    public void config() throws IOException {

        int lineNum = (int) (Math.random() * 100);
        Scanner s = new Scanner(resourceLoader.getResource("classpath:useragents.txt").getInputStream());
        for(int i=0; i < lineNum; i++) s.nextLine();
        USER_AGENT = s.nextLine();
        logger.debug("Using user agent '{}'", USER_AGENT);

        if(useHeliumApi && useStakejoyApi) throw new IllegalStateException("Can't use both APIs - choose one");
        if( ! useHeliumApi && ! useStakejoyApi) useHeliumApi = true;

        if(useStakejoyApi) 
            HS_BASE = HS_BASE_STAKEJOY;
        else
            HS_BASE = HS_BASE_HELIUM;

        logger.info("Using {} API", useHeliumApi ? "Helium" : "Stakejoy");

        HS_ACTIVITY_BASE = HS_BASE+"/v1/hotspots/%s/activity";
        HS_ACTIVITY_CURSOR = HS_ACTIVITY_BASE + "?min_time=%s&max_time=%s";//&limit=%s";
        HS_ACTIVITY_DATA = HS_ACTIVITY_BASE + "?cursor=%s";
        HS_DETAILS = HS_BASE+"/v1/hotspots/%s";

    }

    private HttpResponse<String> sendRequest(String url) throws HeliumApiException {
        StopWatch sw = new StopWatch("Helium Api"); 
        sw.start("call");
        logger.debug("Calling {}", url);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .setHeader("user-agent", USER_AGENT)
            .setHeader("cache-control", "no-cache")
            .setHeader("pragma", "no-cache")
            .setHeader("accept", "application/json")
            // .setHeader("accept-encoding", "gzip, deflate, br")
            .build();

        HttpResponse<String> resp;
        try {
            resp = _client.send(req, BodyHandlers.ofString());
            if(logger.isTraceEnabled()) {
                logger.trace("<- {}", resp.body());
            }
            sw.stop();
            if(logger.isDebugEnabled()) {
                logger.debug("Timing: Helium call took {}s", sw.getTotalTimeSeconds());
            }
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
     * @throws UnsupportedEncodingException
     * @throws IOException
     * @throws InterruptedException
     */
    JsonObject fetchHotspotActivityForDate(String hotspotAddress, DateTime date) throws HeliumApiException {
        // Build the URL
        String min_time = format.format(date.withTime(0, 0, 0, 0).toDate());
        String max_time = format.format(date.withTime(23, 59, 59, 999).toDate());
        try {
            min_time = URLEncoder.encode(min_time, "utf-8");
            max_time = URLEncoder.encode(max_time, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new HeliumApiException("Unexpected", e);
        }

        String url = String.format(HS_ACTIVITY_CURSOR, hotspotAddress, min_time, max_time);
        // Process the response and extract the cursor hash
        HttpResponse<String> resp = sendRequest(url);

        String json = resp.body();

        JsonObject jsObj = JsonParser.parseString(json).getAsJsonObject();
        if(jsObj.has("error")) {
            throw new HeliumApiException(jsObj.get("error").getAsString());
        }

        return jsObj;
    }

    public JsonObject fetchTransactions(String hotspotAddress, String cursor, String hotspotName) throws HeliumApiException {
        // Fetch the data
        String url = String.format(HS_ACTIVITY_DATA, hotspotAddress, cursor);
        HttpResponse<String> resp = sendRequest(url);

        // Process the transactions
        String json = resp.body();
        return JsonParser.parseString(json).getAsJsonObject();
    }
}