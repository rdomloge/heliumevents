package com.domloge.heliumevents;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HeliumApiTest {

    @Autowired
    private HeliumApi target;

   

    @Test
    public void crossMidnight() throws HeliumApiException {

        String hotspotAddress = "112Xa4p36ExdVDktAPFKx9zd8EwiMw7vC35hxyqiiYANC527BLiF";

        // call before midnight
        DateTime dateCursor = new DateTime().plusDays(-1).withTime(23, 56, 0, 0);
        JsonObject response = target.fetchHotspotActivityForDate(hotspotAddress, dateCursor);
        
        assertTrue(response.has("data"));
        assertTrue(response.has("cursor"));


        // call after midnight
        dateCursor = new DateTime().withTime(00, 02, 0, 0);
        response = target.fetchHotspotActivityForDate(hotspotAddress, dateCursor);
        assertTrue(response.has("data"));
        assertTrue(response.has("cursor"));
    }
    
}
