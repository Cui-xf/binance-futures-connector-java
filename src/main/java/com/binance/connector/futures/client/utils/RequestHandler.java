package com.binance.connector.futures.client.utils;

import com.binance.connector.futures.client.enums.HttpMethod;
import com.binance.connector.futures.client.enums.RequestType;
import com.binance.connector.futures.client.exceptions.BinanceConnectorException;
import com.binance.connector.futures.client.utils.signaturegenerator.Ed25519SignatureGenerator;
import com.binance.connector.futures.client.utils.signaturegenerator.HmacSignatureGenerator;
import com.binance.connector.futures.client.utils.signaturegenerator.RsaSignatureGenerator;
import com.binance.connector.futures.client.utils.signaturegenerator.SignatureGenerator;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;

public class RequestHandler {
    private final String apiKey;
    private final SignatureGenerator signatureGenerator;
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private final ProxyAuth proxy;

    public RequestHandler(String apiKey, ProxyAuth proxy) {
        this(apiKey, null, proxy);
    }

    public RequestHandler(String apiKey, SignatureGenerator signatureGenerator, ProxyAuth proxy) {
        this.apiKey = apiKey;
        this.signatureGenerator = signatureGenerator;
        this.proxy = proxy;
    }

    /**
     * Build request based on request type and send the requests to server.
     *
     * @param baseUrl     base url
     * @param urlPath     url path
     * @param signature   the signature
     * @param parameters  parameters
     * @param httpMethod  https method
     * @param requestType request type
     * @return String - response from server
     */

    private String sendApiRequest(String baseUrl, String urlPath, String signature, LinkedHashMap<String, Object> parameters,
                                  HttpMethod httpMethod, RequestType requestType, boolean showLimitUsage) {
        String fullUrl = UrlBuilder.buildFullUrl(baseUrl, urlPath, parameters, signature);
        logger.info("{} {}", httpMethod, fullUrl);
        Request request;
        switch (requestType) {
            case PUBLIC:
                request = RequestBuilder.buildPublicRequest(fullUrl, httpMethod);
                break;
            case WITH_API_KEY:
            case SIGNED:
                request = RequestBuilder.buildApiKeyRequest(fullUrl, httpMethod, apiKey);
                break;
            default:
                throw new BinanceConnectorException("[RequestHandler] Invalid request type: " + requestType);
        }
        return ResponseHandler.handleResponse(request, showLimitUsage, proxy);
    }

    public String sendPublicRequest(String baseUrl, String urlPath, LinkedHashMap<String, Object> parameters,
                                    HttpMethod httpMethod, boolean showLimitUsage) {
        return sendApiRequest(baseUrl, urlPath, null, parameters, httpMethod, RequestType.PUBLIC, showLimitUsage);
    }

    public String sendWithApiKeyRequest(String baseUrl, String urlPath, LinkedHashMap<String, Object> parameters,
                                        HttpMethod httpMethod, boolean showLimitUsage) {
        if (null == apiKey || apiKey.isEmpty()) {
            throw new BinanceConnectorException("[RequestHandler] API key cannot be null or empty!");
        }
        return sendApiRequest(baseUrl, urlPath, null, parameters, httpMethod, RequestType.WITH_API_KEY, showLimitUsage);
    }

    public String sendSignedRequest(String baseUrl, String urlPath, LinkedHashMap<String, Object> parameters,
                                    HttpMethod httpMethod, boolean showLimitUsage) {
        if (signatureGenerator.getClass() == HmacSignatureGenerator.class && (null == apiKey || apiKey.isEmpty())) {
            throw new BinanceConnectorException("[RequestHandler] Secret key/API key cannot be null or empty!");
        }
        if ((signatureGenerator.getClass() == RsaSignatureGenerator.class || signatureGenerator.getClass() == Ed25519SignatureGenerator.class) && (null == apiKey || apiKey.isEmpty())) {
            throw new BinanceConnectorException("[RequestHandler] Private key/API key cannot be null or empty!");
        }
        parameters.put("timestamp", UrlBuilder.buildTimestamp());
        String queryString = UrlBuilder.joinQueryParameters(parameters);
        String signature = signatureGenerator.getSignature(queryString);
        return sendApiRequest(baseUrl, urlPath, signature, parameters, httpMethod, RequestType.SIGNED, showLimitUsage);
    }
}
