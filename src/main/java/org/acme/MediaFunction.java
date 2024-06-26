package org.acme;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.util.StringUtil;
import org.acme.model.MediaInput;
import org.acme.model.MediaOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class MediaFunction implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    Logger logger = LoggerFactory.getLogger(MediaFunction.class);
    ObjectMapper mapper = new ObjectMapper();

    Map corsHeaders = Map.of("Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Credentials", "true",
            "Access-Control-Allow-Headers", "*",
            "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD",
            "Access-Control-Max-Age", "100000");

    Map corsHeadersMV = Map.of("Access-Control-Allow-Origin", List.of("*"),
            "Access-Control-Allow-Credentials", List.of("true"),
            "Access-Control-Allow-Headers", List.of("*"),
            "Access-Control-Allow-Methods", List.of("GET, POST, PUT, DELETE, OPTIONS, HEAD"),
            "Access-Control-Max-Age", List.of("100000"));




    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {

        Map headers =  Map.of("Content-Type", "text/plain", "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Credentials", "true",
                "Access-Control-Allow-Headers", "*",
                "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD",
                "Access-Control-Max-Age", "100000");
        Map headersMV =  Map.of("Content-Type", List.of("text/plain"), "Access-Control-Allow-Origin", List.of("*"),
                "Access-Control-Allow-Credentials", List.of("true"),
                "Access-Control-Allow-Headers", List.of("*"),
                "Access-Control-Allow-Methods", List.of("GET, POST, PUT, DELETE, OPTIONS, HEAD"),
                "Access-Control-Max-Age", List.of("100000"));

//        headers.putAll(corsHeaders);
//        headersMV.putAll(corsHeadersMV);


        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        String bucketName = requestEvent.getQueryStringParameters().get("bucketName");
        String fileName = requestEvent.getQueryStringParameters().get("fileName");
        if (bucketName == null || fileName == null) {
            logger.info("Bucket name and file name must be provided");
            responseEvent.setStatusCode(HttpStatusCode.BAD_REQUEST);
            responseEvent.setBody("Bucket name and file name must be provided");
            responseEvent.setHeaders(headers);
            responseEvent.setMultiValueHeaders(headersMV);
            responseEvent.setIsBase64Encoded(false);
            return responseEvent;
        }


        S3Client s3 = S3Client.create();
        try {
            logger.info("Received request - httpMethod = {}, \n body = {}",
                    requestEvent.getHttpMethod(), requestEvent.getBody());
            if ((StringUtil.isNullOrEmpty(requestEvent.getHttpMethod()) // For case when GET request comes from HTTP Api gateway
                    || requestEvent.getHttpMethod().equalsIgnoreCase("GET"))
                    && StringUtil.isNullOrEmpty(requestEvent.getBody())) {


                GetObjectRequest request = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build();

                logger.info("Before s3 get object");
//                InputStream inputStream = s3.getObject(request, ResponseTransformer.toInputStream());
                ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(request);

                byte[] responseBytes = response.asByteArray();

                logger.info("Received download data = " + responseBytes);

                String b64String = "";

                int chunk = 1;
                int chunkSize = responseBytes.length;
                if (!StringUtil.isNullOrEmpty(requestEvent.getHeaders().get("chunk"))) {
                    chunk = Integer.parseInt((String) requestEvent.getHeaders().get("chunk"));
                    if (!StringUtil.isNullOrEmpty(requestEvent.getHeaders().get("chunk-size"))) {
                        chunkSize = Integer.parseInt((String) requestEvent.getHeaders().get("chunk-size"));
                        b64String = Base64.getEncoder().encodeToString(Arrays.copyOfRange(responseBytes,
                                (chunk-1)*chunkSize,
                                chunk*chunkSize > responseBytes.length ? responseBytes.length : chunk*chunkSize));
                    } else {
                        return responseEvent.withBody("CHUNK-SIZE header cannot be empty when CHUNK header is set for GET requests!")
                                .withStatusCode(HttpStatusCode.BAD_REQUEST)
                                .withHeaders(headers)
                                .withMultiValueHeaders(headersMV)
                                .withIsBase64Encoded(false);
                    }
                } else {
                    b64String = Base64.getEncoder().encodeToString(responseBytes);
                }

                logger.info("Encoded download data = " + b64String);
                logger.info("Encoded download data length = " + b64String.length());

                Map headersMedia =  Map.of("Content-Type", getContentTypeFromFileName(fileName),
                        "Access-Control-Allow-Origin", "*",
                        "Access-Control-Allow-Credentials", "true",
                        "Access-Control-Allow-Headers", "*",
                        "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD",
                        "Access-Control-Max-Age", "100000");
                Map headersMediaMV =  Map.of("Content-Type", List.of(getContentTypeFromFileName(fileName)),
                        "Access-Control-Allow-Origin", List.of("*"),
                        "Access-Control-Allow-Credentials", List.of("true"),
                        "Access-Control-Allow-Headers", List.of("*"),
                        "Access-Control-Allow-Methods", List.of("GET, POST, PUT, DELETE, OPTIONS, HEAD"),
                        "Access-Control-Max-Age", List.of("100000"));

                return responseEvent.withBody(b64String)
                        .withStatusCode(HttpStatusCode.OK)
                        .withHeaders(headersMedia)
                        .withMultiValueHeaders(headersMediaMV)
                        .withIsBase64Encoded(true);

            } else if (!StringUtil.isNullOrEmpty(requestEvent.getHttpMethod())
                    && requestEvent.getHttpMethod().equalsIgnoreCase("PUT")
                    && !StringUtil.isNullOrEmpty(requestEvent.getBody())) {


                String key = fileName;
                if (!StringUtil.isNullOrEmpty(requestEvent.getHeaders().get("chunk"))) {
                    key = getChunkFileName(fileName, requestEvent.getHeaders().get("chunk"));
                }
                byte[] decodedBytes = Base64.getDecoder().decode(requestEvent.getBody()); //fileInputStream.readAllBytes());
                logger.info("Decoded upload data = " + decodedBytes);
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();

                s3.putObject(request, RequestBody.fromBytes(decodedBytes));
//                s3.putObject(request, RequestBody.fromInputStream(fileInputStream, -1));

                return responseEvent.withBody("File uploaded successfully : " + bucketName + "/" + key)
                        .withStatusCode(HttpStatusCode.OK)
                        .withHeaders(headers)
                        .withMultiValueHeaders(headersMV)
                        .withIsBase64Encoded(false);
            } else {
                return responseEvent.withBody("HttpMethod or Request body is invalid!")
                        .withStatusCode(HttpStatusCode.BAD_REQUEST)
                        .withHeaders(headers)
                        .withMultiValueHeaders(headersMV)
                        .withIsBase64Encoded(false);
            }

        } catch (Exception e) {
            logger.info("Failed to download file: " + e.getMessage());

            return responseEvent.withBody("Failed to download file: " + e.getMessage())
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withHeaders(headers)
                    .withMultiValueHeaders(headersMV)
                    .withIsBase64Encoded(false);
        } finally {
            s3.close();
        }
    }


