package com.domloge.heliumevents;

import java.net.URI;

import javax.annotation.PostConstruct;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ElasticSearchApi {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchApi.class);

    private RestTemplate restTemplate = new RestTemplate();

    @Value(value = "${ES_SERVER_ADDRESS}")
    private String elasticSearchBase;

    private String elasticSearchUrlTemplate;

    private String esSearchUrlTemplate;

    @PostConstruct
    public void config() {
        elasticSearchUrlTemplate = elasticSearchBase + "/%s/_doc/%s";
        esSearchUrlTemplate = elasticSearchBase + "/%s/_search";
        logger.info("Using ElasticSearch at {}", elasticSearchBase);
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

    public ResponseEntity<String> getLatestDoc(String indexName) {
        //   {
        //     "query": {
        //       "match_all": {}
        //     },
        //     "size": 1,
        //     "sort": [
        //       {
        //         "time": {
        //           "order": "desc"
        //         }
        //       }
        //     ]
        //   }
        JsonObject query = new JsonObject();
        JsonObject queryDetails = new JsonObject();
        queryDetails.add("match_all", new JsonArray(0));
        query.add("query", queryDetails);
        query.addProperty("size", 1);

        JsonArray sort = new JsonArray();
        JsonObject timeSort = new JsonObject();
        timeSort.addProperty("order", "desc");
        sort.add(timeSort);
        query.add("sort", sort);
        
        String json = new Gson().toJson(query);
        return search(indexName, json);
    }

    private ResponseEntity<String> search(String indexName, String query) {
        String url = String.format(esSearchUrlTemplate, indexName);
        RequestEntity<String> req = new RequestEntity<String>(query, HttpMethod.GET, URI.create(url));
        return restTemplate.exchange(req, String.class);
    }

    public void patchDoc(String indexName, String docIdentifier, String document) {
        // Patch the document in ElasticSearch
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<String>(document, headers);
        String url = String.format(elasticSearchUrlTemplate, indexName, docIdentifier);
        restTemplate.put(String.format(url, indexName, docIdentifier), request, String.class);
    }
}
