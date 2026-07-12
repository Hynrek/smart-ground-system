package ch.jp.shooting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartGroundApplication {
  public static void main(String[] args) {
    SpringApplication.run(SmartGroundApplication.class, args);
  }
}
