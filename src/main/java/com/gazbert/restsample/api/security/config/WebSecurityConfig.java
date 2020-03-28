package com.gazbert.restsample.api.security.config;

import com.gazbert.restsample.api.security.authentication.JwtAuthenticationEntryPoint;
import com.gazbert.restsample.api.security.authentication.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

/**
 * Encapsulates the Spring web security config for the app.
 *
 * <p>Code originated from the excellent JWT and Spring Boot example by Stephan Zerhusen:
 * https://github.com/szerhusenBC/jwt-spring-security-demo
 *
 * @author gazbert
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final UserDetailsService userDetailsService;

  @Autowired
  public WebSecurityConfig(
      CorsFilter corsFilter,
      JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
      UserDetailsService userDetailsService) {
    this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    this.userDetailsService = userDetailsService;
  }

  /**
   * Must be done to work with Spring Boot 2.0.
   * https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.0-Migration-Guide#authenticationmanager-bean
   *
   * @return the authentication manager bean.
   * @throws Exception id anything unexpected happens.
   */
  @Override
  @Bean
  public AuthenticationManager authenticationManagerBean() throws Exception {
    return super.authenticationManagerBean();
  }

  /**
   * Creates an Authentication Manager implementation for authenticating users using Bcrypt
   * passwords.
   *
   * @param authenticationManagerBuilder the Authentication Manager.
   * @throws Exception if anything breaks building the Authentication Manager.
   */
  @Autowired
  public void configureAuthentication(AuthenticationManagerBuilder authenticationManagerBuilder)
      throws Exception {
    authenticationManagerBuilder
        .userDetailsService(this.userDetailsService)
        .passwordEncoder(bcryptPasswordEncoder());
  }

  /*
   * Use bcrypt password encoding.
   * https://docs.spring.io/spring-security/site/docs/5.0.5.RELEASE/reference/htmlsingle/#pe-bcpe
   */
  @Bean
  public BCryptPasswordEncoder bcryptPasswordEncoder() {
    return new BCryptPasswordEncoder(12); // tuned to 1 sec; default is 10 rounds.
  }

  @Bean
  public JwtAuthenticationFilter authenticationTokenFilterBean() {
    return new JwtAuthenticationFilter();
  }

  @Override
  protected void configure(HttpSecurity httpSecurity) throws Exception {
    httpSecurity
        // Default behaviour is to enable CSRF protection.
        // We need to override this behaviour for our stateless (no cookies used!) REST endpoints.
        // https://security.stackexchange.com/questions/166724/should-i-use-csrf-protection-on-rest-api-endpoints
        // https://stackoverflow.com/questions/27390407/post-request-to-spring-server-returns-403-forbidden
        .csrf()
        .disable()
        .exceptionHandling()
        .authenticationEntryPoint(jwtAuthenticationEntryPoint)

        // No need to create session as JWT auth is stateless / per request
        .and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

        // Allow anyone to try and authenticate to get a token
        .and()
        .authorizeRequests()
        .antMatchers("/api/token")
        .permitAll()

        // Allow CORS pre-flighting for everything
        .antMatchers(HttpMethod.OPTIONS, "/**")
        .permitAll() // allow CORS pre-flighting

        // Allow anyone access to Swagger docs
        .antMatchers(
            HttpMethod.GET,
            "/api-docs",
            "/swagger-resources/**",
            "/swagger-resources/**",
            "/swagger-ui.html**",
            "/webjars/**",
            "/favicon.ico")
        .permitAll()

        // Lock down everything else
        .anyRequest()
        .authenticated();

    // Add our custom JWT security filter before Spring Security's Username/Password filter
    httpSecurity.addFilterBefore(
        authenticationTokenFilterBean(), UsernamePasswordAuthenticationFilter.class);

    // Disable page caching in the browser
    httpSecurity.headers().cacheControl().disable();
  }

  // --------------------------------------------------------------------------
  // TODO: Uncomment bean to redirect HTTP connections to HTTPS in Production
  // --------------------------------------------------------------------------

  //    @Bean
  //    public EmbeddedServletContainerFactory servletContainer() {
  //
  //        final TomcatEmbeddedServletContainerFactory tomcat = new
  // TomcatEmbeddedServletContainerFactory() {
  //
  //            @Override
  //            protected void postProcessContext(Context context) {
  //                final SecurityConstraint securityConstraint = new SecurityConstraint();
  //                securityConstraint.setUserConstraint("CONFIDENTIAL");
  //                final SecurityCollection collection = new SecurityCollection();
  //                collection.addPattern("/*");
  //                securityConstraint.addCollection(collection);
  //                context.addConstraint(securityConstraint);
  //            }
  //        };
  //
  //        tomcat.addAdditionalTomcatConnectors(initiateHttpConnector());
  //        return tomcat;
  //    }
  //
  //    private Connector initiateHttpConnector() {
  //        final Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
  //        connector.setScheme("http");
  //        connector.setPort(8080); // TODO: get ports from config
  //        connector.setSecure(false);
  //        connector.setRedirectPort(8443);
  //        return connector;
  //    }
}
