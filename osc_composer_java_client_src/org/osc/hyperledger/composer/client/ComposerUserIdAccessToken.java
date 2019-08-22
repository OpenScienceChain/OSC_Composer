
package org.osc.hyperledger.composer.client;

public class ComposerUserIdAccessToken {
    
    private String userId;
    private String accessToken;

    public ComposerUserIdAccessToken(String userId, String accessToken) {
        this.userId = userId;
        this.accessToken = accessToken;
    }
  
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public String toString() {
        return "ComposerUserIdAccessToken{" + "userId=" + userId + ", accessToken=" + accessToken + '}';
    }
    
}
