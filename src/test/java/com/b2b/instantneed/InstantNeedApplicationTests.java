package com.b2b.instantneed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.jwt.secret=aW5zdGFudC1uZWVkLXRlc3Qtb25seS1qd3Qtc2lnbmluZy1rZXktMzItYnl0ZXMtbWluaW11bQ==")
class InstantNeedApplicationTests {

    @Test
    void contextLoads() {
    }

}
