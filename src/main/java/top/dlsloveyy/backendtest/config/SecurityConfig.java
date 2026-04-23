package top.dlsloveyy.backendtest.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/ws/**",
                                "/ws/info/**",
                                "/api/user/login",
                                "/api/user/register",
                                "/api/auth/refresh",
                                "/api/goods/list",
                                "/api/goods/hot",
                                "/api/goods/detail/**",
                                "/api/sysNotice/list",
                                "/api/user/public-info/**",
                                "/api/user/public-goods/**",
                                "/api/user/public-followees/**",
                                "/api/review/seller/**",
                                "/api/comment/flat",
                                "/api/comment/list",
                                "/api/comment/tree",
                                "/api/upload/**",
                                "/uploads/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
