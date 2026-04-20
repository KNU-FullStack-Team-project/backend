package org.team12.teamproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// (exclude = SecurityAutoConfiguration.class)
public class TeamprojectApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamprojectApplication.class, args);
    }
}