package com.edf.teamedf.common.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretBase64;

    @Value("${jwt.access-exp}")
    private long accessExpSeconds;

    @Value("${jwt.refresh-exp}")
    private long refreshExpSeconds;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
    }

    public String createAccessToken(String uuid, Map<String, Object> claims) {
        return createToken(uuid, claims, accessExpSeconds);
    }

    public String createRefreshToken(String uuid) {
        return createToken(uuid, Map.of("typ", "refresh"), refreshExpSeconds);
    }

    private String createToken(String subject, Map<String, Object> claims, long expSec) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expSec)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validate(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

            return true;

        } catch (io.jsonwebtoken.security.SignatureException |
                 MalformedJwtException |
                 UnsupportedJwtException |
                 IllegalArgumentException e) {
            return false;

        } catch (ExpiredJwtException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }


    public String getSubject(String token) {
        return getClaims(token).getSubject();
    }

    public long getRefreshExpSeconds() {
        return refreshExpSeconds;
    }
}
