package top.dlsloveyy.backendtest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors()   // ✅ 开启 CORS 支持
                .and()
                .csrf().disable()
                .authorizeHttpRequests()
                .requestMatchers("/**").permitAll(); // 允许所有请求访问

        return http.build();
    }
}
