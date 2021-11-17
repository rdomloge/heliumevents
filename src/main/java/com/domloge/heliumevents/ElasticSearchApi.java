package com.domloge.heliumevents;

import javax.annotation.PostConstruct;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ElasticSearchApi {

    private RestTemplate restTemplate = new RestTemplate();

    @Value(value = "${ES_SERVER_ADDRESS}")
    private String elasticSearchBase;

    private String elasticSearchUrlTemplate;

    @PostConstruct
    public void config() {
        elasticSearchUrlTemplate = elasticSearchBase + "/%s/_doc/%s";
    }
    
    public void postDoc(String indexName, String docIdentifier, String docStr) {
        // Post the document to ElasticSearch
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<String>(docStr, headers);
        String url = String.format(elasticSearchUrlTemplate, indexName, docIdentifier);
        restTemplate.put(String.format(url, indexName, docIdentifier), request, String.class);
    }

    public void postDocRaw(String relativeUrl, String docStr) {
        // Post the document to ElasticSearch
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<String>(docStr, headers);
        restTemplate.put(elasticSearchBase + relativeUrl, request, String.class);
    }

    public JsonObject getDoc(String indexName, String docIdentifier) {
        ResponseEntity<String> doc = 
            restTemplate.exchange(String.format(elasticSearchUrlTemplate, indexName, docIdentifier), 
                HttpMethod.GET, 
                null, 
                String.class);
        
        if(doc.getStatusCode() == HttpStatus.OK) {
            return JsonParser.parseString(doc.getBody()).getAsJsonObject().get("_source").getAsJsonObject();
        }

        return null;
    }

    public ResponseEntity<String> getRaw(String relativeUrl) {
        try {
            return restTemplate.exchange(elasticSearchBase + relativeUrl, 
                HttpMethod.GET, 
                null, 
                String.class);
        } catch(HttpStatusCodeException e) {
            return ResponseEntity.status(e.getRawStatusCode()).headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString());
        }
    }

    public boolean exists(String indexName, String docIdentifier) {
        try {
            restTemplate.headForHeaders(String.format(elasticSearchUrlTemplate, indexName, docIdentifier));
            return true;
        }
        catch(RestClientException rex) {
            return false;
        }
    }
}
