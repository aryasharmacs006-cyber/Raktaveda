package com.raktaveda.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Refuses to start in an unsafe combination of settings. */
@Component
public class StartupChecks implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(StartupChecks.class);
    private final RaktavedaProperties props;

    public StartupChecks(RaktavedaProperties props) {
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        boolean dev = props.otp().devMode();
        // Needs review before real use: "require-https=true" is how we recognise production.
        if (dev && props.security().requireHttps()) {
            throw new IllegalStateException(
                    "OTP_DEV_MODE=true is not allowed together with RAKTAVEDA_REQUIRE_HTTPS=true.");
        }
        if (dev) {
            log.warn("OTP_DEV_MODE is ON: login codes are returned in API responses. Development only.");
        }
    }
}