//    @Override
//    public MediaOutput handleRequest(MediaInput mediaInput, Context context) {
//
//        MediaOutput mediaOutput = new MediaOutput();
//        String bucketName = mediaInput.getBucketName();
//        String fileName = mediaInput.getFileName();
//        if (bucketName == null || fileName == null) {
//            logger.info("Bucket name and file name must be provided");
//            mediaOutput.setBody("Bucket name and file name must be provided");
//            mediaOutput.setContentType("text/plain");
//            mediaOutput.setStatusCode(HttpStatusCode.BAD_REQUEST);
//            return mediaOutput;
//        }
//
//        S3Client s3 = S3Client.create();
//        try {
//            GetObjectRequest request = GetObjectRequest.builder()
//                    .bucket(bucketName)
//                    .key(fileName)
//                    .build();
//
//            logger.info("Before s3 get object");
////            InputStream inputStream = s3.getObject(request, ResponseTransformer.toInputStream());
//            ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(request);
//
//            byte[] responseBytes = response.asByteArray();
//
//            logger.info("Received download data = " + responseBytes);
//
//            String b64String = Base64.getEncoder().encodeToString(responseBytes);
//
//            logger.info("Encoded download data = " + b64String);
//
//            mediaOutput.setBucketName(bucketName);
//            mediaOutput.setFileName(fileName);
//            mediaOutput.setBody(b64String);
//            mediaOutput.setBinBody(responseBytes);
//            mediaOutput.setContentType(getContentTypeFromFileName(fileName));
//            mediaOutput.setStatusCode(HttpStatusCode.OK);
//
//            return mediaOutput;
//
//        } catch (Exception e) {
//            logger.info("Failed to download file: " + e.getMessage());
//
//            mediaOutput.setBody("Failed to download file: " + e.getMessage());
//            mediaOutput.setContentType("text/plain");
//            mediaOutput.setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR);
//            return mediaOutput;
//
//        } finally {
//            s3.close();
//        }
//
//    }





