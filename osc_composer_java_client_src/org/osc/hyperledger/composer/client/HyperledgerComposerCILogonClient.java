/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.osc.hyperledger.composer.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

public class HyperledgerComposerCILogonClient {

    private static final String COMPOSER_AUTH_URL = "https://osc-beta.sdsc.edu:3400/auth/cilogon";
    private static final String GITHUB_LOGIN_URL = "https://github.com/login";
    private static final String GITHUB_LOGIN_SUBMIT_URL = "https://github.com/session";
    private static final String GITHUB_URL = "https://github.com";
    private static final String TOKEN_PREFIX = "<input type=\"hidden\" name=\"authenticity_token\" value=\"";
    private static final String TOKEN_POSTFIX = "\" />";

    public static HttpClientBuilder createTrustAllHttpClientBuilder() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, (chain, authType) -> true);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
        return HttpClients.custom().setSSLSocketFactory(sslsf);
    }

    private static String getGithubAuthenticityToken(CloseableHttpClient httpclient, String location) throws IOException {

        String authenticityToken = null;
        HttpGet httpGet = new HttpGet(location);
        CloseableHttpResponse response = httpclient.execute(httpGet);

        try {
            //System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();

            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
            String line = reader.readLine();
            while (line != null) {
                int index = line.indexOf(TOKEN_PREFIX);
                if (index != -1) {
                    line = line.substring(index + TOKEN_PREFIX.length());
                    index = line.indexOf(TOKEN_POSTFIX);
                    authenticityToken = line.substring(0, index);
                    break;
                }
                line = reader.readLine();
            }
            EntityUtils.consume(entity);
            return authenticityToken;
        } finally {
            response.close();
        }
    }

    private static int loginToGithub(CloseableHttpClient httpclient,
            String authenticityToken, String username, String password)
            throws UnsupportedEncodingException, IOException {

        HttpPost httpPost = new HttpPost(GITHUB_LOGIN_SUBMIT_URL);
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("login", username));
        nvps.add(new BasicNameValuePair("password", password));
        nvps.add(new BasicNameValuePair("commit", "Sign in"));
        nvps.add(new BasicNameValuePair("utf8", "&#x2713;"));
        nvps.add(new BasicNameValuePair("authenticity_token", authenticityToken));

        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        CloseableHttpResponse response = httpclient.execute(httpPost);

        int statusCode = -1;
        String redirectURL = null;
        try {
            statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();

            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
            String line = reader.readLine();
            while (line != null) {
                System.out.println(line);

                int index = line.indexOf("<html><body>You are being <a href=\"");
                if (index != -1) {
                    line = line.substring(35);
                    index = line.indexOf("\">redirected</a>");
                    redirectURL = line.substring(0, index);
                }
                line = reader.readLine();
            }

            EntityUtils.consume(entity);
        } finally {
            response.close();
        }

        System.out.println("===============================");
        System.out.println("redirectURL = " + redirectURL);
        HttpGet httpGet = new HttpGet(redirectURL);
        response = httpclient.execute(httpGet);
        try {
            //System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();

            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
            String line = reader.readLine();
            while (line != null) {
                System.out.println(line);
                line = reader.readLine();
            }
            EntityUtils.consume(entity);
        } finally {
            response.close();
        }

        return statusCode;
    }

    private static String getComposerCILogonAuthPage(CloseableHttpClient httpclient) throws IOException {

        String csrf = null;
        HttpGet httpGet = new HttpGet(COMPOSER_AUTH_URL);
        CloseableHttpResponse response = httpclient.execute(httpGet);

        try {
            //System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();

            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
            String line = reader.readLine();
            while (line != null) {
                //System.out.println(line);

                int index = line.indexOf("name=\"CSRF\" value=\"");
                if (index != -1) {
                    line = line.substring(index + 19);
                    index = line.indexOf("\" />");
                    csrf = line.substring(0, index);
                    System.out.println("=========> CSRF " + csrf);
                }

                line = reader.readLine();
            }
            EntityUtils.consume(entity);
        } finally {
            response.close();
        }

        return csrf;
    }

    public static String submitIdPSelection(CloseableHttpClient httpclient, String csrf) throws UnsupportedEncodingException, IOException {

        /*
        providerId: https://github.com/login/oauth/authorize
        searchlist: Github
        CSRF: af6006b54a3e1caa897f07041959e6be
        submit: Log On
        previouspage: WAYF
         */
        String location = null;

        HttpPost httpPost = new HttpPost("https://cilogon.org/authorize/");
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("providerId", "https://github.com/login/oauth/authorize"));
        nvps.add(new BasicNameValuePair("searchlist", "Github"));
        nvps.add(new BasicNameValuePair("CSRF", csrf));
        nvps.add(new BasicNameValuePair("submit", "Log On"));
        nvps.add(new BasicNameValuePair("previouspage", "WAYF"));

        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        CloseableHttpResponse response = httpclient.execute(httpPost);

        int statusCode = -1;
        try {
            System.out.println(response.getStatusLine());
            statusCode = response.getStatusLine().getStatusCode();

            System.out.println("======> " + response.containsHeader("Location"));
            Header[] headers = response.getHeaders("Location");
            System.out.println("======> location header: " + headers.length);
            System.out.println("======> location: " + headers[0].getValue());

            location = headers[0].getValue();

            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity);
        } finally {
            response.close();
        }
        return location;

    }

    public static ComposerUserIdAccessToken getAdminComposerAccessToken() throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();

        CookieStore httpCookieStore = new BasicCookieStore();

        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, (chain, authType) -> true);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);

        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultRequestConfig(globalConfig)
                .setDefaultCookieStore(httpCookieStore)
                .setSSLSocketFactory(sslsf)
                .build();

        String csrf = getComposerCILogonAuthPage(httpclient);

        String location = submitIdPSelection(httpclient, csrf);

        String authenticityToken = getGithubAuthenticityToken(httpclient, location);
        System.out.println("==================================");
        System.out.println("Github Authenticity Token = " + authenticityToken);

        int statusCode = loginToGithub(httpclient, authenticityToken, "klinucsd", "sin(pi/6)=1/2");
        System.out.println("==================================");
        System.out.println("Login to Github = " + statusCode);

        String composerAccessToken = null;
        String composerUserId = null;
        List<Cookie> cookies = httpCookieStore.getCookies();
        for (Cookie cookie : cookies) {
            //System.out.println("name=" + cookie.getName() + ", value=" + cookie.getValue());
            if (cookie.getName().equals("access_token")) {
                composerAccessToken = cookie.getValue();
            } else if (cookie.getName().equals("userId")) {
                composerUserId = cookie.getValue();
            }
        }

        System.out.println("==================================");
        System.out.println("composerAccessToken=" + composerAccessToken);
        System.out.println("composerUserId=" + composerUserId);

        if (composerAccessToken.startsWith("s%3A")) {
            composerAccessToken = composerAccessToken.substring(4);
            int index = composerAccessToken.indexOf(".");
            if (index != -1) {
                composerAccessToken = composerAccessToken.substring(0, index);
            }
        }

        if (composerUserId.startsWith("s%3A1.")) {
            composerUserId = composerUserId.substring(6);
            int index = composerUserId.indexOf("%2B");
            if (index != -1) {
                composerUserId = composerUserId.substring(0, index);
            }
        }

        return new ComposerUserIdAccessToken(composerUserId, composerAccessToken);

    }

    public static boolean getContributorByUserId(ComposerUserIdAccessToken idToken) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, (chain, authType) -> true);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();

        System.out.println("==================================");
        System.out.println("https://osc-beta.sdsc.edu:3400/api/Contributor/" + idToken.getUserId() + "?access_token=" + idToken.getAccessToken());

        HttpGet httpGet = new HttpGet("https://osc-beta.sdsc.edu:3400/api/Contributor/" + idToken.getUserId() + "?access_token=" + idToken.getAccessToken());
        CloseableHttpResponse response = httpclient.execute(httpGet);

        try {
            //System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();

            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
            String content = "";
            String line = reader.readLine();
            while (line != null) {
                content += line;
                //System.out.println(line);
                line = reader.readLine();
            }
            EntityUtils.consume(entity);

            return !content.contains("\"error\":{\"statusCode\":");
        } finally {
            response.close();
        }

    }

    public static boolean createContributorByUserId(ComposerUserIdAccessToken idToken) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, (chain, authType) -> true);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();
        HttpPost httpPost = new HttpPost("https://osc-beta.sdsc.edu:3400/api/Contributor?access_token=" + idToken.getAccessToken());

        String JSON_STRING = "{\"$class\": \"org.osc.Contributor\", \"cid\": \""
                + idToken.getUserId() + "\", \"contact\": { \"$class\": \"org.osc.ContactInformation\", \"firstName\": \"First Name\",\"lastName\": \"Last Name\", \"email\": \"unknown@email.com\"}}";
        HttpEntity stringEntity = new StringEntity(JSON_STRING, ContentType.APPLICATION_JSON);
        httpPost.setEntity(stringEntity);

        CloseableHttpResponse response = httpclient.execute(httpPost);
        try {
            //System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();
            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
            String content = "";
            String line = reader.readLine();
            while (line != null) {
                content += line;
                //System.out.println(line);
                line = reader.readLine();
            }
            EntityUtils.consume(entity);
            return !content.contains("\"error\":{\"statusCode\":");
        } finally {
            response.close();
        }

    }

    public static ComposerCard[] getComposerWallet(ComposerUserIdAccessToken idToken) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, (chain, authType) -> true);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();

        System.out.println("==================================");
        System.out.println("https://osc-beta.sdsc.edu:3400/api/wallet?access_token=" + idToken.getAccessToken());

        HttpGet httpGet = new HttpGet("https://osc-beta.sdsc.edu:3400/api/wallet?access_token=" + idToken.getAccessToken());
        CloseableHttpResponse response = httpclient.execute(httpGet);

        try {
            //System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();

            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
            String content = "";
            String line = reader.readLine();
            while (line != null) {
                content += line;
                System.out.println(line);
                line = reader.readLine();
            }
            EntityUtils.consume(entity);

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(content, ComposerCard[].class);

        } finally {
            response.close();
        }
    }

    public static void uploadCard(ComposerUserIdAccessToken idToken, File file) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        SSLContextBuilder builder1 = new SSLContextBuilder();
        builder1.loadTrustMaterial(null, (chain, authType) -> true);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder1.build(), NoopHostnameVerifier.INSTANCE);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();

        HttpPost httpPost = new HttpPost("https://osc-beta.sdsc.edu:3400/api/wallet/import?access_token=" + idToken.getAccessToken());

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addBinaryBody("card", file, ContentType.DEFAULT_BINARY, file.getName());
        HttpEntity reqEntity = builder.build();
        httpPost.setEntity(reqEntity);

        CloseableHttpResponse response = httpclient.execute(httpPost);
        try {
            System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity);

            file.delete();
        } finally {
            response.close();
        }

    }

    public static void issueCard(ComposerUserIdAccessToken idToken, File file) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, (chain, authType) -> true);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();
        HttpPost httpPost = new HttpPost("https://osc-beta.sdsc.edu:3400/api/system/identities/issue?access_token=" + idToken.getAccessToken());

        String JSON_STRING = "{\"participant\": \"org.osc.Contributor#" + idToken.getUserId() + "\", \"userID\": \"" + idToken.getUserId() + "\", \"options\": {}}";
        HttpEntity stringEntity = new StringEntity(JSON_STRING, ContentType.APPLICATION_JSON);
        httpPost.setEntity(stringEntity);

        CloseableHttpResponse response = httpclient.execute(httpPost);
        try {
            System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();

            BufferedInputStream input = new BufferedInputStream(entity.getContent());
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file));

            int inByte;
            while ((inByte = input.read()) != -1) {
                output.write(inByte);
            }
            input.close();
            output.close();

            EntityUtils.consume(entity);
        } finally {
            response.close();
        }

    }

}
