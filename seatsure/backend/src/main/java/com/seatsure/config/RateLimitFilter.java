package com.seatsure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user token bucket on seat-hold requests, so one client (or bot) can't
 * lock up the inventory. In-memory on purpose: with several instances you
 * would move the bucket to Redis (e.g. Bucket4j + Redis) — see README.
 * Not a @Component: it is added only to the security chain (after JWT auth),
 * never as a plain servlet filter.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final int holdCapacity;
    private final int aiCapacity;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(SeatSureProperties props) {
        this.holdCapacity = props.rateLimit().holdsPerMinute();
        // AI calls cost real money per token, so they get their own, tighter bucket.
        this.aiCapacity = props.ai().requestsPerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || limitFor(request.getRequestURI()) == 0;
    }

    private int limitFor(String uri) {
        if (uri.equals("/api/holds")) return holdCapacity;
        if (uri.startsWith("/api/ai/") || uri.startsWith("/api/admin/ai/")) return aiCapacity;
        return 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String uri = req.getRequestURI();
        int capacity = limitFor(uri);
        String scope = uri.equals("/api/holds") ? "hold" : "ai";
        String key = scope + ":" + (auth != null ? auth.getName() : req.getRemoteAddr());
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        if (bucket.tryConsume(capacity, capacity / 60_000_000_000.0)) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", "10");
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\","
                    + "\"detail\":\"Too many requests. Please wait a few seconds.\"}");
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill = System.nanoTime();

        Bucket(int capacity) { this.tokens = capacity; }

        synchronized boolean tryConsume(int capacity, double refillPerNano) {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}