//    public void handleRequest(InputStream is, OutputStream outputStream, Context context) {
//
////        OutputStream outputStream = new ByteArrayOutputStream();
//        String bucketName = "lng-courses"; //requestEvent.getQueryStringParameters().get("bucketName");
//        String fileName = "unnamed.jpg"; //requestEvent.getQueryStringParameters().get("fileName");
//        if (bucketName == null || fileName == null) {
//            logger.info("Bucket name and file name must be provided");
//            try {
//                outputStream.write("Bucket name and file name must be provided".getBytes());
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            return;
//        }
//
//        S3Client s3 = S3Client.create();
//        try {
//            GetObjectRequest request = GetObjectRequest.builder()
//                    .bucket(bucketName)
//                    .key(fileName)
//                    .build();
//
//            logger.info("Before s3 get object");
////            InputStream inputStream = s3.getObject(request, ResponseTransformer.toInputStream());
//            ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(request);
//
//            byte[] responseBytes = response.asByteArray();
//
//            logger.info("Received download data = " + responseBytes);
//
//            String b64String = Base64.getEncoder().encodeToString(responseBytes);
//
//            logger.info("Encoded download data = " + b64String);
//
//                try {
////                    byte[] buffer = new byte[1024];
////                    int bytesRead;
////                    while ((bytesRead = inputStream.read(buffer)) != -1) {
//////                        logger.info("Decoded download data = " + buffer);
////                        outputStream.write(buffer, 0, bytesRead);
////                    }
////                    inputStream.close();
//
//                    outputStream.write(b64String.getBytes());
//                } catch (IOException e) {
//                    logger.info("Failed to read file from S3: " + e.getMessage());
//                    throw new RuntimeException("Failed to read file from S3: " + e.getMessage());
//                }
//
//                return;
//
////            byte[] responseBytes = response.asByteArray();
////
////            logger.info("Received download data = " + responseBytes);
////
////            String b64String = Base64.getEncoder().encodeToString(responseBytes);
////
////            logger.info("Encoded download data = " + b64String);
////            logger.info("Encoded download data length = " + b64String.length());
//
//
//        } catch (Exception e) {
//            logger.info("Failed to download file: " + e.getMessage());
//
////            return responseEvent.withBody("Failed to download file: " + e.getMessage())
////                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
////                    .withHeaders(Map.of("Content-Type", "text/plain"))
////                    .withMultiValueHeaders(Map.of("Content-Type", List.of("text/plain")))
////                    .withIsBase64Encoded(false);
//
//
//        } finally {
//            s3.close();
//        }
//
//    }

    private String getContentTypeFromFileName(String fn) {
        if(fn.toLowerCase(Locale.ROOT).endsWith(".jpg") || fn.toLowerCase(Locale.ROOT).endsWith(".jpeg")) return "image/jpeg";
        else if(fn.toLowerCase(Locale.ROOT).endsWith(".png")) return "image/png";
        else if(fn.toLowerCase(Locale.ROOT).endsWith(".mp4")) return "video/mp4";
        else return "application/octet-stream";
    }

    private String getChunkFileName(String fn, String chunk) {
        int slashIndex = fn.lastIndexOf("/");
        if(slashIndex < 0) return "TMP/".concat(fn).concat(".").concat(chunk);
        else {
            String path = fn.substring(0, slashIndex);
            String file = fn.substring(slashIndex + 1);
            return path.concat("/TMP/").concat(file).concat(".").concat(chunk);
        }
    }

}