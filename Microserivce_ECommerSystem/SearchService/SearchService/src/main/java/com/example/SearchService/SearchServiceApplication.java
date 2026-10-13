package com.example.SearchService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling   // cho ProductIndexSyncService.scheduledSync
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
