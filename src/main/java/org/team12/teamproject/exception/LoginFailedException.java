package org.team12.teamproject.exception;

public class LoginFailedException extends RuntimeException {

    private final boolean captchaRequired;

    public LoginFailedException(String message, boolean captchaRequired) {
        super(message);
        this.captchaRequired = captchaRequired;
    }

    public boolean isCaptchaRequired() {
        return captchaRequired;
    }
}
