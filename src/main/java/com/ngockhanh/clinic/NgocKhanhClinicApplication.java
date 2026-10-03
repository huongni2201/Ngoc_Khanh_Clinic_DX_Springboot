package com.ngockhanh.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class NgocKhanhClinicApplication {

    static void main(String[] args) {
        SpringApplication.run(NgocKhanhClinicApplication.class, args);
    }

}
