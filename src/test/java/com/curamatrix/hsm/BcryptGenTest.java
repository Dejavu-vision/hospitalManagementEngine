package com.curamatrix.hsm;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BcryptGenTest {
	@Test
	void printPasswordHash() {
		System.out.println("BCRYPT_HASH_START:" + new BCryptPasswordEncoder().encode("fullaccess@curametix.com") + ":BCRYPT_HASH_END");
	}
}
