package com.seatsure.common;

import com.seatsure.config.SeatSureProperties;
import com.seatsure.event.EventDtos.CreateEventRequest;
import com.seatsure.event.EventRepository;
import com.seatsure.event.EventService;
import com.seatsure.user.Role;
import com.seatsure.user.User;
import com.seatsure.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Creates the admin account and two demo events on first start. Safe to run on every start. */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final EventRepository events;
    private final EventService eventService;
    private final PasswordEncoder passwordEncoder;
    private final SeatSureProperties props;

    public DataSeeder(UserRepository users, EventRepository events, EventService eventService,
                      PasswordEncoder passwordEncoder, SeatSureProperties props) {
        this.users = users;
        this.events = events;
        this.eventService = eventService;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String adminEmail = props.admin().email().toLowerCase();
        if (!users.existsByEmailIgnoreCase(adminEmail)) {
            users.save(new User(adminEmail, passwordEncoder.encode(props.admin().password()), "Admin", Role.ADMIN));
            log.info("Created admin account {}", adminEmail);
        }
        if (props.seedDemoEvent() && events.count() == 0) {
            Instant base = Instant.now().plus(Duration.ofDays(21)).truncatedTo(ChronoUnit.HOURS);
            eventService.create(new CreateEventRequest(
                    "Indie Night Live", "Marina Arena, Chennai", base,
                    "An evening of live indie music. Seats sell out fast.",
                    10, 16, new BigDecimal("4500"), new BigDecimal("2500"), new BigDecimal("1200")));
            eventService.create(new CreateEventRequest(
                    "Tech Leaders Summit 2026", "Lakeside Convention Hall, Bengaluru",
                    base.plus(Duration.ofDays(14)),
                    "Talks on distributed systems, AI engineering and platform teams.",
                    8, 12, new BigDecimal("3000"), new BigDecimal("1800"), new BigDecimal("900")));
            log.info("Seeded demo events");
        }
    }
}
