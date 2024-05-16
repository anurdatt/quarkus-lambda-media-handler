package org.acme.model;

public class MediaOutput {
    String fileName;
    String bucketName;
    String body;

    byte[] binBody;

    String contentType;
    int statusCode;

    public MediaOutput(String fileName, String bucketName) {
        this.fileName = fileName;
        this.bucketName = bucketName;
    }

    public MediaOutput() {
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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public byte[] getBinBody() {
        return binBody;
    }

    public void setBinBody(byte[] binBody) {
        this.binBody = binBody;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
}
