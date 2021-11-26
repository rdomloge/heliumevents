package com.domloge.heliumevents;

public class Stats {

    private int newDocs;

    private int duplicateDocs;

    public int getNewDocs() {
        return newDocs;
    }

    public void incrementNewDocs() {
        newDocs++;
    }

    public int getDuplicateDocs() {
        return duplicateDocs;
    }

    public void incrementDuplicateDocs() {
        duplicateDocs++;
    }
    
}
