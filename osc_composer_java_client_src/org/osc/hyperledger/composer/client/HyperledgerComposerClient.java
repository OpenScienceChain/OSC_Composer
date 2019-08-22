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
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

public class HyperledgerComposerClient {

    private static final String GITHUB_LOGIN_URL = "https://github.com/login";
    private static final String GITHUB_LOGIN_SUBMIT_URL = "https://github.com/session";
    private static final String GITHUB_URL = "https://github.com";
    private static final String COMPOSER_AUTH_URL = "http://osc-beta.sdsc.edu:3300/auth/github";

    private static final String TOKEN_PREFIX = "<input type=\"hidden\" name=\"authenticity_token\" value=\"";
    private static final String TOKEN_POSTFIX = "\" />";

    public static ComposerUserIdAccessToken adminIdToken = null;

    static {
        try {
            adminIdToken = getAdminComposerAccessToken();
        } catch (Exception ex) {}
    }

    private static String getGithubAuthenticityToken(CloseableHttpClient httpclient) throws IOException {

        String authenticityToken = null;
        HttpGet httpGet = new HttpGet(GITHUB_LOGIN_URL);
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
        try {
            statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity);
        } finally {
            response.close();
        }
        return statusCode;
    }

    private static void getLogggedPage(CloseableHttpClient httpclient) throws IOException {

        HttpGet httpGet = new HttpGet(GITHUB_URL);
        CloseableHttpResponse response = httpclient.execute(httpGet);

        try {
            System.out.println(response.getStatusLine());
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
    }

    private static void getComposerGithubAuthPage(CloseableHttpClient httpclient) throws IOException {

        String authenticityToken = null;
        HttpGet httpGet = new HttpGet(COMPOSER_AUTH_URL);
        CloseableHttpResponse response = httpclient.execute(httpGet);

        try {
            //System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();

            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
            String line = reader.readLine();
            while (line != null) {
                //System.out.println(line);
                line = reader.readLine();
            }
            EntityUtils.consume(entity);
        } finally {
            response.close();
        }

    }

    public static ComposerUserIdAccessToken getAdminComposerAccessToken() throws IOException {

        RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();

        CookieStore httpCookieStore = new BasicCookieStore();

        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultRequestConfig(globalConfig)
                .setDefaultCookieStore(httpCookieStore)
                .build();

        String authenticityToken = getGithubAuthenticityToken(httpclient);
        //System.out.println("==================================");
        //System.out.println("authenticityToken = " + authenticityToken);

        int statusCode = loginToGithub(httpclient, authenticityToken, "klinucsd", "sin(pi/6)=1/2");
        //System.out.println("==================================");
        //System.out.println("status code = " + statusCode);

        if (statusCode == 302) {
            //System.out.println("==================================");
            //System.out.println("logged in = yes");
            //getLogggedPage(httpclient);

            getComposerGithubAuthPage(httpclient);
            //System.out.println("==================================");
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

            /*
            System.out.println("==================================");
            System.out.println("composerAccessToken=" + composerAccessToken);
            System.out.println("composerUserId=" + composerUserId);            
             */
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
        } else {
            return null;
        }
    }

    public static boolean getContributorByUserId(ComposerUserIdAccessToken idToken) throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();

        System.out.println("==================================");
        System.out.println("http://osc-beta.sdsc.edu:3300/api/Contributor/" + idToken.getUserId() + "?access_token=" + idToken.getAccessToken());

        HttpGet httpGet = new HttpGet("http://osc-beta.sdsc.edu:3300/api/Contributor/" + idToken.getUserId() + "?access_token=" + idToken.getAccessToken());
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

    public static boolean createContributorByUserId(ComposerUserIdAccessToken idToken) throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://osc-beta.sdsc.edu:3300/api/Contributor?access_token=" + idToken.getAccessToken());

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

    public static ComposerCard[] getComposerWallet(ComposerUserIdAccessToken idToken) throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();

        System.out.println("==================================");
        System.out.println("http://osc-beta.sdsc.edu:3300/api/wallet?access_token=" + idToken.getAccessToken());

        HttpGet httpGet = new HttpGet("http://osc-beta.sdsc.edu:3300/api/wallet?access_token=" + idToken.getAccessToken());
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

    public static void uploadCard(ComposerUserIdAccessToken idToken, File file) throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://osc-beta.sdsc.edu:3300/api/wallet/import?access_token=" + idToken.getAccessToken());

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

    public static void issueCard(ComposerUserIdAccessToken idToken, File file) throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://osc-beta.sdsc.edu:3300/api/system/identities/issue?access_token=" + idToken.getAccessToken());

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
