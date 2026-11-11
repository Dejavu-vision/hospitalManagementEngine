package com.curamatrix.hsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.Socket;

@SpringBootApplication
@EnableScheduling
public class HsmApplication {

	public static void main(String[] args) {
		// Diagnostic: print DB config and test TCP connectivity before Spring starts
		String dbUrl = System.getenv("DB_URL");
		String datasourceUrl = System.getenv("SPRING_DATASOURCE_URL");
		String databaseUrl = System.getenv("DATABASE_URL");
		System.out.println("=== DB DIAGNOSTIC ===");
		System.out.println("DB_URL env: " + dbUrl);
		System.out.println("SPRING_DATASOURCE_URL env: " + datasourceUrl);
		System.out.println("DATABASE_URL env: " + databaseUrl);

		// Test TCP to external hostname
		String host = "dpg-dbq51ron74is739oc4m0-a.virginia-postgres.render.com";
		int port = 5432;
		try (Socket socket = new Socket()) {
			socket.connect(new java.net.InetSocketAddress(host, port), 10000);
			System.out.println("TCP test to " + host + ":" + port + " => SUCCESS");
		} catch (Exception e) {
			System.out.println("TCP test to " + host + ":" + port + " => FAILED: " + e.getMessage());
		}

		// Test TCP to internal hostname
		String internalHost = "dpg-dbq51ron74is739oc4m0-a";
		try (Socket socket = new Socket()) {
			socket.connect(new java.net.InetSocketAddress(internalHost, port), 10000);
			System.out.println("TCP test to " + internalHost + ":" + port + " => SUCCESS");
		} catch (Exception e) {
			System.out.println("TCP test to " + internalHost + ":" + port + " => FAILED: " + e.getMessage());
		}
		System.out.println("=== END DB DIAGNOSTIC ===");

		SpringApplication.run(HsmApplication.class, args);
	}

}
