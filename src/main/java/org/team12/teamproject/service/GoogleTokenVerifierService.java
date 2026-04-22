package org.team12.teamproject.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class GoogleTokenVerifierService {

    private final GsonFactory gsonFactory = GsonFactory.getDefaultInstance();

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    public GoogleIdToken.Payload verify(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new RuntimeException("Google credential is required.");
        }

        if (googleClientId == null || googleClientId.isBlank()) {
            throw new RuntimeException("Google client ID is not configured.");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                gsonFactory
        )
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new RuntimeException("Google token verification failed.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new RuntimeException("Google account email is not verified.");
            }

            return payload;
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Google token verification failed.", e);
        }
    }
}
