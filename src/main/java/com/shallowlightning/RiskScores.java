package com.shallowlightning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class RiskScores extends ESHit{
    private String entityHash;
    private String entityType;
    private String entityName;
    private double score;
    private long timestamp;
    private boolean isAnomalous = true;
    public RiskScores(){}
    public RiskScores(RiskyEntity e) {
        this.entityHash = e.getEntityHash();
        this.entityType = e.getEntityType();
        this.entityName = e.getEntityName();
        this.score = e.getCurrentScore();
        this.timestamp = e.getActiveTimeEnd();
        this.isAnomalous = e.hasAnomalies();
    }
    public String getEntityHash() {
        return entityHash;
    }
    public void setEntityHash(String entityHash) {
        this.entityHash = entityHash;
    }

    @Override
    public String toString() {
        return "RiskScores{" +
                "entityHash='" + entityHash + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityName='" + entityName + '\'' +
                ", score=" + score +
                ", timestamp=" + timestamp +
                ", isAnomalous=" + isAnomalous +
                '}';
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isAnomalous() {
        return isAnomalous;
    }

    public void setAnomalous(boolean anomalous) {
        isAnomalous = anomalous;
    }

}
