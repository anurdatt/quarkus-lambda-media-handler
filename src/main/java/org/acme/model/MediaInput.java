package org.acme.model;

public class MediaInput {
    String fileName;
    String bucketName;

    public MediaInput(String fileName, String bucketName) {
        this.fileName = fileName;
        this.bucketName = bucketName;
    }

    public MediaInput() {
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }
}
