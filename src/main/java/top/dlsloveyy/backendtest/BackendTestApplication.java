package top.dlsloveyy.backendtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableScheduling
@MapperScan("top.dlsloveyy.backendtest.mapper")
public class BackendTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendTestApplication.class, args);
    }
}
