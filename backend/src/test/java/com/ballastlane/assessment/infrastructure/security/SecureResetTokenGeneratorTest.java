package com.ballastlane.assessment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureResetTokenGeneratorTest {

    private final SecureResetTokenGenerator generator = new SecureResetTokenGenerator();

    @Test
    void tokensAreUrlSafeBase64Of32BytesAndDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = generator.generate();
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            seen.add(token);
        }
        assertThat(seen).hasSize(100);
    }

    @Test
    void hashIsTheSha256HexOfTheToken() {
        // sha256("abc"), the standard test vector
        assertThat(generator.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashIsDeterministicAndDoesNotContainTheToken() {
        String token = generator.generate();

        assertThat(generator.hash(token)).isEqualTo(generator.hash(token)).hasSize(64).doesNotContain(token);
    }
}
